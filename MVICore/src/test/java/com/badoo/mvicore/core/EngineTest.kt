package com.badoo.mvicore.core

import com.badoo.mvicore.TestHelper
import com.badoo.mvicore.TestHelper.Companion.conditionalMultiplier
import com.badoo.mvicore.TestHelper.Companion.initialCounter
import com.badoo.mvicore.TestHelper.Companion.initialLoading
import com.badoo.mvicore.TestHelper.Companion.instantFulfillAmount1
import com.badoo.mvicore.TestHelper.TestEffect
import com.badoo.mvicore.TestHelper.TestEffect.ConditionalThingHappened
import com.badoo.mvicore.TestHelper.TestEffect.LoopbackEffect1
import com.badoo.mvicore.TestHelper.TestFeature
import com.badoo.mvicore.TestHelper.TestState
import com.badoo.mvicore.TestHelper.TestWish
import com.badoo.mvicore.TestHelper.TestWish.FulfillableAsync
import com.badoo.mvicore.TestHelper.TestWish.FulfillableInstantly1
import com.badoo.mvicore.TestHelper.TestWish.LoopbackWish1
import com.badoo.mvicore.TestHelper.TestWish.LoopbackWish2
import com.badoo.mvicore.TestHelper.TestWish.LoopbackWish3
import com.badoo.mvicore.TestHelper.TestWish.LoopbackWishInitial
import com.badoo.mvicore.TestHelper.TestWish.MaybeFulfillable
import com.badoo.mvicore.TestHelper.TestWish.TranslatesTo3Effects
import com.badoo.mvicore.TestHelper.TestWish.Unfulfillable
import com.badoo.mvicore.element.News
import com.badoo.mvicore.onNextEvents
import com.badoo.mvicore.overrideAssertsForTesting
import io.reactivex.observers.TestObserver
import io.reactivex.subjects.PublishSubject
import junit.framework.Assert.assertEquals
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.MockitoAnnotations

