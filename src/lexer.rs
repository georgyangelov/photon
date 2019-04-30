use std::io;
use std::fmt;

use support::char_iterator::CharIterator;
use types::{Location, Error};

#[derive(Debug, PartialEq, Clone)]
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
    Pipe,
    Dollar,
    DoubleColon,

    UnaryOperator,
    BinaryOperator,

    Name,

    Number,
    String,
    Bool
}

#[derive(Debug, Clone)]
pub struct Token {
    pub token_type: TokenType,
    pub string: String,
    pub location: Location,
    pub had_whitespace_before: bool
}

impl fmt::Display for Token {
    fn fmt(&self, f: &mut fmt::Formatter) -> fmt::Result {
        match self.token_type {
            TokenType::Bool           => write!(f, "(Bool '{}')",           self.string),
            TokenType::Number         => write!(f, "(Number '{}')",         self.string),
            TokenType::String         => write!(f, "(String '{}')",         self.string),
            TokenType::Name           => write!(f, "(Name '{}')",           self.string),
            TokenType::UnaryOperator  => write!(f, "(UnaryOperator '{}')",  self.string),
            TokenType::BinaryOperator => write!(f, "(BinaryOperator '{}')", self.string),

            _ => write!(f, "({:?})", self.token_type),
        }
    }
}

pub struct Lexer<'a> {
    file_name: String,

    c: char,
    put_back_char: Option<char>,

    at_start: bool,
    at_end: bool,

    line: u32,
    column: u32,

    chars_iter: CharIterator<&'a mut io::Read>
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

            chars_iter: CharIterator::new(source),
        }
    }

    pub fn file_name(&self) -> &str {
        &self.file_name
    }

    pub fn next_token(&mut self) -> Result<Token, Error> {
        let mut string = String::new();

        if self.at_start {
            self.at_start = false;
            self.next();
        }

        let had_whitespace = self.skip_whitespace_and_comments();

        let start_location = Location {
            file_name: Some(self.file_name.clone()),
            start_line: self.line,
            start_column: self.column,
            end_line: self.line,
            end_column: self.column
        };

        if self.at_end {
            return Ok(Token {
                token_type: TokenType::EOF,
                string: String::from("EOF"),
                location: self.token_location(&start_location),
                had_whitespace_before: had_whitespace
            });
        }

        let token_type = match self.c {
            '\n' | ';' => Some(TokenType::NewLine),
            '('  => Some(TokenType::OpenParen),
            ')'  => Some(TokenType::CloseParen),
            '['  => Some(TokenType::OpenBracket),
            ']'  => Some(TokenType::CloseBracket),
            '{'  => Some(TokenType::OpenBrace),
            '}'  => Some(TokenType::CloseBrace),
            ','  => Some(TokenType::Comma),
            '.'  => Some(TokenType::Dot),
            '|'  => Some(TokenType::Pipe),
            '$'  => {
                self.next(); // $

                if is_start_part_of_name(self.c) {
                    self.put_back_char = Some(self.c);
                    self.c = '$';

                    None
                } else {
                    self.put_back_char = Some(self.c);
                    self.c = '$';

                    Some(TokenType::Dollar)
                }
            },
            _    => None
        };

        if let Some(token_type) = token_type {
            string.push(self.next());

            return Ok(Token {
                token_type: token_type,
                string: string,
                location: self.token_location(&start_location),
                had_whitespace_before: had_whitespace
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
                    location: self.token_location(&start_location),
                    had_whitespace_before: had_whitespace
                });
            },
            ':' => {
                string.push(self.next());

                if self.c == ':' {
                    string.push(self.next());

                    return Ok(Token {
                        token_type: TokenType::DoubleColon,
                        string,
                        location: self.token_location(&start_location),
                        had_whitespace_before: had_whitespace
                    });
                } else {
                    return Ok(Token {
                        token_type: TokenType::Colon,
                        string,
                        location: self.token_location(&start_location),
                        had_whitespace_before: had_whitespace
                    });
                }
            },
            '"' => {
                return Ok(Token {
                    token_type: TokenType::String,
                    string: self.read_string(true),
                    location: self.token_location(&start_location),
                    had_whitespace_before: had_whitespace
                });
            },
            '\'' => {
                return Ok(Token {
                    token_type: TokenType::String,
                    string: self.read_string(false),
                    location: self.token_location(&start_location),
                    had_whitespace_before: had_whitespace
                });
            },
            '!' => {
                string.push(self.next()); // !

                if self.c == '=' {
                    string.push(self.next()); // =

                    return Ok(Token {
                        token_type: TokenType::BinaryOperator,
                        string: string,
                        location: self.token_location(&start_location),
                        had_whitespace_before: had_whitespace
                    });
                }

                return Ok(Token {
                    token_type: TokenType::UnaryOperator,
                    string: string,
                    location: self.token_location(&start_location),
                    had_whitespace_before: had_whitespace
                });
            },
            _ => ()
        }

        if self.c.is_digit(10) {
            return Ok(Token {
                token_type: TokenType::Number,
                string: self.read_number(),
                location: self.token_location(&start_location),
                had_whitespace_before: had_whitespace
            });
        }

        if is_start_part_of_name(self.c) {
            string.push(self.next());

            while is_part_of_name(self.c) {
                string.push(self.next());
            }

            if self.c == '!' || self.c == '?' {
                string.push(self.next());
            }

            let token_type = match string.as_str() {
                "and" | "or"     => TokenType::BinaryOperator,
                "true" | "false" => TokenType::Bool,
                _                => TokenType::Name
            };

            return Ok(Token {
                token_type: token_type,
                string: string,
                location: self.token_location(&start_location),
                had_whitespace_before: had_whitespace
            });
        }

        Err(Error::LexError {
            message: format!("Unexpected token '{}' at {}:{}:{}", self.c, self.file_name(), self.line, self.column),
            location: self.token_location(&start_location)
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
                self.put_back_char = Some(self.c);
                self.c = '.';
            }
        }

        string
    }

    fn read_string(&mut self, with_escape_sequences: bool) -> String {
        let mut string = String::new();
        let mut in_escape_sequence = false;

        let quote = self.c;

        self.next(); // " | '

        while in_escape_sequence || self.c != quote {
            if in_escape_sequence {
                in_escape_sequence = false;

                if with_escape_sequences {
                    string.push(Lexer::escape_sequence(self.next()));
                } else {
                    if self.c != quote && self.c != '\\' {
                        string.push('\\');
                    }

                    string.push(self.next());
                }
            } else if self.c == '\\' {
                in_escape_sequence = true;
                self.next(); // \
            } else {
                string.push(self.next());
            }
        }

        self.next(); // " | '

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

    fn skip_whitespace_and_comments(&mut self) -> bool {
        let mut in_comment = false;
        let mut had_whitespace = false;

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

            had_whitespace = true;
        }

        had_whitespace
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

    fn token_location(&self, start_location: &Location) -> Location {
        Location {
            file_name: start_location.file_name.clone(),

            start_line: start_location.start_line,
            start_column: start_location.start_column,

            end_line: self.line,
            end_column: self.column
        }
    }
}

fn is_start_part_of_name(c: char) -> bool {
    c.is_alphabetic() || c == '_' || c == '@' || c == '$'
}

fn is_part_of_name(c: char) -> bool {
    c.is_alphanumeric() || c == '_' || c == '@'
}
