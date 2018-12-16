use super::*;
use ::core::ast::*;

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

            left = match op.string.as_str() {
                "=" => AST::Assignment(Assignment {
                    name: Box::new(left),
                    expr: Box::new(right)
                }),

                ":" => AST::TypeHint(TypeHint {
                    expr: Box::new(left),
                    type_expr: Box::new(right)
                }),

                _ => AST::FnCall(FnCall {
                    target: Box::new(left),
                    name: op.string,
                    args: vec![right],
                    may_be_var_call: false
                })
            }
        }
    }

    fn parse_primary(&mut self) -> Result<AST, ParseError> {
        if self.token().token_type == TokenType::BinaryOperator && self.token().string == "-" {
            self.read()?; // -

            let expression = self.parse_primary()?;
            let expression = match expression {
                AST::IntLiteral { value } => AST::IntLiteral { value: -value },
                AST::FloatLiteral { value } => AST::FloatLiteral { value: -value },
                _ => AST::FnCall(FnCall {
                    name: String::from("-"),
                    target: Box::new(expression),
                    args: vec![],
                    may_be_var_call: false
                })
            };

            return Ok(expression);
        }

        let mut target = self.parse_method_target()?;

        loop {
            target = self.maybe_parse_method_call(target)?;

            if self.token().token_type != TokenType::Dot && (
                !self.newline || self.token().token_type != TokenType::OpenParen
            ) {
                break;
            }
        }

        Ok(target)
    }

    fn parse_method_target(&mut self) -> Result<AST, ParseError> {
        match self.token().token_type {
            // TokenType::Nil => {
            //     self.read()?;
            //
            //     Ok(AST::NilLiteral)
            // },
            TokenType::Bool        => self.parse_bool(),
            TokenType::Number      => self.parse_number(),
            TokenType::String      => Ok(AST::StringLiteral { value: self.read()?.string }),
            TokenType::Name        => Ok(AST::Name { name: self.read()?.string }),
            TokenType::If          => self.parse_if(),
            // TokenType::While       => self.parse_loop(),
            TokenType::Def         => self.parse_def(),
            TokenType::Begin       => self.parse_standalone_block(),
            TokenType::OpenBracket => self.parse_array_literal(),
            TokenType::Dollar      => self.parse_struct_literal(),
            TokenType::Module      => self.parse_module(),

            // TokenType::OpenBrace |
            // TokenType::Do          => self.parse_lambda(),

            TokenType::UnaryOperator => Ok(AST::FnCall(FnCall {
                name: self.read()?.string,
                target: Box::new(self.parse_primary()?),
                args: vec![],
                may_be_var_call: false
            })),

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

    fn parse_struct_literal(&mut self) -> Result<AST, ParseError> {
        self.read()?; // $

        if self.token().token_type != TokenType::OpenBrace {
            return Err(self.parse_error());
        }

        self.read()?; // {

        if self.token().token_type == TokenType::CloseBrace {
            self.read()?; // }
            return Ok(AST::StructLiteral(StructLiteral { tuples: Vec::new() }));
        }

        let tuples = self.parse_struct_literal_tuples()?;

        if self.token().token_type != TokenType::CloseBrace {
            return Err(self.parse_error());
        }

        self.read()?; // }

        Ok(AST::StructLiteral(StructLiteral { tuples }))
    }

    fn parse_struct_literal_tuples(&mut self) -> Result<Vec<(String, Box<AST>)>, ParseError> {
        let mut params = Vec::new();

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

            params.push((name, Box::new(value)));

            if self.token().token_type != TokenType::Comma {
                break;
            }

            self.read()?; // ,
        }

        Ok(params)
    }

    // fn parse_struct(&mut self) -> Result<AST, ParseError> {
    //     self.read()?; // struct
    //
    //     if self.token().token_type != TokenType::Name {
    //         return Err(self.parse_error());
    //     }
    //
    //     let name = self.read()?.string;
    //     let body = self.parse_block()?;
    //
    //     if self.token().token_type != TokenType::End {
    //         return Err(self.parse_error());
    //     }
    //     self.read()?; // end
    //
    //     Ok(AST::StructDef(StructDef { name, body }))
    // }

    fn parse_module(&mut self) -> Result<AST, ParseError> {
        self.read()?; // module

        if self.token().token_type != TokenType::Name {
            return Err(self.parse_error());
        }

        let name = self.read()?.string;
        let body = self.parse_block()?;

        if self.token().token_type != TokenType::End {
            return Err(self.parse_error());
        }
        self.read()?; // end

        Ok(AST::ModuleDef(ModuleDef { name, body }))
    }

    // fn parse_lambda(&mut self) -> Result<AST, ParseError> {
    //     let is_multiline = self.token().token_type == TokenType::Do;
    //
    //     self.read()?; // do or {
    //
    //     let params: Vec<UnparsedFnParam>;
    //
    //     if self.token().token_type == TokenType::Pipe {
    //         self.read()?; // |
    //         params = self.parse_params()?;
    //
    //         if self.token().token_type != TokenType::Pipe {
    //             return Err(self.parse_error());
    //         }
    //         self.read()?; // |
    //     } else {
    //         params = vec![];
    //     }
    //
    //     let body = self.parse_block()?;
    //
    //     if is_multiline && self.token().token_type != TokenType::End ||
    //        !is_multiline && self.token().token_type != TokenType::CloseBrace {
    //         return Err(self.parse_error());
    //     }
    //     self.read()?; // end or }
    //
    //     Ok(AST::Lambda { params: params, body: body })
    // }

    fn parse_standalone_block(&mut self) -> Result<AST, ParseError> {
        self.read()?; // begin

        let block = self.parse_block()?;

        if self.token().token_type != TokenType::End {
            return Err(self.parse_error());
        }

        self.read()?; // end

        Ok(AST::Block(block))
    }

    fn parse_def(&mut self) -> Result<AST, ParseError> {
        self.read()?; // def

        if self.token().token_type != TokenType::Name {
            return Err(self.parse_error());
        }

        let name = self.read()?.string;
        let params: Vec<UnparsedFnParam>;

        if self.token().token_type == TokenType::OpenParen {
            self.read()?; // (

            params = self.parse_params()?;

            if self.token().token_type != TokenType::CloseParen {
                return Err(self.parse_error());
            }
            self.read()?;
        } else {
            params = vec![];
        }

        let return_type_expr = if self.token().token_type == TokenType::Colon {
            self.read()?; // :

            self.parse_next()?
        } else {
            AST::Name {
                name: String::from("None")
            }
        };

        let body = self.parse_block()?;

        if self.token().token_type != TokenType::End {
            return Err(self.parse_error());
        }
        self.read()?; // end

        Ok(AST::FnDef(FnDef {
            name: name,
            params: params,
            return_type_expr: Box::new(return_type_expr),
            body: body
        }))
    }

    fn parse_params(&mut self) -> Result<Vec<UnparsedFnParam>, ParseError> {
        let mut params = Vec::<UnparsedFnParam>::new();

        loop {
            params.push(self.parse_param()?);

            if self.token().token_type != TokenType::Comma {
                break;
            }

            self.read()?; // ,
        }

        Ok(params)
    }

    fn parse_param(&mut self) -> Result<UnparsedFnParam, ParseError> {
        if self.token().token_type != TokenType::Name {
            return Err(self.parse_error());
        }
        let name = self.read()?.string;

        if self.token().token_type != TokenType::Colon {
            return Err(self.parse_error());
        }
        self.read()?; // :

        let type_expr = self.parse_next()?;

        Ok(UnparsedFnParam {
            name: name,
            type_expr: Box::new(type_expr)
        })
    }

    fn parse_if(&mut self) -> Result<AST, ParseError> {
        self.read()?; // `if` or `elsif`

        let condition = self.parse_next()?;

        if !self.newline {
            return Err(self.parse_error());
        }

        let true_branch = self.parse_block()?;
        let mut false_branch = Block {
            exprs: vec![]
        };

        if self.token().token_type == TokenType::Else {
            self.read()?; // else
            false_branch = self.parse_block()?;

            if self.token().token_type != TokenType::End {
                return Err(self.parse_error());
            }

            self.read()?; // end
        } else if self.token().token_type == TokenType::Elsif {
            false_branch = Block {
                exprs: vec![self.parse_if()?]
            };
        } else {
            if self.token().token_type != TokenType::End {
                return Err(self.parse_error());
            }

            self.read()?; // end
        }

        Ok(AST::Branch(Branch {
            condition: Box::new(condition),
            true_branch: true_branch,
            false_branch: false_branch
        }))
    }

    // fn parse_loop(&mut self) -> Result<AST, ParseError> {
    //     self.read()?; // while
    //
    //     let condition = self.parse_next()?;
    //
    //     if !self.newline {
    //         return Err(self.parse_error());
    //     }
    //
    //     let body = self.parse_block()?;
    //
    //     if self.token().token_type != TokenType::End {
    //         return Err(self.parse_error());
    //     }
    //
    //     self.read()?; // end
    //
    //     Ok(AST::Loop {
    //         condition: Box::new(condition),
    //         body: body
    //     })
    // }

    fn parse_block(&mut self) -> Result<Block, ParseError> {
        let mut exprs = Vec::<AST>::new();

        while !self.block_ends() {
            exprs.push(self.parse_next()?);
        }

        Ok(Block {
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

            return Ok(AST::FnCall(FnCall {
                name: name.string,
                target: Box::new(target),
                args: args,
                may_be_var_call: false
            }));
        }

        // name a
        // name(a)
        if let AST::Name { ref name, .. } = target {
            if !self.current_expression_may_end() {
                let args = self.parse_arguments()?;

                return Ok(AST::FnCall(FnCall {
                    name: name.clone(),
                    target: Box::new(AST::Name {
                        name: String::from("self")
                    }),
                    args: args,
                    may_be_var_call: true
                }));
            }

        // expression( ... )
        } else if !self.token().had_whitespace_before && self.token().token_type == TokenType::OpenParen {
            self.read()?; // (

            let args = if self.token().token_type != TokenType::CloseParen {
                self.parse_ast_list()?
            } else {
                Vec::<AST>::new()
            };

            if self.token().token_type != TokenType::CloseBracket {
                return Err(self.parse_error());
            }
            self.read()?; // )

            return Ok(AST::FnCall(FnCall {
                name: String::from("call"),
                target: Box::new(target),
                args: args,
                may_be_var_call: false
            }));
        }

        Ok(target)
    }

    fn parse_array_literal(&mut self) -> Result<AST, ParseError> {
        self.read()?; // [
        let elements = self.parse_ast_list()?;

        if self.token().token_type != TokenType::CloseBracket {
            return Err(self.parse_error());
        }
        self.read()?; // ]

        Ok(AST::FnCall(FnCall {
            name: String::from("new"),
            target: Box::new(AST::Name {
                name: String::from("Array")
            }),
            args: elements,
            may_be_var_call: false
        }))
    }

    fn parse_ast_list(&mut self) -> Result<Vec<AST>, ParseError> {
        let mut nodes = Vec::<AST>::new();

        loop {
            nodes.push(self.parse_next()?);

            if self.token().token_type != TokenType::Comma {
                break;
            }

            self.read()?; // ,
        }

        Ok(nodes)
    }

    fn parse_arguments(&mut self) -> Result<Vec<AST>, ParseError> {
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

    fn parse_bool(&mut self) -> Result<AST, ParseError> {
        let string = self.read()?.string;

        if string == "true" {
            Ok(AST::BoolLiteral { value: true })
        } else if string == "false" {
            Ok(AST::BoolLiteral { value: false })
        } else {
            Err(self.parse_error())
        }
    }

    fn parse_number(&mut self) -> Result<AST, ParseError> {
        let string = self.read()?.string;

        if string.contains(".") {
            let value = string.parse::<f64>();

            if let Ok(value) = value {
                Ok(AST::FloatLiteral { value: value })
            } else {
                Err(self.parse_error())
            }
        } else {
            let value = string.parse::<i64>();

            if let Ok(value) = value {
                Ok(AST::IntLiteral { value: value })
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
        t.token_type == TokenType::CloseBracket ||
        t.token_type == TokenType::Pipe ||
        self.block_ends()
    }

    fn block_ends(&self) -> bool {
        let t = self.token();

        t.token_type == TokenType::End ||
        t.token_type == TokenType::Else ||
        t.token_type == TokenType::Elsif ||
        t.token_type == TokenType::Catch ||
        t.token_type == TokenType::CloseBrace
    }

    // fn is_const_name(name: &str) -> bool {
    //     name.chars().nth(0).unwrap().is_uppercase()
    // }

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
            ":" => 7,
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
