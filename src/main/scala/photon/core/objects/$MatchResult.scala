package photon.core.objects

import photon.base._
import photon.core._

object $MatchResult extends Type {
  val metaType = new Type {
    override def typ(scope: Scope): Type = $Type
    override val methods: Map[String, Method] = Map(
      "of" -> new DefaultMethod {
        override val signature = MethodSignature.any($MatchResult)
        override protected def apply(env: Environment, spec: CallSpec, location: Option[Location]) = {
          // TODO: This needs to eval the arguments so that we get the closures
          ???
//          $Object(
//            ArgumentsWithoutSelf(spec.args.positional, spec.args.named),
//            $MatchResult,
//            location
//          )
        }
      }
    )
  }

  override def typ(scope: Scope): Type = metaType
  override val methods: Map[String, Method] = Map.empty
}