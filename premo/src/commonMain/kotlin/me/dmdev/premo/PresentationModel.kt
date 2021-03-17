/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2020-2021 Dmitriy Gorbunov (dmitriy.goto@gmail.com)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package me.dmdev.premo

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import me.dmdev.premo.navigation.NavigationMessage
import me.dmdev.premo.navigation.PmFactory
import me.dmdev.premo.navigation.PmRouter

abstract class PresentationModel {

    val pmScope = CoroutineScope(SupervisorJob() + Dispatchers.UI)

    var pmInForegroundScope: CoroutineScope? = null
        private set

    var tag: String = randomUUID()
        internal set

    var parentPm: PresentationModel? = null
        internal set

    private var routerOrNull: PmRouter? = null

    protected fun PresentationModel.Router(pmFactory: PmFactory, initialDescription: Saveable?): PmRouter {
        return routerOrNull ?: PmRouter(this, pmFactory).also { router ->
            if (initialDescription != null) router.push(initialDescription)
            routerOrNull = router
        }
    }

    @Suppress("UNCHECKED_CAST")
    protected fun <PM : PresentationModel> Child(pm: PM): PM {
        pm.parentPm = this
        children.add(pm)
        pm.moveLifecycleTo(lifecycleState.value)
        return pm
    }

    protected var <T> State<T>.value: T
        get() = mutableStateFlow.value
        set(value) {
            mutableStateFlow.value = value
        }

    internal val saveableStates = mutableListOf<SaveableState<*, *>>()
    private val children = mutableListOf<PresentationModel>()

    internal val lifecycleState = MutableStateFlow(LifecycleState.INITIALIZED)
    private val lifecycleEvent = MutableSharedFlow<LifecycleEvent>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    @Suppress("MemberVisibilityCanBePrivate")
    protected open fun onCreate() {
    }

    @Suppress("MemberVisibilityCanBePrivate")
    protected open fun onForeground() {
    }

    @Suppress("MemberVisibilityCanBePrivate")
    protected open fun onBackground() {
    }

    @Suppress("MemberVisibilityCanBePrivate")
    protected open fun onDestroy() {
    }

    protected open fun handleNavigationMessage(message: NavigationMessage) {
        parentPm?.handleNavigationMessage(message)
    }

    open fun handleBack(): Boolean {
        val router = routerOrNull
        if (router != null) {
            val handledByNestedPm =
                router.pmStack.value.lastOrNull()?.pm?.handleBack() ?: false
            if (handledByNestedPm.not()) {
                if (router.pmStack.value.size > 1) {
                    router.pop()
                    return true
                }
            } else {
                return true
            }
            return false
        } else {
            return false
        }
    }

    internal fun saveState(pm: PresentationModel = this): PmState {

        val router = pm.routerOrNull
        val routerState = router?.pmStack?.value?.map { entry ->
            BackStackEntryState(
                description = entry.description,
                pmState = saveState(entry.pm)
            )
        } ?: listOf()

        return PmState(
            pmTag = pm.tag,
            routerState = routerState,
            children = pm.children.map { childPm -> saveState(childPm) },
            states = pm.saveableStates.map { state ->
                state.saveableValue
            }
        )
    }

    internal fun restoreState(pm: PresentationModel = this, pmState: PmState) {
        pm.tag = pmState.pmTag
        pmState.states.forEachIndexed { index, saveable ->
            pm.saveableStates[index].saveableValue = saveable
        }
        pmState.children.forEachIndexed { index, pmState ->
            restoreState(pm.children[index], pmState)
        }

        val router = pm.routerOrNull
        if (router != null) {
            pmState.routerState.forEach { entry ->
                router.push(entry.description, entry.pmState.pmTag)
                restoreState(router.pmStack.value.last().pm, entry.pmState)
            }
        }
    }

    internal fun moveLifecycleTo(targetLifecycle: LifecycleState) {

        fun moveChildren(targetLifecycle: LifecycleState) {
            children.forEach { pm ->
                pm.moveLifecycleTo(targetLifecycle)
            }
            routerOrNull?.pmStack?.value?.lastOrNull()?.pm?.moveLifecycleTo(targetLifecycle)
        }

        fun doOnCreate() {
            lifecycleState.value = LifecycleState.CREATED
            lifecycleEvent.tryEmit(LifecycleEvent.ON_CREATE)
            onCreate()
            moveChildren(LifecycleState.CREATED)
        }

        fun doOnForeground() {
            pmInForegroundScope = CoroutineScope(SupervisorJob() + Dispatchers.UI)
            lifecycleState.value = LifecycleState.IN_FOREGROUND
            lifecycleEvent.tryEmit(LifecycleEvent.ON_FOREGROUND)
            onForeground()
            moveChildren(LifecycleState.IN_FOREGROUND)
        }

        fun doOnBackground() {
            moveChildren(LifecycleState.CREATED)
            lifecycleState.value = LifecycleState.CREATED
            lifecycleEvent.tryEmit(LifecycleEvent.ON_BACKGROUND)
            onBackground()
            pmInForegroundScope?.cancel()
        }

        fun doOnDestroy() {
            moveChildren(LifecycleState.DESTROYED)
            lifecycleState.value = LifecycleState.DESTROYED
            lifecycleEvent.tryEmit(LifecycleEvent.ON_DESTROY)
            onDestroy()
            pmScope.cancel()
        }

        when (targetLifecycle) {
            LifecycleState.INITIALIZED -> {
                // do nothing, initial lifecycle state is INITIALIZED
            }
            LifecycleState.CREATED -> {
                when (lifecycleState.value) {
                    LifecycleState.INITIALIZED -> {
                        doOnCreate()
                    }
                    LifecycleState.IN_FOREGROUND -> {
                        doOnBackground()
                    }
                    else -> { /*do nothing */
                    }
                }
            }
            LifecycleState.IN_FOREGROUND -> {
                when (lifecycleState.value) {
                    LifecycleState.INITIALIZED -> {
                        doOnCreate()
                        doOnForeground()
                    }
                    LifecycleState.CREATED -> {
                        doOnForeground()
                    }
                    else -> { /*do nothing */
                    }
                }
            }
            LifecycleState.DESTROYED -> {
                when (lifecycleState.value) {
                    LifecycleState.INITIALIZED -> {
                        doOnDestroy()
                    }
                    LifecycleState.CREATED -> {
                        doOnDestroy()
                    }
                    LifecycleState.IN_FOREGROUND -> {
                        doOnBackground()
                        doOnDestroy()
                    }
                    LifecycleState.DESTROYED -> { /*do nothing */
                    }
                }
            }
        }
    }
}