Core.define_macro "if", { |parser|
  condition = parser.parse_one
  if_true = parser.parse_one
  if_false = (parser.token.string == "else").if_else({ #parser.parse_one }, { {} })

  (#condition).to_bool.if_else({ #if_true }, { if_false })()
}

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
