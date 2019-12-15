package photon

import photon.Operation.Block

import scala.collection.immutable.ListMap
import scala.util.control.Breaks._

class ParseError(message: String, location: Location) extends PhotonError(message, Some(location)) {}

object Parser {
  trait MacroHandler {}
  val BlankMacroHandler: MacroHandler = new MacroHandler {}
}

class Parser(
  private val lexer: Lexer,
  private val macroHandler: Parser.MacroHandler
) {
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
      tokens += parseOne()
    }

    tokens.result
  }

  def parseOne(): Value = {
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
          arguments = Vector(right),
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
        arguments = Vector(),
        mayBeVarCall = false
      ), Some(location))
    }

    var target = parseCallTarget()

    while (true) {
      target = tryToParseCall(target)

      val isFollowedByMethodCall = token.tokenType == TokenType.Dot
      val isFollowedByAnotherArgumentList = !newline && token.tokenType == TokenType.OpenParen

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

        // TODO: Macros

        Value.Operation(Operation.NameReference(token.string), Some(lastLocation))

      case TokenType.Dollar => parseStruct()
      case TokenType.OpenBrace => parseLambda()
      case TokenType.UnaryOperator => parseUnaryOperator()
      case TokenType.OpenParen =>
        read() // (
        val value = parseOne()
        if (token.tokenType != TokenType.CloseParen) {
          parseError("Unmatched parentheses or extra expressions. Expected ')'")
        }
        read() // )

        value

      case _ => parseError()
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
        name = "$call",
        arguments = arguments,
        mayBeVarCall = false
      ), target.location.map(_.extendWith(lastLocation)))
    }

    target
  }

  private def parseArguments(): Seq[Value] = {
    var withParentheses = false

    if (token.tokenType == TokenType.OpenParen) {
      read() // (
      withParentheses = true
    }

    if (!withParentheses && currentExpressionMayEnd) {
      return Seq.empty
    }

    if (withParentheses && token.tokenType == TokenType.CloseParen) {
      read() // )
      return Seq.empty
    }

    val values = parseASTList()

    if (withParentheses) {
      if (token.tokenType != TokenType.CloseParen) {
        parseError(s"Expected CloseParen ')'")
      }

      read() // )
    } else if (!currentExpressionMayEnd) {
      parseError("Expected current expression to end (either new line or ')')")
    }

    values
  }

  private def parseASTList(): Seq[Value] = {
    var values = Vector.newBuilder[Value]
    var value = parseOne()

    values += value

    while (token.tokenType == TokenType.Comma) {
      read() // ,

      value = parseOne()
      values += value
    }

    values.result
  }

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

        if (token.tokenType != TokenType.Colon) parseError("Expected Colon ':'")
        read() // :

        val value = parseOne()

        map.addOne(key, value)

        if (token.tokenType != TokenType.Comma) break
        read() // ,
      }
    }

    map.result
  }

  private def parseLambda(): Value.Lambda = {
    read() // {

    val startLocation = lastLocation

    val parameters = if (token.tokenType == TokenType.Pipe) {
      parseLambdaParameters()
    } else {
      Seq.empty
    }

    val body = parseBlock()

    if (token.tokenType != TokenType.CloseBrace) parseError("Expected CloseBrace '}'")
    read() // }

    Value.Lambda(
      Lambda(parameters, body),
      Some(startLocation.extendWith(lastLocation))
    )
  }

  private def parseLambdaParameters(): Seq[String] = {
    val names = Vector.newBuilder[String]

    read() // |

    breakable {
      while (true) {
        if (token.tokenType != TokenType.Name) parseError("Expected Name")

        names += read().string

        if (token.tokenType != TokenType.Comma) break
        read() // ,
      }
    }

    if (token.tokenType != TokenType.Pipe) parseError("Expected Pipe '|'")
    read() // |

    names.result
  }

  private def parseBlock(): Operation.Block = {
    val values = Vector.newBuilder[Value]

    while (token.tokenType != TokenType.CloseBrace) {
      values += parseOne()
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
        arguments = Seq.empty,
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
    var nextToken = lexer.nextToken()

    while (nextToken.tokenType == TokenType.NewLine) {
      newline = true
      nextToken = lexer.nextToken()
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
      s"Unexpected token ${token.tokenType.name} '${token.string}. $explanation".strip(),
      token.location
    )
  }
}
