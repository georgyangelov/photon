use ::core::core::{Value};

pub enum AST {
    BoolLiteral   { value: bool },
    IntLiteral    { value: i64 },
    FloatLiteral  { value: f64 },
    StringLiteral { value: String },

    Name { name: String },

    TypeHint(TypeHint),
    Assignment(Assignment),
    Block(Block),
    FnCall(FnCall),
    Branch(Branch),

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

pub struct Branch {
    pub condition: Box<AST>,
    pub true_branch: Block,
    pub false_branch: Block
}

pub struct TypeHint {
    pub expr: Box<AST>,
    pub type_expr: Box<AST>
}

pub struct Assignment {
    pub name: Box<AST>,
    pub expr: Box<AST>
}
