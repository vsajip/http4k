package org.http4k.chaos

import com.fasterxml.jackson.databind.JsonNode
import org.http4k.chaos.ChaosTriggers.Always
import org.http4k.chaos.ChaosTriggers.Countdown
import org.http4k.chaos.ChaosTriggers.Deadline
import org.http4k.chaos.ChaosTriggers.Delay
import org.http4k.chaos.ChaosTriggers.MatchRequest
import org.http4k.chaos.ChaosTriggers.Once
import org.http4k.chaos.ChaosTriggers.PercentageBased
import org.http4k.core.Method
import org.http4k.core.Request
import java.time.Clock
import java.time.Clock.systemUTC
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

fun interface Trigger : (Request) -> Boolean

operator fun Trigger.not() = object : Trigger {
    override fun invoke(req: Request) = !this@not(req)
    override fun toString() = "NOT " + this@not.toString()
}

infix fun Trigger.and(that: Trigger): Trigger = object : Trigger {
    override fun invoke(req: Request) = this@and(req) && that(req)
    override fun toString() = this@and.toString() + " AND " + that.toString()
}

infix fun Trigger.or(that: Trigger): Trigger = object : Trigger {
    override fun invoke(req: Request) = this@or(req) || that(req)
    override fun toString() = this@or.toString() + " OR " + that.toString()
}

object ChaosTriggers {
    /**
     * Single application predicated on the ChaosTrigger. Further matches don't apply
     */
    object Once {
        operator fun invoke(trigger: Trigger? = Always()) = object : Trigger {
            private val active = AtomicBoolean(true)
            override fun invoke(request: Request) =
                if (trigger?.invoke(request) != false) active.get().also { active.set(false) } else false

            override fun toString() = "Once" + (trigger?.let { " (trigger = $trigger)" } ?: "")
        }
    }

    /**
     * Applies to every transaction.
     */
    object Always {
        operator fun invoke() = object : Trigger {
            override fun invoke(request: Request) = true
            override fun toString() = "Always"
        }
    }

    /**
     * Applies n% of the time, based on result of a Random.
     */
    object PercentageBased {
        operator fun invoke(injectionFrequency: Int, selector: Random = Random) = object : Trigger {
            override fun invoke(request: Request) = selector.nextInt(100) <= injectionFrequency
            override fun toString() = "PercentageBased ($injectionFrequency%)"
        }

        /**
         * Get a percentage from the environment.
         * Defaults to CHAOS_PERCENTAGE and a value of 50%
         */
        fun fromEnvironment(
            env: (String) -> String? = System::getenv,
            defaultPercentage: Int = 50,
            name: String = "CHAOS_PERCENTAGE"
        ) = PercentageBased(env(name)?.let(Integer::parseInt) ?: defaultPercentage)
    }

    /**
     * Activates after a particular instant in time.
     */
    object Deadline {
        operator fun invoke(endTime: Instant, clock: Clock = systemUTC()) = object : Trigger {
            override fun invoke(req: Request) = clock.instant().isAfter(endTime)
            override fun toString() = "Deadline ($endTime)"
        }
    }

    /**
     * Activates after a particular delay (compared to instantiation).
     */
    object Delay {
        operator fun invoke(period: Duration, clock: Clock = systemUTC()) = object : Trigger {
            private val endTime = Instant.now(clock).plus(period)
            override fun invoke(req: Request) = clock.instant().isAfter(endTime)
            override fun toString() = "Delay (expires $endTime)"
        }
    }

    private class RequestMatcher(val description: String, predicate: (Request) -> Boolean) :
            (Request) -> Boolean by predicate

    /**
     * Activates when matching attributes of a single received request are met.
     */
    object MatchRequest {
        operator fun invoke(
            method: String? = null,
            path: Regex? = null,
            queries: Map<String, Regex>? = null,
            headers: Map<String, Regex>? = null,
            body: Regex? = null
        ): Trigger {
            val headerMatchers =
                headers?.map { (k, v) ->
                    RequestMatcher("header '$k' matches '$v'") {
                        it.headerValues(k).any { v.matches(it ?: "") }
                    }
                }
                    ?: emptyList()
            val queriesMatchers =
                queries?.map { (k, v) ->
                    RequestMatcher("query '$k' matches '$v'") {
                        it.queries(k).any { v.matches(it ?: "") }
                    }
                }
                    ?: emptyList()
            val pathMatchers =
                path?.let { p -> listOf(RequestMatcher("path matches '$p'") { it.uri.path.matches(p) }) } ?: emptyList()
            val bodyMatchers =
                body?.let { b -> listOf(RequestMatcher("body matches '$b'") { it.bodyString().matches(b) }) }
                    ?: emptyList()
            val methodMatchers =
                method?.let { m -> listOf(RequestMatcher("method == ${m.uppercase()}") { it.method == Method.valueOf(m.uppercase()) }) }
                    ?: emptyList()
            val all = methodMatchers + pathMatchers + queriesMatchers + headerMatchers + bodyMatchers

            val matcher = if (all.isEmpty()) RequestMatcher("anything") { true } else
                all.reduce { acc, next ->
                    RequestMatcher(acc.description + " AND " + next.description) { acc(it) && next(it) }
                }

            return object : Trigger {
                override fun invoke(req: Request) = matcher(req)
                override fun toString() = matcher.description
            }
        }
    }

    /**
     * Activates for a maximum number of calls.
     */
    object Countdown {
        operator fun invoke(initial: Int): Trigger = object : Trigger {
            private val count = AtomicInteger(initial)

            override fun invoke(req: Request) = if (count.get() > 0) {
                count.decrementAndGet(); false
            } else true

            override fun toString() = "Countdown (${count.get()} remaining)"
        }
    }
}

internal fun JsonNode.asTrigger(clock: Clock): Trigger = when (nonNullable<String>("type")) {
    "deadline" -> Deadline(nonNullable("endTime"), clock)
    "delay" -> Delay(nonNullable("period"), clock)
    "countdown" -> Countdown(nonNullable("count"))
    "request" -> MatchRequest(
        asNullable("method"),
        asNullable("path"),
        toRegexMap("queries"),
        toRegexMap("headers"),
        asNullable("body")
    )

    "once" -> Once(this["trigger"]?.asTrigger(clock))
    "percentage" -> PercentageBased(this["percentage"].asInt())
    "always" -> Always()
    else -> throw IllegalArgumentException("unknown trigger")
}

private fun JsonNode.toRegexMap(name: String) = asNullable<Map<String, String>>(name)?.mapValues { it.value.toRegex() }
