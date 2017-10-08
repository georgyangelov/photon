use std::io;
use std::io::Read;
use std::fmt;

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
    // line: u32,
    // column: u32,
}

impl fmt::Display for Token {
    fn fmt(&self, f: &mut fmt::Formatter) -> fmt::Result {
        match self.token_type {
            TokenType::EOF => write!(f, "(EOF)"),

            TokenType::Number         => write!(f, "(Number '{}')",         self.string),
            TokenType::String         => write!(f, "(String '{}')",         self.string),
            TokenType::Name           => write!(f, "(Name '{}')",           self.string),
            TokenType::UnaryOperator  => write!(f, "(UnaryOperator '{}')",  self.string),
            TokenType::BinaryOperator => write!(f, "(BinaryOperator '{}')", self.string),

            _ => write!(f, "({:?} '{}')", self.token_type, self.string),
        }
    }
}

#[derive(Debug)]
pub struct LexerError {
    pub message: String,
}

pub struct Lexer<'a> {
    file_name: String,
    source: &'a mut io::Read,

    c: char,
    put_back_char: Option<char>,

    at_start: bool,
    at_end: bool,
}

impl<'a> Lexer<'a> {
    pub fn new(file_name: &str, source: &'a mut io::Read) -> Lexer<'a> {
        Lexer {
            file_name: String::from(file_name),
            source: source,
            at_start: true,
            at_end: false,
            c: '\0',
            put_back_char: None
        }
    }

    pub fn file_name(&self) -> &str {
        &self.file_name
    }

    pub fn next_token(&mut self) -> Result<Token, LexerError> {
        // TODO: Extract this for performance
        let mut string = String::new();

        if self.at_start {
            self.at_start = false;
            self.next();
        }

        self.skip_whitespace_and_comments();

        if self.at_end {
            return Ok(Token { token_type: TokenType::EOF, string: String::from("EOF") });
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
                string: string
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
                    string: string
                });
            },
            '"' => {
                return Ok(Token {
                    token_type: TokenType::String,
                    string: self.read_string()
                });
            },
            '!' => {
                string.push(self.next()); // !

                if self.c == '=' {
                    string.push(self.next()); // =

                    return Ok(Token {
                        token_type: TokenType::BinaryOperator,
                        string: string
                    });
                }

                return Ok(Token {
                    token_type: TokenType::UnaryOperator,
                    string: string
                });
            },
            _ => ()
        }

        if self.c.is_digit(10) {
            return Ok(Token {
                token_type: TokenType::Number,
                string: self.read_number()
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
                string: string
            });
        }

        Err(LexerError {
            message: format!("Could not lex token {}", self.c)
        })
    }

    fn read_number(&mut self) -> String {
        let mut string = String::with_capacity(20);

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
                // TODO: Extract the iterator for performance
                let mut chars = self.source.chars();

                if let Some(result) = chars.next() {
                    result.expect("test")
                } else {
                    self.at_end = true;

                    '\0'
                }
            }
        };

        old_c
    }
}
