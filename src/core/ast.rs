use ::core::core::{Value};

pub enum AST {
    BoolLiteral   { value: bool },
    IntLiteral    { value: i64 },
    FloatLiteral  { value: f64 },
    StringLiteral { value: String },

    Name { name: String },

    TypeHint {
        expr: Box<AST>,
        type_expr: Box<AST>
    },

    Assignment {
        name: Box<AST>,
        expr: Box<AST>
    },

    Block(Block),

    FnCall(FnCall),

    Branch {
        condition: Box<AST>,
        true_branch: Block,
        false_branch: Block
    },

    // Loop {
    //     condition: Box<AST>,
    //     body: Block
    // },

    FnDef(FnDef),

    // Used to support partial evaluation
    Value(Value)
}

pub struct FnDef {
    pub name: String,
    pub params: Vec<UnparsedFnParam>,
    pub return_type_expr: Box<AST>,

    pub body: Block
}

pub struct UnparsedFnParam {
    pub name: String,
    pub type_expr: Box<AST>
}

pub struct FnCall {
    pub target: Box<AST>,
    pub name: String,
    pub args: Vec<AST>,
    pub may_be_var_call: bool
}

pub struct Block {
    pub exprs: Vec<AST>
}
