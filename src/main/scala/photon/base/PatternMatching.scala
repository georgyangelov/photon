package photon.base

sealed trait Pattern {
  def bindings: Seq[String]
}
object Pattern {
  case class SpecificValue(value: EValue) extends Pattern {
    override def bindings = Seq.empty
  }

  case class Val(name: String, typ: Option[Type]) extends Pattern {
    override def bindings = Seq(name)
  }

  case class Glob() extends Pattern {
    override def bindings = Seq()
  }

  // TODO: Support named arguments
//  case class Call(target: EValue, name: String, arguments: Arguments[Pattern]) extends Pattern {
//    override def bindings = (arguments.positional ++ arguments.named.values).flatMap(_.bindings)
//  }
}

case class PatternMatch(bindings: Seq[(String, EValue)])

object PatternMatching {
  def matchValue(pattern: Pattern, value: EValue): Option[PatternMatch] = ???
}
