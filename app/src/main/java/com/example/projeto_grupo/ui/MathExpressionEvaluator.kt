package com.example.projeto_grupo.ui

import com.example.projeto_grupo.data.model.PointOfInterest
import com.example.projeto_grupo.data.model.PointType
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

object MathExpressionEvaluator {

    fun evaluate(
        expression: String,
        x: Double,
        z: Double,
        a: Double,
        b: Double,
        c: Double
    ): Double {
        val tokens = tokenize(expression)
        if (tokens.isEmpty()) return Double.NaN
        val parser = Parser(
            tokens = tokens,
            variables = mapOf(
                "x" to x,
                "z" to z,
                "y" to z,
                "a" to a,
                "b" to b,
                "c" to c,
                "pi" to PI,
                "e" to Math.E
            )
        )
        val result = parser.parseExpression()
        if (parser.hasRemaining()) {
            throw IllegalArgumentException("Token inesperado perto de '${parser.peek()}'")
        }
        return result
    }

    fun validate(expression: String, a: Double, b: Double, c: Double): String? {
        if (expression.isBlank()) return "A expressão não pode estar vazia."
        return try {
            val test = evaluate(expression, 0.5, 0.5, a, b, c)
            if (test.isNaN()) "Expressão matemática inválida." else null
        } catch (e: Exception) {
            e.message ?: "Erro de sintaxe na expressão."
        }
    }

    fun computePointsOfInterest(
        expression: String,
        a: Double,
        b: Double,
        c: Double
    ): List<PointOfInterest> {
        val minCoord = -2.0
        val maxCoord = 2.0

        // Check if the function varies along Z (3D surface) or only along X (2D curve)
        val zTest1 = runCatching { evaluate(expression, 0.35, -0.8, a, b, c) }.getOrDefault(0.0)
        val zTest2 = runCatching { evaluate(expression, 0.35, 0.8, a, b, c) }.getOrDefault(0.0)
        val variesInZ = abs(zTest1 - zTest2) > 1e-6

        val maxima = mutableListOf<PointOfInterest>()
        val minima = mutableListOf<PointOfInterest>()
        val zeros = mutableListOf<PointOfInterest>()
        var idCounter = 1
        val eps = 1e-7

        if (!variesInZ) {
            // 2D function f(x) evaluated along the central plane z = 0.0
            val steps = 80
            val stepSize = (maxCoord - minCoord) / steps
            val values = DoubleArray(steps + 1) { ix ->
                val x = minCoord + ix * stepSize
                runCatching { evaluate(expression, x, 0.0, a, b, c) }.getOrDefault(Double.NaN)
            }

            for (ix in 1 until steps) {
                val prev = values[ix - 1]
                val curr = values[ix]
                val next = values[ix + 1]
                if (!prev.isFinite() || !curr.isFinite() || !next.isFinite()) continue

                val x = minCoord + ix * stepSize

                if (curr > prev + eps && curr >= next - eps && isFarEnough(maxima, x, 0.0, 0.25)) {
                    maxima += PointOfInterest(
                        id = "poi_${idCounter++}",
                        type = PointType.MAXIMA,
                        x = round2(x),
                        y = round2(curr),
                        z = 0.0,
                        label = "Máx (${round2(x)}, 0.0) = ${round2(curr)}"
                    )
                } else if (curr < prev - eps && curr <= next + eps && isFarEnough(minima, x, 0.0, 0.25)) {
                    minima += PointOfInterest(
                        id = "poi_${idCounter++}",
                        type = PointType.MINIMA,
                        x = round2(x),
                        y = round2(curr),
                        z = 0.0,
                        label = "Mín (${round2(x)}, 0.0) = ${round2(curr)}"
                    )
                }

                if ((abs(curr) < 0.05 || (prev > 0.0 && curr < 0.0) || (prev < 0.0 && curr > 0.0)) &&
                    isFarEnough(zeros, x, 0.0, 0.25)
                ) {
                    zeros += PointOfInterest(
                        id = "poi_${idCounter++}",
                        type = PointType.ZERO,
                        x = round2(x),
                        y = 0.0,
                        z = 0.0,
                        label = "Zero (${round2(x)}, 0.0)"
                    )
                }
            }
        } else {
            // 3D surface f(x, z)
            val steps = 32
            val stepSize = (maxCoord - minCoord) / steps
            val grid = Array(steps + 1) { ix ->
                val x = minCoord + ix * stepSize
                DoubleArray(steps + 1) { iz ->
                    val z = minCoord + iz * stepSize
                    runCatching { evaluate(expression, x, z, a, b, c) }.getOrDefault(Double.NaN)
                }
            }

            for (ix in 1 until steps) {
                for (iz in 1 until steps) {
                    val center = grid[ix][iz]
                    if (!center.isFinite()) continue

                    val x = minCoord + ix * stepSize
                    val z = minCoord + iz * stepSize

                    var isMaxCandidate = true
                    var strictlyGreaterThanOne = false
                    var isMinCandidate = true
                    var strictlyLessThanOne = false
                    var crossesZero = false

                    for (dx in -1..1) {
                        for (dz in -1..1) {
                            if (dx == 0 && dz == 0) continue
                            val neighbor = grid[ix + dx][iz + dz]
                            if (!neighbor.isFinite()) {
                                isMaxCandidate = false
                                isMinCandidate = false
                                continue
                            }
                            if (neighbor > center + eps) isMaxCandidate = false
                            if (center > neighbor + eps) strictlyGreaterThanOne = true

                            if (neighbor < center - eps) isMinCandidate = false
                            if (center < neighbor - eps) strictlyLessThanOne = true

                            if ((center > 0.0 && neighbor < 0.0) || (center < 0.0 && neighbor > 0.0)) {
                                crossesZero = true
                            }
                        }
                    }

                    if (isMaxCandidate && strictlyGreaterThanOne && isFarEnough(maxima, x, z, 0.32)) {
                        maxima += PointOfInterest(
                            id = "poi_${idCounter++}",
                            type = PointType.MAXIMA,
                            x = round2(x),
                            y = round2(center),
                            z = round2(z),
                            label = "Máx (${round2(x)}, ${round2(z)}) = ${round2(center)}"
                        )
                    } else if (isMinCandidate && strictlyLessThanOne && isFarEnough(minima, x, z, 0.32)) {
                        minima += PointOfInterest(
                            id = "poi_${idCounter++}",
                            type = PointType.MINIMA,
                            x = round2(x),
                            y = round2(center),
                            z = round2(z),
                            label = "Mín (${round2(x)}, ${round2(z)}) = ${round2(center)}"
                        )
                    } else if ((abs(center) < 0.05 || crossesZero) && isFarEnough(zeros, x, z, 0.45)) {
                        zeros += PointOfInterest(
                            id = "poi_${idCounter++}",
                            type = PointType.ZERO,
                            x = round2(x),
                            y = 0.0,
                            z = round2(z),
                            label = "Zero (${round2(x)}, ${round2(z)})"
                        )
                    }
                }
            }
        }

        // Guarantee Maxima, Minima, and Zeros each get dedicated slots
        return maxima.sortedByDescending { it.y }.take(8) +
                minima.sortedBy { it.y }.take(8) +
                zeros.take(8)
    }

