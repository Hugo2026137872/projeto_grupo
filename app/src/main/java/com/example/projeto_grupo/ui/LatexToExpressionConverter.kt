package com.example.projeto_grupo.ui

data class LatexConversionResult(
    val rawLatex: String,
    val expression: String,
    val warning: String? = null
)

object LatexToExpressionConverter {

    private val unsupportedMacros = listOf(
        "\\int", "\\iint", "\\oint", "\\sum", "\\prod", "\\begin", "\\end",
        "\\infty", "\\lim", "\\rightarrow", "\\Rightarrow",
        "\\leftrightarrow", "\\Leftrightarrow", "\\approx", "\\equiv", "\\le", "\\ge",
        "\\ne", "\\forall", "\\exists", "\\in", "\\notin", "\\subset"
    )

    private val functionNames = listOf(
        "sin", "cos", "tan", "sqrt", "abs", "exp", "ln", "log",
        "floor", "ceil", "pdx", "pdy", "grad"
    )

    fun convert(rawLatex: String): LatexConversionResult {
        val trimmed = rawLatex.trim()
        if (trimmed.isEmpty()) {
            return LatexConversionResult(
                rawLatex = rawLatex,
                expression = "",
                warning = "A rede neuronal não detetou nenhuma expressão."
            )
        }

        val foundUnsupported = unsupportedMacros.firstOrNull { trimmed.contains(it) }

        var s = trimmed
            .replace("\\left", " ")
            .replace("\\right", " ")
            .replace("\\,", " ")
            .replace("\\;", " ")
            .replace("\\:", " ")
            .replace("\\!", " ")
            .replace("\\quad", " ")
            .replace("\\qquad", " ")
            .replace("\\vdots", " ")
            .replace("\\backslash", " ")
            .replace("\\\\", " ")
            .replace("\\#", " ")
            .replace("\\%", " ")
            .replace("\\&", " ")
            .replace("\\_", "_")
            .replace("\\cdot", " * ")
            .replace("\\times", " * ")
            .replace("\\bullet", " * ")
            .replace("\\circ", " * ")
            .replace("\\odot", " * ")
            .replace("\\otimes", " * ")
            .replace("\\div", " / ")
            .replace("\\pm", " + ")
            .replace("\\oplus", " + ")
            .replace("\\mp", " - ")
            .replace("\\ominus", " - ")
            .replace("\\pi", " pi ")
            .replace("\\Pi", " pi ")
            .replace("\\infty", " 1000 ")
            .replace("\\langle", "(")
            .replace("\\rangle", ")")
            .replace("\\{", "(")
            .replace("\\}", ")")
            .replace("^{\\prime}", "")
            .replace("^\\prime", "")
            .replace("\\prime", "")
            .replace("&", " ")

        // Strip matrix/environment wrappers if present
        s = Regex("""\\begin\s*\{[^}]*\}""").replace(s, "(")
        s = Regex("""\\end\s*\{[^}]*\}""").replace(s, ")")

        // Join spaced function names that token-level decoders often output (e.g. "s i n" -> "sin")
        s = joinSpacedFunctionLetters(s)

        // Strip optional LHS assignment like "z = ...", "y = ...", "f(x, y) = ..."
        s = stripLeftHandSide(s)
        s = s.replace(",", " ")

        // Handle partial derivatives & nabla BEFORE general \frac replacement
        s = replacePartialDerivatives(s)

        // Convert \frac{num}{den} and \sqrt[n]{arg} / \sqrt{arg} recursively
        s = replaceFractions(s)
        s = replaceSquareRoots(s)

        // Unwrap decorators like \hat{u}, \tilde{u}, \vec{u}, \dot{u}, \overline{u}, \underline{u}, \mathbb{Z}
        s = replaceDecorators(s)

        // Convert \lfloor ... \rfloor -> floor(...), \lceil ... \rceil -> ceil(...)
        s = replaceFloorAndCeil(s)

        // Convert \|...\| and |...| into abs(...)
        s = replaceAbsoluteBars(s)

        // Convert square brackets [ ... ] to ( ... )
        s = s.replace('[', '(').replace(']', ')')

        // Join subscripts like a _ { 1 } or a _ 1 -> a_1
        s = replaceSubscripts(s)

        // Convert remaining \macro tokens (e.g. \sin -> sin, \partial -> partial, \alpha -> alpha)
        s = Regex("""\\([a-zA-Z]+)""").replace(s) { m -> " ${m.groupValues[1]} " }

        // Convert e^{...} -> exp(...)
        s = replaceEulerExponents(s)

        // Convert remaining curly braces { ... } -> ( ... )
        s = s.replace('{', '(').replace('}', ')')

        // Ensure function names have parenthesized arguments (e.g. "sin x" -> "sin(x)")
        s = ensureFunctionParentheses(s)

        // Collapse whitespace and clean spacing around operators/parentheses
        s = cleanWhitespaceAndOperators(s)

        val warning = when {
            foundUnsupported != null ->
                "A expressão contém símbolos avançados ($foundUnsupported) que podem precisar de ajuste manual."
            s.isBlank() ->
                "Não foi possível converter o LaTeX automaticamente. Edite a expressão manualmente."
            else -> null
        }

        return LatexConversionResult(
            rawLatex = rawLatex,
            expression = s,
            warning = warning
        )
    }

