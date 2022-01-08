package photon.frontend

import photon.lib.LookAheadReader
import photon.{Location, PhotonError}

import scala.collection.immutable.ListMap
import scala.util.control.Breaks._

class ParseError(message: String, location: Location) extends PhotonError(message, Some(location)) {}

object Parser {
  trait MacroHandler {
    def apply(name: String, parser: Parser): Option[ASTValue]
  }

  val BlankMacroHandler: MacroHandler = (_: String, _: Parser) => None
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

  def skipNextToken(): Unit = read()

  def parseAll(): Seq[ASTValue] = {
    val tokens = Vector.newBuilder[ASTValue]

    while (hasMoreTokens) {
      tokens += parseCompleteExpression()
    }

    tokens.result
  }

  def parseCompleteExpression(): ASTValue = {
    if (atStart) read()

    val expression = parseExpression(requireCallParens = false)

    if (token.tokenType != TokenType.EOF && !newline) {
      parseError("Expected newline or semicolon")
    }

    expression
  }

  def parseNext(): ASTValue = {
    if (atStart) read()

    parseExpression(requireCallParens = false)
  }

  private def parseExpression(minPrecedence: Int = 0, requireCallParens: Boolean): ASTValue = {
    var left = parsePrimary(requireCallParens)

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
      val right = parseExpression(precedence + 1, requireCallParens)
      val location = left.location.get.extendWith(right.location.get)

      left = if (operator.string == "=") {
        left match {
          case ASTValue.NameReference(name, _) =>
            val body = parseRestOfBlock()

            ASTValue.Let(
              name,
              right,
              body,
              Some(location)
            )
          case _ => throw new ParseError("Left side of assignment must have a name", location)
        }
      } else if (operator.tokenType == TokenType.Colon) {
        ASTValue.Call(
          target = ASTValue.NameReference("Core", Some(location)),
          name = "typeCheck",
          arguments = ASTArguments(Seq(left, right), Map.empty),
          mayBeVarCall = false,
          location = Some(location)
        )
      } else {
        ASTValue.Call(
          target = left,
          name = operator.string,
          arguments = ASTArguments(Seq(right), Map.empty),
          mayBeVarCall = false,
          Some(location)
        )
      }
    }

