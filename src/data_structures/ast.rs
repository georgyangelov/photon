pub enum AST {
    NilLiteral,
    BoolLiteral   { value: bool },
    IntLiteral    { value: i64 },
    FloatLiteral  { value: f64 },
    StringLiteral { value: String },

    Name { name: String },

    Assignment {
        name: String,
        expr: Box<AST>
    },

    Block(BlockAST),

    MethodCall(MethodCallAST),

    Branch {
        condition: Box<AST>,
        true_branch: BlockAST,
        false_branch: BlockAST
    },

    Loop {
        condition: Box<AST>,
        body: BlockAST
    },

    Lambda {
        params: Vec<MethodParam>,
        body: BlockAST
    },

    MethodDef(MethodDefAST),
}

pub struct MethodDefAST {
    pub name: String,
    pub return_kind: String,
    pub params: Vec<MethodParam>,
    pub body: BlockAST
}

pub struct MethodCallAST {
    pub target: Box<AST>,
    pub name: String,
    pub args: Vec<AST>,
    pub may_be_var_call: bool
}

pub struct BlockAST {
    pub exprs: Vec<AST>,
    pub catches: Vec<Catch>
}

pub struct MethodParam {
    pub name: String,
    pub kind: String
}

pub struct Catch {
    pub kind: String,
    pub name: Option<String>,
    pub body: BlockAST
}