    private fun joinSpacedFunctionLetters(input: String): String {
        var s = input
        val spacedPatterns = listOf(
            Regex("""(?<![a-zA-Z\\])s\s+i\s+n(?![a-zA-Z])""") to "sin",
            Regex("""(?<![a-zA-Z\\])c\s+o\s+s(?![a-zA-Z])""") to "cos",
            Regex("""(?<![a-zA-Z\\])t\s+a\s+n(?![a-zA-Z])""") to "tan",
            Regex("""(?<![a-zA-Z\\])e\s+x\s+p(?![a-zA-Z])""") to "exp",
            Regex("""(?<![a-zA-Z\\])l\s+o\s+g(?![a-zA-Z])""") to "log",
            Regex("""(?<![a-zA-Z\\])l\s+n(?![a-zA-Z])""") to "ln"
        )
        for ((regex, replacement) in spacedPatterns) {
            s = regex.replace(s, replacement)
        }
        return s
    }

    private fun stripLeftHandSide(input: String): String {
        var current = input
        while (true) {
            val eqIdx = current.indexOf('=')
            if (eqIdx <= 0) break
            val lhs = current.substring(0, eqIdx).replace(" ", "")
            val isSimpleLhs = lhs in setOf("z", "y", "f(x)", "f(x,y)", "f(x,z)", "g(x)", "g(x,y)", "h(x,y)") ||
                Regex("""^[a-zA-Z](\([a-zA-Z](,[a-zA-Z])?\))?$""").matches(lhs)
            if (!isSimpleLhs) break
            current = current.substring(eqIdx + 1).trim()
        }
        return current
    }

    private fun replacePartialDerivatives(input: String): String {
        var s = input
        // \frac{\partial^2 expr}{\partial x^2} or \frac{\partial^2}{\partial x^2} expr
        val secondOrderRegex = Regex(
            """\\frac\s*\{\s*\\partial\s*\^\s*\{?\s*2\s*\}?\s*(.*?)\}\s*\{\s*\\partial\s*([xy])\s*\^\s*\{?\s*2\s*\}?\s*\}"""
        )
        s = secondOrderRegex.replace(s) { m ->
            val inner = m.groupValues[1].trim()
            val fn = if (m.groupValues[2] == "x") "pdx" else "pdy"
            if (inner.isNotEmpty()) "$fn($fn($inner))" else "$fn($fn"
        }

        // \frac{\partial expr}{\partial x} or \frac{\partial}{\partial x}
        val firstOrderRegex = Regex(
            """\\frac\s*\{\s*\\partial\s*(.*?)\}\s*\{\s*\\partial\s*([xy])\s*\}"""
        )
        while (true) {
            val match = firstOrderRegex.find(s) ?: break
            val inner = match.groupValues[1].trim()
            val fn = if (match.groupValues[2] == "x") "pdx" else "pdy"
            if (inner.isNotEmpty()) {
                s = s.substring(0, match.range.first) + "$fn($inner)" + s.substring(match.range.last + 1)
            } else {
                val after = skipSpaces(s, match.range.last + 1)
                val arg = readGroupOrExprToken(s, after)
                if (arg != null) {
                    s = s.substring(0, match.range.first) + "$fn(${arg.first})" + s.substring(arg.second)
                } else {
                    s = s.substring(0, match.range.first) + "$fn(x)" + s.substring(match.range.last + 1)
                }
            }
        }

        // \partial_x (expr) or \partial_{y} expr
        val subPartialRegex = Regex("""\\partial\s*_\s*\{?\s*([xy])\s*\}?""")
        while (true) {
            val match = subPartialRegex.find(s) ?: break
            val fn = if (match.groupValues[1] == "x") "pdx" else "pdy"
            val after = skipSpaces(s, match.range.last + 1)
            val arg = readGroupOrExprToken(s, after)
            if (arg != null) {
                s = s.substring(0, match.range.first) + "$fn(${arg.first})" + s.substring(arg.second)
            } else {
                s = s.substring(0, match.range.first) + fn + s.substring(match.range.last + 1)
            }
        }

        // \nabla (expr) -> grad(expr)
        while (true) {
            val idx = s.indexOf("\\nabla")
            if (idx < 0) break
            val after = skipSpaces(s, idx + 6)
            val arg = readGroupOrExprToken(s, after)
            if (arg != null) {
                s = s.substring(0, idx) + "grad(${arg.first})" + s.substring(arg.second)
            } else {
                s = s.substring(0, idx) + "nabla" + s.substring(idx + 6)
            }
        }

        return s
    }

