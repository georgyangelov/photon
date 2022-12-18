package photon.core.objects

import photon.base._
import photon.core._
import photon.core.operations.$Call

import scala.collection.mutable

object $Internal extends Type {
  var typeCache = mutable.Map[Seq[Value], Type]()

  override def typ(scope: Scope): Type = $Type
  override val methods: Map[String, Method] = Map(
    "arrayNew" -> new DefaultMethod {
      override val signature = MethodSignature.any($NativeHandle)
      override protected def apply(env: Environment, spec: CallSpec, location: Option[Location]) = {
        val items = spec.requireAllConcretePositional(env)

        // items.mapValue(Array(_))
        ???
      }
    },

    "cacheType" -> new CompileTimeOnlyMethod {
      override val signature = MethodSignature.any($AnyStatic)
      override protected def apply(env: Environment, spec: CallSpec, location: Option[Location]) = {
        val keys = spec.args.positional.dropRight(1).map(_.evaluate(env).value)
        val typeFn = spec.args.positional.last.evaluate(env)

        val result = typeCache.getOrElseUpdate(keys, {
          $Call("call", Arguments.empty(typeFn.value), location).evaluate(env).value.asType
        })

        EvalResult(result, Seq.empty)
      }
    }
  )
}
