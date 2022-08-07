package photon.core.objects

import photon.base._
import photon.core._

object $Class extends Type {
  override def typ(scope: Scope) = $Type
  override val methods = Map(
    // Class.new
//    "new" -> new CompileTimeOnlyMethod {
//      override val signature = MethodSignature.any($AnyStatic)
//
//      override protected def apply(env: Environment, spec: CallSpec, location: Option[Location]) = {
//
//      }
//    }
  )
}
