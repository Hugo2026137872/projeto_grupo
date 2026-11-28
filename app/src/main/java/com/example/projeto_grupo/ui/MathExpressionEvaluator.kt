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

    private data class RawExtremum(
        val x: Double,
        val y: Double,
        val z: Double,
        val isBoundary: Boolean
    )

    fun extractParameters(expression: String): List<String> {
        val tokens = runCatching { tokenize(expression) }.getOrNull() ?: return emptyList()
        val params = mutableSetOf<String>()
        for (tok in tokens) {
            if (tok is Token.Identifier) {
                val name = tok.name.lowercase()
                if (!isFunctionName(name) && name !in setOf("x", "y", "z", "pi", "e")) {
                    params.add(name)
                }
            }
        }
        return params.sorted()
    }

    fun compile(expression: String): CompiledExpression {
        val tokens = tokenize(expression)
        if (tokens.isEmpty()) {
            return CompiledExpression(ExprNode.Const(Double.NaN))
        }
        val parser = Parser(tokens = tokens)
        val root = parser.parseExpression()
        if (parser.hasRemaining()) {
            throw IllegalArgumentException("Token inesperado perto de '${parser.peek()}'")
        }
        return CompiledExpression(root)
    }

    fun evaluate(
        expression: String,
        x: Double,
        y: Double,
        params: Map<String, Double>
    ): Double = compile(expression).evaluate(x, y, params)

    class CompiledExpression internal constructor(
        private val root: ExprNode
    ) {
        fun evaluate(
            x: Double,
            y: Double,
            params: Map<String, Double> = emptyMap()
        ): Double = evalNode(root, x, y, params)

        private fun evalNode(
            node: ExprNode,
            x: Double,
            y: Double,
            params: Map<String, Double>
        ): Double = when (node) {
            is ExprNode.Const -> node.value
            is ExprNode.Var -> when (node.name) {
                "x" -> x
                "y" -> y
                "pi" -> PI
                "e" -> Math.E
                else -> params[node.name] ?: 1.0
            }
            is ExprNode.UnaryMinus -> -evalNode(node.operand, x, y, params)
            is ExprNode.BinaryOp -> {
                val l = evalNode(node.left, x, y, params)
                val r = evalNode(node.right, x, y, params)
                when (node.op) {
                    '+' -> l + r
                    '-' -> l - r
                    '*' -> l * r
                    '/' -> l / r
                    '^' -> l.pow(r)
                    else -> Double.NaN
                }
            }
            is ExprNode.FuncCall -> {
                val arg = evalNode(node.arg, x, y, params)
                applyFunction(node.name, arg)
            }
        }
    }

    internal sealed interface ExprNode {
        data class Const(val value: Double) : ExprNode
        data class Var(val name: String) : ExprNode
        data class UnaryMinus(val operand: ExprNode) : ExprNode
        data class BinaryOp(val op: Char, val left: ExprNode, val right: ExprNode) : ExprNode
        data class FuncCall(val name: String, val arg: ExprNode) : ExprNode
    }

    fun evaluate(
        expression: String,
        x: Double,
        y: Double,
        a: Double = 1.0,
        b: Double = 1.0,
        c: Double = 0.0
    ): Double = evaluate(expression, x, y, mapOf("a" to a, "b" to b, "c" to c))

    fun validate(expression: String, params: Map<String, Double>): String? {
        if (expression.isBlank()) return "A expressão não pode estar vazia."
        return try {
            val test = evaluate(expression, 0.5, 0.5, params)
            if (test.isNaN()) "Expressão matemática inválida." else null
        } catch (e: Exception) {
            e.message ?: "Erro de sintaxe na expressão."
        }
    }

    fun validate(expression: String, a: Double = 1.0, b: Double = 1.0, c: Double = 0.0): String? =
        validate(expression, mapOf("a" to a, "b" to b, "c" to c))

    /**
     * Computes Points of Interest for z = f(x, y) on [-xDomain, xDomain] x [-yDomain, yDomain].
     * PointOfInterest stores (x = mathX, y = mathY, z = f(x, y)).
     */
    fun computePointsOfInterest(
        expression: String,
        params: Map<String, Double>,
        xDomain: Double = 2.0,
        yDomain: Double = 2.0
    ): List<PointOfInterest> {
        val minX = -xDomain
        val maxX = xDomain
        val minY = -yDomain
        val maxY = yDomain
        val eps = 1e-7
        val minDist1D = (xDomain * 0.125).coerceAtLeast(0.08)
        val minDist2D = (maxOf(xDomain, yDomain) * 0.18).coerceAtLeast(0.12)
        val minDistZero2D = (maxOf(xDomain, yDomain) * 0.22).coerceAtLeast(0.15)

        val compiled = runCatching { compile(expression) }.getOrNull() ?: return emptyList()
        val normalizedParams = params.mapKeys { it.key.lowercase() }

        val yTest1 = runCatching { compiled.evaluate(xDomain * 0.175, -yDomain * 0.4, normalizedParams) }.getOrDefault(0.0)
        val yTest2 = runCatching { compiled.evaluate(xDomain * 0.175, yDomain * 0.4, normalizedParams) }.getOrDefault(0.0)
        val variesInY = abs(yTest1 - yTest2) > 1e-6

        val maxima = mutableListOf<PointOfInterest>()
        val minima = mutableListOf<PointOfInterest>()
        val zeros = mutableListOf<PointOfInterest>()
        var idCounter = 1

        if (!variesInY) {
            val steps = 160
            val stepSize = (maxX - minX) / steps
            val values = DoubleArray(steps + 1) { ix ->
                val x = minX + ix * stepSize
                runCatching { compiled.evaluate(x, 0.0, normalizedParams) }.getOrDefault(Double.NaN)
            }

            val finiteValues = values.filter { it.isFinite() }
            if (finiteValues.isEmpty()) return emptyList()
            val globalRange = (finiteValues.maxOrNull() ?: 0.0) - (finiteValues.minOrNull() ?: 0.0)
            if (globalRange <= 1e-6) return emptyList()

            val interiorMaxCandidates = mutableListOf<RawExtremum>()
            val interiorMinCandidates = mutableListOf<RawExtremum>()

            for (ix in 1 until steps) {
                val prev = values[ix - 1]
                val curr = values[ix]
                val next = values[ix + 1]
                if (!prev.isFinite() || !curr.isFinite() || !next.isFinite()) continue

                val x = minX + ix * stepSize

                if (curr >= prev - eps && curr >= next - eps &&
                    (curr > prev + eps || curr > next + eps)
                ) {
                    interiorMaxCandidates += RawExtremum(x, 0.0, curr, isBoundary = false)
                } else if (curr <= prev + eps && curr <= next + eps &&
                    (curr < prev - eps || curr < next - eps)
                ) {
                    interiorMinCandidates += RawExtremum(x, 0.0, curr, isBoundary = false)
                }

                if ((abs(curr) < 0.035 || (prev > 0.0 && curr < 0.0) || (prev < 0.0 && curr > 0.0)) &&
                    isFarEnough(zeros, x, 0.0, minDist1D)
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

            interiorMaxCandidates.sortByDescending { it.z }
            for (cand in interiorMaxCandidates) {
                if (isFarEnough(maxima, cand.x, cand.y, minDist1D)) {
                    maxima += PointOfInterest(
                        id = "poi_${idCounter++}",
                        type = PointType.MAXIMA,
                        x = round2(cand.x),
                        y = 0.0,
                        z = round2(cand.z),
                        label = "Máx (${round2(cand.x)}, 0.0) = ${round2(cand.z)}"
                    )
                }
            }

            interiorMinCandidates.sortBy { it.z }
            for (cand in interiorMinCandidates) {
                if (isFarEnough(minima, cand.x, cand.y, minDist1D)) {
                    minima += PointOfInterest(
                        id = "poi_${idCounter++}",
                        type = PointType.MINIMA,
                        x = round2(cand.x),
                        y = 0.0,
                        z = round2(cand.z),
                        label = "Mín (${round2(cand.x)}, 0.0) = ${round2(cand.z)}"
                    )
                }
            }

            val boundaryMaxCandidates = mutableListOf<RawExtremum>()
            val boundaryMinCandidates = mutableListOf<RawExtremum>()

            if (values[0].isFinite() && values[1].isFinite()) {
                if (values[0] > values[1] + eps) {
                    boundaryMaxCandidates += RawExtremum(minX, 0.0, values[0], isBoundary = true)
                } else if (values[0] < values[1] - eps) {
                    boundaryMinCandidates += RawExtremum(minX, 0.0, values[0], isBoundary = true)
                }
            }
            if (values[steps].isFinite() && values[steps - 1].isFinite()) {
                if (values[steps] > values[steps - 1] + eps) {
                    boundaryMaxCandidates += RawExtremum(maxX, 0.0, values[steps], isBoundary = true)
                } else if (values[steps] < values[steps - 1] - eps) {
                    boundaryMinCandidates += RawExtremum(maxX, 0.0, values[steps], isBoundary = true)
                }
            }

            val bestInteriorMaxZ = maxima.maxOfOrNull { it.z } ?: Double.NEGATIVE_INFINITY
            boundaryMaxCandidates.sortByDescending { it.z }
            for (cand in boundaryMaxCandidates) {
                if ((maxima.isEmpty() || cand.z > bestInteriorMaxZ + eps) &&
                    isFarEnough(maxima, cand.x, cand.y, minDist1D)
                ) {
                    maxima += PointOfInterest(
                        id = "poi_${idCounter++}",
                        type = PointType.MAXIMA,
                        x = round2(cand.x),
                        y = 0.0,
                        z = round2(cand.z),
                        label = "Máx (borda) (${round2(cand.x)}, 0.0) = ${round2(cand.z)}"
                    )
                }
            }

            val bestInteriorMinZ = minima.minOfOrNull { it.z } ?: Double.POSITIVE_INFINITY
            boundaryMinCandidates.sortBy { it.z }
            for (cand in boundaryMinCandidates) {
                if ((minima.isEmpty() || cand.z < bestInteriorMinZ - eps) &&
                    isFarEnough(minima, cand.x, cand.y, minDist1D)
                ) {
                    minima += PointOfInterest(
                        id = "poi_${idCounter++}",
                        type = PointType.MINIMA,
                        x = round2(cand.x),
                        y = 0.0,
                        z = round2(cand.z),
                        label = "Mín (borda) (${round2(cand.x)}, 0.0) = ${round2(cand.z)}"
                    )
                }
            }
        } else {
            val steps = 80
            val stepX = (maxX - minX) / steps
            val stepY = (maxY - minY) / steps
            var globalMin = Double.POSITIVE_INFINITY
            var globalMax = Double.NEGATIVE_INFINITY

            val grid = Array(steps + 1) { ix ->
                val x = minX + ix * stepX
                DoubleArray(steps + 1) { iy ->
                    val y = minY + iy * stepY
                    val v = runCatching { compiled.evaluate(x, y, normalizedParams) }.getOrDefault(Double.NaN)
                    if (v.isFinite()) {
                        if (v < globalMin) globalMin = v
                        if (v > globalMax) globalMax = v
                    }
                    v
                }
            }

            if (!globalMin.isFinite() || !globalMax.isFinite() || (globalMax - globalMin) <= 1e-6) {
                return emptyList()
            }

            val interiorMaxCandidates = mutableListOf<RawExtremum>()
            val interiorMinCandidates = mutableListOf<RawExtremum>()
            val zeroCandidates = mutableListOf<Pair<Double, Double>>()

            val axes = arrayOf(
                intArrayOf(1, 0),
                intArrayOf(0, 1),
                intArrayOf(1, 1),
                intArrayOf(1, -1)
            )

            for (ix in 1 until steps) {
                for (iy in 1 until steps) {
                    val center = grid[ix][iy]
                    if (!center.isFinite()) continue

                    val x = minX + ix * stepX
                    val y = minY + iy * stepY

                    var isFullMaxCandidate = true
                    var strictlyGreaterThanOne = false
                    var isFullMinCandidate = true
                    var strictlyLessThanOne = false
                    var crossesZero = false

                    for (dx in -1..1) {
                        for (dy in -1..1) {
                            if (dx == 0 && dy == 0) continue
                            val neighbor = grid[ix + dx][iy + dy]
                            if (!neighbor.isFinite()) {
                                isFullMaxCandidate = false
                                isFullMinCandidate = false
                                continue
                            }
                            if (neighbor > center + eps) isFullMaxCandidate = false
                            if (center > neighbor + eps) strictlyGreaterThanOne = true

                            if (neighbor < center - eps) isFullMinCandidate = false
                            if (center < neighbor - eps) strictlyLessThanOne = true

                            if ((center > 0.0 && neighbor < 0.0) || (center < 0.0 && neighbor > 0.0)) {
                                crossesZero = true
                            }
                        }
                    }

                    var peakAxesCount = 0
                    var valleyAxesCount = 0
                    for (axis in axes) {
                        val aVal = grid[ix + axis[0]][iy + axis[1]]
                        val bVal = grid[ix - axis[0]][iy - axis[1]]
                        if (!aVal.isFinite() || !bVal.isFinite()) continue
                        if (center > aVal + eps && center > bVal + eps) peakAxesCount++
                        if (center < aVal - eps && center < bVal - eps) valleyAxesCount++
                    }

                    val isPointOrRidgeMax = (isFullMaxCandidate && strictlyGreaterThanOne) ||
                            (peakAxesCount >= 2 && valleyAxesCount == 0)
                    val isPointOrRidgeMin = (isFullMinCandidate && strictlyLessThanOne) ||
                            (valleyAxesCount >= 2 && peakAxesCount == 0)

                    if (isPointOrRidgeMax) {
                        interiorMaxCandidates += RawExtremum(x, y, center, isBoundary = false)
                    } else if (isPointOrRidgeMin) {
                        interiorMinCandidates += RawExtremum(x, y, center, isBoundary = false)
                    } else if (abs(center) < 0.035 || crossesZero) {
                        zeroCandidates += Pair(x, y)
                    }
                }
            }

            interiorMaxCandidates.sortByDescending { it.z }
            for (cand in interiorMaxCandidates) {
                if (isFarEnough(maxima, cand.x, cand.y, minDist2D)) {
                    maxima += PointOfInterest(
                        id = "poi_${idCounter++}",
                        type = PointType.MAXIMA,
                        x = round2(cand.x),
                        y = round2(cand.y),
                        z = round2(cand.z),
                        label = "Máx (${round2(cand.x)}, ${round2(cand.y)}) = ${round2(cand.z)}"
                    )
                }
            }

            interiorMinCandidates.sortBy { it.z }
            for (cand in interiorMinCandidates) {
                if (isFarEnough(minima, cand.x, cand.y, minDist2D)) {
                    minima += PointOfInterest(
                        id = "poi_${idCounter++}",
                        type = PointType.MINIMA,
                        x = round2(cand.x),
                        y = round2(cand.y),
                        z = round2(cand.z),
                        label = "Mín (${round2(cand.x)}, ${round2(cand.y)}) = ${round2(cand.z)}"
                    )
                }
            }

            val perimeter = ArrayList<Pair<Int, Int>>(steps * 4)
            for (ix in 0 until steps) perimeter += Pair(ix, 0)
            for (iy in 0 until steps) perimeter += Pair(steps, iy)
            for (ix in steps downTo 1) perimeter += Pair(ix, steps)
            for (iy in steps downTo 1) perimeter += Pair(0, iy)

            val boundaryMaxCandidates = mutableListOf<RawExtremum>()
            val boundaryMinCandidates = mutableListOf<RawExtremum>()
            val pSize = perimeter.size

            for (k in 0 until pSize) {
                val (ix, iy) = perimeter[k]
                val center = grid[ix][iy]
                if (!center.isFinite()) continue

                val (prevIx, prevIy) = perimeter[(k - 1 + pSize) % pSize]
                val (nextIx, nextIy) = perimeter[(k + 1) % pSize]
                val prevVal = grid[prevIx][prevIy]
                val nextVal = grid[nextIx][nextIy]
                if (!prevVal.isFinite() || !nextVal.isFinite()) continue

                val inIx = when (ix) {
                    0 -> 1
                    steps -> steps - 1
                    else -> ix
                }
                val inIy = when (iy) {
                    0 -> 1
                    steps -> steps - 1
                    else -> iy
                }
                val inwardVal = grid[inIx][inIy]
                if (!inwardVal.isFinite()) continue

                val x = minX + ix * stepX
                val y = minY + iy * stepY

                val isPerimeterMax = center >= prevVal - eps && center >= nextVal - eps &&
                        (center > prevVal + eps || center > nextVal + eps) &&
                        center > inwardVal + eps

                val isPerimeterMin = center <= prevVal + eps && center <= nextVal + eps &&
                        (center < prevVal - eps || center < nextVal - eps) &&
                        center < inwardVal - eps

                if (isPerimeterMax) {
                    boundaryMaxCandidates += RawExtremum(x, y, center, isBoundary = true)
                } else if (isPerimeterMin) {
                    boundaryMinCandidates += RawExtremum(x, y, center, isBoundary = true)
                }
            }

            val bestInteriorMaxZ = maxima.maxOfOrNull { it.z } ?: Double.NEGATIVE_INFINITY
            boundaryMaxCandidates.sortByDescending { it.z }
            for (cand in boundaryMaxCandidates) {
                if ((maxima.isEmpty() || cand.z > bestInteriorMaxZ + eps) &&
                    isFarEnough(maxima, cand.x, cand.y, minDist2D)
                ) {
                    maxima += PointOfInterest(
                        id = "poi_${idCounter++}",
                        type = PointType.MAXIMA,
                        x = round2(cand.x),
                        y = round2(cand.y),
                        z = round2(cand.z),
                        label = "Máx (borda) (${round2(cand.x)}, ${round2(cand.y)}) = ${round2(cand.z)}"
                    )
                }
            }

            val bestInteriorMinZ = minima.minOfOrNull { it.z } ?: Double.POSITIVE_INFINITY
            boundaryMinCandidates.sortBy { it.z }
            for (cand in boundaryMinCandidates) {
                if ((minima.isEmpty() || cand.z < bestInteriorMinZ - eps) &&
                    isFarEnough(minima, cand.x, cand.y, minDist2D)
                ) {
                    minima += PointOfInterest(
                        id = "poi_${idCounter++}",
                        type = PointType.MINIMA,
                        x = round2(cand.x),
                        y = round2(cand.y),
                        z = round2(cand.z),
                        label = "Mín (borda) (${round2(cand.x)}, ${round2(cand.y)}) = ${round2(cand.z)}"
                    )
                }
            }

            for ((zx, zy) in zeroCandidates) {
                if (isFarEnough(zeros, zx, zy, minDistZero2D)) {
                    zeros += PointOfInterest(
                        id = "poi_${idCounter++}",
                        type = PointType.ZERO,
                        x = round2(zx),
                        y = round2(zy),
                        z = 0.0,
                        label = "Zero (${round2(zx)}, ${round2(zy)})"
                    )
                }
            }
        }

        return maxima.sortedByDescending { it.z }.take(8) +
                minima.sortedBy { it.z }.take(8) +
                zeros.take(8)
    }

    fun computePointsOfInterest(
        expression: String,
        a: Double = 1.0,
        b: Double = 1.0,
        c: Double = 0.0,
        xDomain: Double = 2.0,
        yDomain: Double = 2.0
    ): List<PointOfInterest> =
        computePointsOfInterest(expression, mapOf("a" to a, "b" to b, "c" to c), xDomain, yDomain)

    private fun isFarEnough(existing: List<PointOfInterest>, x: Double, y: Double, minDist: Double): Boolean {
        val minDistSq = minDist * minDist
        return existing.none { poi ->
            val dx = poi.x - x
            val dy = poi.y - y
            (dx * dx + dy * dy) < minDistSq
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
        private val tokens: List<Token>
    ) {
        private var pos = 0

        fun hasRemaining(): Boolean = pos < tokens.size
        fun peek(): Token? = tokens.getOrNull(pos)

        fun parseExpression(): ExprNode {
            var left = parseTerm()
            while (pos < tokens.size) {
                val tok = tokens[pos]
                if (tok is Token.Operator && (tok.op == '+' || tok.op == '-')) {
                    pos++
                    val right = parseTerm()
                    left = ExprNode.BinaryOp(tok.op, left, right)
                } else {
                    break
                }
            }
            return left
        }

        private fun parseTerm(): ExprNode {
            var left = parsePower()
            while (pos < tokens.size) {
                val tok = tokens[pos]
                if (tok is Token.Operator && (tok.op == '*' || tok.op == '/')) {
                    pos++
                    val right = parsePower()
                    left = ExprNode.BinaryOp(tok.op, left, right)
                } else {
                    break
                }
            }
            return left
        }

        private fun parsePower(): ExprNode {
            val base = parseUnary()
            val tok = tokens.getOrNull(pos)
            if (tok is Token.Operator && tok.op == '^') {
                pos++
                val exponent = parsePower()
                return ExprNode.BinaryOp('^', base, exponent)
            }
            return base
        }

        private fun parseUnary(): ExprNode {
            val tok = tokens.getOrNull(pos)
            if (tok is Token.Operator && (tok.op == '+' || tok.op == '-')) {
                pos++
                val operand = parseUnary()
                return if (tok.op == '-') ExprNode.UnaryMinus(operand) else operand
            }
            return parsePrimary()
        }

        private fun parsePrimary(): ExprNode {
            val tok = tokens.getOrNull(pos)
                ?: throw IllegalArgumentException("Fim inesperado da expressão.")

            return when (tok) {
                is Token.Number -> {
                    pos++
                    ExprNode.Const(tok.value)
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
                        ExprNode.FuncCall(tok.name, arg)
                    } else {
                        ExprNode.Var(tok.name)
                    }
                }
                else -> throw IllegalArgumentException("Token inesperado: $tok")
            }
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