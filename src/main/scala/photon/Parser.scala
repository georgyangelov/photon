package photon

import photon.Operation.Block
import photon.lib.LookAheadReader

import scala.collection.immutable.ListMap
import scala.collection.mutable
import scala.util.control.Breaks._

class ParseError(message: String, location: Location) extends PhotonError(message, Some(location)) {}

object Parser {
  trait MacroHandler {
    def apply(name: String, parser: Parser): Option[Value]
  }

  val BlankMacroHandler: MacroHandler = (_: String, _: Parser) => None
}

class Parser(
  private val lexer: Lexer,
  private val macroHandler: Parser.MacroHandler
) {
  val reader = new LookAheadReader(() => lexer.nextToken())

  var token: Token = _
  var atStart = true
  var lastLocation: Location = _
  var newline: Boolean = false

  def hasMoreTokens: Boolean = {
    if (atStart) read()

    token.tokenType != TokenType.EOF
  }

  def parseAll(): Seq[Value] = {
    val tokens = Vector.newBuilder[Value]

    while (hasMoreTokens) {
      tokens += parseCompleteExpression()
    }

    tokens.result
  }

  def parseCompleteExpression(): Value = {
    if (atStart) read()

    val expression = parseExpression()

    if (token.tokenType != TokenType.EOF && !newline) {
      parseError("Expected newline or semicolon")
    }

    expression
  }

  def parseNext(): Value = {
    if (atStart) read()

    parseExpression()
  }

  private def parseExpression(minPrecedence: Int = 0): Value = {
    var left = parsePrimary()

    while (true) {
      if (newline) return left

      if (token.tokenType != TokenType.BinaryOperator) {
        return left
      }

      val precedence = operatorPrecedence(token)
      if (precedence < minPrecedence) {
        return left
      }

      val operator = read()
      val right = parseExpression(precedence + 1)
      val location = left.location.get.extendWith(right.location.get)

      left = if (operator.string == "=") {
        left match {
          case Value.Operation(Operation.NameReference(name), _) =>
            Value.Operation(Operation.Assignment(
              name = name,
              value = right
            ), Some(location))
          case _ => throw new ParseError("Left side of assignment must have a name", location)
        }
      } else {
        Value.Operation(Operation.Call(
          target = left,
          name = operator.string,
          arguments = Arguments(Seq(right), Map.empty),
          mayBeVarCall = false
        ), Some(location))
      }
    }

    throw new RuntimeException("This should not happen")
  }

  private def parsePrimary(): Value = {
    if (token.tokenType == TokenType.BinaryOperator && token.string == "-") {
      val startLocation = token.location
      read() // -

      val expression = parsePrimary()
      val location = startLocation.extendWith(expression.location.get)

      return Value.Operation(Operation.Call(
        target = expression,
        name = "-",
        arguments = Arguments.empty,
        mayBeVarCall = false
      ), Some(location))
    }

    var target = parseCallTarget()

    while (true) {
      target = tryToParseCall(target)

      val isFollowedByMethodCall = token.tokenType == TokenType.Dot
      val isFollowedByAnotherArgumentList = !newline && !token.hadWhitespaceBefore && token.tokenType == TokenType.OpenParen

      if (!isFollowedByMethodCall && !isFollowedByAnotherArgumentList) {
        return target
      }
    }

    target
  }

  private def parseCallTarget(): Value = {
    token.tokenType match {
      case TokenType.BoolLiteral => parseBool()
      case TokenType.NumberLiteral => parseNumber()
      case TokenType.StringLiteral => parseString()
      case TokenType.UnknownLiteral => Value.Unknown(Some(read().location))
      case TokenType.Name =>
        val token = read()

        macroHandler.apply(token.string, this) getOrElse {
          Value.Operation(Operation.NameReference(token.string), Some(lastLocation))
        }

      case TokenType.Dollar => parseStruct()
      case TokenType.OpenBrace => parseLambda()
      case TokenType.UnaryOperator => parseUnaryOperator()
      case TokenType.OpenParen =>
        if (isOpenBraceForLambda) {
          parseLambda()
        } else {
          read() // (

          val value = parseExpression()

          if (token.tokenType != TokenType.CloseParen) {
            parseError("Unmatched parentheses or extra expressions. Expected ')'")
          }

          read() // )

          value
        }

      case _ => parseError()
    }
  }

  private def isOpenBraceForLambda: Boolean = {
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

      // TODO: Support return type for lambda
      // Examples:
      // (1 + 2): Int
      // (a: Int): Int { a + 42 }
      case TokenType.Colon => false

      case TokenType.Dollar => true
      case TokenType.BinaryOperator => false
      case TokenType.Name => true
      case TokenType.NumberLiteral => true
      case TokenType.StringLiteral => true
      case TokenType.BoolLiteral => true
      case TokenType.UnknownLiteral => true

      // TODO: Are these correct?
      case TokenType.Pipe => false
      case TokenType.DoubleColon => true
      case TokenType.UnaryOperator => true
    }
  }

  private def tryToParseCall(target: Value): Value = {
    val startLocation = token.location

    // target.call
    if (token.tokenType == TokenType.Dot) {
      read() // .

      if (token.tokenType != TokenType.Name) parseError("Expected name")

      val name = read()
      val arguments = parseArguments()
      val location = startLocation.extendWith(lastLocation)

      return Value.Operation(Operation.Call(
        target,
        name.string,
        arguments,
        mayBeVarCall = false
      ), Some(location))
    }

    // name a
    // name(a)
    target match {
      case Value.Operation(Operation.NameReference(name), targetLocation) =>
        if (!currentExpressionMayEnd) {
          val arguments = parseArguments()

          return Value.Operation(Operation.Call(
            target = Value.Operation(Operation.NameReference("self"), targetLocation),
            name,
            arguments,
            mayBeVarCall = true
          ), targetLocation.map(_.extendWith(lastLocation)))
        }

      case _ => ()
    }

    // expression( ... )
    if (token.tokenType == TokenType.OpenParen && !token.hadWhitespaceBefore) {
      val arguments = parseArguments()

      return Value.Operation(Operation.Call(
        target = target,
        name = "call",
        arguments = arguments,
        mayBeVarCall = false
      ), target.location.map(_.extendWith(lastLocation)))
    }

    target
  }

  private def parseArguments(): Arguments = {
    var withParentheses = false

    if (token.tokenType == TokenType.OpenParen && !token.hadWhitespaceBefore) {
      read() // (
      withParentheses = true
    }

    if (!withParentheses && currentExpressionMayEnd) {
      return Arguments.empty
    }

    if (withParentheses && token.tokenType == TokenType.CloseParen) {
      read() // )
      return Arguments.empty
    }

    val positionalArguments = Vector.newBuilder[Value]
    val namedArguments = ListMap.newBuilder[String, Value]

    var value = parseExpression()
    value match {
      case Value.Operation(Operation.Assignment(name, value), _) => namedArguments.addOne(name, value)
      case _ => positionalArguments.addOne(value)
    }

    while (token.tokenType == TokenType.Comma) {
      read() // ,

      value = parseExpression()
      value match {
        case Value.Operation(Operation.Assignment(name, value), _) => namedArguments.addOne(name, value)
        case _ => positionalArguments.addOne(value)
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

    Arguments(positionalArguments.result(), namedArguments.result())
  }

//  private def parseSingleArgument(): Either[Value, (String, Value)] = {
//    val expressionOrName = parseExpression()
//
//
//  }

//  private def parseASTList(): Seq[Value] = {
//    var values = Vector.newBuilder[Value]
//    var value = parseExpression()
//
//    values += value
//
//    while (token.tokenType == TokenType.Comma) {
//      read() // ,
//
//      value = parseExpression()
//      values += value
//    }
//
//    values.result
//  }

  private def parseBool(): Value.Boolean = {
    val token = read()

    Value.Boolean(token.string.equalsIgnoreCase("true"), Some(token.location))
  }

  private def parseNumber(): Value = {
    val token = read()

    if (token.string.contains(".")) {
      Value.Float(token.string.toDouble, Some(token.location))
    } else {
      Value.Int(token.string.toInt, Some(token.location))
    }
  }

  private def parseString(): Value.String = {
    val token = read()

    Value.String(token.string, Some(token.location))
  }

  private def parseStruct(): Value.Struct = {
    read() // $
    val startLocation = lastLocation

    if (token.tokenType != TokenType.OpenBrace) parseError("Expected OpenBrace '{'")
    read() // {

    val values = if (token.tokenType != TokenType.CloseBrace) {
      parseStructValues()
    } else {
      ListMap.empty[String, Value]
    }

    if (token.tokenType != TokenType.CloseBrace) parseError("Expected CloseBrace '}'")
    read() // }

    Value.Struct(
      Struct(values),
      Some(startLocation.extendWith(lastLocation))
    )
  }

  private def parseStructValues(): ListMap[String, Value] = {
    val map = ListMap.newBuilder[String, Value]

    breakable {
      while (true) {
        if (token.tokenType != TokenType.Name) parseError("Expected name")
        val key = read().string

        if (token.tokenType != TokenType.BinaryOperator || token.string != "=") parseError("Expected equals sign '='")
        read() // =

        val value = parseExpression()

        map.addOne(key, value)

        if (token.tokenType != TokenType.Comma) break
        read() // ,
      }
    }

    map.result
  }

  private def parseLambda(): Value.Lambda = {
    val startLocation = lastLocation

    val hasParameterParens = token.tokenType == TokenType.OpenParen
    val parameters = if (hasParameterParens) {
      parseLambdaParameters()
    } else {
      Seq.empty
    }

    val hasBlock = token.tokenType == TokenType.OpenBrace
    val body = if (hasBlock) {
      read() // {

      val block = parseBlock()

      if (token.tokenType != TokenType.CloseBrace) parseError("Expected CloseBrace '}'")
      read() // }

      block
    } else {
      Block(Seq(parseExpression()))
    }

    Value.Lambda(
      Lambda(parameters, None, body),
      Some(startLocation.extendWith(lastLocation))
    )
  }

  private def parseLambdaParameters(): Seq[String] = {
    val names = Vector.newBuilder[String]

    read() // (

    val hasArguments = token.tokenType != TokenType.CloseParen

    if (hasArguments) {
      breakable {
        while (true) {
          if (token.tokenType != TokenType.Name) parseError("Expected Name")

          names += read().string

          if (token.tokenType != TokenType.Comma) break
          read() // ,
        }
      }
    }

    if (token.tokenType != TokenType.CloseParen) parseError("Expected CloseParen ')'")
    read() // )

    names.result
  }

  private def parseBlock(): Operation.Block = {
    val values = Vector.newBuilder[Value]

    while (token.tokenType != TokenType.CloseBrace) {
      values += parseExpression()
    }

    Block(values.result)
  }

  private def parseUnaryOperator(): Value.Operation = {
    val operator = read()
    val target = parsePrimary()

    Value.Operation(
      Operation.Call(
        target,
        name = operator.string,
        arguments = Arguments.empty,
        mayBeVarCall = false
      ),
      Some(operator.location.extendWith(lastLocation))
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
    blockEnds

  private def blockEnds: Boolean = token.tokenType == TokenType.CloseBrace

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
