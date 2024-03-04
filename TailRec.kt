
import java.util.*
import kotlin.reflect.KProperty

sealed interface TailRec<R>

data class Map<T, R>(val source : TailRec<T>, val transform : (T) -> R) : TailRec<R>

data class FlatMap<T, R>(val source : TailRec<T>, val transform : (T) -> TailRec<R>) : TailRec<R>

data class Suspend<R>(val resume : () -> TailRec<R>) : TailRec<R>

data class Done<R>(val result : R) : TailRec<R>

@Suppress("UNCHECKED_CAST")
operator fun <R> TailRec<R>.getValue(r : Nothing?, p : KProperty<*>?) : R {

    val bindRest = Stack<((Any) -> TailRec<Any>)>()

    var bindFirst : ((Any) -> TailRec<Any>)? = null

    var source : TailRec<*>? = this

    var result : Any? = null

    while (true) {

        if (source != null) when (source) {

            is FlatMap<*, *> -> {
                bindFirst?.let(bindRest::push)
                bindFirst = source.transform as ((Any) -> TailRec<Any>)
                source    = source.source
            }
            is Map<*, *> -> {
                bindFirst?.let(bindRest::push)
                val mapper = source.transform as ((Any) -> Any)
                bindFirst  = { Done(mapper(it)) }
                source     = source.source
            }
            is Done<*> -> {
                result = source.result
                source = null
            }
            is Suspend<*> ->
                source = source.resume()
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
            result != null ->
                return result as R
        }
    }

}

fun <T, R> TailRec<T>.flatMap(transform : (T) -> TailRec<R>) : TailRec<R> = FlatMap(this, transform)

fun <T, R> TailRec<T>.map(transform : (T) -> R) : TailRec<R> = Map(this, transform)

fun <R> tailcall(resume : () -> TailRec<R>) : TailRec<R> = Suspend(resume)

infix operator fun TailRec<Long>.plus(operand : TailRec<Long>) : TailRec<Long> = flatMap { a -> operand.map { b -> a + b } }

class TailRecScope<R>(private val f : TailRecScope<R>.(R) -> TailRec<R>) {
    fun tailcall(value : R) : TailRec<R> = tailcall { f(value) }
}

fun <R> tailrec(zero : R, scope : TailRecScope<R>.(R) -> TailRec<R>): TailRec<R> = TailRecScope(scope).scope(zero)

infix fun <R> Boolean.yield(value : R) : TailRec<R>? = value.takeIf { this }?.let(::Done)

fun main() {

    val fibonacci by tailrec(35L) {
        (it <= 1L) yield(it) ?: tailcall(it - 1) + tailcall(it - 2)
    }

    println(fibonacci)

}
