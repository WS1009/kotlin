/*
 * Copyright 2010-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

@file:kotlin.jvm.JvmName("IntrinsicsKt")

package kotlin.coroutines.intrinsics

import kotlin.coroutines.*

/**
 * Obtains the current continuation instance inside suspend functions and either suspends
 * currently running coroutine or returns result immediately without suspension.
 *
 * If the [block] returns the special [COROUTINE_SUSPENDED] value, it means that suspend function did suspend the execution and will
 * not return any result immediately. In this case, the [Continuation] provided to the [block] shall be invoked at some moment in the
 * future when the result becomes available to resume the computation.
 *
 * Otherwise, the return value of the [block] must have a type assignable to [T] and represents the result of this suspend function.
 * It means that the execution was not suspended and the [Continuation] provided to the [block] shall not be invoked.
 * As the result type of the [block] is declared as `Any?` and cannot be correctly type-checked,
 * its proper return type remains on the conscience of the suspend function's author.
 *
 * Note that it is not recommended to call either [Continuation.resume] nor [Continuation.resumeWithException] functions synchronously
 * in the same stackframe where suspension function is run. Use [suspendCoroutine] as a safer way to obtain current
 * continuation instance.
 */
@SinceKotlin("1.3")
@Suppress("UNUSED_PARAMETER")
public suspend inline fun <T> suspendCoroutineOrReturn(crossinline block: (Continuation<T>) -> Any?): T =
    suspendCoroutineUninterceptedOrReturn { cont -> block(cont.intercepted()) }

/**
 * Obtains the current continuation instance inside suspend functions and either suspends
 * currently running coroutine or returns result immediately without suspension.
 *
 * Unlike [suspendCoroutineOrReturn] it does not intercept continuation.
 */
@SinceKotlin("1.3")
public suspend inline fun <T> suspendCoroutineUninterceptedOrReturn(crossinline block: (Continuation<T>) -> Any?): T =
    throw NotImplementedError("Implementation of suspendCoroutineUninterceptedOrReturn is intrinsic")

/**
 * Intercept continuation with [ContinuationInterceptor].
 */
@SinceKotlin("1.3")
public inline fun <T> Continuation<T>.intercepted(): Continuation<T> =
    throw NotImplementedError("Implementation of intercepted is intrinsic")

/**
 * This value is used as a return value of [suspendCoroutineOrReturn] `block` argument to state that
 * the execution was suspended and will not return any result immediately.
 */
@SinceKotlin("1.3")
public val COROUTINE_SUSPENDED: Any = Any()

// JVM declarations

/**
 * Creates a coroutine without receiver and with result type [T].
 * This function creates a new, fresh instance of suspendable computation every time it is invoked.
 *
 * To start executing the created coroutine, invoke `resume(Unit)` on the returned [Continuation] instance.
 * The [completion] continuation is invoked when coroutine completes with result or exception.
 *
 * This function is _unchecked_. Repeated invocation of any resume function on the resulting continuation corrupts the
 * state machine of the coroutine and may result in arbitrary behaviour or exception.
 */
@SinceKotlin("1.3")
public fun <T> (suspend () -> T).createCoroutineUnchecked(
    completion: Continuation<T>
): Continuation<Unit> =
    if (this !is kotlin.coroutines.jvm.internal.CoroutineImpl)
        buildContinuationByInvokeCall(completion) {
            @Suppress("UNCHECKED_CAST")
            (this as Function1<Continuation<T>, Any?>).invoke(completion)
        }
    else
        (this.create(completion) as kotlin.coroutines.jvm.internal.CoroutineImpl).facade

/**
 * Creates a coroutine with receiver type [R] and result type [T].
 * This function creates a new, fresh instance of suspendable computation every time it is invoked.
 *
 * To start executing the created coroutine, invoke `resume(Unit)` on the returned [Continuation] instance.
 * The [completion] continuation is invoked when coroutine completes with result or exception.
 *
 * This function is _unchecked_. Repeated invocation of any resume function on the resulting continuation corrupts the
 * state machine of the coroutine and may result in arbitrary behaviour or exception.
 */
@SinceKotlin("1.3")
public fun <R, T> (suspend R.() -> T).createCoroutineUnchecked(
    receiver: R,
    completion: Continuation<T>
): Continuation<Unit> =
    if (this !is kotlin.coroutines.jvm.internal.CoroutineImpl)
        buildContinuationByInvokeCall(completion) {
            @Suppress("UNCHECKED_CAST")
            (this as Function2<R, Continuation<T>, Any?>).invoke(receiver, completion)
        }
    else
        (this.create(receiver, completion) as kotlin.coroutines.jvm.internal.CoroutineImpl).facade

// INTERNAL DEFINITIONS

@SinceKotlin("1.3")
private inline fun <T> buildContinuationByInvokeCall(
    completion: Continuation<T>,
    crossinline block: () -> Any?
): Continuation<Unit> {
    val continuation =
        object : Continuation<Unit> {
            override val context: CoroutineContext
                get() = completion.context

            override fun resume(value: Unit) {
                processBareContinuationResume(completion, block)
            }

            override fun resumeWithException(exception: Throwable) {
                completion.resumeWithException(exception)
            }
        }

    return kotlin.coroutines.jvm.internal.interceptContinuationIfNeeded(completion.context, continuation)
}