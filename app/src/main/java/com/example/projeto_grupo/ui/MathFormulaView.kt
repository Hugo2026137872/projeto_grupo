package com.example.projeto_grupo.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Renders an expression string (e.g. "1/2 * x^2 + sqrt(x^2 + z^2)") using true
 * mathematical typography: stacked fractions, superscript powers, and radical bars.
 */
@Composable
fun MathFormulaView(
    expression: String,
    modifier: Modifier = Modifier,
    prefix: String? = "f(x, z) = ",
    fontSize: TextUnit = 15.sp,
    color: Color = LocalContentColor.current
) {
    val ast = remember(expression) {
        runCatching { MathAstParser.parse(expression) }.getOrNull()
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (!prefix.isNullOrEmpty()) {
            MathSymbolText(
                text = prefix,
                fontSize = fontSize,
                color = color,
                italic = true
            )
        }

        if (ast != null) {
            RenderMathNode(
                node = ast,
                fontSize = fontSize,
                color = color
            )
        } else {
            Text(
                text = expression,
                fontSize = fontSize,
                color = color,
                fontFamily = FontFamily.Serif
            )
        }
    }
}

@Composable
private fun RenderMathNode(
    node: MathAstNode,
    fontSize: TextUnit,
    color: Color
) {
    when (node) {
        is MathAstNode.Number -> {
            MathSymbolText(
                text = node.display,
                fontSize = fontSize,
                color = color,
                italic = false
            )
        }

        is MathAstNode.Symbol -> {
            MathSymbolText(
                text = node.display,
                fontSize = fontSize,
                color = color,
                italic = node.isItalic
            )
        }

        is MathAstNode.UnaryMinus -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MathSymbolText(
                    text = "−",
                    fontSize = fontSize,
                    color = color,
                    italic = false
                )
                RenderMathNode(node = node.operand, fontSize = fontSize, color = color)
            }
        }

        is MathAstNode.BinaryOp -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RenderMathNode(node = node.left, fontSize = fontSize, color = color)
                when (node.op) {
                    "+" -> MathSymbolText(" + ", fontSize, color, italic = false)
                    "-" -> MathSymbolText(" − ", fontSize, color, italic = false)
                    "*" -> {
                        if (node.explicitDot) {
                            MathSymbolText(" · ", fontSize, color, italic = false)
                        } else {
                            Spacer(modifier = Modifier.width(1.5.dp))
                        }
                    }
                }
                RenderMathNode(node = node.right, fontSize = fontSize, color = color)
            }
        }

        is MathAstNode.Fraction -> {
            val childFontSize = (fontSize.value * 0.82f).coerceAtLeast(9f).sp
            Column(
                modifier = Modifier
                    .width(IntrinsicSize.Max)
                    .padding(horizontal = 3.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 3.dp),
                    contentAlignment = Alignment.Center
                ) {
                    RenderMathNode(node = node.numerator, fontSize = childFontSize, color = color)
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 1.5.dp)
                        .height(1.2.dp)
                        .background(color)
                )
                Box(
                    modifier = Modifier.padding(horizontal = 3.dp),
                    contentAlignment = Alignment.Center
                ) {
                    RenderMathNode(node = node.denominator, fontSize = childFontSize, color = color)
                }
            }
        }

        is MathAstNode.Power -> {
            val expFontSize = (fontSize.value * 0.72f).coerceAtLeast(8.5f).sp
            Row(verticalAlignment = Alignment.Top) {
                Box(modifier = Modifier.padding(top = (fontSize.value * 0.22f).dp)) {
                    RenderMathNode(node = node.base, fontSize = fontSize, color = color)
                }
                Box(modifier = Modifier.padding(start = 1.dp, bottom = (fontSize.value * 0.25f).dp)) {
                    RenderMathNode(node = node.exponent, fontSize = expFontSize, color = color)
                }
            }
        }

        is MathAstNode.Sqrt -> {
            Row(
                modifier = Modifier
                    .height(IntrinsicSize.Max)
                    .padding(horizontal = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Vector radical sign matching the exact height of the inner expression
                Canvas(
                    modifier = Modifier
                        .width(11.dp)
                        .fillMaxHeight()
                ) {
                    val w = size.width
                    val h = size.height
                    val strokePx = 1.35.dp.toPx()
                    val topY = strokePx * 0.8f
                    val path = Path().apply {
                        moveTo(0f, h * 0.56f)
                        lineTo(w * 0.28f, h * 0.50f)
                        lineTo(w * 0.56f, h - strokePx)
                        lineTo(w, topY)
                    }
                    drawPath(
                        path = path,
                        color = color,
                        style = Stroke(
                            width = strokePx,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                }

                // Overbar (vinculum) connected to the radical tip
                Column(modifier = Modifier.width(IntrinsicSize.Max)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.35.dp)
                            .background(color)
                    )
                    Box(modifier = Modifier.padding(start = 2.dp, end = 3.dp, top = 1.dp)) {
                        RenderMathNode(node = node.radicand, fontSize = fontSize, color = color)
                    }
                }
            }
        }

        is MathAstNode.Abs -> {
            Row(
                modifier = Modifier
                    .height(IntrinsicSize.Max)
                    .padding(horizontal = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .width(1.3.dp)
                        .fillMaxHeight()
                        .padding(vertical = 1.dp)
                        .background(color)
                )
                Box(modifier = Modifier.padding(horizontal = 3.dp)) {
                    RenderMathNode(node = node.inner, fontSize = fontSize, color = color)
                }
                Box(
                    modifier = Modifier
                        .width(1.3.dp)
                        .fillMaxHeight()
                        .padding(vertical = 1.dp)
                        .background(color)
                )
            }
        }

        is MathAstNode.FunctionCall -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MathSymbolText(
                    text = node.name,
                    fontSize = fontSize,
                    color = color,
                    italic = false
                )
                ScalingParenGroup(
                    inner = node.argument,
                    fontSize = fontSize,
                    color = color
                )
            }
        }

        is MathAstNode.ParenGroup -> {
            ScalingParenGroup(
                inner = node.inner,
                fontSize = fontSize,
                color = color
            )
        }
    }
}