    private fun isFarEnough(existing: List<PointOfInterest>, x: Double, z: Double, minDist: Double): Boolean {
        val minDistSq = minDist * minDist
        return existing.none { poi ->
            val dx = poi.x - x
            val dz = poi.z - z
            (dx * dx + dz * dz) < minDistSq
        }
    }

    private fun round2(v: Double): Double = round(v * 100.0) / 100.0

    private sealed interface Token {
        data class Number(val value: Double) : Token
        data class Identifier(val name: String) : Token
        data class Operator(val op: Char) : Token
        data object LParen : Token
        data object RParen : Token
    }

    private fun tokenize(input: String): List<Token> {
        val raw = mutableListOf<Token>()
        var i = 0
        val s = input.trim()
        while (i < s.length) {
            val ch = s[i]
            when {
                ch.isWhitespace() -> i++
                ch.isDigit() || ch == '.' -> {
                    val start = i
                    while (i < s.length && (s[i].isDigit() || s[i] == '.')) i++
                    val num = s.substring(start, i).toDoubleOrNull()
                        ?: throw IllegalArgumentException("Número inválido: '${s.substring(start, i)}'")
                    raw += Token.Number(num)
                }
                ch.isLetter() -> {
                    val start = i
                    while (i < s.length && (s[i].isLetterOrDigit() || s[i] == '_')) i++
                    raw += Token.Identifier(s.substring(start, i).lowercase())
                }
                ch == '(' -> {
                    raw += Token.LParen
                    i++
                }
                ch == ')' -> {
                    raw += Token.RParen
                    i++
                }
                ch in charArrayOf('+', '-', '*', '/', '^') -> {
                    raw += Token.Operator(ch)
                    i++
                }
                else -> throw IllegalArgumentException("Carácter inválido: '$ch'")
            }
        }

        val result = mutableListOf<Token>()
        for (idx in raw.indices) {
            val curr = raw[idx]
            result += curr
            if (idx + 1 < raw.size) {
                val next = raw[idx + 1]
                val currEndsValue = curr is Token.Number || curr is Token.RParen ||
                        (curr is Token.Identifier && !isFunctionName(curr.name))
                val nextStartsValue = next is Token.Number || next is Token.LParen || next is Token.Identifier
                if (currEndsValue && nextStartsValue) {
                    result += Token.Operator('*')
                }
            }
        }
        return result
    }

