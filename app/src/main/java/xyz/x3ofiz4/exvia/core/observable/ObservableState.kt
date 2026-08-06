package xyz.x3ofiz4.exvia.core.observable


import java.util.concurrent.CopyOnWriteArrayList

/** Lightweight lifecycle-neutral observable used by Exvia's ViewModels. */
class ObservableState<T>(initialValue: T) {
    private val observers = CopyOnWriteArrayList<(T) -> Unit>()

    @Volatile
    var value: T = initialValue
        private set

    fun update(transform: (T) -> T) {
        set(transform(value))
    }

    fun set(next: T) {
        value = next
        observers.forEach { it(next) }
    }

    fun observe(observer: (T) -> Unit): AutoCloseable {
        observers += observer
        observer(value)
        return AutoCloseable { observers -= observer }
    }
}

/** One-shot event stream for dialogs, reload requests, and transient errors. */
class EventStream<T> {
    private val observers = CopyOnWriteArrayList<(T) -> Unit>()

    fun emit(event: T) {
        observers.forEach { it(event) }
    }

    fun observe(observer: (T) -> Unit): AutoCloseable {
        observers += observer
        return AutoCloseable { observers -= observer }
    }
}
