use super::*;
use ::data_structures::ast::*;

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

            left = if op.string == "=" {
                if let AST::Name { ref name } = left {
                    AST::Assignment {
                        name: name.clone(),
                        expr: Box::new(right)
                    }
                } else {
                    return Err(self.parse_error());
                }
            } else {
                AST::MethodCall(MethodCallAST {
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
                _ => AST::MethodCall(MethodCallAST {
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
            TokenType::Nil => {
                self.read()?;

                Ok(AST::NilLiteral)
            },
            TokenType::Bool        => self.parse_bool(),
            TokenType::Number      => self.parse_number(),
            TokenType::String      => Ok(AST::StringLiteral { value: self.read()?.string }),
            TokenType::Name        => Ok(AST::Name { name: self.read()?.string }),
            TokenType::If          => self.parse_if(),
            TokenType::While       => self.parse_loop(),
            TokenType::Def         => self.parse_def(),
            TokenType::Begin       => self.parse_standalone_block(),
            TokenType::OpenBracket => self.parse_array_literal(),

            TokenType::OpenBrace |
            TokenType::Do          => self.parse_lambda(),

            TokenType::UnaryOperator => Ok(AST::MethodCall(MethodCallAST {
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

    fn parse_lambda(&mut self) -> Result<AST, ParseError> {
        let is_multiline = self.token().token_type == TokenType::Do;

        self.read()?; // do or {

        let params: Vec<MethodParam>;

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

        let body = self.parse_block(false)?;

        if is_multiline && self.token().token_type != TokenType::End ||
           !is_multiline && self.token().token_type != TokenType::CloseBrace {
            return Err(self.parse_error());
        }
        self.read()?; // end or }

        Ok(AST::Lambda { params: params, body: body })
    }

    fn parse_standalone_block(&mut self) -> Result<AST, ParseError> {
        self.read()?; // begin

        let block = self.parse_block(false)?;

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
        let params: Vec<MethodParam>;

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

        let return_kind = if self.token().token_type == TokenType::Colon {
            self.read()?; // :

            if self.token().token_type != TokenType::Name {
                return Err(self.parse_error());
            }

            self.read()?.string
        } else {
            String::from("Nil")
        };

        let body = self.parse_block(false)?;

        if self.token().token_type != TokenType::End {
            return Err(self.parse_error());
        }
        self.read()?; // end

        Ok(AST::MethodDef(MethodDefAST {
            name: name,
            params: params,
            return_kind: return_kind,
            body: body
        }))
    }

    fn parse_params(&mut self) -> Result<Vec<MethodParam>, ParseError> {
        let mut params = Vec::<MethodParam>::new();

        loop {
            params.push(self.parse_param()?);

            if self.token().token_type != TokenType::Comma {
                break;
            }

            self.read()?; // ,
        }

        Ok(params)
    }

    fn parse_param(&mut self) -> Result<MethodParam, ParseError> {
        if self.token().token_type != TokenType::Name {
            return Err(self.parse_error());
        }
        let name = self.read()?.string;

        if self.token().token_type != TokenType::Colon {
            return Err(self.parse_error());
        }
        self.read()?; // :

        if self.token().token_type != TokenType::Name {
            return Err(self.parse_error());
        }
        let kind = self.read()?.string;

        Ok(MethodParam {
            name: name,
            kind: kind
        })
    }

    fn parse_if(&mut self) -> Result<AST, ParseError> {
        self.read()?; // `if` or `elsif`

        let condition = self.parse_next()?;

        if !self.newline {
            return Err(self.parse_error());
        }

        let true_branch = self.parse_block(false)?;
        let mut false_branch = BlockAST { exprs: vec![], catches: vec![] };

        if self.token().token_type == TokenType::Else {
            self.read()?; // else
            false_branch = self.parse_block(false)?;

            if self.token().token_type != TokenType::End {
                return Err(self.parse_error());
            }

            self.read()?; // end
        } else if self.token().token_type == TokenType::Elsif {
            false_branch = BlockAST {
                exprs: vec![self.parse_if()?],
                catches: vec![]
            };
        } else {
            if self.token().token_type != TokenType::End {
                return Err(self.parse_error());
            }

            self.read()?; // end
        }

        Ok(AST::Branch {
            condition: Box::new(condition),
            true_branch: true_branch,
            false_branch: false_branch
        })
    }

    fn parse_loop(&mut self) -> Result<AST, ParseError> {
        self.read()?; // while

        let condition = self.parse_next()?;

        if !self.newline {
            return Err(self.parse_error());
        }

        let body = self.parse_block(false)?;

        if self.token().token_type != TokenType::End {
            return Err(self.parse_error());
        }

        self.read()?; // end

        Ok(AST::Loop {
            condition: Box::new(condition),
            body: body
        })
    }

    fn parse_block(&mut self, skip_catch: bool) -> Result<BlockAST, ParseError> {
        let mut exprs = Vec::<AST>::new();
        let mut catches = Vec::<Catch>::new();

        while !self.block_ends() {
            exprs.push(self.parse_next()?);
        }

        if !skip_catch {
            while self.token().token_type == TokenType::Catch {
                catches.push(self.parse_catch()?);
            }
        }

        Ok(BlockAST {
            exprs: exprs,
            catches: catches
        })
    }

    fn parse_catch(&mut self) -> Result<Catch, ParseError> {
        self.read()?; // catch

        let mut name: Option<String> = None;
        let mut kind: String = String::from("Error");

        if !self.newline {
            if self.token().token_type == TokenType::Name && !Self::is_const_name(self.token().string.as_str()) {
                name = Some(self.read()?.string);

                if self.token().token_type == TokenType::Colon {
                    self.read()?; // :

                    if self.token().token_type != TokenType::Name {
                        return Err(self.parse_error());
                    }
                    kind = self.read()?.string;
                }
            } else if self.token().token_type == TokenType::Name {
                kind = self.read()?.string;
            }
        }

        let body = self.parse_block(true)?;

        Ok(Catch {
            name: name,
            kind: kind,
            body: body
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

            return Ok(AST::MethodCall(MethodCallAST {
                name: name.string,
                target: Box::new(target),
                args: args,
                may_be_var_call: false
            }));
        }

        // name a
        // name(a)
        if let AST::Name { ref name } = target {
            if !self.current_expression_may_end() {
                let args = self.parse_arguments()?;

                return Ok(AST::MethodCall(MethodCallAST {
                    name: name.clone(),
                    target: Box::new(AST::Name { name: String::from("self") }),
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

            return Ok(AST::MethodCall(MethodCallAST {
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

        Ok(AST::MethodCall(MethodCallAST {
            name: String::from("new"),
            target: Box::new(AST::Name { name: String::from("Array") }),
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
        t.token_type == TokenType::Comma ||
        t.token_type == TokenType::CloseParen ||
        t.token_type == TokenType::Dot ||
        t.token_type == TokenType::CloseBracket ||
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

    fn is_const_name(name: &str) -> bool {
        name.chars().nth(0).unwrap().is_uppercase()
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

    // fn expect_token() -> Option<Token> {
    //
    // }

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
