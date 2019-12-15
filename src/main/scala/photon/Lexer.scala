package photon

import java.io._
import java.lang.StringBuilder

import photon.lib.PushbackStringReader

import scala.util.control.Breaks._

sealed class TokenType(val name: String) {
  override def toString: String = name
}

object TokenType {
  case object EOF extends TokenType("EOF")
  case object NewLine extends TokenType("NewLine")
  case object OpenParen extends TokenType("OpenParen")
  case object CloseParen extends TokenType("CloseParen")
  case object OpenBrace extends TokenType("OpenBrace")
  case object CloseBrace extends TokenType("CloseBrace")
  case object OpenBracket extends TokenType("OpenBracket")
  case object CloseBracket extends TokenType("CloseBracket")
  case object Comma extends TokenType("Comma")
  case object Dot extends TokenType("Dot")
  case object Colon extends TokenType("Colon")
  case object Pipe extends TokenType("Pipe")
  case object Dollar extends TokenType("Dollar")
  case object DoubleColon extends TokenType("DoubleColon")
  case object UnaryOperator extends TokenType("UnaryOperator")
  case object BinaryOperator extends TokenType("BinaryOperator")
  case object Name extends TokenType("Name")
  case object NumberLiteral extends TokenType("Number")
  case object StringLiteral extends TokenType("String")
  case object BoolLiteral extends TokenType("Bool")
  case object UnknownLiteral extends TokenType("Unknown")
}

case class Token(tokenType: TokenType, string: String, location: Location, hadWhitespaceBefore: Boolean) {
  def inspect: String = {
    tokenType match {
      case TokenType.EOF
        | TokenType.NewLine
        | TokenType.OpenParen
        | TokenType.CloseParen
        | TokenType.OpenBracket
        | TokenType.CloseBracket
        | TokenType.OpenBrace
        | TokenType.CloseBrace
        | TokenType.Comma
        | TokenType.Dot
        | TokenType.Colon
        | TokenType.Pipe
        | TokenType.Dollar
        | TokenType.DoubleColon
        | TokenType.UnknownLiteral
        => s"($tokenType)"

      case TokenType.UnaryOperator
        | TokenType.BinaryOperator
        | TokenType.Name
        | TokenType.NumberLiteral
        | TokenType.StringLiteral
        | TokenType.BoolLiteral
        => s"($tokenType '$string')"
    }
  }
}

class LexerError(message: String, location: Location) extends PhotonError(message, Some(location)) {}

class Lexer private(val fileName: String, val reader: PushbackStringReader) {
  private var c: Int = '\u0000'
  private var atStart = true
  private var atEnd = false

  private var line = 1
  private var column = -1

  def this(fileName: String, code: String) {
    this(fileName, new PushbackStringReader(new StringReader(code)))
  }

  def this(fileName: String, codeInput: InputStream) {
    this(fileName, new PushbackStringReader(new InputStreamReader(new BufferedInputStream(codeInput))))
  }

  def readAll(): Seq[Token] = {
    val tokens = Vector.newBuilder[Token]

    while (true) {
      val token = nextToken()

      tokens += token

      if (token.tokenType == TokenType.EOF) {
        return tokens.result
      }
    }

    tokens.result
  }

