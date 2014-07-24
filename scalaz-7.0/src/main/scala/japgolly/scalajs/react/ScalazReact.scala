package japgolly.scalajs.react

import org.scalajs.dom
import vdom.ReactVDom._

import scala.scalajs.js.{UndefOr, undefined}
import scalaz._
import Scalaz.Id
import scalaz.effect.IO

object ScalazReact {
  // Don't edit this directly. Run sync-scala70

  sealed trait ExecUnsafe[M[+_]] {
    def execUnsafe[A](m: M[A]): A
    final def execUnsafeFn[A, B](m: A => M[B]): A => B = a => execUnsafe(m(a))
  }
  implicit object ExecUnsafeId extends ExecUnsafe[Id] {
    override def execUnsafe[A](m: Id[A]): A = m
  }
  implicit object ExecUnsafeIO extends ExecUnsafe[IO] {
    override def execUnsafe[A](m: IO[A]): A = m.unsafePerformIO()
  }

  implicit final class SzRExt_Attr(val a: Attr) extends AnyVal {

    def ~~>(io: IO[Unit]) =
      a --> io.unsafePerformIO()

    def ~~>[E <: dom.Node](eventHandler: SyntheticEvent[E] => IO[Unit]) =
      a.==>[E](eventHandler(_).unsafePerformIO())
  }

  type OpCallbackIO = UndefOr[IO[Unit]]
  implicit def OpCallbackFromIO(cb: OpCallbackIO): OpCallback = cb.map(f => () => f.unsafePerformIO())

  // CompStateAccess[C] should really be a class param but then we lose the AnyVal
  // Not using default arguments here cos they prevent `doStuff >>= T.setStateIO`
  implicit final class SzRExt_CompStateAccessOps[C[_], S](val u: C[S]) extends AnyVal {
    type CC = CompStateAccess[C]

    def stateIO(implicit C: CC): IO[S] =
      IO(u.state)

    def setStateIO(s: S)(implicit C: CC): IO[Unit] = setStateIO(s, undefined)
    def setStateIO(s: S, cb: OpCallbackIO)(implicit C: CC): IO[Unit] =
      IO(u.setState(s, cb))

    def modStateIO(f: S => S)(implicit C: CC): IO[Unit] = modStateIO(f, undefined)
    def modStateIO(f: S => S, cb: OpCallbackIO)(implicit C: CC): IO[Unit] =
      IO(u.modState(f, cb))

    def modStateIOM[M[+_]](f: S => M[S])(implicit C: CC, M: ExecUnsafe[M]): IO[Unit] = modStateIOM(f, undefined)
    def modStateIOM[M[+_]](f: S => M[S], cb: OpCallbackIO)(implicit C: CC, M: ExecUnsafe[M]): IO[Unit] =
      modStateIO(M.execUnsafeFn(f), cb)

    def runStateIO[M[+_]](m: StateT[M, S, Unit])(implicit C: CC, M: ExecUnsafe[M]): IO[Unit] = runStateIO(m, undefined)
    def runStateIO[M[+_]](m: StateT[M, S, Unit], cb: OpCallbackIO)(implicit C: CC, M: ExecUnsafe[M]): IO[Unit] =
      modStateIO(M.execUnsafeFn(m.apply) andThen (_._1), cb)

    def _runStateIO[I, M[+_]](f: I => StateT[M, S, Unit])(implicit C: CC, M: ExecUnsafe[M]): I => IO[Unit] =
      i => runStateIO(f(i))

    def _runStateIO[I, M[+_]](f: I => StateT[M, S, Unit], callback: I => IO[Unit])(implicit C: CC, M: ExecUnsafe[M]): I => IO[Unit] =
      i => runStateIO(f(i), callback(i))

    def _runStateIO[I, M[+_]](f: I => StateT[M, S, Unit], callback: IO[Unit])(implicit C: CC, M: ExecUnsafe[M]): I => IO[Unit] =
      i => runStateIO(f(i), callback)
  }

  // Seriously, Scala, get your shit together.
  @inline final implicit def moarScalaHandHolding[P,S](b: BackendScope[P,S]): SzRExt_CompStateAccessOps[ComponentScope_SS, S] = (b: ComponentScope_SS[S])
  @inline final implicit def moarScalaHandHolding[P,S,B](b: ComponentScopeU[P,S,B]): SzRExt_CompStateAccessOps[ComponentScope_SS, S] = (b: ComponentScope_SS[S])

  implicit final class SzRExt_C_M(val u: ComponentScope_M) extends AnyVal {
    def forceUpdateIO = IO(u.forceUpdate())
  }

  implicit final class SzRExt_SEvent[N <: dom.Node](val e: SyntheticEvent[N]) extends AnyVal {
    /**
     * Stops the default action of an element from happening.
     * For example: Prevent a submit button from submitting a form Prevent a link from following the URL
     */
    def preventDefaultIO = IO(e.preventDefault())
    /**
     * Stops the bubbling of an event to parent elements, preventing any parent event handlers from being execUnsafeuted.
     */
    def stopPropagationIO = IO(e.stopPropagation())
  }

  val preventDefaultIO  = (_: SyntheticEvent[dom.Node]).preventDefaultIO
  val stopPropagationIO = (_: SyntheticEvent[dom.Node]).stopPropagationIO
}