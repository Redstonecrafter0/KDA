package net.redstonecraft.kda.components

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.events.GenericEvent
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.SelectMenuInteractionEvent
import net.dv8tion.jda.api.hooks.EventListener
import net.dv8tion.jda.api.sharding.DefaultShardManagerBuilder
import net.dv8tion.jda.api.sharding.ShardManager
import java.util.TreeMap

class ComponentInteractionManager(
    private val buttons: Map<String, ButtonInteractionEvent.(String) -> Unit>,
    private val selectMenus: Map<String, SelectMenuInteractionEvent.(String) -> Unit>,
    private val modals: Map<String, ModalInteractionEvent.(String) -> Unit>
): EventListener {

    override fun onEvent(event: GenericEvent) {
        when (event) {
            is ButtonInteractionEvent -> {
                val key = buttons.keys.firstOrNull { event.componentId.startsWith(it) }
                if (key != null) {
                    buttons[key]!!.invoke(event, event.componentId.removePrefix(key))
                }
            }
            is SelectMenuInteractionEvent -> {
                val key = selectMenus.keys.firstOrNull { event.componentId.startsWith(it) }
                if (key != null) {
                    selectMenus[key]!!.invoke(event, event.componentId.removePrefix(key))
                }
            }
            is ModalInteractionEvent -> {
                val key = modals.keys.firstOrNull { event.modalId.startsWith(it) }
                if (key != null) {
                    modals[key]!!.invoke(event, event.modalId.removePrefix(key))
                }
            }
        }
    }
}

fun JDABuilder.componentInteractionManager(block: ComponentInteractionManagerDSL.() -> Unit): ComponentInteractionManager {
    val dsl = ComponentInteractionManagerDSL()
    dsl.block()
    val manager = ComponentInteractionManager(dsl.buttons, dsl.selectMenus, dsl.modals)
    addEventListeners(manager)
    return manager
}

fun DefaultShardManagerBuilder.componentInteractionManager(block: ComponentInteractionManagerDSL.() -> Unit): ComponentInteractionManager {
    val dsl = ComponentInteractionManagerDSL()
    dsl.block()
    val manager = ComponentInteractionManager(dsl.buttons, dsl.selectMenus, dsl.modals)
    addEventListeners(manager)
    return manager
}

fun JDA.componentInteractionManager(block: ComponentInteractionManagerDSL.() -> Unit): ComponentInteractionManager {
    val dsl = ComponentInteractionManagerDSL()
    dsl.block()
    val manager = ComponentInteractionManager(dsl.buttons, dsl.selectMenus, dsl.modals)
    addEventListener(manager)
    return manager
}

fun ShardManager.componentInteractionManager(block: ComponentInteractionManagerDSL.() -> Unit): ComponentInteractionManager {
    val dsl = ComponentInteractionManagerDSL()
    dsl.block()
    val manager = ComponentInteractionManager(dsl.buttons, dsl.selectMenus, dsl.modals)
    addEventListener(manager)
    return manager
}

class ComponentInteractionManagerDSL {

    internal val buttons = TreeMap<String, ButtonInteractionEvent.(String) -> Unit> { k1, k2 -> k2.length.compareTo(k1.length) }
    internal val selectMenus = TreeMap<String, SelectMenuInteractionEvent.(String) -> Unit> { k1, k2 -> k2.length.compareTo(k1.length) }
    internal val modals = TreeMap<String, ModalInteractionEvent.(String) -> Unit> { k1, k2 -> k2.length.compareTo(k1.length) }

    fun button(name: String, block: ButtonInteractionEvent.(String) -> Unit) {
        buttons += name to block
    }

    fun selectMenu(name: String, block: SelectMenuInteractionEvent.(String) -> Unit) {
        selectMenus += name to block
    }

    fun modal(name: String, block: ModalInteractionEvent.(String) -> Unit) {
        modals += name to block
    }
}
