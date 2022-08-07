package photon.core.objects

import photon.base._
import photon.core._

object $Boolean extends Type {
  override def typ(scope: Scope): Type = $Type
  override val methods: Map[String, Method] = Map(
//    "ifElse" -> new DefaultMethod {
//      override val signature = MethodSignature.of(
//        Seq("then" -> )
//      )
//      override protected def apply(env: Environment, spec: CallSpec, location: Option[Location]) = ???
//    }
  )
}