    private fun isFunctionName(name: String): Boolean =
        name in setOf("sin", "cos", "tan", "sqrt", "abs", "exp", "ln", "log")

    private class Parser(
        private val tokens: List<Token>,
        private val variables: Map<String, Double>
    ) {
        private var pos = 0

        fun hasRemaining(): Boolean = pos < tokens.size
        fun peek(): Token? = tokens.getOrNull(pos)

        fun parseExpression(): Double {
            var left = parseTerm()
            while (pos < tokens.size) {
                val tok = tokens[pos]
                if (tok is Token.Operator && (tok.op == '+' || tok.op == '-')) {
                    pos++
                    val right = parseTerm()
                    left = if (tok.op == '+') left + right else left - right
                } else {
                    break
                }
            }
            return left
        }

        private fun parseTerm(): Double {
            var left = parsePower()
            while (pos < tokens.size) {
                val tok = tokens[pos]
                if (tok is Token.Operator && (tok.op == '*' || tok.op == '/')) {
                    pos++
                    val right = parsePower()
                    left = if (tok.op == '*') left * right else left / right
                } else {
                    break
                }
            }
            return left
        }

        private fun parsePower(): Double {
            val base = parseUnary()
            val tok = tokens.getOrNull(pos)
            if (tok is Token.Operator && tok.op == '^') {
                pos++
                val exponent = parsePower()
                return base.pow(exponent)
            }
            return base
        }

        private fun parseUnary(): Double {
            val tok = tokens.getOrNull(pos)
            if (tok is Token.Operator && (tok.op == '+' || tok.op == '-')) {
                pos++
                val operand = parseUnary()
                return if (tok.op == '-') -operand else operand
            }
            return parsePrimary()
        }

        private fun parsePrimary(): Double {
            val tok = tokens.getOrNull(pos)
                ?: throw IllegalArgumentException("Fim inesperado da expressão.")

            return when (tok) {
                is Token.Number -> {
                    pos++
                    tok.value
                }
                is Token.LParen -> {
                    pos++
                    val inner = parseExpression()
                    if (tokens.getOrNull(pos) != Token.RParen) {
                        throw IllegalArgumentException("Parêntese ')' em falta.")
                    }
                    pos++
                    inner
                }
                is Token.Identifier -> {
                    pos++
                    if (isFunctionName(tok.name)) {
                        if (tokens.getOrNull(pos) != Token.LParen) {
                            throw IllegalArgumentException("Esperado '(' após '${tok.name}'.")
                        }
                        pos++
                        val arg = parseExpression()
                        if (tokens.getOrNull(pos) != Token.RParen) {
                            throw IllegalArgumentException("Parêntese ')' em falta após '${tok.name}(...)'.")
                        }
                        pos++
                        applyFunction(tok.name, arg)
                    } else {
                        variables[tok.name]
                            ?: throw IllegalArgumentException("Variável desconhecida: '${tok.name}'. Use x, z, a, b, c.")
                    }
                }
                else -> throw IllegalArgumentException("Token inesperado: $tok")
            }
        }

        private fun applyFunction(name: String, arg: Double): Double = when (name) {
            "sin" -> sin(arg)
            "cos" -> cos(arg)
            "tan" -> tan(arg)
            "sqrt" -> if (arg < 0.0) Double.NaN else sqrt(arg)
            "abs" -> abs(arg)
            "exp" -> exp(arg)
            "ln" -> if (arg <= 0.0) Double.NaN else ln(arg)
            "log" -> if (arg <= 0.0) Double.NaN else log10(arg)
            else -> throw IllegalArgumentException("Função desconhecida: $name")
        }
    }
}