@Composable
private fun ScalingParenGroup(
    inner: MathAstNode,
    fontSize: TextUnit,
    color: Color
) {
    val isTall = remember(inner) { inner.containsTallStructure() }
    if (!isTall) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MathSymbolText("(", fontSize, color, italic = false)
            RenderMathNode(node = inner, fontSize = fontSize, color = color)
            MathSymbolText(")", fontSize, color, italic = false)
        }
    } else {
        Row(
            modifier = Modifier.height(IntrinsicSize.Max),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CurvedParenCanvas(isLeft = true, color = color)
            Box(modifier = Modifier.padding(horizontal = 1.dp)) {
                RenderMathNode(node = inner, fontSize = fontSize, color = color)
            }
            CurvedParenCanvas(isLeft = false, color = color)
        }
    }
}

@Composable
private fun CurvedParenCanvas(
    isLeft: Boolean,
    color: Color
) {
    Canvas(
        modifier = Modifier
            .width(6.dp)
            .fillMaxHeight()
            .padding(vertical = 1.dp)
    ) {
        val w = size.width
        val h = size.height
        val strokePx = 1.25.dp.toPx()
        val path = Path().apply {
            if (isLeft) {
                moveTo(w * 0.85f, 0f)
                quadraticTo(w * 0.05f, h * 0.5f, w * 0.85f, h)
            } else {
                moveTo(w * 0.15f, 0f)
                quadraticTo(w * 0.95f, h * 0.5f, w * 0.15f, h)
            }
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = strokePx, cap = StrokeCap.Round)
        )
    }
}

@Composable
private fun MathSymbolText(
    text: String,
    fontSize: TextUnit,
    color: Color,
    italic: Boolean
) {
    Text(
        text = text,
        fontSize = fontSize,
        color = color,
        fontFamily = FontFamily.Serif,
        fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        softWrap = false
    )
}

private sealed interface MathAstNode {
    data class Number(val display: String) : MathAstNode
    data class Symbol(val display: String, val isItalic: Boolean) : MathAstNode
    data class UnaryMinus(val operand: MathAstNode) : MathAstNode
    data class BinaryOp(
        val op: String,
        val left: MathAstNode,
        val right: MathAstNode,
        val explicitDot: Boolean = false
    ) : MathAstNode
    data class Fraction(val numerator: MathAstNode, val denominator: MathAstNode) : MathAstNode
    data class Power(val base: MathAstNode, val exponent: MathAstNode) : MathAstNode
    data class Sqrt(val radicand: MathAstNode) : MathAstNode
    data class Abs(val inner: MathAstNode) : MathAstNode
    data class FunctionCall(val name: String, val argument: MathAstNode) : MathAstNode
    data class ParenGroup(val inner: MathAstNode) : MathAstNode

    fun containsTallStructure(): Boolean = when (this) {
        is Fraction, is Sqrt -> true
        is UnaryMinus -> operand.containsTallStructure()
        is BinaryOp -> left.containsTallStructure() || right.containsTallStructure()
        is Power -> base.containsTallStructure()
        is Abs -> inner.containsTallStructure()
        is FunctionCall -> argument.containsTallStructure()
        is ParenGroup -> inner.containsTallStructure()
        else -> false
    }
}

private object MathAstParser {
    private sealed interface Tok {
        data class Num(val text: String) : Tok
        data class Id(val name: String) : Tok
        data class Op(val ch: Char) : Tok
        data object LParen : Tok
        data object RParen : Tok
    }

    private val functions = setOf("sin", "cos", "tan", "sqrt", "abs", "exp", "ln", "log")

    fun parse(expression: String): MathAstNode {
        val tokens = tokenize(expression)
        if (tokens.isEmpty()) return MathAstNode.Symbol("", false)
        val state = ParserState(tokens)
        return state.parseAddSub()
    }

