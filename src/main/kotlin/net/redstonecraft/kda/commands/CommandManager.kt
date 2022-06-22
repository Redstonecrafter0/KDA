package net.redstonecraft.kda.commands

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.events.GenericEvent
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.MessageContextInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.UserContextInteractionEvent
import net.dv8tion.jda.api.hooks.EventListener
import net.dv8tion.jda.api.interactions.commands.Command.Choice
import net.dv8tion.jda.api.interactions.commands.CommandInteractionPayload
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.interactions.commands.build.OptionData
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData
import net.dv8tion.jda.api.interactions.commands.build.SubcommandGroupData
import net.dv8tion.jda.api.sharding.DefaultShardManagerBuilder
import net.dv8tion.jda.api.sharding.ShardManager
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1

class CommandManager(
    val commands: Map<String, Command<*>>,
    val userCommands: Map<String, UserCommand>,
    val messageCommands: Map<String, MessageCommand>
): EventListener {

    override fun onEvent(event: GenericEvent) {
        when (event) {
            is SlashCommandInteractionEvent -> {
                getCommand(event)?.invoke(event, event.options)
            }
            is CommandAutoCompleteInteractionEvent -> {
                val command = getCommand(event)
                if (command != null) {
                    event.replyChoices(command.autocomplete[event.focusedOption.name]!!.invoke(event)).queue()
                }
            }
            is UserContextInteractionEvent -> {
                userCommands[event.name]?.runnable?.invoke(event)
            }
            is MessageContextInteractionEvent -> {
                messageCommands[event.name]?.runnable?.invoke(event)
            }
        }
    }

    private fun getCommand(event: CommandInteractionPayload) = when {
        event.subcommandName == null && event.subcommandGroup == null -> commands[event.name]
        event.subcommandName != null && event.subcommandGroup == null -> commands[event.name]?.subCommands?.get(
            event.subcommandName
        )
        event.subcommandName != null && event.subcommandGroup != null -> commands[event.name]?.groupCommands?.get(
            event.subcommandName
        )?.subCommands?.get(event.name)
        else -> null
    }
}

fun JDA.commandManager(block: CommandDSL.() -> Unit): CommandManager {
    val dsl = CommandDSL()
    dsl.block()
    val commandManager = CommandManager(dsl.commands, dsl.userCommands, dsl.messageCommands)
    addEventListener(commandManager)
    return commandManager
}

fun ShardManager.commandManager(block: CommandDSL.() -> Unit): CommandManager {
    val dsl = CommandDSL()
    dsl.block()
    val commandManager = CommandManager(dsl.commands, dsl.userCommands, dsl.messageCommands)
    addEventListener(commandManager)
    return commandManager
}

fun JDABuilder.commandManager(block: CommandDSL.() -> Unit): CommandManager {
    val dsl = CommandDSL()
    dsl.block()
    val commandManager = CommandManager(dsl.commands, dsl.userCommands, dsl.messageCommands)
    addEventListeners(commandManager)
    return commandManager
}

fun DefaultShardManagerBuilder.commandManager(block: CommandDSL.() -> Unit): CommandManager {
    val dsl = CommandDSL()
    dsl.block()
    val commandManager = CommandManager(dsl.commands, dsl.userCommands, dsl.messageCommands)
    addEventListeners(commandManager)
    return commandManager
}

fun JDA.updateKDACommands(guild: Guild? = null) {
    val target = guild?.updateCommands() ?: updateCommands()
    val commandManagers = eventManager.registeredListeners.filterIsInstance<CommandManager>()
    target.addCommands(
        commandManagers
            .map { it.commands.values }
            .flatten()
            .map { Commands.slash(it.name, it.description).apply {
                addSubcommands(it.subCommands.values.mapSubCommands())
                addSubcommandGroups(it.groupCommands.values.mapSubCommandGroups())
                addOptions(it.argsFactory.properties.values.mapOptions())
            } }
                + commandManagers.map { it.userCommands.values.map { i -> Commands.user(i.name) } }.flatten()
                + commandManagers.map { it.messageCommands.values.map { i -> Commands.message(i.name) } }.flatten()
    ).queue()
}