    throw new RuntimeException("This should not happen")
  }

  private def parsePrimary(requireCallParens: Boolean): ASTValue = {
    if (token.tokenType == TokenType.BinaryOperator && token.string == "-") {
      val startLocation = token.location
      read() // -

      val expression = parsePrimary(requireCallParens)
      val location = startLocation.extendWith(expression.location.get)

      return ASTValue.Call(
        target = expression,
        name = "-",
        arguments = ASTArguments.empty,
        mayBeVarCall = false,
        Some(location)
      )
    }

    var target = parseCallTarget(requireCallParens)

    while (true) {
      target = tryToParseCall(target, requireCallParens)

      val isFollowedByMethodCall = token.tokenType == TokenType.Dot
      val isFollowedByAnotherArgumentList = !newline && !token.hadWhitespaceBefore && token.tokenType == TokenType.OpenParen

      if (!isFollowedByMethodCall && !isFollowedByAnotherArgumentList) {
        return target
      }
    }

    target
  }

  private def parseCallTarget(requireCallParens: Boolean): ASTValue = {
    token.tokenType match {
      case TokenType.BoolLiteral => parseBool()
      case TokenType.NumberLiteral => parseNumber()
      case TokenType.StringLiteral => parseString()
      case TokenType.Name =>
        val token = read()

        macroHandler.apply(token.string, this) getOrElse {
          ASTValue.NameReference(token.string, Some(lastLocation))
        }

      case TokenType.OpenBrace => parseLambda()
      case TokenType.UnaryOperator => parseUnaryOperator(requireCallParens)
      case TokenType.OpenParen =>
        if (isOpenParenForLambda) {
          parseLambda()
        } else {
          read() // (

          val value = if (PARENS_FOR_BLOCKS) {
            val valueBuilder = Seq.newBuilder[ASTValue]
            val startLocation = lastLocation

            do {
              valueBuilder.addOne(parseExpression(requireCallParens = false))
            } while (token.tokenType != TokenType.CloseParen && newline)

            if (token.tokenType != TokenType.CloseParen) {
              parseError("Unmatched parentheses or extra expressions. Expected ')'")
            }

            val values = valueBuilder.result

            if (values.size == 1) {
              values.head
            } else {
              ASTValue.Block(values, Some(startLocation.extendWith(token.location)))
            }
          } else {
            parseExpression(requireCallParens = false)
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
      // (thisIsAfunction)(42)
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

      // This is for return type of lambdas. For example:
      // (1 + 2): Int
      // (a: Int): Int { a + 42 }
      case TokenType.Colon => true

      case TokenType.Dollar => true
      case TokenType.BinaryOperator => false
      case TokenType.Name => true
      case TokenType.NumberLiteral => true
      case TokenType.StringLiteral => true
      case TokenType.BoolLiteral => true

      // TODO: Are these correct?
      case TokenType.Pipe => false
      case TokenType.DoubleColon => true
      case TokenType.UnaryOperator => true
    }
  }

  private def tryToParseCall(target: ASTValue, requireCallParens: Boolean): ASTValue = {
    val startLocation = token.location

    // target.call
    if (token.tokenType == TokenType.Dot) {
      read() // .

      val canBeAMethodName =
        token.tokenType == TokenType.Name ||
        token.tokenType == TokenType.BinaryOperator ||
        token.tokenType == TokenType.UnaryOperator

      if (!canBeAMethodName) parseError("Expected method name")

      val name = read()
      val arguments = parseArguments(requireCallParens)
      val location = startLocation.extendWith(lastLocation)

      return ASTValue.Call(
        target,
        name.string,
        arguments,
        mayBeVarCall = false,
        location = Some(location)
      )
    }

    // name a
    // name(a)
    target match {
      case ASTValue.NameReference(name, targetLocation) =>
        if (!requireCallParens && !currentExpressionMayEnd) {
          val arguments = parseArguments(requireCallParens)

          return ASTValue.Call(
            target = ASTValue.NameReference("self", targetLocation),
            name,
            arguments,
            mayBeVarCall = true,
            location = targetLocation.map(_.extendWith(lastLocation))
          )
        }

      case _ => ()
    }

    // expression( ... )
    if (token.tokenType == TokenType.OpenParen && !token.hadWhitespaceBefore) {
      val arguments = parseArguments(requireCallParens)

      return ASTValue.Call(
        target = target,
        name = "call",
        arguments = arguments,
        mayBeVarCall = false,
        target.location.map(_.extendWith(lastLocation))
      )
    }

    target
  }

  private def parseArguments(requireParens: Boolean): ASTArguments = {
    var withParentheses = false

    if (token.tokenType == TokenType.OpenParen && !token.hadWhitespaceBefore) {
      read() // (
      withParentheses = true
    }

    if (!withParentheses && currentExpressionMayEnd) {
      return ASTArguments.empty
    }

    if (!withParentheses && requireParens) {
      return ASTArguments.empty
    }

    if (withParentheses && token.tokenType == TokenType.CloseParen) {
      read() // )
      return ASTArguments.empty
    }

    val positionalArguments = Vector.newBuilder[ASTValue]
    val namedArguments = ListMap.newBuilder[String, ASTValue]

    var value = parseArgument()
    value match {
      case (Some(name), value) => namedArguments.addOne(name, value)
      case (None, value) => positionalArguments.addOne(value)
    }

    while (token.tokenType == TokenType.Comma) {
      read() // ,

      value = parseArgument()
      value match {
        case (Some(name), value) => namedArguments.addOne(name, value)
        case (None, value) => positionalArguments.addOne(value)
      }
    }

    if (withParentheses) {
      if (token.tokenType != TokenType.CloseParen) {
        parseError(s"Expected CloseParen ')'")
      }

      read() // )
    } else if (!currentExpressionMayEnd) {
      parseError("Expected current expression to end (either new line or ')')")
    }

    ASTArguments(positionalArguments.result(), namedArguments.result())
  }

  private def parseArgument(): (Option[String], ASTValue) = {
    val name = if (isNamedArgument) {
      val name = read()

      read() // =

      Some(name.string)
    } else { None }

    val value = parseExpression(requireCallParens = false)

    (name, value)
  }

  private def isNamedArgument: Boolean = {
    val reader = this.reader.lookAhead()
    val hasName = token.tokenType == TokenType.Name

    if (!hasName) {
      return false
    }

    val nextToken = reader.nextToken()
    val hasEqualsSign = nextToken.tokenType == TokenType.BinaryOperator && nextToken.string == "="

    hasEqualsSign
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

  private def parseLambda(): ASTValue = {
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

      Some(parseExpression(requireCallParens = true))
    } else {
      None
    }

    val hasBlock = token.tokenType == TokenType.OpenBrace
    val body = if (hasBlock) {
      read() // {

      val block = parseBlock()

      if (token.tokenType != TokenType.CloseBrace) parseError("Expected CloseBrace '}'")
      read() // }

      block
    } else {
      parseExpression(requireCallParens = false)
    }

    ASTValue.Function(
      parameters,
      body,
      returnType,
      Some(startLocation.extendWith(lastLocation))
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

          val name = read().string
          val startLocation = lastLocation
          val typeValue = if (token.tokenType == TokenType.Colon) {
            read() // :

            Some(parseExpression(requireCallParens = false))
          } else {
            None
          }

          parameters.addOne(ASTParameter(name, typeValue, Some(lastLocation.extendWith(startLocation))))

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
      values += parseExpression(requireCallParens = false)
    }

    val valuesResult = values.result

    if (valuesResult.length == 1) {
      valuesResult.head
    } else {
      ASTValue.Block(values.result, Some(startLocation.extendWith(lastLocation)))
    }
  }

  private def parseRestOfBlock() = {
    val values = Vector.newBuilder[ASTValue]
    val startLocation = lastLocation

    while (token.tokenType != TokenType.CloseBrace && token.tokenType != TokenType.CloseParen && token.tokenType != TokenType.EOF) {
      values += parseExpression(requireCallParens = false)
    }

    val valuesResult = values.result

    if (valuesResult.length == 1) {
      valuesResult.head
    } else {
      ASTValue.Block(values.result, Some(startLocation.extendWith(lastLocation)))
    }
  }

  private def parseUnaryOperator(requireCallParens: Boolean): ASTValue = {
    val operator = read()
    val target = parsePrimary(requireCallParens)

    ASTValue.Call(
      target,
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
    token.tokenType == TokenType.DoubleColon ||
    token.tokenType == TokenType.CloseBracket ||
    token.tokenType == TokenType.Pipe ||
    token.tokenType == TokenType.CloseBrace

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

  private def parseError(explanation: String = "") = {
    throw new ParseError(
      s"Unexpected token ${token.tokenType.name} '${token.string}'. $explanation".strip(),
      token.location
    )
  }
}
