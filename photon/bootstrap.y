# Required native functions
#
# eval
# define_literal_method
# define_syntax
# loop, break
#

# Conditionals
TrueModule.define_method "if_true", { |lambda| lambda.call }
FalseModule.define_method "if_true", { |_| $nothing }

TrueModule.define_method "not", { false }
FalseModule.define_method "not", { true }

Global.define_method "not", { |value| value.to_bool.not }

define_syntax "if", { |parser|
  $condition = parser.read_expression
  $block = parser.read_block

  run {
    eval($condition).to_bool.if_true { eval $block }
  }
}

define_syntax "while", { |parser|
  $condition = parser.read_expression
  $block = parser.read_block

  run {
    loop {
      condition = eval($condition).to_bool

      condition.not.if_true { break }

      eval $block
    }
  }
}
