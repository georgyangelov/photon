use super::*;

pub enum AST {
    IntLiteral    { value: i64 },
    FloatLiteral  { value: f64 },
    StringLiteral { value: String },

    Name { name: String },

    Block {
        exprs: Vec<AST>,
    },

    MethodCall {
        target: Box<AST>,
        name: String,
        args: Vec<AST>
    },

    Branch {
        condition: Box<AST>,
        true_branch: Box<AST>,
        false_branch: Box<AST>
    },

    Loop {
        condition: Box<AST>,
        body: Box<AST>
    }
}

pub struct Parser<'a> {
    lexer: Lexer<'a>,

    t: Option<Token>,
    newline: bool,
}

impl<'a> Parser<'a> {
    pub fn new(lexer: Lexer<'a>) -> Parser<'a> {
        Parser {
            lexer: lexer,

            t: None,
            newline: false
        }
    }

    pub fn has_more_tokens(&mut self) -> Result<bool, ParseError> {
        if self.t.is_none() {
            self.read()?;
        }

        Ok(self.token().token_type != TokenType::EOF)
    }

    pub fn parse_next(&mut self) -> Result<AST, ParseError> {
        if self.t.is_none() {
            self.read()?;
        }

        self.parse_expression(0)
    }

    fn parse_expression(&mut self, min_precedence: u8) -> Result<AST, ParseError> {
        let mut left = self.parse_primary()?;

        loop {
            if self.newline {
                return Ok(left);
            }

            if self.token().token_type != TokenType::BinaryOperator {
                return Ok(left);
            }

            let op_precedence = Parser::operator_precedence(self.token());
            if op_precedence < min_precedence {
                return Ok(left);
            }

            let op = self.read()?;
            let right = self.parse_expression(op_precedence + 1)?;

            left = AST::MethodCall {
                target: Box::new(left),
                name: op.string,
                args: vec![right]
            };
        }
    }

    fn parse_primary(&mut self) -> Result<AST, ParseError> {
        if self.token().token_type == TokenType::BinaryOperator && self.token().string == "-" {
            self.read()?; // -

            let expression = self.parse_primary()?;
            let expression = match expression {
                AST::IntLiteral { value } => {
                    AST::IntLiteral { value: -value }
                },
                AST::FloatLiteral { value } => {
                    AST::FloatLiteral { value: -value }
                },
                _ => AST::MethodCall {
                    name: String::from("-"),
                    target: Box::new(expression),
                    args: vec![]
                }
            };

            return Ok(expression);
        }

        let target = self.parse_method_target()?;

        self.maybe_parse_method_call(target)
    }

    fn parse_method_target(&mut self) -> Result<AST, ParseError> {
        match self.token().token_type {
            TokenType::Number => Self::parse_number(self.read()?.string.as_str()),
            TokenType::String => Ok(AST::StringLiteral { value: self.read()?.string }),
            TokenType::Name   => Ok(AST::Name { name: self.read()?.string }),
            TokenType::If     => self.parse_if(),
            TokenType::While  => self.parse_loop(),

            TokenType::UnaryOperator => Ok(AST::MethodCall {
                name: self.read()?.string,
                target: Box::new(self.parse_primary()?),
                args: vec![]
            }),

            TokenType::OpenParen => {
                self.read()?; // (
                let ast = self.parse_next()?;

                if self.token().token_type != TokenType::CloseParen {
                    return Err(self.parse_error());
                }

                self.read()?; // )
                Ok(ast)
            },

            _ => Err(self.parse_error())
        }
    }

    fn parse_if(&mut self) -> Result<AST, ParseError> {
        self.read()?; // `if` or `elsif`

        let condition = self.parse_next()?;

        if !self.newline {
            return Err(self.parse_error());
        }

        let true_branch = self.parse_block()?;
        let mut false_branch = AST::Block { exprs: vec![] };

        if self.token().token_type == TokenType::Else {
            self.read()?; // else
            false_branch = self.parse_block()?;

            if self.token().token_type != TokenType::End {
                return Err(self.parse_error());
            }

            self.read()?; // end
        } else if self.token().token_type == TokenType::Elsif {
            false_branch = AST::Block {
                exprs: vec![self.parse_if()?]
            };
        } else {
            if self.token().token_type != TokenType::End {
                return Err(self.parse_error());
            }

            self.read()?; // end
        }

        Ok(AST::Branch {
            condition: Box::new(condition),
            true_branch: Box::new(true_branch),
            false_branch: Box::new(false_branch)
        })
    }

    fn parse_loop(&mut self) -> Result<AST, ParseError> {
        self.read()?; // while

        let condition = self.parse_next()?;

        if !self.newline {
            return Err(self.parse_error());
        }

        let body = self.parse_block()?;

        if self.token().token_type != TokenType::End {
            return Err(self.parse_error());
        }

        self.read()?; // end

        Ok(AST::Loop {
            condition: Box::new(condition),
            body: Box::new(body)
        })
    }

    fn parse_block(&mut self) -> Result<AST, ParseError> {
        let mut exprs = Vec::<AST>::new();

        while !self.block_ends() {
            exprs.push(self.parse_next()?);
        }

        Ok(AST::Block {
            exprs: exprs
        })
    }

    fn maybe_parse_method_call(&mut self, target: AST) -> Result<AST, ParseError> {
        // target.call
        if self.token().token_type == TokenType::Dot {
            self.read()?; // .

            if self.token().token_type != TokenType::Name {
                return Err(self.parse_error());
            }

            let name = self.read()?;
            let args = self.parse_arguments()?;

            return Ok(AST::MethodCall {
                name: name.string,
                target: Box::new(target),
                args: args
            });
        }

        // target a
        if let AST::Name { ref name } = target {
            if !self.current_expression_may_end() {
                let args = self.parse_arguments()?;

                return Ok(AST::MethodCall {
                    name: name.clone(),
                    target: Box::new(AST::Name { name: String::from("self") }),
                    args: args
                });
            }
        }

        Ok(target)
    }

    fn parse_arguments(&mut self) -> Result<Vec<AST>, ParseError> {
        let mut args = Vec::<AST>::new();
        let mut with_parens = false;

        if self.token().token_type == TokenType::OpenParen {
            self.read()?; // (
            with_parens = true;
        }

        if !with_parens && self.current_expression_may_end() {
            return Ok(args)
        }

        if with_parens && self.token().token_type == TokenType::CloseParen {
            self.read()?; // )
            return Ok(args);
        }

        loop {
            args.push(self.parse_next()?);

            if self.token().token_type != TokenType::Comma {
                break;
            }

            self.read()?; // ,
        }

        if with_parens {
            if self.token().token_type != TokenType::CloseParen {
                return Err(self.parse_error());
            }

            self.read()?; // )
        } else if !self.current_expression_may_end() {
            return Err(self.parse_error());
        }

        Ok(args)
    }

    fn parse_number(number: &str) -> Result<AST, ParseError> {
        if number.contains(".") {
            let value = number.parse::<f64>();

            if let Ok(value) = value {
                Ok(AST::FloatLiteral { value: value })
            } else {
                Err(ParseError { message: String::from("Could not parse float number") })
            }
        } else {
            let value = number.parse::<i64>();

            if let Ok(value) = value {
                Ok(AST::IntLiteral { value: value })
            } else {
                Err(ParseError { message: String::from("Could not parse integer number") })
            }
        }
    }

    fn current_expression_may_end(&self) -> bool {
        let t = self.token();

        self.newline ||
        t.token_type == TokenType::EOF ||
        t.token_type == TokenType::BinaryOperator ||
        t.token_type == TokenType::Comma ||
        t.token_type == TokenType::CloseParen ||
        self.block_ends()
    }

    fn block_ends(&self) -> bool {
        let t = self.token();

        t.token_type == TokenType::End ||
        t.token_type == TokenType::Else ||
        t.token_type == TokenType::Elsif
    }

    fn parse_error(&self) -> ParseError {
        ParseError {
            message: format!("Unexpected token {:?}", self.token())
        }
    }

    fn operator_precedence(op: &Token) -> u8 {
        match op.string.as_str() {
            "="   => 1,
            "or"  => 2,
            "and" => 3,
            "==" | "<" | ">" | "<=" | ">=" | "!=" => 4,
            "+" | "-" => 5,
            "*" | "/" => 6,
            _ => panic!(format!("Unknown binary operator '{}'", op.string))
        }
    }

    fn token(&self) -> &Token {
        self.t.as_ref().expect("First token not initialized")
    }

    // TODO: Figure out a way to not clone the result
    fn read(&mut self) -> Result<Token, ParseError> {
        let old_token = self.t.clone();

        self.newline = false;

        let mut token = self.lexer.next_token()?;

        while token.token_type == TokenType::NewLine {
            self.newline = true;
            token = self.lexer.next_token()?;
        }

        self.t = Some(token);

        if let Some(ref token) = old_token {
            Ok(token.clone())
        } else {
            Ok(self.token().clone())
        }
    }
}
