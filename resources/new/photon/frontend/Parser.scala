package photon.frontend

import photon.base._
import photon.lib.LookAheadReader

import scala.collection.immutable.ListMap
import scala.collection.mutable
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
  val letNameStack = mutable.Stack.empty[String]

  var token: Token = _
  var atStart = true
  var lastLocation: Location = _
  var newline: Boolean = false

  def hasMoreTokens: Boolean = {
    if (atStart) read()

    token.tokenType != TokenType.EOF
  }

  def currentLetName = letNameStack.top

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

    val expression = parseExpression(requireCallParens = false, hasLowerPriorityTarget = false)

    if (token.tokenType != TokenType.EOF && !newline) {
      parseError("Expected newline or semicolon")
    }

    expression
  }

  def parseNext(requireCallParens: Boolean = false): ASTValue = {
    if (atStart) read()

    parseExpression(requireCallParens = requireCallParens, hasLowerPriorityTarget = false)
  }

  private def parseExpression(
    minPrecedence: Int = 0,
    requireCallParens: Boolean,
    hasLowerPriorityTarget: Boolean
  ): ASTValue = {
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

  private def parsePrimary(requireCallParens: Boolean, hasLowerPriorityTarget: Boolean): ASTValue = {
    if (token.tokenType == TokenType.Val) {
      val startLocation = token.location
      read() // val

      if (token.tokenType != TokenType.Name) {
        parseError("`val` needs to be followed by a name")
      }
      val name = read()

      val typ =
        if (token.tokenType == TokenType.Colon) {
          read() // :

          Some(parseExpression(requireCallParens = true, hasLowerPriorityTarget = false))
        } else None

      if (token.tokenType != TokenType.Equals) {
        parseError("Val needs to have an =")
      }
      val equals = read() // =

      letNameStack.push(name.string)
      val value = try {
        parseExpression(operatorPrecedence(equals) + 1, requireCallParens, hasLowerPriorityTarget)
      } finally {
        letNameStack.pop()
      }

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

      return ASTValue.Let(
        name.string,
        valueWithType,
        body,
        Some(location)
      )
    }

    if (token.tokenType == TokenType.BinaryOperator && token.string == "-") {
      val startLocation = token.location
      read() // -

      val expression = parsePrimary(requireCallParens, hasLowerPriorityTarget)
      val location = startLocation.extendWith(expression.location.get)

      return ASTValue.Call(
        target = expression,
        name = "-",
        arguments = ASTArguments.empty,
        mayBeVarCall = false,
        Some(location)
      )
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

  private def parseCallTarget(requireCallParens: Boolean, hasLowerPriorityTarget: Boolean): ASTValue = {
    token.tokenType match {
      case TokenType.BoolLiteral => parseBool()
      case TokenType.NumberLiteral => parseNumber()
      case TokenType.StringLiteral => parseString()
      case TokenType.Name =>
        val token = read()

        macroHandler.apply(token.string, this, token.location) getOrElse {
          ASTValue.NameReference(token.string, Some(lastLocation))
        }

      case TokenType.OpenBrace => parseLambda(hasLowerPriorityTarget)
      case TokenType.UnaryOperator => parseUnaryOperator(requireCallParens, hasLowerPriorityTarget)
      case TokenType.OpenParen =>
        if (isOpenParenForLambda) {
          parseLambda(hasLowerPriorityTarget)
        } else {
          read() // (

          val value = if (PARENS_FOR_BLOCKS) {
            val valueBuilder = Seq.newBuilder[ASTValue]
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
              ASTValue.Block(values, Some(startLocation.extendWith(token.location)))
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

  private def tryToParseCall(target: ASTValue, requireCallParens: Boolean, hasLowerPriorityTarget: Boolean): Option[ASTValue] = {
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
      val isPattern = arguments.exists(_._2.isInstanceOf[ASTValue.Pattern])

      if (isPattern) {
        return Some(ASTValue.Pattern.Call(
          target,
          name.string,
          toPatternArguments(arguments),
          mayBeVarCall = false,
          location = target.location.map(_.extendWith(lastLocation))
        ))
      } else {
        return Some(ASTValue.Call(
          target,
          name.string,
          toArguments(arguments),
          mayBeVarCall = false,
          location = target.location.map(_.extendWith(lastLocation))
        ))
      }
    }

    // name a
    // name(a)
    target match {
      case ASTValue.NameReference(name, targetLocation) =>
        val isDefinitelyACall = token.tokenType == TokenType.OpenParen

        if (!currentExpressionMayEnd && (!requireCallParens || isDefinitelyACall)) {
          val arguments = parseArguments(requireCallParens, hasLowerPriorityTarget = true)
          val isPattern = arguments.exists(_._2.isInstanceOf[ASTValue.Pattern])

          if (isPattern) {
            return Some(ASTValue.Pattern.Call(
              target = ASTValue.NameReference("self", targetLocation),
              name,
              toPatternArguments(arguments),
              mayBeVarCall = true,
              location = targetLocation.map(_.extendWith(lastLocation))
            ))
          } else {
            return Some(ASTValue.Call(
              target = ASTValue.NameReference("self", targetLocation),
              name,
              toArguments(arguments),
              mayBeVarCall = true,
              location = targetLocation.map(_.extendWith(lastLocation))
            ))
          }
        }

      case _ => ()
    }

    // expression( ... )
    if (token.tokenType == TokenType.OpenParen && !token.hadWhitespaceBefore) {
      val arguments = parseArguments(requireCallParens, hasLowerPriorityTarget = false)
      val isPattern = arguments.exists(_._2.isInstanceOf[ASTValue.Pattern])

      if (isPattern) {
        return Some(ASTValue.Pattern.Call(
          target,
          name = "call",
          args = toPatternArguments(arguments),
          mayBeVarCall = false,
          target.location.map(_.extendWith(lastLocation))
        ))
      } else {
        return Some(ASTValue.Call(
          target = target,
          name = "call",
          arguments = toArguments(arguments),
          mayBeVarCall = false,
          target.location.map(_.extendWith(lastLocation))
        ))
      }
    }

    None
  }

  private def parseArguments(requireParens: Boolean, hasLowerPriorityTarget: Boolean): Seq[(Option[String], ASTValue)] = {
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


    val arguments = Seq.newBuilder[(Option[String], ASTValue)]

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

  private def toArguments(args: Seq[(Option[String], ASTValue)]): ASTArguments = {
    val (positional, named) = args.partition(_._1.isEmpty)

    ASTArguments(
      positional.map(_._2),
      named.map { case (name, value) => name.get -> value }.toMap
    )
  }

  private def toPatternArguments(args: Seq[(Option[String], ASTValue)]): ArgumentsWithoutSelf[ASTValue.Pattern] = {
    val patternArgs = args.map { case (name, value) =>
      name -> (value match {
        case pattern: ASTValue.Pattern => pattern
        case _ => ASTValue.Pattern.SpecificValue(value)
      })
    }
    val (positional, named) = patternArgs.partition(_._1.isEmpty)

    ArgumentsWithoutSelf(
      positional.map(_._2),
      named.map { case (name, value) => name.get -> value }.toMap
    )
  }

  private def parseArgument(hasLowerPriorityTarget: Boolean): (Option[String], ASTValue) = {
    val name = if (isNamedArgument) {
      val name = read()

      read() // =

      Some(name.string)
    } else { None }

    val value = if (token.tokenType == TokenType.Val) {
      parsePattern(hasLowerPriorityTarget)
    } else {
      parseExpression(requireCallParens = false, hasLowerPriorityTarget = hasLowerPriorityTarget)
    }

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

  private def parseLambda(hasLowerPriorityTarget: Boolean): ASTValue = {
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

    val hasBlock = token.tokenType == TokenType.OpenBrace
    val body = if (hasBlock) {
      read() // {

      val block = parseBlock()

      if (token.tokenType != TokenType.CloseBrace) parseError("Expected CloseBrace '}'")
      read() // }

      block
    } else {
      parseExpression(requireCallParens = false, hasLowerPriorityTarget = hasLowerPriorityTarget)
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

          val outName = read().string
          val startLocation = lastLocation

          val inName = if (token.tokenType == TokenType.Name && token.string == "as") {
            read() // as

            if (token.tokenType != TokenType.Name) parseError("Expected name")

            read().string
          } else outName

          val typeValue = if (token.tokenType == TokenType.Colon) {
            read() // :

            Some(parsePattern(hasLowerPriorityTarget = false))
          } else {
            None
          }

          parameters.addOne(
            ASTParameter(outName, inName, typeValue, Some(startLocation.extendWith(lastLocation)))
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

  private def parsePattern(hasLowerPriorityTarget: Boolean): ASTValue.Pattern = {
    if (token.tokenType == TokenType.Val) {
      read() // val
      val startLocation = lastLocation

      if (token.tokenType != TokenType.Name) parseError("Expected name after `val` in pattern")
      val name = read() // name

      ASTValue.Pattern.Binding(name.string, Some(startLocation.extendWith(lastLocation)))
    } else {
      val value = parseExpression(requireCallParens = true, hasLowerPriorityTarget = hasLowerPriorityTarget)

      value match {
        case pattern: ASTValue.Pattern => pattern
        case _ => ASTValue.Pattern.SpecificValue(value)
      }
    }
  }

  private def parseBlock() = {
    val values = Vector.newBuilder[ASTValue]
    val startLocation = lastLocation

    while (token.tokenType != TokenType.CloseBrace) {
      values += parseExpression(requireCallParens = false, hasLowerPriorityTarget = false)
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
      values += parseExpression(requireCallParens = false, hasLowerPriorityTarget = false)
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
    token.tokenType == TokenType.CloseBracket ||
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

  def parseError(explanation: String = "") = {
    throw new ParseError(
      s"Unexpected token ${token.tokenType.name} '${token.string}'. $explanation".strip(),
      token.location
    )
  }
}