    private fun replaceFractions(input: String): String {
        var s = input
        while (true) {
            val idx = s.indexOf("\\frac")
            if (idx < 0) break
            var cursor = idx + 5
            val num = readBraceOrToken(s, cursor) ?: break
            cursor = num.second
            val den = readBraceOrToken(s, cursor) ?: break
            cursor = den.second
            val convertedNum = replaceFractions(num.first)
            val convertedDen = replaceFractions(den.first)
            s = s.substring(0, idx) + "(($convertedNum)/($convertedDen))" + s.substring(cursor)
        }
        return s
    }

    private fun replaceSquareRoots(input: String): String {
        var s = input
        while (true) {
            val idx = s.indexOf("\\sqrt")
            if (idx < 0) break
            var cursor = skipSpaces(s, idx + 5)
            var rootOrder: String? = null
            if (cursor < s.length && s[cursor] == '[') {
                val closeBracket = s.indexOf(']', cursor + 1)
                if (closeBracket > cursor) {
                    rootOrder = s.substring(cursor + 1, closeBracket).trim()
                    cursor = closeBracket + 1
                }
            }
            val arg = readBraceOrToken(s, cursor) ?: break
            cursor = arg.second
            val convertedArg = replaceSquareRoots(arg.first)
            val replacement = if (rootOrder.isNullOrBlank()) {
                "sqrt($convertedArg)"
            } else {
                "(($convertedArg)^(1/($rootOrder)))"
            }
            s = s.substring(0, idx) + replacement + s.substring(cursor)
        }
        return s
    }

    private fun replaceDecorators(input: String): String {
        var s = input
        val decorators = listOf("\\hat", "\\tilde", "\\vec", "\\dot", "\\overline", "\\underline", "\\mathbb")
        for (dec in decorators) {
            while (true) {
                val idx = s.indexOf(dec)
                if (idx < 0) break
                val nextIdx = idx + dec.length
                if (nextIdx < s.length && s[nextIdx].isLetter()) break
                val arg = readBraceOrToken(s, nextIdx) ?: break
                s = s.substring(0, idx) + arg.first + s.substring(arg.second)
            }
        }
        return s
    }

    private fun replaceFloorAndCeil(input: String): String {
        var s = input
            .replace("\\lfloor", " floor(")
            .replace("\\rfloor", ") ")
            .replace("\\lceil", " ceil(")
            .replace("\\rceil", ") ")
        return s
    }

    private fun replaceAbsoluteBars(input: String): String {
        val s = input.replace("\\|", "|")
        val out = StringBuilder()
        var inAbs = false
        var i = 0
        while (i < s.length) {
            val ch = s[i]
            if (ch == '|') {
                if (!inAbs) {
                    out.append("abs(")
                    inAbs = true
                } else {
                    out.append(")")
                    inAbs = false
                }
            } else {
                out.append(ch)
            }
            i++
        }
        if (inAbs) {
            out.append(")")
        }
        return out.toString()
    }

