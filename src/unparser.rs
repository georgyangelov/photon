use std::fmt;
use types::*;

struct UnparseFormatter<'a> {
    values: &'a [&'a Value]
}

impl <'a> fmt::Display for UnparseFormatter<'a> {
    fn fmt(&self, f: &mut fmt::Formatter) -> fmt::Result {
        unparse_values(self.values, f)
    }
}

pub fn unparse(code: &[&Value]) -> String {
    format!("{}", UnparseFormatter { values: code })
}

pub fn unparse_single(value: &Value) -> String {
    let values = vec![value];

    unparse(&values)
}

fn unparse_values(code: &[&Value], f: &mut fmt::Formatter) -> fmt::Result {
    for (i, value) in code.iter().enumerate() {
        if i > 0 {
            write!(f, "\n")?;
        }

        unparse_value(value, f)?;
    }

    Ok(())
}

pub fn unparse_value(photon_value: &Value, f: &mut fmt::Formatter) -> fmt::Result {
    let object = &photon_value.object;

    match object {
        &Object::Unknown => panic!("Cannot unparse Object::Unknown"),
        &Object::Nothing => write!(f, "$nothing")?,
        &Object::Bool(value) => write!(f, "{}", value)?,
        &Object::Int(value) => write!(f, "{}", value)?,
        &Object::Float(value) => write!(f, "{}", value)?,
        &Object::Str(ref value) => write!(f, "{:?}", value)?,

        &Object::Struct(ref value) => {
            write!(f, "${{")?;

            for (i, (key, val)) in value.values.iter().enumerate() {
                if i > 0 {
                    write!(f, ", ")?;
                }

                let values = [val];

                write!(f, "{}: {}", key, UnparseFormatter { values: &values })?;
            }

            write!(f, "}}")?;
        },

        &Object::Lambda(ref lambda) => {
            let multiline = lambda.body.exprs.len() > 1;

            write!(f, "{{ ")?;

            if lambda.params.len() > 0 {
                write!(f, "|")?;

                for (i, param) in lambda.params.iter().enumerate() {
                    if i > 0 {
                        write!(f, ", ")?;
                    }

                    write!(f, "{}", param.name)?;
                }


                if multiline {
                    write!(f, "|\n")?;
                } else {
                    write!(f, "| ")?;
                }
            }

            for expr in &lambda.body.exprs {
                let values = [expr];

                write!(f, "{}", UnparseFormatter { values: &values })?;

                if multiline {
                    write!(f, "\n")?;
                } else {
                    write!(f, " ")?;
                }
            }

            write!(f, "}}")?;
        },

        &Object::Op(Op::Assign(ref assign)) => {
            let values = [assign.value.as_ref()];

            write!(f, "{} = {}",
                assign.name,
                UnparseFormatter { values: &values }
            )?;
        },

        &Object::Op(Op::Block(ref block)) => {
            let multiline = block.exprs.len() > 1;

            for expr in &block.exprs {
                let values = [expr];

                write!(f, "{}", UnparseFormatter { values: &values })?;

                if multiline {
                    write!(f, "\n")?;
                } else {
                    write!(f, " ")?;
                }
            }
        },

        &Object::Op(Op::Call(ref call)) => {
            let values = [call.target.as_ref()];

            // TODO: Module resolve?
            write!(f, "{}.{}(", UnparseFormatter { values: &values }, call.name)?;

            for (i, val) in call.args.iter().enumerate() {
                if i > 0 {
                    write!(f, ", ")?;
                }

                let values = [val];
                write!(f, "{}", UnparseFormatter { values: &values })?;
            }

            write!(f, ")")?;
        },

        &Object::Op(Op::NameRef(ref name)) => {
            write!(f, "{}", name.name)?;
        },

        &Object::NativeValue(_) => panic!("Cannot unparse native values"),
        &Object::NativeLambda(_) => panic!("Cannot unparse native lambdas")
    };

    Ok(())
}
