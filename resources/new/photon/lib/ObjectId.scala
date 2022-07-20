package photon.lib

import java.util.concurrent.atomic.AtomicLong

case class ObjectId(id: Long) extends AnyVal

object ObjectId {
  val idCounter = new AtomicLong(1)

  def apply(): ObjectId = new ObjectId(idCounter.getAndIncrement())
}
