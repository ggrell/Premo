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

package me.dmdev.premo.navigation

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import me.dmdev.premo.*

class PmRouter internal constructor(
    private val hostPm: PresentationModel,
    private val pmFactory: PmFactory
) {

    val pmStack: State<List<BackStackEntry>> = hostPm.State(listOf())

    val pmStackChanges: Flow<PmStackChange> = flow {
        var oldPmStack: List<BackStackEntry> = pmStack.value
        pmStack.stateFlow().collect { newPmStack ->

            val oldTop = oldPmStack.lastOrNull()
            val newTop = newPmStack.lastOrNull()

            val pmStackChange = if (newTop != null && oldTop != null) {
                when {
                    oldTop === newTop -> {
                        PmStackChange.Set(newTop.pm)
                    }
                    oldPmStack.any { it === newTop } -> {
                        PmStackChange.Pop(newTop.pm, oldTop.pm)
                    }
                    else -> {
                        PmStackChange.Push(newTop.pm, oldTop.pm)
                    }
                }
            } else if (newTop != null) {
                PmStackChange.Set(newTop.pm)
            } else {
                null
            }
            if (pmStackChange != null) {
                emit(pmStackChange)
            }
            oldPmStack = newPmStack
        }
    }

    fun push(description: Saveable, pmTag: String = randomUUID()) {
        pmStack.value.lastOrNull()?.pm?.moveLifecycleTo(LifecycleState.CREATED)
        val pm = pmFactory.createPm(description)
        pm.tag = pmTag
        pm.parentPm = hostPm
        pm.moveLifecycleTo(hostPm.lifecycleState.value)
        pmStack.value = pmStack.value.plus(BackStackEntry(pm, description))
    }

    fun pop() {
        pmStack.value.last().pm.moveLifecycleTo(LifecycleState.DESTROYED)
        pmStack.value = pmStack.value.dropLast(1)
        pmStack.value.lastOrNull()?.pm?.moveLifecycleTo(hostPm.lifecycleState.value)
    }

    class BackStackEntry(
        val pm: PresentationModel,
        val description: Saveable
    )
}