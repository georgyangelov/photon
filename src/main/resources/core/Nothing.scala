package photon.core

import photon.{New, TypeType}

object NothingType extends New.TypeObject {
  override val typeObject = TypeType
  override val instanceMethods = Map.empty
}
