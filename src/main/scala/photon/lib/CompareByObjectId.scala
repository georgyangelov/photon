package photon.lib

import photon.TypeParam.TypeVar

trait CompareByObjectId extends Equals {
  private val objectId: Long = ObjectId().id

  def uniqueId = objectId

  override def canEqual(that: Any): Boolean = that.isInstanceOf[CompareByObjectId]
  override def equals(that: Any): Boolean = {
    that match {
      case other: CompareByObjectId => this.objectId == other.objectId
      case _ => false
    }
  }

  override def hashCode(): Int = objectId.hashCode
}
