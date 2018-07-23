pub enum AST {
    NilLiteral,
    BoolLiteral   { value: bool },
    IntLiteral    { value: i64 },
    FloatLiteral  { value: f64 },
    StringLiteral { value: String },

    Name {
        name: String,

        value_type: Option<Type>
    },

    Assignment {
        name: String,
        expr: Box<AST>,

        value_type: Option<Type>
    },

    Block(Block),

    MethodCall(MethodCall),

    Branch {
        condition: Box<AST>,
        true_branch: Block,
        false_branch: Block
    },

    Loop {
        condition: Box<AST>,
        body: Block
    },

    Lambda {
        params: Vec<UnparsedMethodParam>,
        body: Block
    },

    MethodDef(MethodDef),
}

pub struct MethodDef {
    pub name: String,
    pub params: Vec<UnparsedMethodParam>,
    pub return_type: String,

    pub body: Block
}

pub struct UnparsedMethodParam {
    pub name: String,
    pub kind: String
}

pub struct MethodCall {
    pub target: Box<AST>,
    pub name: String,
    pub args: Vec<AST>,
    pub may_be_var_call: bool,

    pub value_type: Option<Type>
}

pub struct Block {
    pub exprs: Vec<AST>,
    pub catches: Vec<Catch>,

    pub value_type: Option<Type>
}

pub struct Catch {
    pub kind: String,
    pub name: Option<String>,
    pub body: Block
}

// ---

#[derive(Debug)]
pub enum Type {
    None,
    Bool,
    Int,
    Float,
    Ref(RefType)
}

#[derive(Debug)]
pub struct RefType {
    pub methods: Vec<MethodSignature>
}

#[derive(Debug)]
pub struct MethodSignature {
    pub name: String,
    pub params: Vec<MethodParam>,
    pub return_type: Type
}

#[derive(Debug)]
pub struct MethodParam {
    pub name: String,
    pub kind: Type
}