    private fun tokenize(input: String): List<Tok> {
        val raw = mutableListOf<Tok>()
        var i = 0
        val s = input.trim()
        while (i < s.length) {
            val ch = s[i]
            when {
                ch.isWhitespace() -> i++
                ch.isDigit() || ch == '.' -> {
                    val start = i
                    while (i < s.length && (s[i].isDigit() || s[i] == '.')) i++
                    raw += Tok.Num(s.substring(start, i))
                }
                ch.isLetter() -> {
                    val start = i
                    while (i < s.length && (s[i].isLetterOrDigit() || s[i] == '_')) i++
                    raw += Tok.Id(s.substring(start, i))
                }
                ch == '(' -> {
                    raw += Tok.LParen; i++
                }
                ch == ')' -> {
                    raw += Tok.RParen; i++
                }
                ch in charArrayOf('+', '-', '*', '/', '^') -> {
                    raw += Tok.Op(ch); i++
                }
                else -> i++
            }
        }

        // Insert implicit multiplication tokens where needed (e.g. 2x -> 2 * x)
        val out = mutableListOf<Tok>()
        for (idx in raw.indices) {
            val curr = raw[idx]
            out += curr
            if (idx + 1 < raw.size) {
                val next = raw[idx + 1]
                val currEndsVal = curr is Tok.Num || curr is Tok.RParen ||
                        (curr is Tok.Id && curr.name.lowercase() !in functions)
                val nextStartsVal = next is Tok.Num || next is Tok.LParen || next is Tok.Id
                if (currEndsVal && nextStartsVal) {
                    out += Tok.Op('*')
                }
            }
        }
        return out
    }

    private class ParserState(private val tokens: List<Tok>) {
        private var pos = 0

        fun parseAddSub(): MathAstNode {
            var left = parseMulDiv()
            while (pos < tokens.size) {
                val tok = tokens[pos]
                if (tok is Tok.Op && (tok.ch == '+' || tok.ch == '-')) {
                    pos++
                    val right = parseMulDiv()
                    left = MathAstNode.BinaryOp(tok.ch.toString(), left, right)
                } else {
                    break
                }
            }
            return left
        }

        private fun parseMulDiv(): MathAstNode {
            var left = parsePower()
            while (pos < tokens.size) {
                val tok = tokens[pos]
                if (tok is Tok.Op && (tok.ch == '*' || tok.ch == '/')) {
                    pos++
                    val right = parsePower()
                    left = if (tok.ch == '/') {
                        MathAstNode.Fraction(unwrapParens(left), unwrapParens(right))
                    } else {
                        val needsDot = right is MathAstNode.Number || right is MathAstNode.Fraction
                        MathAstNode.BinaryOp("*", left, right, explicitDot = needsDot)
                    }
                } else {
                    break
                }
            }
            return left
        }

        private fun parsePower(): MathAstNode {
            val base = parseUnary()
            val tok = tokens.getOrNull(pos)
            if (tok is Tok.Op && tok.ch == '^') {
                pos++
                val exp = unwrapParens(parsePower())
                return MathAstNode.Power(base, exp)
            }
            return base
        }

        private fun parseUnary(): MathAstNode {
            val tok = tokens.getOrNull(pos)
            if (tok is Tok.Op && (tok.ch == '+' || tok.ch == '-')) {
                pos++
                val operand = parseUnary()
                return if (tok.ch == '-') MathAstNode.UnaryMinus(operand) else operand
            }
            return parsePrimary()
        }

        private fun parsePrimary(): MathAstNode {
            val tok = tokens.getOrNull(pos) ?: return MathAstNode.Symbol("", false)
            return when (tok) {
                is Tok.Num -> {
                    pos++
                    MathAstNode.Number(tok.text)
                }
                is Tok.LParen -> {
                    pos++
                    val inner = parseAddSub()
                    if (tokens.getOrNull(pos) == Tok.RParen) pos++
                    MathAstNode.ParenGroup(inner)
                }
                is Tok.Id -> {
                    pos++
                    val lower = tok.name.lowercase()
                    if (lower in functions && tokens.getOrNull(pos) == Tok.LParen) {
                        pos++
                        val arg = parseAddSub()
                        if (tokens.getOrNull(pos) == Tok.RParen) pos++
                        when (lower) {
                            "sqrt" -> MathAstNode.Sqrt(unwrapParens(arg))
                            "abs" -> MathAstNode.Abs(unwrapParens(arg))
                            "exp" -> MathAstNode.Power(
                                base = MathAstNode.Symbol("e", isItalic = true),
                                exponent = unwrapParens(arg)
                            )
                            else -> MathAstNode.FunctionCall(lower, unwrapParens(arg))
                        }
                    } else {
                        when (lower) {
                            "pi" -> MathAstNode.Symbol("π", isItalic = false)
                            "e" -> MathAstNode.Symbol("e", isItalic = true)
                            else -> MathAstNode.Symbol(tok.name, isItalic = true)
                        }
                    }
                }
                else -> {
                    pos++
                    MathAstNode.Symbol("", false)
                }
            }
        }

        private fun unwrapParens(node: MathAstNode): MathAstNode =
            if (node is MathAstNode.ParenGroup) node.inner else node
    }
}