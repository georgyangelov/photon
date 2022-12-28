package photon.compiler.types

import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.interop.InteropLibrary
import com.oracle.truffle.api.interop.TruffleObject
import com.oracle.truffle.api.library.ExportLibrary
import photon.compiler.core.*

@ExportLibrary(InteropLibrary::class)
object IntType: Type(), TruffleObject {
  override val methods = mapOf(
    Pair("+", IntType.javaClass.getMethod("plus", PObject::class.java, PObject::class.java))
  )

  override fun typeOf(frame: VirtualFrame): Type = RootType
  override fun executeGeneric(frame: VirtualFrame): Any = this

  fun plus(a: PObject<Int>, b: PObject<Int>): PObject<Int> {
    return PObject(a.`object` + b.`object`, IntType)
  }
}