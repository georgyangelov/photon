package photon.base

trait Type extends RealEValue {
  override val location = None
  override def unboundNames = Set.empty

  val methods: Map[String, Method]

  def method(name: String): Option[Method] = methods.get(name)
}