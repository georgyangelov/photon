package photon.frontend

import photon.lib.LookAheadReader

import scala.reflect.ClassTag
import scala.util.control.Breaks._

class ParseError(message: String, location: Location) extends PhotonError(message, Some(location)) {}

object Parser {
  trait MacroHandler {
    def apply(name: String, parser: Parser, location: Location): Option[ASTValue]
  }

  val BlankMacroHandler: MacroHandler = (_: String, _: Parser, _: Location) => None
}

class Parser(
  private val lexer: Lexer,
  private val macroHandler: Parser.MacroHandler
) {
  val PARENS_FOR_BLOCKS = true

  val reader = new LookAheadReader(() => lexer.nextToken())

  var token: Token = _
  var atStart = true
  var lastLocation: Location = _
  var newline: Boolean = false

  def hasMoreTokens: Boolean = {
    if (atStart) read()

    token.tokenType != TokenType.EOF
  }

  def readToken(tokenType: TokenType, message: String) = {
    if (token.tokenType != tokenType) {
      parseError(message)
    }

    read()
  }

  def parseAST[T <: ASTValue](implicit tag: ClassTag[T]): T =
    parseNext() match {
      case value: T => value
      case value => parseError(s"Read $value, expected $tag")
    }

  def skipNextToken(): Unit = read()

  def parseRoot(): ASTValue = {
    val values = parseAll()
    val startLocation = lastLocation

    values.size match {
      case 1 => values.head
      case 0 => ASTValue.Block(values, Some(startLocation.extendWith(lastLocation)))
    }
  }

  def parseAll(): Seq[ASTValue] = {
    val values = Vector.newBuilder[ASTValue]

    while (hasMoreTokens) {
      values += parseCompleteExpression()
    }

    values.result
  }

  def parseCompleteExpression(): ASTValue = {
    if (atStart) read()

    val expression = assertASTValue(
      parseExpression(requireCallParens = false, hasLowerPriorityTarget = false)
    )

    if (token.tokenType != TokenType.EOF && !newline) {
      parseError("Expected newline or semicolon")
    }

    expression
  }

  def parseNext(requireCallParens: Boolean = false): ASTValue = {
    if (atStart) read()

    assertASTValue(
      parseExpression(requireCallParens = requireCallParens, hasLowerPriorityTarget = false)
    )
  }

  private def parseExpression(
    minPrecedence: Int = 0,
    requireCallParens: Boolean,
    hasLowerPriorityTarget: Boolean
  ): ASTValueOrPattern = {
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
      val location = left.location.get.extendWith(right.location.get)

      left = if (operator.tokenType == TokenType.Colon) {
        ASTValueOrPattern.Value(ASTValue.Call(
          target = ASTValue.NameReference("Core", Some(location)),
          name = "typeCheck",
          arguments = ASTArguments(Seq(left, right).map(assertASTValue), Map.empty),
          mayBeVarCall = false,
          location = Some(location)
        ))
      } else if (right.isInstanceOf[ASTValueOrPattern.Pattern]) {
        ASTValueOrPattern.Pattern(Pattern.Call(
          target = assertASTValue(left),
          name = operator.string,
          arguments = ArgumentsWithoutSelf(Seq(coerceToPattern(right)), Map.empty),
          mayBeVarCall = false,
          Some(location)
        ))
      } else {
        ASTValueOrPattern.Value(ASTValue.Call(
          target = assertASTValue(left),
          name = operator.string,
          arguments = ASTArguments(Seq(assertASTValue(right)), Map.empty),
          mayBeVarCall = false,
          Some(location)
        ))
      }
    }

    throw new RuntimeException("This should not happen")
  }

  private def parsePrimary(requireCallParens: Boolean, hasLowerPriorityTarget: Boolean): ASTValueOrPattern = {
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
          Some(startLocation.extendWith(name.location))
        ))
      }

      val typ =
        if (token.tokenType == TokenType.Colon) {
          read() // :

          Some(assertASTValue(parseExpression(requireCallParens = true, hasLowerPriorityTarget = false)))
        } else None

      if (token.tokenType != TokenType.Equals) {
        parseError("Val needs to have an =")
      }
      val equals = read() // =

      val value = assertASTValue(parseExpression(operatorPrecedence(equals) + 1, requireCallParens, hasLowerPriorityTarget))

      val valueWithType = typ match {
        case Some(typ) => ASTValue.Call(
          target = ASTValue.NameReference("Core", typ.location),
          name = "typeCheck",
          arguments = ASTArguments(Seq(value, typ), Map.empty),
          mayBeVarCall = false,
          location = typ.location
        )
        case None => value
      }

      val body = parseRestOfBlock()
      val location = startLocation.extendWith(body.location.get)

      return ASTValueOrPattern.Value(ASTValue.Let(
        name.string,
        valueWithType,
        body,
        Some(location)
      ))
    }

    if (token.tokenType == TokenType.BinaryOperator && token.string == "-") {
      val startLocation = token.location
      read() // -

      val expression = assertASTValue(parsePrimary(requireCallParens, hasLowerPriorityTarget))
      val location = startLocation.extendWith(expression.location.get)

      return ASTValueOrPattern.Value(ASTValue.Call(
        target = expression,
        name = "-",
        arguments = ASTArguments.empty,
        mayBeVarCall = false,
        Some(location)
      ))
    }

    var target = parseCallTarget(requireCallParens, hasLowerPriorityTarget)

    while (true) {
      tryToParseCall(target, requireCallParens, hasLowerPriorityTarget) match {
        case None => return target
        case Some(newTarget) => target = newTarget
      }
    }

    target
  }

  private def parseCallTarget(requireCallParens: Boolean, hasLowerPriorityTarget: Boolean): ASTValueOrPattern = {
    token.tokenType match {
      case TokenType.BoolLiteral => ASTValueOrPattern.Value(parseBool())
      case TokenType.NumberLiteral => ASTValueOrPattern.Value(parseNumber())
      case TokenType.StringLiteral => ASTValueOrPattern.Value(parseString())
      case TokenType.Name =>
        val token = read()

        ASTValueOrPattern.Value(
          macroHandler.apply(token.string, this, token.location) getOrElse {
            ASTValue.NameReference(token.string, Some(lastLocation))
          }
        )

      case TokenType.OpenBrace => parseLambdaOrLambdaType(hasLowerPriorityTarget)
      case TokenType.UnaryOperator => ASTValueOrPattern.Value(
        parseUnaryOperator(requireCallParens, hasLowerPriorityTarget)
      )

      case TokenType.OpenParen =>
        if (isOpenParenForLambda) {
          parseLambdaOrLambdaType(hasLowerPriorityTarget)
        } else {
          read() // (

          val value = if (PARENS_FOR_BLOCKS) {
            val valueBuilder = Seq.newBuilder[ASTValueOrPattern]
            val startLocation = lastLocation

            do {
              valueBuilder.addOne(parseExpression(requireCallParens = false, hasLowerPriorityTarget = false))
            } while (token.tokenType != TokenType.CloseParen && newline)

            if (token.tokenType != TokenType.CloseParen) {
              parseError("Unmatched parentheses or extra expressions. Expected ')'")
            }

            val values = valueBuilder.result

            if (values.size == 1) {
              values.head
            } else {
              ASTValueOrPattern.Value(
                ASTValue.Block(values.map(assertASTValue), Some(startLocation.extendWith(token.location)))
              )
            }
          } else {
            parseExpression(requireCallParens = false, hasLowerPriorityTarget = false)
          }

          if (token.tokenType != TokenType.CloseParen) {
            parseError("Unmatched parentheses or extra expressions. Expected ')'")
          }

          read() // )

          value
        }

      case _ => parseError()
    }
  }

  private def isOpenParenForLambda: Boolean = {
    val reader = this.reader.lookAhead()
    var nestedParenLevel = 1

    var token = reader.nextToken() // (

    while (nestedParenLevel > 0) {
      if (token.tokenType == TokenType.EOF) {
        return false
      }

      token.tokenType match {
        case TokenType.OpenParen => nestedParenLevel += 1
        case TokenType.CloseParen => nestedParenLevel -= 1
        case _ => ()
      }

      token = reader.nextToken()
    }

    token.tokenType match {
      case TokenType.EOF => false
      case TokenType.NewLine => false

      // This is intentionally false, because it is ambiguous:
      // (thisIsAFunction)(42)
      // (function.call + something)(42)
      // (argument) (42 + argument)
      case TokenType.OpenParen => false

      case TokenType.CloseParen => false
      case TokenType.OpenBrace => true
      case TokenType.CloseBrace => false
      case TokenType.OpenBracket => true
      case TokenType.CloseBracket => false
      case TokenType.Comma => false
      case TokenType.Dot => false
      case TokenType.Equals => false

      // This is for return type of lambdas. For example:
      // (1 + 2): Int
      // (a: Int): Int { a + 42 }
      case TokenType.Colon => true

      case TokenType.Dollar => true
      case TokenType.BinaryOperator => false
      case TokenType.Val => false
      case TokenType.Name => true
      case TokenType.NumberLiteral => true
      case TokenType.StringLiteral => true
      case TokenType.BoolLiteral => true

      // TODO: Are these correct?
      case TokenType.UnaryOperator => true
    }
  }

  private def tryToParseCall(target: ASTValueOrPattern, requireCallParens: Boolean, hasLowerPriorityTarget: Boolean): Option[ASTValueOrPattern] = {
    // target.call
    if (token.tokenType == TokenType.Dot) {
      if (token.hadWhitespaceBefore && hasLowerPriorityTarget) {
        // array.map { 42 } .filter (x) x > 0
        return None
      }

      read() // .

      val canBeAMethodName =
        token.tokenType == TokenType.Name ||
        token.tokenType == TokenType.BinaryOperator ||
        token.tokenType == TokenType.UnaryOperator

      if (!canBeAMethodName) parseError("Expected method name")

      val name = read()
      val arguments = parseArguments(requireCallParens, hasLowerPriorityTarget = true)
      val isPattern = arguments.exists(_._2.isInstanceOf[ASTValueOrPattern.Pattern])

      if (isPattern) {
        return Some(ASTValueOrPattern.Pattern(Pattern.Call(
          assertASTValue(target),
          name.string,
          toPatternArguments(arguments),
          mayBeVarCall = false,
          location = target.location.map(_.extendWith(lastLocation))
        )))
      } else {
        return Some(ASTValueOrPattern.Value(ASTValue.Call(
          assertASTValue(target),
          name.string,
          toArguments(arguments),
          mayBeVarCall = false,
          location = target.location.map(_.extendWith(lastLocation))
        )))
      }
    }

    // name a
    // name(a)
    target match {
      case ASTValueOrPattern.Value(ASTValue.NameReference(name, targetLocation)) =>
        val isDefinitelyACall = token.tokenType == TokenType.OpenParen

        if (!currentExpressionMayEnd && (!requireCallParens || isDefinitelyACall)) {
          val arguments = parseArguments(requireCallParens, hasLowerPriorityTarget = true)
          val isPattern = arguments.exists(_._2.isInstanceOf[ASTValueOrPattern.Pattern])

          if (isPattern) {
            return Some(ASTValueOrPattern.Pattern(Pattern.Call(
              target = ASTValue.NameReference("self", targetLocation),
              name,
              toPatternArguments(arguments),
              mayBeVarCall = true,
              location = targetLocation.map(_.extendWith(lastLocation))
            )))
          } else {
            return Some(ASTValueOrPattern.Value(ASTValue.Call(
              target = ASTValue.NameReference("self", targetLocation),
              name,
              toArguments(arguments),
              mayBeVarCall = true,
              location = targetLocation.map(_.extendWith(lastLocation))
            )))
          }
        }

      case _ => ()
    }

    // expression( ... )
    if (token.tokenType == TokenType.OpenParen && !token.hadWhitespaceBefore) {
      val arguments = parseArguments(requireCallParens, hasLowerPriorityTarget = false)
      val isPattern = arguments.exists(_._2.isInstanceOf[ASTValueOrPattern.Pattern])

      if (isPattern) {
        return Some(ASTValueOrPattern.Pattern(Pattern.Call(
          assertASTValue(target),
          name = "call",
          arguments = toPatternArguments(arguments),
          mayBeVarCall = false,
          target.location.map(_.extendWith(lastLocation))
        )))
      } else {
        return Some(ASTValueOrPattern.Value(ASTValue.Call(
          target = assertASTValue(target),
          name = "call",
          arguments = toArguments(arguments),
          mayBeVarCall = false,
          target.location.map(_.extendWith(lastLocation))
        )))
      }
    }

    None
  }

  private def parseArguments(requireParens: Boolean, hasLowerPriorityTarget: Boolean): Seq[(Option[String], ASTValueOrPattern)] = {
    var withParentheses = false

    if (token.tokenType == TokenType.OpenParen && !token.hadWhitespaceBefore) {
      read() // (
      withParentheses = true
    }

    if (!withParentheses && currentExpressionMayEnd) {
      return Seq.empty
    }

    if (!withParentheses && requireParens) {
      return Seq.empty
    }

    if (withParentheses && token.tokenType == TokenType.CloseParen) {
      read() // )
      return Seq.empty
    }


    val arguments = Seq.newBuilder[(Option[String], ASTValueOrPattern)]

    arguments.addOne(parseArgument(hasLowerPriorityTarget = hasLowerPriorityTarget && !withParentheses))
    while (token.tokenType == TokenType.Comma) {
      read() // ,
      arguments.addOne(parseArgument(hasLowerPriorityTarget = hasLowerPriorityTarget && !withParentheses))
    }

    if (withParentheses) {
      if (token.tokenType != TokenType.CloseParen) {
        parseError(s"Expected CloseParen ')'")
      }

      read() // )
    } else if (!currentExpressionMayEnd) {
      parseError("Expected current expression to end (either new line or ')')")
    }

    arguments.result
  }

  private def toArguments(args: Seq[(Option[String], ASTValueOrPattern)]): ASTArguments = {
    val (positional, named) = args
      .map { case name -> value => name -> assertASTValue(value) }
      .partition(_._1.isEmpty)

    ASTArguments(
      positional.map(_._2),
      named.map { case (name, value) => name.get -> value }.toMap
    )
  }

  private def toPatternArguments(args: Seq[(Option[String], ASTValueOrPattern)]): ArgumentsWithoutSelf[Pattern] = {
    val patternArgs = args.map { case (name, value) => name -> coerceToPattern(value) }
    val (positional, named) = patternArgs.partition(_._1.isEmpty)

    ArgumentsWithoutSelf(
      positional.map(_._2),
      named.map { case (name, value) => name.get -> value }.toMap
    )
  }

  private def parseArgument(hasLowerPriorityTarget: Boolean): (Option[String], ASTValueOrPattern) = {
    val name = if (isNamedArgument) {
      val name = read()

      read() // =

      Some(name.string)
    } else { None }

    val value = parseExpression(requireCallParens = false, hasLowerPriorityTarget = hasLowerPriorityTarget)

    (name, value)
  }

  private def isNamedArgument: Boolean = {
    val reader = this.reader.lookAhead()
    val hasName = token.tokenType == TokenType.Name

    if (!hasName) {
      return false
    }

    val nextToken = reader.nextToken()

    nextToken.tokenType == TokenType.Equals
  }

  private def parseBool(): ASTValue.Boolean = {
    val token = read()

    ASTValue.Boolean(token.string.equalsIgnoreCase("true"), Some(token.location))
  }

  private def parseNumber(): ASTValue = {
    val token = read()

    if (token.string.contains(".")) {
      ASTValue.Float(token.string.toDouble, Some(token.location))
    } else {
      ASTValue.Int(token.string.toInt, Some(token.location))
    }
  }

  private def parseString(): ASTValue.String = {
    val token = read()

    ASTValue.String(token.string, Some(token.location))
  }

  private def parseLambdaOrLambdaType(hasLowerPriorityTarget: Boolean): ASTValueOrPattern = {
    // This aims to fix parse of lambdas using only braces on a separate line, e.g. `{ a }`
    // Since there was a newline before, but we don't care
    newline = false

    val startLocation = lastLocation

    val hasParameterParens = token.tokenType == TokenType.OpenParen
    val parameters = if (hasParameterParens) {
      parseLambdaParameters()
    } else {
      Seq.empty
    }

    val hasReturnType = token.tokenType == TokenType.Colon
    val returnType = if (hasReturnType) {
      read() // :

      Some(parseExpression(requireCallParens = true, hasLowerPriorityTarget = false))
    } else {
      None
    }

    val hasBody = !currentExpressionMayEnd

    if (!hasBody) {
      val returns = returnType.getOrElse { parseError("Function types need to have explicit return type") }

      val location = Some(startLocation.extendWith(lastLocation))

      val hasPatternArguments = parameters.exists { param =>
        param.typePattern match {
          case Some(_: Pattern.SpecificValue) => false
          case Some(_: Pattern) => true
          case None => parseError("Function type needs to have defined parameter types")
        }
      }

      val hasReturnTypePattern = returns.isInstanceOf[ASTValueOrPattern.Pattern]

      if (hasPatternArguments || hasReturnTypePattern) {
        val typeParams = parameters.map { param =>
          ASTPatternParameter(
            name = param.outName,
            typ = param.typePattern.get,
            location = param.location
          )
        }

        val returnTypePattern = coerceToPattern(returns)

        return ASTValueOrPattern.Pattern(Pattern.FunctionType(typeParams, returnTypePattern, location))
      } else {
        val typeParameters = parameters.map { param =>
          ASTTypeParameter(
            name = param.outName,
            typ = param.typePattern match {
              case Some(Pattern.SpecificValue(value)) => value
              case Some(_) => parseError("Function type cannot use patterns in types of parameters")
              case _ => parseError("Function type needs to have defined parameter types")
            },
            location = param.location
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

    ASTValueOrPattern.Value(
      ASTValue.Function(
        parameters,
        body,
        returnType.map(assertASTValue),
        Some(startLocation.extendWith(lastLocation))
      )
    )
  }

  private def parseLambdaParameters(): Seq[ASTParameter] = {
    val parameters = Vector.newBuilder[ASTParameter]

    read() // (

    val hasArguments = token.tokenType != TokenType.CloseParen

    if (hasArguments) {
      breakable {
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

            Some(parseExpression(requireCallParens = true, hasLowerPriorityTarget = false))
          } else {
            None
          }

          parameters.addOne(
            ASTParameter(outName, inName, typeValue.map(coerceToPattern), Some(startLocation.extendWith(lastLocation)))
          )

          if (token.tokenType != TokenType.Comma) break
          read() // ,
        }
      }
    }

    if (token.tokenType != TokenType.CloseParen) parseError("Expected CloseParen ')'")
    read() // )

    parameters.result
  }

  private def parseBlock() = {
    val values = Vector.newBuilder[ASTValue]
    val startLocation = lastLocation

    while (token.tokenType != TokenType.CloseBrace) {
      values += assertASTValue(parseExpression(requireCallParens = false, hasLowerPriorityTarget = false))
    }

    val valuesResult = values.result

    if (valuesResult.length == 1) {
      valuesResult.head
    } else {
      ASTValue.Block(values.result, Some(startLocation.extendWith(lastLocation)))
    }
  }

  def parseRestOfBlock() = {
    val values = Vector.newBuilder[ASTValue]
    val startLocation = lastLocation

    while (token.tokenType != TokenType.CloseBrace && token.tokenType != TokenType.CloseParen && token.tokenType != TokenType.EOF) {
      values += assertASTValue(parseExpression(requireCallParens = false, hasLowerPriorityTarget = false))
    }

    val valuesResult = values.result

    if (valuesResult.length == 1) {
      valuesResult.head
    } else {
      ASTValue.Block(values.result, Some(startLocation.extendWith(lastLocation)))
    }
  }

  private def parseUnaryOperator(requireCallParens: Boolean, hasLowerPriorityTarget: Boolean): ASTValue = {
    val operator = read()
    val target = parsePrimary(requireCallParens, hasLowerPriorityTarget)

    ASTValue.Call(
      assertASTValue(target),
      name = operator.string,
      arguments = ASTArguments.empty,
      mayBeVarCall = false,
      location = Some(operator.location.extendWith(lastLocation))
    )
  }

  private def currentExpressionMayEnd: Boolean =
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

  private def operatorPrecedence(token: Token): Int = {
    token.string match {
      case "="   => 1
      case "or"  => 2
      case "and" => 3
      case "==" | "<" | ">" | "<=" | ">=" | "!=" => 4
      case "+" | "-" => 5
      case "*" | "/" => 6
      case ":" => 7
      case _ => throw new RuntimeException(s"Unknown operator '${token.string}'")
    }
  }

  private def read(): Token = {
    newline = false

    val oldToken = token
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

    oldToken
  }

  def parseError(explanation: String = "") = {
    throw new ParseError(
      s"Unexpected token ${token.tokenType.name} '${token.string}'. $explanation".strip(),
      token.location
    )
  }

  private def assertASTValue(valueOrPattern: ASTValueOrPattern): ASTValue = valueOrPattern match {
    case ASTValueOrPattern.Value(value) => value
    case ASTValueOrPattern.Pattern(pattern) => throw new ParseError(
      s"Cannot use pattern in this context (pattern was $pattern)",
      pattern.location.get
    )
  }

  private def coerceToPattern(valueOrPattern: ASTValueOrPattern): Pattern = valueOrPattern match {
    case ASTValueOrPattern.Value(value) => Pattern.SpecificValue(value)
    case ASTValueOrPattern.Pattern(pattern) => pattern
  }

  private def areAnyPatterns(valuesOrPatterns: ASTValueOrPattern*) =
    valuesOrPatterns.exists(_.isInstanceOf[ASTValueOrPattern.Pattern])
}
