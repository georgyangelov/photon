package photon.lib

abstract class Lazy[+T] {
  def resolved: Boolean
  def resolve: T

  def map[R](transform: T => R): Lazy[R]
  def flatMap[R](transform: T => Lazy[R]): Lazy[R]

  override def equals(obj: Any): Boolean = obj match {
    case that: Lazy[T] => resolve == that.resolve
    case _ => false
  }
}

class LazyFn[+T] private (
  private[this] var resolver: Option[() => (_ <: T)]
) extends Lazy[T] {
  private[this] var value: Option[_ <: T] = None

  def this(resolver: () => (_ <: T)) = this(Some(resolver))

  def resolved = value.isDefined

  def resolve = {
    if (this.value.isDefined)
      this.value.get
    else {
      this.value = Some(this.resolver.get())
      this.resolver = None
      this.value.get
    }
  }

  def map[R](transform: T => R) = new LazyFn[R](() => transform(resolve))
  def flatMap[R](transform: T => Lazy[R]) = new LazyFn[R](() => transform(resolve).resolve)
}

case class Eager[+T](resolve: T) extends Lazy[T] {
  val resolved = true

  def map[R](transform: T => R) = Eager(transform(resolve))
  def flatMap[R](transform: T => Lazy[R]) = transform(resolve)
}

object Lazy {
  def of[T](resolve: () => T): Lazy[T] = new LazyFn[T](resolve)
  def ofValue[T](value: T): Lazy[T] = Eager[T](value)
  def selfReferencing[T](resolve: Lazy[T] => T): Lazy[T] = {
    var self: Option[Lazy[T]] = None
    var resolved = false
    val lazyFn = new LazyFn[T](() => {
      if (resolved) {
        throw new Error("Self-referencing Lazy value directly references itself")
      }
      resolved = true

      resolve(self.get)
    })

    self = Some(lazyFn)

    lazyFn
  }

  def all[T1](lazy1: Lazy[T1]): Lazy[T1] = lazy1

  def all[T1, T2](lazy1: Lazy[T1], lazy2: Lazy[T2]) = {
    // TODO: Is this worth it or should all of these be `LazyFn` directly?
    if (lazy1.isInstanceOf[Eager[_]] && lazy2.isInstanceOf[Eager[_]])
      Eager((lazy1.resolve, lazy2.resolve))
    else
      new LazyFn(() => (lazy1.resolve, lazy2.resolve))
  }

  def all[T1, T2, T3](lazy1: Lazy[T1], lazy2: Lazy[T2], lazy3: Lazy[T3]) = {
    // TODO: Is this worth it or should all of these be `LazyFn` directly?
    if (lazy1.isInstanceOf[Eager[_]] && lazy2.isInstanceOf[Eager[_]] && lazy3.isInstanceOf[Eager[_]])
      Eager((lazy1.resolve, lazy2.resolve, lazy3.resolve))
    else
      new LazyFn(() => (lazy1.resolve, lazy2.resolve, lazy3.resolve))
  }

  def all[T](inputs: Iterable[Lazy[T]]): Lazy[Iterable[T]] = new LazyFn(() => inputs.map(_.resolve))

  def allResolved(lazies: Seq[Lazy[_]]): Boolean = lazies.forall(_.resolved)
}