  def nextToken(): Token = {
    if (atStart) {
      atStart = false
      next()
    }

    val hadWhitespace = skipWhitespaceAndComments()
    val startLocation = currentLocation
    val string = new StringBuilder

    if (atEnd) {
      return Token(TokenType.EOF, "", startLocation, hadWhitespace)
    }

    c match {
      case '\n' | ';' => singleCharToken(TokenType.NewLine, startLocation, hadWhitespace)
      case '(' => singleCharToken(TokenType.OpenParen, startLocation, hadWhitespace)
      case ')' => singleCharToken(TokenType.CloseParen, startLocation, hadWhitespace)
      case '[' => singleCharToken(TokenType.OpenBracket, startLocation, hadWhitespace)
      case ']' => singleCharToken(TokenType.CloseBracket, startLocation, hadWhitespace)
      case '{' => singleCharToken(TokenType.OpenBrace, startLocation, hadWhitespace)
      case '}' => singleCharToken(TokenType.CloseBrace, startLocation, hadWhitespace)
      case ',' => singleCharToken(TokenType.Comma, startLocation, hadWhitespace)
      case '.' => singleCharToken(TokenType.Dot, startLocation, hadWhitespace)
      case '|' => singleCharToken(TokenType.Pipe, startLocation, hadWhitespace)
      case '$' if peek() == '?' =>
        next() // $
        next() // ?

        Token(TokenType.UnknownLiteral, "$?", startLocation.extendWith(currentLocation), hadWhitespace)

      case '$' if !isStartPartOfName(peek()) =>
        singleCharToken(TokenType.Dollar, startLocation, hadWhitespace)

      case '=' | '+' | '-' | '*' | '/' | '<' | '>' =>
        string.appendCodePoint(next())

        if (c == '=') {
          string.appendCodePoint(next())
        }

        Token(TokenType.BinaryOperator, string.toString, startLocation.extendWith(currentLocation), hadWhitespace)

      case ':' if peek() == ':' =>
        next() // :
        next() // :

        Token(
          TokenType.DoubleColon,
          "::",
          startLocation.extendWith(currentLocation),
          hadWhitespace
        )

      case ':' => singleCharToken(TokenType.Colon, startLocation, hadWhitespace)
      case '"' | '\'' =>
        val string = readString()

        Token(TokenType.StringLiteral, string.toString, startLocation.extendWith(currentLocation), hadWhitespace)

      case '!' if peek() == '=' =>
        next() // !
        next() // =

        Token(
          TokenType.BinaryOperator,
          "!=",
          startLocation.extendWith(currentLocation),
          hadWhitespace
        )
      case '!' => singleCharToken(TokenType.UnaryOperator, startLocation, hadWhitespace)

      case _ if Character.isDigit(c) =>
        val string = readNumber()
        Token(
          TokenType.NumberLiteral,
          string,
          startLocation.extendWith(currentLocation),
          hadWhitespace
        )

      case _ if isStartPartOfName(c) =>
        val name = readName()
        val tokenType = name match {
          case "and" | "or" => TokenType.BinaryOperator
          case "true" | "false" => TokenType.BoolLiteral
          case _ => TokenType.Name
        }

        Token(tokenType, name, startLocation.extendWith(currentLocation), hadWhitespace)

      case _ => throw new PhotonError(s"Unexpected token '${Character.toString(c)}' at $startLocation")
    }
  }

  private def readString() = {
    val string = new StringBuilder
    var inEscapeSequence = false
    val quote = next() // ' or "
    val withEscapeSequences = quote == '"'

    while (inEscapeSequence || c != quote) {
      if (inEscapeSequence) {
        inEscapeSequence = false

        if (withEscapeSequences) {
          string.appendCodePoint(escapeSequence(next()))
        } else {
          if (c != '\\' && c != quote) {
            string.appendCodePoint('\\')
          }

          string.appendCodePoint(next())
        }
      } else if (c == '\\') {
        inEscapeSequence = true
        next()
      } else {
        string.appendCodePoint(next())
      }
    }

    next() // ' or "

    string.toString
  }

  private def readNumber(): String = {
    val string = new StringBuilder

    while (Character.isDigit(c)) {
      string.appendCodePoint(next())
    }

    if (c == '.' && Character.isDigit(peek())) {
      string.appendCodePoint(next()) // .

      while (Character.isDigit(c)) {
        string.appendCodePoint(next())
      }
    }

    string.toString
  }

  private def readName(): String = {
    val string = new StringBuilder
    string.appendCodePoint(next())

    while (isPartOfName(c)) {
      string.appendCodePoint(next())
    }

    if (c == '!' || c == '?') {
      string.appendCodePoint(next())
    }

    string.toString
  }

  private def escapeSequence(c: Int): Int = {
    c match {
      case 'r' => '\r'
      case 'n' => '\n'
      case 't' => '\t'
      case _ => c
    }
  }

  private def skipWhitespaceAndComments(): Boolean = {
    var inComment = false
    var hadWhitespace = false

    breakable {
      while (!atEnd) {
        if (inComment) {
          if (c == '\n') {
            inComment = false
          }
          next()
        } else if (Character.isWhitespace(c) && c != '\n') {
          next()
        } else if (c == '#') {
          next()
          inComment = true
        } else {
          break
        }

        hadWhitespace = true
      }
    }

    hadWhitespace
  }

  private def next(): Int = {
    val oldC = c
    c = reader.read()

    if (c == -1) {
      atEnd = true
    }

    if (c == '\n') {
      line += 1
      column = 0
    } else {
      column += 1
    }

    oldC
  }

  private def peek(): Int = {
    val nextC = reader.read()

    reader.unread(nextC)

    nextC
  }

  def currentLocation: Location =
    Location.at(fileName, line, column)

  private def singleCharToken(tokenType: TokenType, startLocation: Location, hadWhitespaceBefore: Boolean): Token = {
    val string = Character.toString(next())

    Token(tokenType, string, startLocation.extendWith(currentLocation), hadWhitespaceBefore)
  }

  private def isStartPartOfName(c: Int): Boolean =
    Character.isAlphabetic(c) || c == '_' || c == '@' || c == '$'

  private def isPartOfName(c: Int): Boolean =
    Character.isAlphabetic(c) || Character.isDigit(c) || c == '_' || c == '@'
}
