package com.badoo.mvicore.feature.arkadii

import com.badoo.mvicore.extension.overrideAssertsForTesting
import org.amshove.kluent.shouldBeNull
import org.amshove.kluent.shouldEqual
import org.junit.Before
import org.junit.Test

class ConcreteFeature2ProblemTest {
    private val testRequest = ""

    @Before
    fun before() {
        overrideAssertsForTesting(false)

    }

    @Test
    fun `fail if reducing state in Actor is a BAD idea`() {

        val maxCounter = 5
        val feature = ConcreteFeature2(maxCounter)

        (1..maxCounter).forEach {
            feature.accept(ConcreteFeature2.Wish.LoadIfNeeded(testRequest))
        }

        feature.state.counter shouldEqual maxCounter - 1 //cause test feature fails to increment at this point
        feature.state.data.shouldBeNull() // cause it failed to increment
    }

    @Test
    fun `lets not reduce state in Actors!`() {

        val maxCounter = 5
        val feature = ConcreteFeature2Fixed(maxCounter)

        (1..maxCounter).forEach {
            feature.accept(ConcreteFeature2Fixed.Wish.LoadIfNeeded(testRequest))
        }

        feature.state.counter shouldEqual maxCounter - 1 //cause test feature fails to increment at this point
        feature.state.data.shouldBeNull() // cause it failed to increment
    }


}