abstract class EngineTest(
    private val engine: Engine<TestState, TestWish, TestEffect>
) {
    private lateinit var feature: Store<TestState, TestWish>
    private lateinit var states: TestObserver<TestState>
    private lateinit var newsSubject: PublishSubject<News>
    private lateinit var newsSubjectTest: TestObserver<News>
    private lateinit var wishSubject: PublishSubject<TestWish>
    private lateinit var actorInvocationLog: PublishSubject<Pair<TestWish, TestState>>
    private lateinit var actorInvocationLogTest: TestObserver<Pair<TestWish, TestState>>

    @Before
    fun prepare() {
        MockitoAnnotations.initMocks(this)
        overrideAssertsForTesting(false)

        newsSubject = PublishSubject.create<News>()
        newsSubjectTest = newsSubject.test()
        wishSubject = PublishSubject.create<TestWish>()
        actorInvocationLog = PublishSubject.create<Pair<TestWish, TestState>>()
        actorInvocationLogTest = actorInvocationLog.test()

        feature = TestFeature(engine, newsSubject, actorInvocationLog)
        feature.connectSource(wishSubject)
        states = feature.states.test()
    }

    @After
    fun tearDown() {
        feature.dispose()
    }

    @Test
    fun `if there are no wishes, feature only emits initial state`() {
        assertEquals(1, states.onNextEvents().size)
    }

    @Test
    fun `emitted initial state is correct`() {
        val state: TestState = states.onNextEvents().first() as TestState
        assertEquals(initialCounter, state.counter)
        assertEquals(initialLoading, state.loading)
    }

    @Test
    fun `there should be no state emission besides the initial one for unfulfillable wishes`() {
        wishSubject.onNext(Unfulfillable)
        wishSubject.onNext(Unfulfillable)
        wishSubject.onNext(Unfulfillable)

        assertEquals(1, states.onNextEvents().size)
    }

    @Test
    fun `there should be the same amount of states as wishes that translate 1 - 1 to effects plus one for initial state`() {
        val wishes = listOf<TestWish>(
                // all of them are mapped to 1 effect each
            FulfillableInstantly1,
            FulfillableInstantly1,
            FulfillableInstantly1
        )

        wishes.forEach { wishSubject.onNext(it) }

        assertEquals(1 + wishes.size, states.onNextEvents().size)
    }

    @Test
    fun `there should be 3 times as many states as wishes that translate 1 - 3 to effects plus one for initial state`() {
        val wishes = listOf<TestWish>(
                TranslatesTo3Effects,
                TranslatesTo3Effects,
                TranslatesTo3Effects
        )

        wishes.forEach { wishSubject.onNext(it) }

        assertEquals(1 + wishes.size * 3, states.onNextEvents().size)
    }

    @Test
    fun `last state correctly reflects expected changes in simple case`() {
        val wishes = listOf<TestWish>(
            FulfillableInstantly1,
            FulfillableInstantly1,
            FulfillableInstantly1
        )

        wishes.forEach { wishSubject.onNext(it) }

        val state = states.onNextEvents().last() as TestState
        assertEquals(initialCounter + wishes.size * instantFulfillAmount1, state.counter)
        assertEquals(false, state.loading)
    }

    @Test
    fun `intermediate state matches expectations in async case`() {
        val wishes = listOf(
            FulfillableAsync
        )

        wishes.forEach { wishSubject.onNext(it) }

        val state = states.onNextEvents().last() as TestState
        assertEquals(true, state.loading)
        assertEquals(initialCounter, state.counter)
    }

    @Test
    fun `final state matches expectations in async case`() {
        val wishes = listOf(
                FulfillableAsync
        )

        wishes.forEach { wishSubject.onNext(it) }

        Thread.sleep(TestHelper.mockServerDelayMs + 200)

        val state = states.onNextEvents().last() as TestState
        assertEquals(false, state.loading)
        assertEquals(initialCounter + TestHelper.delayedFulfillAmount, state.counter)
    }

    @Test
    fun `the number of state emissions should reflect the number of effects plus one for initial state in complex case`() {
        val wishes = listOf(
            FulfillableInstantly1,  // maps to 1 effect
            FulfillableInstantly1,  // maps to 1 effect
            MaybeFulfillable,       // maps to 0 in this case
            Unfulfillable,          // maps to 0
            FulfillableInstantly1,  // maps to 1
            FulfillableInstantly1,  // maps to 1
            MaybeFulfillable,       // maps to 1 in this case
            TranslatesTo3Effects    // maps to 3
        )

        wishes.forEach { wishSubject.onNext(it) }

        assertEquals(8 + 1, states.onNextEvents().size)
    }

    @Test
    fun `last state correctly reflects expected changes in complex case`() {
        val wishes = listOf(
            FulfillableInstantly1,  // should increase +2 (total: 102)
            FulfillableInstantly1,  // should increase +2 (total: 104)
            MaybeFulfillable,       // should not do anything in this state, as total of 2 is not divisible by 3
            Unfulfillable,          // should not do anything
            FulfillableInstantly1,  // should increase +2 (total: 106)
            FulfillableInstantly1,  // should increase +2 (total: 108)
            MaybeFulfillable,       // as total of 108 is divisible by 3, it should multiply by *10 (total: 1080)
            TranslatesTo3Effects    // should not affect state
        )

        wishes.forEach { wishSubject.onNext(it) }

        val state = states.onNextEvents().last() as TestState
        assertEquals((initialCounter + 4 * instantFulfillAmount1) * conditionalMultiplier, state.counter)
        assertEquals(false, state.loading)
    }

    @Test
    fun `the number and type of news emitted should match expectations`() {
        val wishes = listOf(
            FulfillableInstantly1,  // no news
            FulfillableInstantly1,  // no news
            MaybeFulfillable,       // should not do anything in this state, no news
            Unfulfillable,          // should not do anything
            FulfillableInstantly1,  // no news
            FulfillableInstantly1,  // no news
            MaybeFulfillable,       // as total of 108 is divisible by 3, it should emit news
            TranslatesTo3Effects    // should not affect state
        )

        wishes.forEach { wishSubject.onNext(it) }

        assertEquals(1, newsSubjectTest.onNextEvents().size)
        assertEquals(true, newsSubjectTest.onNextEvents().last() is ConditionalThingHappened)
    }

    @Test
    fun `loopback from news to multiple wishes has access to correct latest state`() {
        newsSubject.subscribe {
            if (it is LoopbackEffect1) {
                wishSubject.onNext(LoopbackWish2)
                wishSubject.onNext(LoopbackWish3)
            }
        }

        wishSubject.onNext(LoopbackWishInitial)
        wishSubject.onNext(LoopbackWish1)
        assertEquals(4, actorInvocationLogTest.onNextEvents().size)
        assertEquals(LoopbackWish1 to TestHelper.loopBackInitialState, actorInvocationLogTest.onNextEvents()[1])
        assertEquals(LoopbackWish2 to TestHelper.loopBackState1, actorInvocationLogTest.onNextEvents()[2])
        assertEquals(LoopbackWish3 to TestHelper.loopBackState2, actorInvocationLogTest.onNextEvents()[3])
    }

    @Test
    fun `an additional connected source can also trigger state emissions`() {
        val additionalSource = PublishSubject.create<TestWish>()
        val wishes = listOf<TestWish>(
            // all of them are mapped to 1 effect each
            FulfillableInstantly1,
            FulfillableInstantly1,
            FulfillableInstantly1
        )

        feature.connectSource(additionalSource)
        wishes.forEach { additionalSource.onNext(it) }

        assertEquals(1 + wishes.size, states.onNextEvents().size)
    }

    @Test
    fun `disconnecting a source prevents state emissions triggered from that source`() {
        feature.disconnectSource(wishSubject)
        val wishes = listOf<TestWish>(
            // all of them are mapped to 1 effect each
            FulfillableInstantly1,
            FulfillableInstantly1,
            FulfillableInstantly1
        )

        wishes.forEach { wishSubject.onNext(it) }

        // only 1 for initial state
        assertEquals(1, states.onNextEvents().size)
    }
}
