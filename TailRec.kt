import kotlin.reflect.KProperty
import java.util.LinkedList

typealias Endo<A> = (A) -> A

class TailRecScope<A, R>(private val f : TailRecScope<A, R>.(A) -> TailRec<R>) {
    fun tailcall(value : A) : TailRec<R> = Suspend { f(value) }
}

operator fun <R> TailRec<R>.getValue(r : Nothing?, p : KProperty<*>?) : R = eval()

infix fun <R> R.doneIf(condition : Boolean) : TailRec<R>? = takeIf { condition }?.let { Pure(it) }

infix operator fun TailRec<Long>.plus(operand : TailRec<Long>) : TailRec<Long> = FlatMap(this) { a -> Map(operand) { b -> a + b } }

fun <A, R> tailrec(scope : TailRecScope<A, R>.(A) -> TailRec<R>) : TailRec<(A) -> R> = TailRecScope(scope).run { Pure { scope(it).eval() } }

fun main() {

    val fibonacci : Endo<Long> by tailrec {
        it doneIf(it <= 1) ?: tailcall(it - 1) + tailcall(it - 2)
    }

    println(fibonacci(35))

}

sealed interface TailRec<R>

data class FlatMap<T, R>(val source : TailRec<T>, val transform : (T) -> TailRec<R>) : TailRec<R>

data class Map<T, R>(val source : TailRec<T>, val transform : (T) -> R) : TailRec<R>

data class Suspend<R>(val resume : () -> TailRec<R>) : TailRec<R>

data class Delay<R>(val value : () -> R) : TailRec<R>

data class Pure<R>(val value : R) : TailRec<R>

@Suppress("UNCHECKED_CAST")
fun <R> TailRec<R>.eval() : R {

    val bindRest = LinkedList<(Any) -> TailRec<Any>>()

    var bindFirst : ((Any) -> TailRec<Any>)? = null

    var source : TailRec<*>? = this

    var result : Any? = null

    while (true) {
        if (source != null) when (source) {
            is FlatMap<*, *> -> {
                bindFirst?.let(bindRest::push)
                bindFirst = source.transform as (Any) -> TailRec<Any>
                source    = source.source
            }

            is Map<*, *> -> {
                bindFirst?.let(bindRest::push)
                val transform = source.transform as (Any) -> Any
                bindFirst     = { Delay { transform(it) } }
                source        = source.source
            }

            is Pure<*> -> {
                result = source.value
                source = null
            }

            is Delay<*> -> {
                result = source.value()
                source = null
            }

            is Suspend<*> -> {
                source = source.resume()
            }
        }

        when {
            result != null && bindFirst != null -> {
                source    = bindFirst(result)
                result    = null
                bindFirst = null
                continue
            }

            result != null && bindRest.isNotEmpty() -> {
                source    = (bindRest.pop())(result)
                result    = null
                bindFirst = null
                continue
            }

            result != null -> {
                return result as R
            }
        }
    }
}
