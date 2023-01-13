package photon.frontend

import photon.core.Location
import photon.core.PhotonError
import photon.lib.PushbackStringReader
import java.io.*

sealed class TokenType(val name: String) {
  override fun toString(): String = name

  object EOF : TokenType("EOF")
  object NewLine : TokenType("NewLine")
  object OpenParen : TokenType("OpenParen")
  object CloseParen : TokenType("CloseParen")
  object OpenBrace : TokenType("OpenBrace")
  object CloseBrace : TokenType("CloseBrace")
  object OpenBracket : TokenType("OpenBracket")
  object CloseBracket : TokenType("CloseBracket")
  object Comma : TokenType("Comma")
  object Dot : TokenType("Dot")
  object At : TokenType("At")
  object Colon : TokenType("Colon")
  object Dollar : TokenType("Dollar")
  object Equals : TokenType("Equals")
  object Val : TokenType("Val")
  object UnaryOperator : TokenType("UnaryOperator")
  object BinaryOperator : TokenType("BinaryOperator")
  object Name : TokenType("Name")
  object NumberLiteral : TokenType("Number")
  object StringLiteral : TokenType("String")
  object BoolLiteral : TokenType("Bool")
}

data class Token(
  val tokenType: TokenType,
  val string: String,
  val location: Location,
  val hadWhitespaceBefore: Boolean
) {
  fun inspect(): String = when (tokenType) {
    TokenType.EOF,
    TokenType.NewLine,
    TokenType.OpenParen,
    TokenType.CloseParen,
    TokenType.OpenBracket,
    TokenType.CloseBracket,
    TokenType.OpenBrace,
    TokenType.CloseBrace,
    TokenType.Comma,
    TokenType.Dot,
    TokenType.At,
    TokenType.Colon,
    TokenType.Dollar,
    TokenType.Equals,
    TokenType.Val -> "($tokenType)"

    TokenType.UnaryOperator,
    TokenType.BinaryOperator,
    TokenType.Name,
    TokenType.BoolLiteral,
    TokenType.NumberLiteral,
    TokenType.StringLiteral -> "($tokenType '$string')"
  }
}

class Lexer private constructor(val fileName: String, val reader: PushbackStringReader) {
  private var c: Int = 0
  private var atStart = true
  private var atEnd = false
  private var hadNewline = false

  private var line = 1
  private var column = -1

  val currentLocation: Location
    get() = Location.at(fileName, line, column)

  constructor(fileName: String, code: String) :
      this(fileName, PushbackStringReader(StringReader(code)))

  constructor(fileName: String, codeInput: InputStream) :
      this(fileName, PushbackStringReader(InputStreamReader(BufferedInputStream(codeInput))))

  companion object {
    fun isStartPartOfName(c: Int): Boolean =
      Character.isAlphabetic(c) || c == '_'.code || c == '@'.code || c == '$'.code

    fun isPartOfName(c: Int): Boolean =
      Character.isAlphabetic(c) || Character.isDigit(c) || c == '_'.code || c == '@'.code || c == '$'.code
  }

  fun readAll(): List<Token> {
    return buildList {
      while (true) {
        val token = nextToken()

        add(token)

        if (token.tokenType == TokenType.EOF) {
          break
        }
      }
    }
  }