fun ShardManager.updateKDACommands(guild: Guild? = null) {
    if (guild == null) {
        shards.forEach { it.updateKDACommands() }
    } else {
        guild.jda.updateKDACommands(guild)
    }
}

private fun Collection<GroupCommand>.mapSubCommandGroups() = map { i ->
    SubcommandGroupData(i.name, i.description).apply {
        addSubcommands(i.subCommands.values.mapSubCommands())
    }
}

private fun Collection<SubCommand<*>>.mapSubCommands() = map { i ->
    SubcommandData(i.name, i.description).apply {
        addOptions(i.argsFactory.properties.values.mapOptions())
    }
}

private fun Collection<CommandArgsFactory.ArgumentParameter>.mapOptions() = map { p ->
    OptionData(p.optionType, p.name, p.description, !p.optional, p.autocomplete)
        .addChoices(p.choices.map { (name, value) -> when (value) {
            is String ->Choice(name, value)
            is Int ->Choice(name, value.toLong())
            is Long ->Choice(name, value)
            is Double ->Choice(name, value)
            is Float ->Choice(name, value.toDouble())
            else -> null
        } })
}

class CommandDSL {
    internal val commands = mutableMapOf<String, Command<*>>()
    internal val userCommands = mutableMapOf<String, UserCommand>()
    internal val messageCommands = mutableMapOf<String, MessageCommand>()

    inline fun <reified T: Any> command(name: String, description: String, threaded: Boolean = false, noinline block: SlashCommandInteractionEvent.(T) -> Unit) = command(T::class, name, description, threaded, block)

    @PublishedApi
    internal fun <T: Any> command(clazz: KClass<T>, name: String, description: String, threaded: Boolean = false, block: SlashCommandInteractionEvent.(T) -> Unit): SlashCommandAutocompleteDSL<T> {
        val command = Command(name, description, block, threaded, emptyMap(), emptyMap(), clazz)
        commands += name to command
        return SlashCommandAutocompleteDSL(command)
    }

    fun command(name: String, description: String, block: CommandSetupDSL.() -> Unit) {
        val setup = CommandSetupDSL()
        setup.block()
        commands += name to Command(name, description, null, false, setup.subcommands, setup.groupcommands, EmptyArgs::class)
    }

    fun userCommand(name: String, block: UserContextInteractionEvent.() -> Unit) {
        userCommands += name to UserCommand(name, block)
    }

    fun messageCommand(name: String, block: MessageContextInteractionEvent.() -> Unit) {
        messageCommands += name to MessageCommand(name, block)
    }
}

class CommandSetupDSL: SubCommandSetupDSL() {
    internal val groupcommands = mutableMapOf<String, GroupCommand>()

    fun subcommandGroup(name: String, description: String, block: SubCommandSetupDSL.() -> Unit) {
        val setup = SubCommandSetupDSL()
        setup.block()
        groupcommands += name to GroupCommand(name, description, setup.subcommands)
    }
}

open class SubCommandSetupDSL {
    internal val subcommands = mutableMapOf<String, SubCommand<*>>()

    inline fun <reified T: Any> subcommand(name: String, description: String, threaded: Boolean = false, noinline block: SlashCommandInteractionEvent.(T) -> Unit) = subcommand(T::class, name, description, threaded, block)

    @PublishedApi
    internal fun <T: Any> subcommand(clazz: KClass<T>, name: String, description: String, threaded: Boolean = false, block: SlashCommandInteractionEvent.(T) -> Unit) {
        subcommands += name to SubCommand(name, description, block, threaded, clazz)
    }
}

class SlashCommandAutocompleteDSL<T>(private val command: ICommand<*>) {

    fun autocomplete(name: KProperty1<T, *>, block: CommandAutoCompleteInteractionEvent.() -> List<Choice>): SlashCommandAutocompleteDSL<T> {
        command.argsFactory.properties[name.name]?.autocomplete = true
        command.autocomplete += name.name to block
        return this
    }
}
