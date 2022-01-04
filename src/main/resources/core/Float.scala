package photon.core

import photon.{New, TypeType}

object FloatTypeType extends New.TypeObject {
  override val typeObject = TypeType
  override val instanceMethods = Map.empty
}

object FloatType extends New.TypeObject {
  override val typeObject = FloatTypeType
  override val instanceMethods = Map.empty
}
