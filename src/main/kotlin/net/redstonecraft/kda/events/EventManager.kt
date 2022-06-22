package net.redstonecraft.kda.events

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.events.GenericEvent
import net.dv8tion.jda.api.hooks.EventListener
import net.dv8tion.jda.api.sharding.DefaultShardManagerBuilder
import net.dv8tion.jda.api.sharding.ShardManager
import kotlin.reflect.KClass
import kotlin.reflect.full.isSuperclassOf

class EventManager(private val events: Map<KClass<*>, (GenericEvent) -> Unit>): EventListener {

    override fun onEvent(event: GenericEvent) {
        val clazz = event::class
        events.keys.filter { it == clazz || it.isSuperclassOf(clazz) }.forEach { events[it]!!.invoke(event) }
    }
}

fun JDABuilder.eventManager(block: EventManagerDSL.() -> Unit): EventManager {
    val dsl = EventManagerDSL()
    dsl.block()
    val manager = EventManager(dsl.events)
    addEventListeners(manager)
    return manager
}

fun DefaultShardManagerBuilder.eventManager(block: EventManagerDSL.() -> Unit): EventManager {
    val dsl = EventManagerDSL()
    dsl.block()
    val manager = EventManager(dsl.events)
    addEventListeners(manager)
    return manager
}

fun JDA.eventManager(block: EventManagerDSL.() -> Unit): EventManager {
    val dsl = EventManagerDSL()
    dsl.block()
    val manager = EventManager(dsl.events)
    addEventListener(manager)
    return manager
}

fun ShardManager.eventManager(block: EventManagerDSL.() -> Unit): EventManager {
    val dsl = EventManagerDSL()
    dsl.block()
    val manager = EventManager(dsl.events)
    addEventListener(manager)
    return manager
}

class EventManagerDSL {
    internal val events = mutableMapOf<KClass<*>, (GenericEvent) -> Unit>()

    inline fun <reified T: GenericEvent> on(noinline block: (T) -> Unit) = on(T::class, block)

    @Suppress("UNCHECKED_CAST")
    @PublishedApi
    internal fun <T: GenericEvent> on(clazz: KClass<T>, block: (T) -> Unit) {
        events += clazz to block as (GenericEvent) -> Unit
    }
}
