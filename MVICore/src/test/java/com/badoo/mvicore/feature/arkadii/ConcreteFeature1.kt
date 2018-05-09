package com.badoo.mvicore.feature.arkadii

import com.badoo.mvicore.element.Actor
import com.badoo.mvicore.element.Reducer
import com.badoo.mvicore.feature.DefaultFeature
import com.badoo.mvicore.feature.Feature
import io.reactivex.Observable

class ConcreteFeature1 : Feature<ConcreteFeature1.Wish, ConcreteFeature1.State> by DefaultFeature(
        initialState = State(),
        actor = ActorImpl,
        reducer = ReducerImpl
) {

    data class State(
            val items: List<String> = emptyList(),
            val isLoading: Boolean = false,
            val message: String? = null
    )

    sealed class Wish {
        object LoadItems : Wish()
        class RemoveItem(val index: Int) : Wish()
        object HandleMessageConsumed : Wish()
    }

    private sealed class Effect {
        object LoadStarted : Effect()
        class ItemsLoaded(val items: List<String>) : Effect()
        class RemoveItem(val index: Int) : Effect()
        object MessageConsumed : Effect()
    }

    private object ActorImpl : Actor<Wish, State, Effect> {
        override fun invoke(wish: Wish, state: State): Observable<Effect> =
                when (wish) {
                    Wish.LoadItems -> loadItems(state)
                    is Wish.RemoveItem -> Observable.just(Effect.RemoveItem(wish.index))
                    Wish.HandleMessageConsumed -> Observable.just(Effect.MessageConsumed)
                }

        private fun loadItems(state: State): Observable<Effect> =
                if (state.isLoading) {
                    Observable.empty()
                } else {
                    Observable
                            .just(listOf("a", "b", "c"))
                            .map<Effect> { Effect.ItemsLoaded(it) }
                            .startWith(Effect.LoadStarted)
                }
    }

    private object ReducerImpl : Reducer<State, Effect> {
        override fun invoke(state: State, effect: Effect): State =
                when (effect) {
                    Effect.LoadStarted -> state.copy(isLoading = true)

                    is Effect.ItemsLoaded -> state.copy(items = effect.items, isLoading = false)

                    is Effect.RemoveItem ->
                        state
                                .copy(items = state.items.minus(effect.index))
                                .let {
                                    // FIXME: Business logic inside actor
                                    // FIXME: Also we would like to load message from data source but we can't
                                    if (it.items.isEmpty()) {
                                        it.copy(message = "Items are empty")
                                    } else {
                                        it
                                    }
                                }

                    Effect.MessageConsumed -> state.copy(message = null)
                }

        private fun <T> List<T>.minus(index: Int): List<T> = filterIndexedTo(ArrayList(size)) { i, _ -> i != index }
    }
}