    private fun replaceSubscripts(input: String): String {
        var s = Regex("""([a-zA-Z0-9]+)\s*_\s*\{\s*([a-zA-Z0-9 ]+?)\s*\}""").replace(input) { m ->
            val base = m.groupValues[1]
            val sub = m.groupValues[2].replace(" ", "")
            "${base}_${sub}"
        }
        s = Regex("""([a-zA-Z0-9]+)\s*_\s*([a-zA-Z0-9]+)""").replace(s) { m ->
            "${m.groupValues[1]}_${m.groupValues[2]}"
        }
        return s
    }

    private fun replaceEulerExponents(input: String): String {
        val regex = Regex("""(?<![a-zA-Z])e\s*\^\s*\{""")
        var s = input
        while (true) {
            val match = regex.find(s) ?: break
            val braceStart = match.range.last
            val group = readBalancedGroup(s, braceStart, '{', '}') ?: break
            s = s.substring(0, match.range.first) + "exp(${group.first})" + s.substring(group.second)
        }
        return s
    }

    private fun ensureFunctionParentheses(input: String): String {
        var s = input
        for (fn in functionNames) {
            val regex = Regex("""\b$fn\b\s*(?!\()([a-zA-Z0-9_.]+(?:\s*\^\s*[a-zA-Z0-9_.]+)?)""")
            s = regex.replace(s) { m ->
                "$fn(${m.groupValues[1].trim()})"
            }
        }
        return s
    }

    private fun cleanWhitespaceAndOperators(input: String): String {
        var s = input.replace(Regex("""\s+"""), " ").trim()
        // Join multi-digit numbers emitted as separate tokens (e.g. "2 0 0" -> "200")
        s = s.replace(Regex("""(\d)\s+(?=\d)"""), "$1")
        s = s.replace(Regex("""(\d)\s*\.\s*(\d)"""), "$1.$2")
        s = s.replace(Regex("""\s*\(\s*"""), "(")
        s = s.replace(Regex("""\s*\)\s*"""), ")")
        s = s.replace(Regex("""\s*\^\s*"""), "^")
        s = s.replace(Regex("""\s*!\s*"""), "!")
        s = s.replace(Regex("""\s*\*\s*"""), " * ")
        s = s.replace(Regex("""\s*/\s*"""), " / ")
        s = s.replace(Regex("""\s*\+\s*"""), " + ")
        s = s.replace(Regex("""\s*-\s*"""), " - ")
        // Restore tight unary minus right after '(' or '^'
        s = s.replace("( - ", "(-")
        s = s.replace("^ - ", "^-")
        s = s.replace(Regex("""^- """), "-")
        s = s.replace(Regex("""\s+"""), " ").trim()
        return s
    }

    private fun skipSpaces(s: String, start: Int): Int {
        var i = start
        while (i < s.length && s[i].isWhitespace()) i++
        return i
    }

    private fun readGroupOrExprToken(s: String, start: Int): Pair<String, Int>? {
        val i = skipSpaces(s, start)
        if (i >= s.length) return null
        return when (s[i]) {
            '(' -> readBalancedGroup(s, i, '(', ')')
            '{' -> readBalancedGroup(s, i, '{', '}')
            else -> readBraceOrToken(s, i)
        }
    }

    private fun readBraceOrToken(s: String, start: Int): Pair<String, Int>? {
        val i = skipSpaces(s, start)
        if (i >= s.length) return null
        if (s[i] == '{') {
            return readBalancedGroup(s, i, '{', '}')
        }
        var end = i + 1
        if (s[i].isDigit()) {
            while (end < s.length && (s[end].isDigit() || s[end] == '.')) end++
        } else if (s[i] == '\\') {
            while (end < s.length && s[end].isLetter()) end++
        }
        return s.substring(i, end) to end
    }

    private fun readBalancedGroup(s: String, openIdx: Int, openCh: Char, closeCh: Char): Pair<String, Int>? {
        if (openIdx >= s.length || s[openIdx] != openCh) return null
        var depth = 0
        var i = openIdx
        while (i < s.length) {
            when (s[i]) {
                openCh -> depth++
                closeCh -> {
                    depth--
                    if (depth == 0) {
                        return s.substring(openIdx + 1, i) to (i + 1)
                    }
                }
            }
            i++
        }
        return s.substring(openIdx + 1) to s.length
    }
}
