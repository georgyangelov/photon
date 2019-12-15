use std::collections::HashMap;

use types::*;
use lexer::{Lexer, Token, TokenType};
use compiler::Compiler;

pub struct Parser<'a> {
    lexer: Lexer<'a>,
    compiler: Option<&'a Compiler>,

    t: Option<Token>,
    last_location: Location,
    newline: bool,
}

impl<'a> Parser<'a> {
    pub fn new(lexer: Lexer<'a>, compiler: Option<&'a Compiler>) -> Parser<'a> {
        let file_name = lexer.file_name().into();

        Parser {
            lexer,
            compiler,

            t: None,
            last_location: Location {
                file_name: Some(file_name),
                start_line: 0,
                start_column: 0,
                end_line: 0,
                end_column: 0
            },
            newline: false
        }
    }

    pub fn has_more_tokens(&mut self) -> Result<bool, Error> {
        if self.t.is_none() {
            self.read()?;
        }

        Ok(self.token().token_type != TokenType::EOF)
    }

    pub fn parse_next(&mut self) -> Result<Value, Error> {
        if self.t.is_none() {
            self.read()?;
        }

        self.parse_expression(0)
    }

    fn parse_expression(&mut self, min_precedence: u8) -> Result<Value, Error> {
        let mut left = self.parse_primary()?;

        loop {
            if self.newline {
                return Ok(left);
            }

            if self.token().token_type != TokenType::BinaryOperator &&
               self.token().token_type != TokenType::Colon {
                return Ok(left);
            }

            let op_precedence = Parser::operator_precedence(self.token());
            if op_precedence < min_precedence {
                return Ok(left);
            }

            let op = self.read()?;
            let right = self.parse_expression(op_precedence + 1)?;

            let location = {
                let start_location = &left.meta.location.as_ref().unwrap();
                let end_location = &right.meta.location.as_ref().unwrap();

                start_location.extend_with(end_location)
            };

            left = self.as_value_with_location(match op.string.as_str() {
                "=" => {
                    let name = if let Value { object: Object::Op(Op::NameRef(NameRef { ref name })), .. } = left {
                        name.clone()
                    } else {
                        return Err(self.parse_error());
                    };

                    Object::Op(Op::Assign(Assign {
                        name,
                        value: Box::new(right)
                    }))
                },

                _ => Object::Op(Op::Call(Call {
                    target: Box::new(left),
                    name: op.string,
                    args: vec![right],
                    may_be_var_call: false,
                    module_resolve: false
                }))
            }, location)
        }
    }

    fn parse_primary(&mut self) -> Result<Value, Error> {
        if self.token().token_type == TokenType::BinaryOperator && self.token().string == "-" {
            let start_location = self.token().location.clone();
            self.read()?; // -

            let expression = self.parse_primary()?;

            let location = {
                let end_location = expression.meta.location.as_ref().unwrap();

                start_location.extend_with(end_location)
            };

            let object = Object::Op(Op::Call(Call {
                target: Box::new(expression),
                name: String::from("-"),
                args: vec![],
                may_be_var_call: false,
                module_resolve: false
            }));

            return Ok(self.as_value_with_location(object, location));
        }

        let mut target = self.parse_method_target()?;

        loop {
            target = self.maybe_parse_method_call(target)?;

            if self.token().token_type != TokenType::Dot &&
                self.token().token_type != TokenType::DoubleColon && (
                self.newline || self.token().token_type != TokenType::OpenParen
            ) {
                break;
            }
        }

        Ok(target)
    }

    fn parse_method_target(&mut self) -> Result<Value, Error> {
        match self.token().token_type {
            // TokenType::Nil => {
            //     self.read()?;
            //
            //     Ok(AST::NilLiteral)
            // },
            TokenType::Bool => self.parse_bool(),
            TokenType::Number => self.parse_number(),
            TokenType::String => {
                let string = self.read()?;

                Ok(self.as_value(
                    Object::from(string.string.clone()),
                    &string.location,
                    &string.location
                ))
            },
            TokenType::UnknownLiteral => {
                let literal = self.read()?;

                Ok(self.as_value(
                    Object::Unknown,
                    &literal.location,
                    &literal.location
                ))
            },
            TokenType::Name => {
                let name = self.read()?;

                if let Some(ref compiler) = self.compiler {
                    if compiler.has_macro(&name.string) {
                        let result = compiler.handle_macro(&name.string, self, &name.location)?;

                        return Ok(self.as_value(
                            result,
                            &name.location,
                            &self.last_location
                        ));
                    }
                }

                Ok(self.as_value(
                    Object::Op(Op::NameRef(NameRef { name: name.string.clone() })),
                    &name.location,
                    &name.location
                ))
            },
            TokenType::Dollar => self.parse_struct_literal(),

            TokenType::OpenBrace => self.parse_lambda(),

            TokenType::UnaryOperator => {
                let operator = self.read()?;
                let target = self.parse_primary()?;

                Ok(self.as_value(
                    Object::Op(Op::Call(Call {
                        name: operator.string,
                        target: Box::new(target),
                        args: vec![],
                        may_be_var_call: false,
                        module_resolve: false
                    })),
                    &operator.location,
                    &self.last_location
                ))
            },

            TokenType::OpenParen => {
                self.read()?; // (
                let value = self.parse_next()?;

                if self.token().token_type != TokenType::CloseParen {
                    return Err(self.parse_error());
                }

                self.read()?; // )
                Ok(value)
            },

            _ => Err(self.parse_error())
        }
    }

    fn parse_struct_literal(&mut self) -> Result<Value, Error> {
        let start_token = self.read()?; // $

        if self.token().token_type != TokenType::OpenBrace {
            return Err(self.parse_error());
        }

        self.read()?; // {

        if self.token().token_type == TokenType::CloseBrace {
            self.read()?; // }
            return Ok(self.as_value(
                Object::Struct(Struct {
                    values: HashMap::new()
                }),
                &start_token.location,
                &self.last_location
            ));
        }

        let values = self.parse_struct_literal_values()?;

        if self.token().token_type != TokenType::CloseBrace {
            return Err(self.parse_error());
        }

        self.read()?; // }

        Ok(self.as_value(
            Object::Struct(Struct {
                values
            }),
            &start_token.location,
            &self.last_location
        ))
    }

    fn parse_struct_literal_values(&mut self) -> Result<HashMap<String, Value>, Error> {
        let mut values = HashMap::new();

        loop {
            if self.token().token_type != TokenType::Name {
                return Err(self.parse_error());
            }

            let name = self.read()?.string;

            if self.token().token_type != TokenType::Colon {
                return Err(self.parse_error());
            }

            self.read()?; // :

            let value = self.parse_next()?;

            values.insert(name, value);

            if self.token().token_type != TokenType::Comma {
                break;
            }

            self.read()?; // ,
        }

        Ok(values)
    }

    fn parse_lambda(&mut self) -> Result<Value, Error> {
        self.read()?; // {
        let start_location = self.last_location.clone();

        let params: Vec<Param>;

        if self.token().token_type == TokenType::Pipe {
            self.read()?; // |
            params = self.parse_params()?;

            if self.token().token_type != TokenType::Pipe {
                return Err(self.parse_error());
            }
            self.read()?; // |
        } else {
            params = vec![];
        }

        let body = self.parse_block()?;

        if self.token().token_type != TokenType::CloseBrace {
            return Err(self.parse_error());
        }
        self.read()?; // }

        let end_location = self.last_location.clone();

        Ok(self.as_value(
            Object::Lambda(Lambda {
                params,
                body,
                scope: None
            }),
            &start_location,
            &end_location
        ))
    }

    fn parse_params(&mut self) -> Result<Vec<Param>, Error> {
        let mut params = Vec::new();

        loop {
            params.push(self.parse_param()?);

            if self.token().token_type != TokenType::Comma {
                break;
            }

            self.read()?; // ,
        }

        Ok(params)
    }

    fn parse_param(&mut self) -> Result<Param, Error> {
        if self.token().token_type != TokenType::Name {
            return Err(self.parse_error());
        }

        let name = self.read()?.string;

        Ok(Param { name })
    }

    fn parse_block(&mut self) -> Result<Block, Error> {
        let mut exprs = Vec::new();

        while !self.block_ends() {
            exprs.push(self.parse_next()?);
        }

        Ok(Block { exprs })
    }

    fn maybe_parse_method_call(&mut self, target: Value) -> Result<Value, Error> {
        let start_location = target.meta.location.as_ref().unwrap().clone();

        // target::name
        if self.token().token_type == TokenType::DoubleColon {
            self.read()?; // ::

            if self.token().token_type != TokenType::Name {
                return Err(self.parse_error());
            }

            let name = self.read()?;
            let args = self.parse_arguments()?;

            let op = Object::Op(Op::Call(Call {
                target: Box::new(target),
                name: name.string,
                args,
                may_be_var_call: false,
                module_resolve: true
            }));

            return Ok(self.as_value(op, &start_location, &self.last_location));
        }

        // target.call
        if self.token().token_type == TokenType::Dot {
            self.read()?; // .

            if self.token().token_type != TokenType::Name {
                return Err(self.parse_error());
            }

            let name = self.read()?;
            let args = self.parse_arguments()?;

            let op = Object::Op(Op::Call(Call {
                target: Box::new(target),
                name: name.string,
                args,
                may_be_var_call: false,
                module_resolve: false
            }));

            return Ok(self.as_value(op, &start_location, &self.last_location));
        }

        // name a
        // name(a)
        if let Value { object: Object::Op(Op::NameRef(NameRef { ref name })), .. } = target {
            if !self.current_expression_may_end() {
                let args = self.parse_arguments()?;

                let op = Object::Op(Op::Call(Call {
                    target: Box::new(self.as_value(Object::Op(Op::NameRef(NameRef {
                        name: String::from("self")
                    })), &start_location, &start_location)),
                    name: name.clone(),
                    args,
                    may_be_var_call: true,
                    module_resolve: false
                }));

                return Ok(self.as_value(op, &start_location, &self.last_location));
            }

        // expression( ... )
        } else if !self.token().had_whitespace_before && self.token().token_type == TokenType::OpenParen {
            self.read()?; // (

            let args = if self.token().token_type != TokenType::CloseParen {
                self.parse_ast_list()?
            } else {
                Vec::<Value>::new()
            };

            if self.token().token_type != TokenType::CloseParen {
                return Err(self.parse_error());
            }
            self.read()?; // )

            let op = Op::Call(Call {
                name: String::from("$call"),
                target: Box::new(target),
                args,
                may_be_var_call: false,
                module_resolve: false
            });

            return Ok(self.as_value(Object::Op(op), &start_location, &self.last_location));
        }

        Ok(target)
    }

    fn parse_ast_list(&mut self) -> Result<Vec<Value>, Error> {
        let mut nodes = Vec::<Value>::new();

        loop {
            nodes.push(self.parse_next()?);

            if self.token().token_type != TokenType::Comma {
                break;
            }

            self.read()?; // ,
        }

        Ok(nodes)
    }

    fn parse_arguments(&mut self) -> Result<Vec<Value>, Error> {
        let mut with_parens = false;

        if self.token().token_type == TokenType::OpenParen {
            self.read()?; // (
            with_parens = true;
        }

        if !with_parens && self.current_expression_may_end() {
            return Ok(vec![])
        }

        if with_parens && self.token().token_type == TokenType::CloseParen {
            self.read()?; // )
            return Ok(vec![]);
        }

        let args = self.parse_ast_list()?;

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

    fn parse_bool(&mut self) -> Result<Value, Error> {
        let string = self.read()?.string;

        if string == "true" {
            Ok(self.as_value(Object::from(true), &self.last_location, &self.last_location))
        } else if string == "false" {
            Ok(self.as_value(Object::from(false), &self.last_location, &self.last_location))
        } else {
            Err(self.parse_error())
        }
    }

    fn parse_number(&mut self) -> Result<Value, Error> {
        let string = self.read()?.string;

        if string.contains(".") {
            let value = string.parse::<f64>();

            if let Ok(value) = value {
                Ok(self.as_value(Object::from(value), &self.last_location, &self.last_location))
            } else {
                Err(self.parse_error())
            }
        } else {
            let value = string.parse::<i64>();

            if let Ok(value) = value {
                Ok(self.as_value(Object::from(value), &self.last_location, &self.last_location))
            } else {
                Err(self.parse_error())
            }
        }
    }

    fn current_expression_may_end(&self) -> bool {
        let t = self.token();

        self.newline ||
        t.token_type == TokenType::EOF ||
        t.token_type == TokenType::BinaryOperator ||
        t.token_type == TokenType::Colon ||
        t.token_type == TokenType::Comma ||
        t.token_type == TokenType::CloseParen ||
        t.token_type == TokenType::Dot ||
        t.token_type == TokenType::DoubleColon ||
        t.token_type == TokenType::CloseBracket ||
        t.token_type == TokenType::Pipe ||
        self.block_ends()
    }

    fn block_ends(&self) -> bool {
        let t = self.token();

        // t.token_type == TokenType::End ||
        // t.token_type == TokenType::Else ||
        // t.token_type == TokenType::Elsif ||
        // t.token_type == TokenType::Catch ||
        t.token_type == TokenType::CloseBrace
    }

    // fn is_const_name(name: &str) -> bool {
    //     name.chars().nth(0).unwrap().is_uppercase()
    // }

    fn parse_error(&self) -> Error {
        Error::ParseError {
            message: format!("Unexpected token {:?}", self.token()),
            location: self.last_location.clone()
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
            ":" => 7,
            _ => panic!(format!("Unknown binary operator '{}'", op.string))
        }
    }

    fn token(&self) -> &Token {
        self.t.as_ref().expect("First token not initialized")
    }

    // TODO: Figure out a way to not clone the result
    fn read(&mut self) -> Result<Token, Error> {
        let old_token = self.t.clone();

        self.newline = false;

        let mut token = self.lexer.next_token()?;

        while token.token_type == TokenType::NewLine {
            self.newline = true;
            token = self.lexer.next_token()?;
        }

        self.t = Some(token);

        if let Some(ref token) = old_token {
            self.last_location = token.location.clone();

            Ok(token.clone())
        } else {
            Ok(self.token().clone())
        }
    }

    fn as_value(&self, object: Object, start: &Location, end: &Location) -> Value {
        Value {
            object,
            meta: Meta {
                location: Some(start.extend_with(&end))
            }
        }
    }

    fn as_value_with_location(&self, object: Object, location: Location) -> Value {
        Value {
            object,
            meta: Meta {
                location: Some(location)
            }
        }
    }
}
