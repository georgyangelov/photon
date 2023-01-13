package photon.frontend

import photon.core.Location
import photon.core.PhotonError
import photon.lib.LookAheadReader

class ParseError(
  message: String,
  location: Location
) : PhotonError(message, location)

typealias MacroHandler = (String, Parser, Location) -> ASTValue?

class Parser(
  private val lexer: Lexer,
  private val macroHandler: MacroHandler
) {
  companion object {
    val BlankMacroHandler: MacroHandler = { _, _, _ -> null }
  }

  val reader = LookAheadReader { lexer.nextToken() }

  lateinit var token: Token

  var atStart = true
  lateinit var lastLocation: Location
  var newline: Boolean = false

  fun hasMoreTokens(): Boolean {
    if (atStart) read()

    return token.tokenType != TokenType.EOF
  }

  fun readToken(tokenType: TokenType, message: String): Token {
    if (token.tokenType != tokenType) {
      parseError(message)
    }

    return read()
  }

  fun skipNextToken() { read() }

  fun parseRoot(): ASTValue {
    val values = parseAll()
    val startLocation = lastLocation

    return when (values.size) {
      1 -> values.first()
      0 -> ASTValue.Block(values, startLocation.extendWith(lastLocation))
      else -> throw RuntimeException("This should not happen")
    }
  }

  fun parseAll(): List<ASTValue> {
    return buildList {
      while (hasMoreTokens()) {
        add(parseCompleteExpression())
      }
    }
  }

  inline fun <reified T: ASTValue>parseAST(klass: Class<T>): T {
    when (val value = parseNext()) {
      is T -> return value
      else -> parseError("Read $value, expected $klass")
    }
  }

  fun parseCompleteExpression(): ASTValue {
    if (atStart) read()

    val expression = assertASTValue(
      parseExpression(requireCallParens = false, hasLowerPriorityTarget = false)
    )

    if (token.tokenType != TokenType.EOF && !newline) {
      parseError("Expected newline or semicolon")
    }

    return expression
  }

  fun parseNext(requireCallParens: Boolean = false): ASTValue {
    if (atStart) read()

    return assertASTValue(
      parseExpression(requireCallParens = requireCallParens, hasLowerPriorityTarget = false)
    )
  }

  private fun parseExpression(
    minPrecedence: Int = 0,
    requireCallParens: Boolean,
    hasLowerPriorityTarget: Boolean
  ): ASTValueOrPattern {
    var left = parsePrimary(requireCallParens, hasLowerPriorityTarget)

    while (true) {
      if (newline) return left

      if (token.tokenType != TokenType.BinaryOperator && token.tokenType != TokenType.Colon) {
        return left
      }

      val precedence = operatorPrecedence(token)
      if (precedence < minPrecedence) {
        return left
      }

      val operator = read()
      val right = parseExpression(precedence + 1, requireCallParens, hasLowerPriorityTarget)
      val location = left.location!!.extendWith(right.location!!)

      left = if (operator.tokenType == TokenType.Colon) {
        ASTValueOrPattern.Value(ASTValue.Call(
          target = ASTValue.NameReference("Core", location),
          name = "typeCheck",
          arguments = ASTArguments(listOf(left, right).map { assertASTValue(it) }, emptyMap()),
          mayBeVarCall = false,
          location = location
        ))
      } else if (right is ASTValueOrPattern.Pattern) {
        ASTValueOrPattern.Pattern(Pattern.Call(
          target = assertASTValue(left),
          name = operator.string,
          arguments = ArgumentsWithoutSelf(listOf(coerceToPattern(right)), emptyMap()),
          mayBeVarCall = false,
          location
        ))
      } else {
        ASTValueOrPattern.Value(ASTValue.Call(
          target = assertASTValue(left),
          name = operator.string,
          arguments = ASTArguments(listOf(assertASTValue(right)), emptyMap()),
          mayBeVarCall = false,
          location
        ))
      }
    }
  }

  private fun parsePrimary(requireCallParens: Boolean, hasLowerPriorityTarget: Boolean): ASTValueOrPattern {
    if (token.tokenType == TokenType.Val) {
      val startLocation = token.location
      read() // val

      if (token.tokenType != TokenType.Name) {
        parseError("`val` needs to be followed by a name")
      }
      val name = read()

      val isAssignment = token.tokenType == TokenType.Colon || token.tokenType == TokenType.Equals
      if (!isAssignment) {
        return ASTValueOrPattern.Pattern(Pattern.Binding(
          name.string,
          startLocation.extendWith(name.location)
        ))
      }

      val typ =
        if (token.tokenType == TokenType.Colon) {
          read() // :

          assertASTValue(parseExpression(requireCallParens = true, hasLowerPriorityTarget = false))
        } else null

      if (token.tokenType != TokenType.Equals) {
        parseError("Val needs to have an =")
      }
      val equals = read() // =

      val value = assertASTValue(parseExpression(operatorPrecedence(equals) + 1, requireCallParens, hasLowerPriorityTarget))

      val valueWithType = if (typ != null) {
        ASTValue.Call(
          target = ASTValue.NameReference("Core", typ.location),
          name = "typeCheck",
          arguments = ASTArguments(listOf(value, typ), emptyMap()),
          mayBeVarCall = false,
          location = typ.location
        )
      } else value

      val body = parseRestOfBlock()
      val location = startLocation.extendWith(body.location!!)

      return ASTValueOrPattern.Value(ASTValue.Let(
        name.string,
        valueWithType,
        body,
        location
      ))
    }

    if (token.tokenType == TokenType.BinaryOperator && token.string == "-") {
      val startLocation = token.location
      read() // -

      val expression = assertASTValue(parsePrimary(requireCallParens, hasLowerPriorityTarget))
      val location = startLocation.extendWith(expression.location!!)

      return ASTValueOrPattern.Value(ASTValue.Call(
        target = expression,
        name = "-",
        arguments = ASTArguments.empty,
        mayBeVarCall = false,
        location
      ))
    }

    var target = parseCallTarget(requireCallParens, hasLowerPriorityTarget)

    while (true) {
      val newTarget = tryToParseCall(target, requireCallParens, hasLowerPriorityTarget)

      if (newTarget != null) {
        target = newTarget
      } else {
        return target
      }
    }
  }

  private fun parseCallTarget(requireCallParens: Boolean, hasLowerPriorityTarget: Boolean): ASTValueOrPattern {
    return when (token.tokenType) {
      TokenType.BoolLiteral -> ASTValueOrPattern.Value(parseBool())
      TokenType.NumberLiteral -> ASTValueOrPattern.Value(parseNumber())
      TokenType.StringLiteral -> ASTValueOrPattern.Value(parseString())
      TokenType.Name -> {
        val token = read()

        ASTValueOrPattern.Value(
          macroHandler(token.string, this, token.location) ?:
            ASTValue.NameReference(token.string, lastLocation)
        )
      }

      TokenType.OpenBrace -> parseLambdaOrLambdaType(hasLowerPriorityTarget, isCompileTimeOnly = false)

      TokenType.UnaryOperator ->
        ASTValueOrPattern.Value(
          parseUnaryOperator(requireCallParens, hasLowerPriorityTarget)
        )

      TokenType.At -> {
        read() // @

        if (token.tokenType == TokenType.OpenBrace) {
          parseLambdaOrLambdaType(hasLowerPriorityTarget, isCompileTimeOnly = true)
        } else {
          parseExpressionStartingWithOpenParen(hasLowerPriorityTarget, ifLambdaIsItCompileTimeOnly = true)
        }
      }

      TokenType.OpenParen ->
        parseExpressionStartingWithOpenParen(hasLowerPriorityTarget, ifLambdaIsItCompileTimeOnly = false)

      else -> parseError()
    }
  }

  private fun parseExpressionStartingWithOpenParen(
    hasLowerPriorityTarget: Boolean,
    ifLambdaIsItCompileTimeOnly: Boolean
  ): ASTValueOrPattern {
    if (isOpenParenForLambda()) {
      return parseLambdaOrLambdaType(hasLowerPriorityTarget, isCompileTimeOnly = ifLambdaIsItCompileTimeOnly)
    } else {
      read() // (

      val values = mutableListOf<ASTValueOrPattern>()
      val startLocation = lastLocation

      do {
        values.add(parseExpression(requireCallParens = false, hasLowerPriorityTarget = false))
      } while (token.tokenType != TokenType.CloseParen && newline)

      if (token.tokenType != TokenType.CloseParen) {
        parseError("Unmatched parentheses or extra expressions. Expected ')'")
      }

      val value = if (values.size == 1) {
        values.first()
      } else {
        ASTValueOrPattern.Value(
          ASTValue.Block(values.map { assertASTValue(it) }, startLocation.extendWith(token.location))
        )
      }

      if (token.tokenType != TokenType.CloseParen) {
        parseError("Unmatched parentheses or extra expressions. Expected ')'")
      }

      read() // )

      return value
    }
  }

  private fun isOpenParenForLambda(): Boolean {
    val reader = this.reader.lookAhead()
    var nestedParenLevel = 1

    var token = reader.nextToken() // (

    while (nestedParenLevel > 0) {
      if (token.tokenType == TokenType.EOF) {
        return false
      }

      when (token.tokenType) {
        TokenType.OpenParen -> nestedParenLevel += 1
        TokenType.CloseParen -> nestedParenLevel -= 1
        else -> {}
      }

      token = reader.nextToken()
    }

    return when (token.tokenType) {
      TokenType.EOF -> false
      TokenType.NewLine -> false

      // This is intentionally false, because it is ambiguous:
      // (thisIsAFunction)(42)
      // (function.call + something)(42)
      // (argument) (42 + argument)
      TokenType.OpenParen -> false

      TokenType.CloseParen -> false
      TokenType.OpenBrace -> true
      TokenType.CloseBrace -> false
      TokenType.OpenBracket -> true
      TokenType.CloseBracket -> false
      TokenType.Comma -> false
      TokenType.Dot -> false
      TokenType.At -> false
      TokenType.Equals -> false

      // This is for return type of lambdas. For example:
      // (1 + 2): Int
      // (a: Int): Int { a + 42 }
      TokenType.Colon -> true

      TokenType.Dollar -> true
      TokenType.BinaryOperator -> false
      TokenType.Val -> false
      TokenType.Name -> true
      TokenType.NumberLiteral -> true
      TokenType.StringLiteral -> true
      TokenType.BoolLiteral -> true

      // TODO: Are these correct?
      TokenType.UnaryOperator -> true
    }
  }

  private fun tryToParseCall(target: ASTValueOrPattern, requireCallParens: Boolean, hasLowerPriorityTarget: Boolean): ASTValueOrPattern? {
    // target.call
    if (token.tokenType == TokenType.Dot) {
      if (token.hadWhitespaceBefore && hasLowerPriorityTarget) {
        // array.map { 42 } .filter (x) x > 0
        return null
      }

      read() // .

      val canBeAMethodName =
        token.tokenType == TokenType.Name ||
        token.tokenType == TokenType.BinaryOperator ||
        token.tokenType == TokenType.UnaryOperator

      if (!canBeAMethodName) parseError("Expected method name")

      val name = read()
      val arguments = parseArguments(requireCallParens, hasLowerPriorityTarget = true)
      val isPattern = arguments.any { it.second is ASTValueOrPattern.Pattern }

      if (isPattern) {
        return ASTValueOrPattern.Pattern(Pattern.Call(
          assertASTValue(target),
          name.string,
          toPatternArguments(arguments),
          mayBeVarCall = false,
          location = target.location?.extendWith(lastLocation)
        ))
      } else {
        return ASTValueOrPattern.Value(ASTValue.Call(
          assertASTValue(target),
          name.string,
          toArguments(arguments),
          mayBeVarCall = false,
          location = target.location?.extendWith(lastLocation)
        ))
      }
    }

    // name a
    // name(a)
    if (target is ASTValueOrPattern.Value && target.value is ASTValue.NameReference) {
      val isDefinitelyACall = token.tokenType == TokenType.OpenParen

      if (!currentExpressionMayEnd() && (!requireCallParens || isDefinitelyACall)) {
        val arguments = parseArguments(requireCallParens, hasLowerPriorityTarget = true)
        val isPattern = arguments.any { it.second is ASTValueOrPattern.Pattern }

        if (isPattern) {
          return ASTValueOrPattern.Pattern(Pattern.Call(
            target = ASTValue.NameReference("self", target.value.location),
            target.value.name,
            toPatternArguments(arguments),
            mayBeVarCall = true,
            location = target.value.location?.extendWith(lastLocation)
          ))
        } else {
          return ASTValueOrPattern.Value(ASTValue.Call(
            target = ASTValue.NameReference("self", target.value.location),
            target.value.name,
            toArguments(arguments),
            mayBeVarCall = true,
            location = target.value.location?.extendWith(lastLocation)
          ))
        }
      }
    }

    // expression( ... )
    if (token.tokenType == TokenType.OpenParen && !token.hadWhitespaceBefore) {
      val arguments = parseArguments(requireCallParens, hasLowerPriorityTarget = false)
      val isPattern = arguments.any { it.second is ASTValueOrPattern.Pattern }

      if (isPattern) {
        return ASTValueOrPattern.Pattern(Pattern.Call(
          assertASTValue(target),
          name = "call",
          arguments = toPatternArguments(arguments),
          mayBeVarCall = false,
          target.location?.extendWith(lastLocation)
        ))
      } else {
        return ASTValueOrPattern.Value(ASTValue.Call(
          target = assertASTValue(target),
          name = "call",
          arguments = toArguments(arguments),
          mayBeVarCall = false,
          target.location?.extendWith(lastLocation)
        ))
      }
    }

    return null
  }

  private fun parseArguments(requireParens: Boolean, hasLowerPriorityTarget: Boolean): List<Pair<String?, ASTValueOrPattern>> {
    var withParentheses = false

    if (token.tokenType == TokenType.OpenParen && !token.hadWhitespaceBefore) {
      read() // (
      withParentheses = true
    }

    if (!withParentheses && currentExpressionMayEnd()) {
      return emptyList()
    }

    if (!withParentheses && requireParens) {
      return emptyList()
    }

    if (withParentheses && token.tokenType == TokenType.CloseParen) {
      read() // )
      return emptyList()
    }

    val arguments = mutableListOf<Pair<String?, ASTValueOrPattern>>()

    arguments.add(parseArgument(hasLowerPriorityTarget = hasLowerPriorityTarget && !withParentheses))
    while (token.tokenType == TokenType.Comma) {
      read() // ,
      arguments.add(parseArgument(hasLowerPriorityTarget = hasLowerPriorityTarget && !withParentheses))
    }

    if (withParentheses) {
      if (token.tokenType != TokenType.CloseParen) {
        parseError("Expected CloseParen ')'")
      }

      read() // )
    } else if (!currentExpressionMayEnd()) {
      parseError("Expected current expression to end (either new line or ')')")
    }

    return arguments
  }

  private fun toArguments(args: List<Pair<String?, ASTValueOrPattern>>): ASTArguments {
    val (positional, named) = args
      .map { Pair(it.first, assertASTValue(it.second)) }
      .partition { it.first == null }

    return ASTArguments(
      positional.map { it.second },
      named.associate { Pair(it.first!!, it.second) }
    )
  }

  private fun toPatternArguments(args: List<Pair<String?, ASTValueOrPattern>>): ArgumentsWithoutSelf<Pattern> {
    val patternArgs = args.map { Pair(it.first, coerceToPattern(it.second)) }
    val (positional, named) = patternArgs.partition { it.first == null }

    return ArgumentsWithoutSelf(
      positional.map { it.second },
      named.associate { Pair(it.first!!, it.second) }
    )
  }

  private fun parseArgument(hasLowerPriorityTarget: Boolean): Pair<String?, ASTValueOrPattern> {
    val name = if (isNamedArgument()) {
      val name = read()

      read() // =

      name.string
    } else { null }

    val value = parseExpression(requireCallParens = false, hasLowerPriorityTarget = hasLowerPriorityTarget)

    return Pair(name, value)
  }

  private fun isNamedArgument(): Boolean {
    val reader = this.reader.lookAhead()
    val hasName = token.tokenType == TokenType.Name

    if (!hasName) {
      return false
    }

    val nextToken = reader.nextToken()

    return nextToken.tokenType == TokenType.Equals
  }

  private fun parseBool(): ASTValue.Boolean {
    val token = read()

    return ASTValue.Boolean(token.string.equals("true", ignoreCase = true), token.location)
  }

  private fun parseNumber(): ASTValue {
    val token = read()

    return if (token.string.contains(".")) {
      ASTValue.Float(token.string.toDouble(), token.location)
    } else {
      ASTValue.Int(token.string.toInt(), token.location)
    }
  }

  private fun parseString(): ASTValue.String {
    val token = read()

    return ASTValue.String(token.string, token.location)
  }

  private fun parseLambdaOrLambdaType(
    hasLowerPriorityTarget: Boolean,
    isCompileTimeOnly: Boolean
  ): ASTValueOrPattern {
    // This aims to fix parse of lambdas using only braces on a separate line, e.g. `{ a }`
    // Since there was a newline before, but we don't care
    newline = false

    val startLocation = lastLocation

    val hasParameterParens = token.tokenType == TokenType.OpenParen
    val parameters = if (hasParameterParens) {
      parseLambdaParameters()
    } else {
      emptyList()
    }

    val hasReturnType = token.tokenType == TokenType.Colon
    val returnType = if (hasReturnType) {
      read() // :

      parseExpression(requireCallParens = true, hasLowerPriorityTarget = false)
    } else {
      null
    }

    val hasBody = !currentExpressionMayEnd()

    if (!hasBody) {
      val returns = returnType ?: parseError("Function types need to have explicit return type")

      val location = startLocation.extendWith(lastLocation)

      val hasPatternArguments = parameters.any {
        when (it.typePattern) {
          is Pattern.SpecificValue -> false
          is Pattern -> true
          null -> parseError("Function type needs to have defined parameter types")
        }
      }

      val hasReturnTypePattern = returns is ASTValueOrPattern.Pattern

      if (hasPatternArguments || hasReturnTypePattern) {
        val typeParams = parameters.map {
          ASTPatternParameter(
            name = it.outName,
            typ = it.typePattern!!,
            location = it.location
          )
        }

        val returnTypePattern = coerceToPattern(returns)

        return ASTValueOrPattern.Pattern(Pattern.FunctionType(typeParams, returnTypePattern, location))
      } else {
        val typeParameters = parameters.map {
          ASTTypeParameter(
            name = it.outName,
            typ = when (it.typePattern) {
              is Pattern.SpecificValue -> it.typePattern.value
              null -> parseError("Function type needs to have defined parameter types")
              else -> parseError("Function type cannot use patterns in types of parameters")
            },
            location = it.location
          )
        }

        // Function type, not lambda, e.g. `(a: Int): Int`
        return ASTValueOrPattern.Value(
          ASTValue.FunctionType(typeParameters, assertASTValue(returns), location)
        )
      }
    }

    val hasBlock = token.tokenType == TokenType.OpenBrace
    val body = if (hasBlock) {
      read() // {

      val block = parseBlock()

      if (token.tokenType != TokenType.CloseBrace) parseError("Expected CloseBrace '}'")
      read() // }

      block
    } else {
      assertASTValue(
        parseExpression(requireCallParens = false, hasLowerPriorityTarget = hasLowerPriorityTarget)
      )
    }

    return ASTValueOrPattern.Value(
      ASTValue.Function(
        parameters,
        body,
        if (returnType != null) { assertASTValue(returnType) } else null,
        isCompileTimeOnly = isCompileTimeOnly,
        startLocation.extendWith(lastLocation)
      )
    )
  }

  private fun parseLambdaParameters(): List<ASTParameter> {
    val parameters = mutableListOf<ASTParameter>()

    read() // (

    val hasArguments = token.tokenType != TokenType.CloseParen

    if (hasArguments) {
      while (true) {
        if (token.tokenType != TokenType.Name) parseError("Expected Name")

        val outName = read().string
        val startLocation = lastLocation

        val inName = if (token.tokenType == TokenType.Name && token.string == "as") {
          read() // as

          if (token.tokenType != TokenType.Name) parseError("Expected name")

          read().string
        } else outName

        val typeValue = if (token.tokenType == TokenType.Colon) {
          read() // :

          coerceToPattern(parseExpression(requireCallParens = true, hasLowerPriorityTarget = false))
        } else {
          null
        }

        parameters.add(
          ASTParameter(outName, inName, typeValue, startLocation.extendWith(lastLocation))
        )

        if (token.tokenType != TokenType.Comma) break
        read() // ,
      }
    }

    if (token.tokenType != TokenType.CloseParen) parseError("Expected CloseParen ')'")
    read() // )

    return parameters
  }

  private fun parseBlock(): ASTValue {
    val values = mutableListOf<ASTValue>()
    val startLocation = lastLocation

    while (token.tokenType != TokenType.CloseBrace) {
      values += assertASTValue(parseExpression(requireCallParens = false, hasLowerPriorityTarget = false))
    }

    return if (values.size == 1) {
      values.first()
    } else {
      ASTValue.Block(values, startLocation.extendWith(lastLocation))
    }
  }

  fun parseRestOfBlock(): ASTValue {
    val values = mutableListOf<ASTValue>()
    val startLocation = lastLocation

    while (token.tokenType != TokenType.CloseBrace && token.tokenType != TokenType.CloseParen && token.tokenType != TokenType.EOF) {
      values += assertASTValue(parseExpression(requireCallParens = false, hasLowerPriorityTarget = false))
    }

    return if (values.size == 1) {
      values.first()
    } else {
      ASTValue.Block(values, startLocation.extendWith(lastLocation))
    }
  }

  private fun parseUnaryOperator(requireCallParens: Boolean, hasLowerPriorityTarget: Boolean): ASTValue {
    val operator = read()
    val target = parsePrimary(requireCallParens, hasLowerPriorityTarget)

    return ASTValue.Call(
      assertASTValue(target),
      name = operator.string,
      arguments = ASTArguments.empty,
      mayBeVarCall = false,
      location = operator.location.extendWith(lastLocation)
    )
  }

  private fun currentExpressionMayEnd(): Boolean =
    newline ||
    token.tokenType == TokenType.EOF ||
    token.tokenType == TokenType.BinaryOperator ||
    token.tokenType == TokenType.Colon ||
    token.tokenType == TokenType.Comma ||
    token.tokenType == TokenType.CloseParen ||
    token.tokenType == TokenType.Dot ||
    token.tokenType == TokenType.CloseBracket ||
    token.tokenType == TokenType.CloseBrace ||
    // This is because of lambda types
    token.tokenType == TokenType.Equals

  private fun operatorPrecedence(token: Token): Int = when (token.string) {
    "="      -> 1
    "or"     -> 2
    "and"    -> 3
    "==", "<", ">", "<=", ">=", "!=" -> 4
    "+", "-" -> 5
    "*", "/" -> 6
    ":"      -> 7
    else -> throw RuntimeException("Unknown operator '${token.string}'")
  }

  private fun read(): Token {
    newline = false

    val oldToken = if (!atStart) token else Token(TokenType.Name, "this should not happen", Location.beginningOfFile("unknown"), false)
    var nextToken = reader.next()

    while (nextToken.tokenType == TokenType.NewLine) {
      newline = true
      nextToken = reader.next()
    }

    token = nextToken

    if (atStart) {
      atStart = false
      lastLocation = lexer.currentLocation
    } else {
      lastLocation = oldToken.location
    }

    return oldToken
  }

  fun parseError(explanation: String = ""): Nothing {
    throw ParseError(
      "Unexpected token ${token.tokenType.name} '${token.string}'. $explanation",
      token.location
    )
  }

  private fun assertASTValue(valueOrPattern: ASTValueOrPattern): ASTValue = when (valueOrPattern) {
    is ASTValueOrPattern.Value -> valueOrPattern.value
    is ASTValueOrPattern.Pattern -> throw ParseError(
      "Cannot use pattern in this context (pattern was ${valueOrPattern.pattern})",
      valueOrPattern.pattern.location!!
    )
  }

  private fun coerceToPattern(valueOrPattern: ASTValueOrPattern): Pattern = when (valueOrPattern) {
    is ASTValueOrPattern.Value -> Pattern.SpecificValue(valueOrPattern.value)
    is ASTValueOrPattern.Pattern -> valueOrPattern.pattern
  }
}