  fun nextToken(): Token {
    if (atStart) {
      atStart = false
      next()
    }

    val hadWhitespace = skipWhitespaceAndComments() || hadNewline
    hadNewline = false

    val startLocation = currentLocation
    val string = StringBuilder()

    if (atEnd) {
      return Token(TokenType.EOF, "", startLocation, hadWhitespace)
    }

    return when (c) {
      '\n'.code, ';'.code -> {
        hadNewline = true
        singleCharToken(TokenType.NewLine, startLocation, hadWhitespace)
      }

      '('.code -> singleCharToken(TokenType.OpenParen, startLocation, hadWhitespace)
      ')'.code -> singleCharToken(TokenType.CloseParen, startLocation, hadWhitespace)
      '['.code -> singleCharToken(TokenType.OpenBracket, startLocation, hadWhitespace)
      ']'.code -> singleCharToken(TokenType.CloseBracket, startLocation, hadWhitespace)
      '{'.code -> singleCharToken(TokenType.OpenBrace, startLocation, hadWhitespace)
      '}'.code -> singleCharToken(TokenType.CloseBrace, startLocation, hadWhitespace)
      ','.code -> singleCharToken(TokenType.Comma, startLocation, hadWhitespace)
      '.'.code -> singleCharToken(TokenType.Dot, startLocation, hadWhitespace)
      '@'.code -> singleCharToken(TokenType.At, startLocation, hadWhitespace)

      '='.code, '+'.code, '-'.code, '*'.code, '/'.code, '<'.code, '>'.code -> {
        string.appendCodePoint(next())

        if (c == '='.code) {
          string.appendCodePoint(next())
        }

        val str = string.toString()

        Token(
          if (str == "=") TokenType.Equals else TokenType.BinaryOperator,
          str,
          startLocation.extendWith(currentLocation),
          hadWhitespace
        )
      }

      ':'.code -> singleCharToken(TokenType.Colon, startLocation, hadWhitespace)

      '"'.code, '\''.code -> {
        val string = readString()

        Token(TokenType.StringLiteral, string, startLocation.extendWith(currentLocation), hadWhitespace)
      }

      '!'.code -> {
        if (peek() == '='.code) {
          next() // !
          next() // =

          Token(
            TokenType.BinaryOperator,
            "!=",
            startLocation.extendWith(currentLocation),
            hadWhitespace
          )
        } else {
          singleCharToken(TokenType.UnaryOperator, startLocation, hadWhitespace)
        }
      }

      '#'.code -> singleCharToken(TokenType.UnaryOperator, startLocation, hadWhitespace)

      else -> {
        if (Character.isDigit(c)) {
          val string = readNumber()
          Token(
            TokenType.NumberLiteral,
            string,
            startLocation.extendWith(currentLocation),
            hadWhitespace
          )
        } else if (Lexer.isStartPartOfName(c)) {
          val name = readName()
          val tokenType = when (name) {
            "val" -> TokenType.Val
            "and", "or" -> TokenType.BinaryOperator
            "true", "false" -> TokenType.BoolLiteral
            else -> TokenType.Name
          }

          Token(tokenType, name, startLocation.extendWith(currentLocation), hadWhitespace)
        } else if (c == '$'.code) {
          singleCharToken(TokenType.Dollar, startLocation, hadWhitespace)
        } else {
          throw PhotonError("Unexpected token '${Character.toString(c)}' at $startLocation")
        }
      }
    }
  }

  private fun readString(): String {
    val string = StringBuilder()
    var inEscapeSequence = false
    val quote = next() // ' or "
    val withEscapeSequences = quote == '"'.code

    while (inEscapeSequence || c != quote) {
      if (inEscapeSequence) {
        inEscapeSequence = false

        if (withEscapeSequences) {
          string.appendCodePoint(escapeSequence(next()))
        } else {
          if (c != '\\'.code && c != quote) {
            string.appendCodePoint('\\'.code)
          }

          string.appendCodePoint(next())
        }
      } else if (c == '\\'.code) {
        inEscapeSequence = true
        next()
      } else {
        string.appendCodePoint(next())
      }
    }

    next() // ' or "

    return string.toString()
  }

  private fun readNumber(): String {
    val string = StringBuilder()

    while (Character.isDigit(c)) {
      string.appendCodePoint(next())
    }

    if (c == '.'.code && Character.isDigit(peek())) {
      string.appendCodePoint(next()) // .

      while (Character.isDigit(c)) {
        string.appendCodePoint(next())
      }
    }

    return string.toString()
  }

  private fun readName(): String {
    val string = StringBuilder()
    string.appendCodePoint(next())

    while (isPartOfName(c)) {
      string.appendCodePoint(next())
    }

    if (c == '!'.code || c == '?'.code) {
      string.appendCodePoint(next())
    }

    return string.toString()
  }

  private fun escapeSequence(c: Int): Int = when (c) {
    'r'.code -> '\r'.code
    'n'.code -> '\n'.code
    't'.code -> '\t'.code
    else -> c
  }

  private fun skipWhitespaceAndComments(): Boolean {
    var inComment = false
    var hadWhitespace = false

    while (!atEnd) {
      if (inComment) {
        if (c == '\n'.code) {
          inComment = false
        }
        next()
      } else if (Character.isWhitespace(c) && c != '\n'.code) {
        next()
      } else if (c == '#'.code && Character.isWhitespace(peek())) {
        next()
        inComment = true
      } else {
        break
      }

      hadWhitespace = true
    }

    return hadWhitespace
  }

  private fun next(): Int {
    val oldC = c
    c = reader.read()

    if (c == -1) {
      atEnd = true
    }

    if (c == '\n'.code) {
      line += 1
      column = 0
    } else {
      column += 1
    }

    return oldC
  }

  private fun peek(): Int {
    val nextC = reader.read()

    reader.unread(nextC)

    return nextC
  }

  private fun singleCharToken(tokenType: TokenType, startLocation: Location, hadWhitespaceBefore: Boolean): Token {
    val string = Character.toString(next())

    return Token(tokenType, string, startLocation.extendWith(currentLocation), hadWhitespaceBefore)
  }
}