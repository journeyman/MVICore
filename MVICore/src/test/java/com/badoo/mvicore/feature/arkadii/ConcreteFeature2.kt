package com.badoo.mvicore.feature.arkadii

import com.badoo.mvicore.element.Actor
import com.badoo.mvicore.element.Reducer
import com.badoo.mvicore.feature.DefaultFeature
import com.badoo.mvicore.feature.Feature
import io.reactivex.Observable

class ConcreteFeature2(val maxCounter: Int) : Feature<ConcreteFeature2.Wish, ConcreteFeature2.State> by DefaultFeature(
        initialState = State(),
        actor = ActorImpl(maxCounter),
        reducer = ReducerImpl(maxCounter)
) {

    sealed class Wish {
        data class LoadIfNeeded(val userId: String) : Wish()
    }

    data class State(
            val counter: Int = 0,
            val data: String? = null
    )

    private sealed class Effect {
        object Increment : Effect()
        class Loaded(val data: String) : Effect()
    }

    private class ActorImpl(private val maxCounter: Int) : Actor<Wish, State, Effect> {

        override fun invoke(wish: Wish, state: State): Observable<Effect> =
                when (wish) {
                    is Wish.LoadIfNeeded -> {
                        if (state.counter >= maxCounter) {
                            Observable.empty()
                        } else {
                            Observable.just<Effect>(Effect.Increment)
                                    .let {
                                        // FIXME: Evaluation of this expression is actually a hidden state reducing!
                                        // Also it assumes that Increment effect will increment value by 1
                                        // Ideally we need to check for a NEW counter's value here and make a decision
                                        if (state.counter == maxCounter - 1) {
                                            it.concatWith(Observable.just(Effect.Loaded("Data for ${wish.userId}")))
                                        } else {
                                            it
                                        }
                                    }
                        }
                    }
                }
    }

    private class ReducerImpl(private val maxCounter: Int) : Reducer<State, Effect> {
        override fun invoke(state: State, effect: Effect): State =
                when (effect) {
                    Effect.Increment ->  {
                        //NOTE: emulate that something went wrong and Increment didnt happen
                        if (state.counter == maxCounter - 1) {
                            state //something went wrong
                        } else {
                            state.copy(counter = state.counter + 1)
                        }
                    }
                    is Effect.Loaded -> state.copy(data = effect.data)
                }
    }
}