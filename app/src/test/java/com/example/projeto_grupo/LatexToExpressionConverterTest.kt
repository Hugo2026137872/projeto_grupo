package com.example.projeto_grupo

import com.example.projeto_grupo.ui.LatexToExpressionConverter
import com.example.projeto_grupo.ui.MathExpressionEvaluator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

class LatexToExpressionConverterTest {

    @Test
    fun convertsFractionsAndNestedFractions() {
        val simple = LatexToExpressionConverter.convert("\\frac { 1 } { 2 } x ^ 2")
        assertNull(simple.warning)
        assertNull(MathExpressionEvaluator.validate(simple.expression))
        assertEquals(4.5, MathExpressionEvaluator.evaluate(simple.expression, 3.0, 0.0), 1e-6)

        val nested = LatexToExpressionConverter.convert("\\frac { x + \\frac { 1 } { 2 } } { y ^ 2 + 1 }")
        assertNull(nested.warning)
        assertNull(MathExpressionEvaluator.validate(nested.expression))
        assertEquals((2.0 + 0.5) / (1.0 + 1.0), MathExpressionEvaluator.evaluate(nested.expression, 2.0, 1.0), 1e-6)
    }

    @Test
    fun convertsRootsAndEulerExponents() {
        val expr = LatexToExpressionConverter.convert("a \\cos ( b \\sqrt { x ^ 2 + y ^ 2 } ) e ^ { - 0.35 ( x ^ 2 + y ^ 2 ) } + c")
        assertNull(expr.warning)
        val params = mapOf("a" to 1.2, "b" to 3.0, "c" to 0.5)
        assertNull(MathExpressionEvaluator.validate(expr.expression, params))
        val r2 = 0.5 * 0.5 + 0.5 * 0.5
        val expected = 1.2 * cos(3.0 * sqrt(r2)) * exp(-0.35 * r2) + 0.5
        assertEquals(expected, MathExpressionEvaluator.evaluate(expr.expression, 0.5, 0.5, params), 1e-6)

        val nthRoot = LatexToExpressionConverter.convert("\\sqrt [ 3 ] { x ^ 2 + 1 }")
        assertNull(MathExpressionEvaluator.validate(nthRoot.expression))
    }

    @Test
    fun convertsSpacedTrigFunctionsAbsoluteBarsAndStripsLhs() {
        val result = LatexToExpressionConverter.convert("z = f ( x , y ) = s i n ( x ) + | y | - \\pi")
        assertNull(result.warning)
        assertNull(MathExpressionEvaluator.validate(result.expression))
        val expected = sin(1.0) + 2.0 - Math.PI
        assertEquals(expected, MathExpressionEvaluator.evaluate(result.expression, 1.0, -2.0), 1e-6)
    }

    @Test
    fun joinsSpacedMultiDigitNumbersAndDecimals() {
        val res = LatexToExpressionConverter.convert("\\frac { \\sqrt { \\frac { 2 0 0 } { 9 } } } { \\sqrt { 6 } } + 0 . 3 5 x ^ 2")
        assertNull(res.warning)
        assertNull(MathExpressionEvaluator.validate(res.expression))
        val expected = sqrt(200.0 / 9.0) / sqrt(6.0) + 0.35 * 4.0
        assertEquals(expected, MathExpressionEvaluator.evaluate(res.expression, 2.0, 0.0), 1e-6)
    }

    @Test
    fun convertsPartialDerivativesFloorCeilDecoratorsAndGreekMacros() {
        // \frac{\partial}{\partial x}(x^2 + y^2) = 2x
        val pdxExpr = "\\frac{\\partial}{\\partial x}(x^2 + y^2)"
        assertNull(MathExpressionEvaluator.validate(pdxExpr))
        assertEquals(6.0, MathExpressionEvaluator.evaluate(pdxExpr, 3.0, 4.0), 1e-4)

        // \frac{\partial}{\partial y}(x * y^2) = 2*x*y
        val pdyExpr = "\\frac{\\partial (x * y^2)}{\\partial y}"
        assertNull(MathExpressionEvaluator.validate(pdyExpr))
        assertEquals(12.0, MathExpressionEvaluator.evaluate(pdyExpr, 2.0, 3.0), 1e-4)

        // Standalone \partial and Greek letters become valid parameters
        assertNull(MathExpressionEvaluator.validate("\\partial * x + \\alpha * y + \\theta", mapOf("partial" to 2.0, "alpha" to 3.0, "theta" to 1.0)))
        assertEquals(2.0 * 4.0 + 3.0 * 5.0 + 1.0, MathExpressionEvaluator.evaluate("\\partial * x + \\alpha * y + \\theta", 4.0, 5.0, mapOf("partial" to 2.0, "alpha" to 3.0, "theta" to 1.0)), 1e-6)

        // Floor, ceil, factorial, decorators, subscripts
        assertEquals(2.0 + 4.0, MathExpressionEvaluator.evaluate("\\lfloor x \\rfloor + \\lceil y \\rceil", 2.7, 3.2), 1e-6)
        assertEquals(6.0, MathExpressionEvaluator.evaluate("x!", 3.0, 0.0), 1e-6)
        assertNull(MathExpressionEvaluator.validate("\\hat{a} * x + \\tilde{b} * y + a_{1}", mapOf("a" to 1.0, "b" to 2.0, "a_1" to 3.0)))
    }

    @Test
    fun flagsUnsupportedLatexMacrosWithWarning() {
        val res = LatexToExpressionConverter.convert("\\int x ^ 2 d x")
        assertNotNull(res.warning)
    }
}
