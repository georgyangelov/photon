package photon.compiler.core

import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.Node

abstract class Value: Node() {
  abstract fun typeOf(frame: VirtualFrame): Type
  abstract fun executeGeneric(frame: VirtualFrame): Any
}