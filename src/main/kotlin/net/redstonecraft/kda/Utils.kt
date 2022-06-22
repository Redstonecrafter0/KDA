package net.redstonecraft.kda

import net.dv8tion.jda.api.interactions.commands.Command.Choice
import net.dv8tion.jda.api.requests.RestAction
import java.util.concurrent.TimeUnit
import kotlin.time.Duration

fun Enum<*>.toChoice(): Choice = Choice(name, name)

interface NamedEnum {
    val displayName: String

    fun toChoice(): Choice = Choice(displayName, (this as Enum<*>).name)
}

inline fun <reified T : Enum<T>> Choice.toEnum() = T::class.java.enumConstants.firstOrNull { it.name == asString }

fun RestAction<*>.timeout(duration: Duration) = timeout(duration.inWholeMilliseconds, TimeUnit.MILLISECONDS)
fun RestAction<*>.delay(duration: Duration) = delay(duration.inWholeMilliseconds, TimeUnit.MILLISECONDS)
fun RestAction<*>.submitAfter(duration: Duration) = submitAfter(duration.inWholeMilliseconds, TimeUnit.MILLISECONDS)
fun RestAction<*>.queueAfter(duration: Duration) = queueAfter(duration.inWholeMilliseconds, TimeUnit.MILLISECONDS)
fun <T> RestAction<T>.completeAfter(duration: Duration): T = completeAfter(duration.inWholeMilliseconds, TimeUnit.MILLISECONDS)
