use std::io;
use std::io::Read;
use std::fmt;
use std::io::Chars;

#[derive(Debug, PartialEq)]
pub enum TokenType {
    EOF,
    NewLine,

    OpenParen,
    CloseParen,

    OpenBracket,
    CloseBracket,

    OpenBrace,
    CloseBrace,

    Comma,
    Dot,
    Colon,

    UnaryOperator,
    BinaryOperator,

    Name,

    Number,
    String,

    If,
    Else,
    Elsif,
    Do,
    While,
    Def,
    End,
}

#[derive(Debug)]
pub struct Token {
    pub token_type: TokenType,
    pub string: String,
    pub line: u32,
    pub column: u32,
}

impl fmt::Display for Token {
    fn fmt(&self, f: &mut fmt::Formatter) -> fmt::Result {
        match self.token_type {
            TokenType::Number         => write!(f, "(Number '{}')",         self.string),
            TokenType::String         => write!(f, "(String '{}')",         self.string),
            TokenType::Name           => write!(f, "(Name '{}')",           self.string),
            TokenType::UnaryOperator  => write!(f, "(UnaryOperator '{}')",  self.string),
            TokenType::BinaryOperator => write!(f, "(BinaryOperator '{}')", self.string),

            _ => write!(f, "({:?})", self.token_type),
        }
    }
}

#[derive(Debug)]
pub struct LexerError {
    pub message: String,
}

pub struct Lexer<'a> {
    file_name: String,

    c: char,
    put_back_char: Option<char>,

    at_start: bool,
    at_end: bool,

    line: u32,
    column: u32,

    chars_iter: Chars<&'a mut io::Read>
}

impl<'a> Lexer<'a> {
    pub fn new(file_name: &str, source: &'a mut io::Read) -> Lexer<'a> {
        Lexer {
            file_name: String::from(file_name),

            c: '\0',
            put_back_char: None,

            at_start: true,
            at_end: false,

            line: 1,
            column: 0,

            chars_iter: source.chars(),
        }
    }

    pub fn file_name(&self) -> &str {
        &self.file_name
    }

    pub fn next_token(&mut self) -> Result<Token, LexerError> {
        let mut string = String::new();

        if self.at_start {
            self.at_start = false;
            self.next();
        }

        self.skip_whitespace_and_comments();

        let line = self.line;
        let column = self.column;

        if self.at_end {
            return Ok(Token {
                token_type: TokenType::EOF,
                string: String::from("EOF"),
                line: line,
                column: column
            });
        }

        let token_type = match self.c {
            '\n' => Some(TokenType::NewLine),
            '('  => Some(TokenType::OpenParen),
            ')'  => Some(TokenType::CloseParen),
            '['  => Some(TokenType::OpenBracket),
            ']'  => Some(TokenType::CloseBracket),
            '{'  => Some(TokenType::OpenBrace),
            '}'  => Some(TokenType::CloseBrace),
            ','  => Some(TokenType::Comma),
            '.'  => Some(TokenType::Dot),
            ':'  => Some(TokenType::Colon),
            _    => None
        };

        if let Some(token_type) = token_type {
            string.push(self.next());

            return Ok(Token {
                token_type: token_type,
                string: string,
                line: line,
                column: column
            });
        }

        match self.c {
            '=' | '+' | '-' | '*' | '/' | '<' | '>' => {
                string.push(self.next());

                if self.c == '=' {
                    string.push(self.next());
                }

                return Ok(Token {
                    token_type: TokenType::BinaryOperator,
                    string: string,
                    line: line,
                    column: column
                });
            },
            '"' => {
                return Ok(Token {
                    token_type: TokenType::String,
                    string: self.read_string(),
                    line: line,
                    column: column
                });
            },
            '!' => {
                string.push(self.next()); // !

                if self.c == '=' {
                    string.push(self.next()); // =

                    return Ok(Token {
                        token_type: TokenType::BinaryOperator,
                        string: string,
                        line: line,
                        column: column
                    });
                }

                return Ok(Token {
                    token_type: TokenType::UnaryOperator,
                    string: string,
                    line: line,
                    column: column
                });
            },
            _ => ()
        }

        if self.c.is_digit(10) {
            return Ok(Token {
                token_type: TokenType::Number,
                string: self.read_number(),
                line: line,
                column: column
            });
        }

        if self.c.is_alphabetic() || self.c == '_' {
            string.push(self.next());

            while self.c.is_alphanumeric() || self.c == '_' {
                string.push(self.next());
            }

            let token_type = match string.as_str() {
                "do"    => TokenType::Do,
                "if"    => TokenType::If,
                "else"  => TokenType::Else,
                "elsif" => TokenType::Elsif,
                "end"   => TokenType::End,
                "while" => TokenType::While,
                "def"   => TokenType::Def,
                "and"   => TokenType::BinaryOperator,
                "or"    => TokenType::BinaryOperator,
                _       => TokenType::Name
            };

            return Ok(Token {
                token_type: token_type,
                string: string,
                line: line,
                column: column
            });
        }

        Err(LexerError {
            message: format!("Could not lex token '{}' at {}:{}:{}", self.c, self.file_name(), self.line, self.column)
        })
    }

    fn read_number(&mut self) -> String {
        let mut string = String::new();

        while self.c.is_digit(10) {
            string.push(self.next());
        }

        if self.c == '.' {
            self.next(); // .

            if self.c.is_digit(10) {
                string.push('.');

                while self.c.is_digit(10) {
                    string.push(self.next());
                }
            } else {
                self.put_back_char = Some('.');
            }
        }

        string
    }

    fn read_string(&mut self) -> String {
        let mut string = String::new();
        let mut in_escape_sequence = false;

        self.next(); // "

        while in_escape_sequence || self.c != '"' {
            if in_escape_sequence {
                in_escape_sequence = false;
                string.push(Lexer::escape_sequence(self.next()));
            } else if self.c == '\\' {
                in_escape_sequence = true;
                self.next(); // \
            } else {
                string.push(self.next());
            }
        }

        self.next(); // "

        string
    }

    fn escape_sequence(c: char) -> char {
        match c {
            '"'  => '"',
            'n'  => '\n',
            't'  => '\t',
            '\\' => '\\',
            _    => c
        }
    }

    fn skip_whitespace_and_comments(&mut self) {
        let mut in_comment = false;

        loop {
            if self.at_end {
                break;
            }

            if in_comment {
                if self.c == '\n' {
                    in_comment = false;
                }
                self.next();
            } else {
                if self.c.is_whitespace() && self.c != '\n' {
                    self.next(); // any whitespace
                } else if self.c == '#' {
                    self.next(); // #
                    in_comment = true;
                } else {
                    break;
                }
            }
        }
    }

    fn next(&mut self) -> char {
        let old_c = self.c;

        self.c = match self.put_back_char {
            Some(c) => {
                self.put_back_char = None;

                c
            },
            None => {
                if let Some(result) = self.chars_iter.next() {
                    result.expect("Could not read from source stream")
                } else {
                    self.at_end = true;

                    '\0'
                }
            }
        };

        if self.c == '\n' {
            self.line += 1;
            self.column = 0;
        } else {
            self.column += 1;
        }

        old_c
    }
}
