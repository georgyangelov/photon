Core.define_macro "if", { |parser|
  condition = parser.parse_one
  if_true = parser.parse_one
  if_false = (parser.token.string == "else").if_else({ parser.parse_one.eval }, { {} })

  condition.eval.to_bool.if_else(if_true.eval, if_false)
}

# #################
#
# if true { 42 }
#
# ##
#
# condition = <AST: true>
# if_true = <AST: { 42 }>
# if_false = ("" == "else").if_else({ parser.parse_one.eval }, { {} })
#
# condition.eval.to_bool.if_else({ if_true.eval }, { if_false })()
#
# ##
#
# condition = <AST: true>
# if_true = <AST: { 42 }>
# if_false = {}
#
# condition.eval.to_bool.if_else({ if_true.eval }, { if_false })()
#
# ##
#
# <AST: true>.eval.to_bool.if_else({ <AST: { 42 }>.eval }, { {} })()
#
# ##
#
# true.to_bool.if_else({ 42 }, { {} })()
#
# ##
#
# { 42 }()
#
# ##
#
# 42

# #################
#
# if x { y } else { z }
#
# ##
#
# condition = <AST: x>
# if_true = <AST: { y }>
# if_false = true.if_else({ #parser.parse_one }, { {} })
#
# (#condition).to_bool.if_else({ #if_true }, { if_false })()
#
# ##
#
# if_false = #parser.parse_one
#
# x.to_bool.if_else({ { y } }, { if_false })()
#
# ##
#
# if_false = #<AST: { z }>
#
# x.to_bool.if_else({ { y } }, { if_false })()
#
# ##
#
# x.to_bool.if_else({ { y } }, { { z } })()

# {
#  (call
#    (lambda [(param condition)] {
#      (call
#        (lambda [(param if_true)] {
#          (call
#            (lambda [(param if_false)] {
#              (call
#                (if_else (to_bool condition)
#                  (lambda [] { if_true })
#                  (lambda [] { if_false })
#                )
#              )
#            })
#            (if_else (== (string (token parser)) "else")
#              (lambda [] { (# (parse_one parser)) })
#              (lambda [] { (lambda [] {  }) })
#            )
#          )
#        })
#        (# (parse_one parser))
#      )
#    })
#    $?
#  )
#  (lambda [] { 42 })
# }
