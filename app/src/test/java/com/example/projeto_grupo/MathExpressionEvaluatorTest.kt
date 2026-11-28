package com.example.projeto_grupo

import com.example.projeto_grupo.data.model.PointType
import com.example.projeto_grupo.ui.MathExpressionEvaluator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sqrt

class MathExpressionEvaluatorTest {

    @Test
    fun arithmeticPrecedenceAndRightAssociativePower() {
        assertEquals(14.0, MathExpressionEvaluator.evaluate("2 + 3 * 4", 0.0, 0.0), 1e-9)
        assertEquals(20.0, MathExpressionEvaluator.evaluate("(2 + 3) * 4", 0.0, 0.0), 1e-9)
        assertEquals(512.0, MathExpressionEvaluator.evaluate("2^3^2", 0.0, 0.0), 1e-9)
        assertEquals(-9.0, MathExpressionEvaluator.evaluate("-(3^2)", 0.0, 0.0), 1e-9)
        assertEquals(9.0, MathExpressionEvaluator.evaluate("-3^2", 0.0, 0.0), 1e-9)
        assertEquals(9.0, MathExpressionEvaluator.evaluate("(-3)^2", 0.0, 0.0), 1e-9)
    }

    @Test
    fun implicitMultiplicationWorksForVariablesNumbersAndFunctions() {
        assertEquals(11.0, MathExpressionEvaluator.evaluate("2x + 3y", 1.0, 3.0), 1e-9)
        assertEquals(10.0, MathExpressionEvaluator.evaluate("2(x + 1)", 4.0, 0.0), 1e-9)
        val expected = 2.5 * sin(1.5 * 0.8)
        assertEquals(
            expected,
            MathExpressionEvaluator.evaluate("a sin(b x)", 0.8, 0.0, mapOf("a" to 2.5, "b" to 1.5)),
            1e-9
        )
    }

    @Test
    fun builtInFunctionsAndConstantsMatchMath() {
        val params = mapOf("a" to 1.2, "b" to 3.0, "c" to -0.5)
        val x = 0.7
        val y = -0.4
        val expr = "a * cos(b * sqrt(x^2 + y^2)) * exp(-0.35 * (x^2 + y^2)) + c"
        val r2 = x * x + y * y
        val expected = 1.2 * cos(3.0 * sqrt(r2)) * exp(-0.35 * r2) - 0.5
        assertEquals(expected, MathExpressionEvaluator.evaluate(expr, x, y, params), 1e-9)

        assertEquals(PI, MathExpressionEvaluator.evaluate("pi", 0.0, 0.0), 1e-9)
        assertEquals(Math.E, MathExpressionEvaluator.evaluate("e", 0.0, 0.0), 1e-9)
        assertEquals(ln(2.0), MathExpressionEvaluator.evaluate("ln(2)", 0.0, 0.0), 1e-9)
        assertEquals(2.0, MathExpressionEvaluator.evaluate("log(100)", 0.0, 0.0), 1e-9)
        assertEquals(5.0, MathExpressionEvaluator.evaluate("abs(-5)", 0.0, 0.0), 1e-9)
    }

    @Test
    fun compiledExpressionMatchesEvaluateAcrossCoordinates() {
        val expr = "a * sin(b * x) * cos(b * y) + c"
        val params = mapOf("a" to 1.5, "b" to 2.2, "c" to 0.3)
        val compiled = MathExpressionEvaluator.compile(expr)

        val coords = listOf(-2.0, -0.75, 0.0, 0.65, 1.9)
        for (x in coords) {
            for (y in coords) {
                val expected = MathExpressionEvaluator.evaluate(expr, x, y, params)
                val actual = compiled.evaluate(x, y, params)
                assertEquals(expected, actual, 1e-12)
            }
        }
    }

    @Test
    fun extractParametersExcludesVariablesConstantsAndFunctions() {
        val params = MathExpressionEvaluator.extractParameters(
            "k * sin(omega * x + phi) + exp(-y) + pi + e + z"
        )
        assertEquals(listOf("k", "omega", "phi"), params)
    }

    @Test
    fun validateCatchesBlankSyntaxAndDomainErrors() {
        assertNotNull(MathExpressionEvaluator.validate("   "))
        assertNotNull(MathExpressionEvaluator.validate("2 * (x + 1"))
        assertNotNull(MathExpressionEvaluator.validate("x + * y"))
        assertNotNull(MathExpressionEvaluator.validate("sqrt(-4)"))
        assertNull(MathExpressionEvaluator.validate("a * (x^2 + y^2) + c", mapOf("a" to 0.5, "c" to 0.0)))
    }

    @Test
    fun supportsPartialDerivativesFloorCeilFactorialAndLatexInput() {
        assertEquals(6.0, MathExpressionEvaluator.evaluate("pdx(x^2 + y^2)", 3.0, 2.0), 1e-4)
        assertEquals(10.0, MathExpressionEvaluator.evaluate("pdy(x * y^2)", 2.0, 2.5), 1e-4)
        assertEquals(5.0, MathExpressionEvaluator.evaluate("grad(3*x + 4*y)", 1.0, 1.0), 1e-4)
        assertEquals(2.0, MathExpressionEvaluator.evaluate("floor(2.9)", 0.0, 0.0), 1e-9)
        assertEquals(3.0, MathExpressionEvaluator.evaluate("ceil(2.1)", 0.0, 0.0), 1e-9)
        assertEquals(24.0, MathExpressionEvaluator.evaluate("(x + 1)!", 3.0, 0.0), 1e-6)
        assertEquals(listOf("alpha", "partial"), MathExpressionEvaluator.extractParameters("\\partial * x + \\alpha * y"))
    }

    @Test
    fun computePointsOfInterestDetectsInteriorExtremaZerosAndPerimeterMinima() {
        val paraboloidPois = MathExpressionEvaluator.computePointsOfInterest(
            expression = "x^2 + y^2 - 1",
            params = emptyMap(),
            xDomain = 2.0,
            yDomain = 2.0
        )
        val minPoi = paraboloidPois.firstOrNull { it.type == PointType.MINIMA }
        assertNotNull(minPoi)
        assertEquals(0.0, minPoi!!.x, 0.06)
        assertEquals(0.0, minPoi.y, 0.06)
        assertEquals(-1.0, minPoi.z, 0.06)
        assertTrue(paraboloidPois.any { it.type == PointType.ZERO })

        // Verify perimeter minima detection on a monotonic/tilted surface (line 408 fix)
        val planePois = MathExpressionEvaluator.computePointsOfInterest(
            expression = "x + y",
            params = emptyMap(),
            xDomain = 2.0,
            yDomain = 2.0
        )
        val cornerMin = planePois.firstOrNull { it.type == PointType.MINIMA }
        val cornerMax = planePois.firstOrNull { it.type == PointType.MAXIMA }
        assertNotNull(cornerMin)
        assertNotNull(cornerMax)
        assertEquals(-4.0, cornerMin!!.z, 0.1)
        assertEquals(4.0, cornerMax!!.z, 0.1)
    }
}
