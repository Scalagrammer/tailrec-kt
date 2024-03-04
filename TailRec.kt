import java.util.*
import kotlin.reflect.KProperty

class TailRecScope<R>(private val f : TailRecScope<R>.(R) -> TailRec<R>) {

    fun tailcall(value : R) : TailRec<R> = Suspend { f(value) }

    infix fun R.pureIf(condition : Boolean) : TailRec<R>? = takeIf { condition }?.let(::Pure)
    
    infix operator fun TailRec<Long>.plus(operand : TailRec<Long>) : TailRec<Long> = FlatMap(this) { a -> Map(operand) { b -> a + b } }
    
}

sealed interface TailRec<R> {
    companion object {
        operator fun <R> invoke(zero : R, scope : TailRecScope<R>.(R) -> TailRec<R>) : TailRec<R> = TailRecScope(scope).scope(zero)
    }
}

data class Map<T, R>(val source : TailRec<T>, val transform : (T) -> R) : TailRec<R>

data class FlatMap<T, R>(val source : TailRec<T>, val transform : (T) -> TailRec<R>) : TailRec<R>

data class Suspend<R>(val resume : () -> TailRec<R>) : TailRec<R>

data class Delay<R>(val value : () -> R) : TailRec<R>

data class Pure<R>(val value : R) : TailRec<R>

fun main() {

    val fibonacci by TailRec(zero = 20L) {
        it pureIf(it <= 1L) ?: tailcall(it - 1) + tailcall(it - 2)
    }

    println(fibonacci)

}

@Suppress("UNCHECKED_CAST")
operator fun <R> TailRec<R>.getValue(r : Nothing?, p : KProperty<*>?) : R {

    val bindRest = Stack<((Any) -> TailRec<Any>)>()

    var bindFirst: ((Any) -> TailRec<Any>)? = null

    var source: TailRec<*>? = this

    var result: Any? = null

    while (true) {
        if (source != null) when (source) {
            is FlatMap<*, *> -> {
                bindFirst?.let(bindRest::push)
                bindFirst = source.transform as (Any) -> TailRec<Any>
                source = source.source
            }

            is Map<*, *> -> {
                bindFirst?.let(bindRest::push)
                val transform = source.transform as (Any) -> Any
                bindFirst = { Delay { transform(it) } }
                source = source.source
            }

            is Pure<*> -> {
                result = source.value
                source = null
            }

            is Delay<*> -> {
                result = source.value()
                source = null
            }

            is Suspend<*> ->
                source = source.resume()
        }

        when {
            result != null && bindFirst != null -> {
                source = bindFirst(result)
                result = null
                bindFirst = null
                continue
            }

            result != null && bindRest.isNotEmpty() -> {
                source = (bindRest.pop())(result)
                result = null
                bindFirst = null
                continue
            }

            result != null ->
                return result as R
        }
    }
}
