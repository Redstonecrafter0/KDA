package net.redstonecraft.kda.commands

import net.dv8tion.jda.api.entities.*
import net.dv8tion.jda.api.entities.Message.Attachment
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.MessageContextInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.UserContextInteractionEvent
import net.dv8tion.jda.api.interactions.commands.Command
import net.dv8tion.jda.api.interactions.commands.OptionMapping
import net.dv8tion.jda.api.interactions.commands.OptionType
import java.lang.reflect.Member
import java.util.concurrent.Executors
import kotlin.reflect.KClass
import kotlin.reflect.KParameter
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.findAnnotations
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.jvmErasure
import net.dv8tion.jda.api.interactions.commands.Command.Choice as CommandChoice

class CommandArgsFactory<T: Any>(private val clazz: KClass<T>) {

    private companion object {
        private fun extractChoices(param: KParameter): List<Pair<String, Any>> {
            return param.findAnnotations<Choice>().map { it.name to it.value } +
                    param.findAnnotations<IChoice>().map { it.name to it.value } +
                    param.findAnnotations<LChoice>().map { it.name to it.value } +
                    param.findAnnotations<DChoice>().map { it.name to it.value } +
                    param.findAnnotations<FChoice>().map { it.name to it.value }
        }

        private fun translateType(type: KClass<*>): OptionType {
            return when (type) {
                Attachment::class -> OptionType.ATTACHMENT
                String::class -> OptionType.STRING
                Boolean::class -> OptionType.BOOLEAN
                Long::class -> OptionType.INTEGER
                Int::class -> OptionType.INTEGER
                Double::class -> OptionType.NUMBER
                Float::class -> OptionType.NUMBER
                Member::class -> OptionType.USER
                User::class -> OptionType.USER
                Role::class -> OptionType.ROLE
                ChannelType::class -> OptionType.CHANNEL
                GuildChannel::class -> OptionType.CHANNEL
                GuildMessageChannel::class -> OptionType.CHANNEL
                TextChannel::class -> OptionType.CHANNEL
                NewsChannel::class -> OptionType.CHANNEL
                ThreadChannel::class -> OptionType.CHANNEL
                AudioChannel::class -> OptionType.CHANNEL
                VoiceChannel::class -> OptionType.CHANNEL
                StageChannel::class -> OptionType.CHANNEL
                else -> error("Invalid Parameter")
            }
        }
    }

    val properties by lazy {
        clazz.primaryConstructor!!.parameters.map { ArgumentParameter(it.name!!, it.findAnnotation<Description>()?.value ?: error("Missing @Description annotation"), it.isOptional, it.type.jvmErasure, translateType(it.type.jvmErasure), extractChoices(it), false, it) }.associateBy { it.name }
    }

    fun buildArgs(list: List<OptionMapping>): T {
        val map = list.associateBy { it.name }
        val args = mutableMapOf<KParameter, Any?>()
        for ((_, i) in properties) {
            val option = map[i.name]
            if (option != null) {
                args += i.param to when (i.type) {
                    Attachment::class -> option.asAttachment
                    String::class -> option.asString
                    Boolean::class -> option.asBoolean
                    Long::class -> option.asLong
                    Int::class -> option.asInt
                    Double::class -> option.asDouble
                    Float::class -> option.asDouble.toFloat()
                    Member::class -> option.asMember
                    User::class -> option.asUser
                    Role::class -> option.asRole
                    ChannelType::class -> option.channelType
                    GuildChannel::class -> option.asChannel
                    GuildMessageChannel::class -> option.asChannel.asGuildMessageChannel()
                    TextChannel::class -> option.asChannel.asTextChannel()
                    NewsChannel::class -> option.asChannel.asNewsChannel()
                    ThreadChannel::class -> option.asChannel.asThreadChannel()
                    AudioChannel::class -> option.asChannel.asAudioChannel()
                    VoiceChannel::class -> option.asChannel.asVoiceChannel()
                    StageChannel::class -> option.asChannel.asStageChannel()
                    else -> null
                }
            }
        }
        return clazz.primaryConstructor!!.callBy(args)
    }

    data class ArgumentParameter(val name: String, val description: String, val optional: Boolean, val type: KClass<*>, val optionType: OptionType, val choices: List<Pair<String, Any>>, var autocomplete: Boolean, val param: KParameter)
}

interface CommandInfo {
    val name: String
    val description: String
}

interface ICommand<T: Any>: CommandInfo {

    companion object {
        val threadPool = Executors.newCachedThreadPool()
    }

    val runnable: (SlashCommandInteractionEvent.(T) -> Unit)?
    val threaded: Boolean
    val argsFactory: CommandArgsFactory<T>
    val autocomplete: MutableMap<String, CommandAutoCompleteInteractionEvent.() -> List<CommandChoice>>

    operator fun invoke(event: SlashCommandInteractionEvent, list: List<OptionMapping>) = invoke(event, argsFactory.buildArgs(list))

    operator fun invoke(event: SlashCommandInteractionEvent, args: T) {
        if (threaded) {
            threadPool.submit { runnable?.invoke(event, args) }
        } else {
            runnable?.invoke(event, args)
        }
    }
}

class Command<T: Any>(
    override val name: String,
    override val description: String,
    override val runnable: (SlashCommandInteractionEvent.(T) -> Unit)?,
    override val threaded: Boolean,
    val defaultEnabled: Boolean,
    val subCommands: Map<String, SubCommand<*>>,
    val groupCommands: Map<String, GroupCommand>,
    clazz: KClass<T>
): ICommand<T> {

    override val autocomplete: MutableMap<String, CommandAutoCompleteInteractionEvent.() -> List<Command.Choice>> = mutableMapOf()
    override val argsFactory = CommandArgsFactory(clazz)
}

class GroupCommand(
    override val name: String,
    override val description: String,
    val subCommands: Map<String, SubCommand<*>>
): CommandInfo

class SubCommand<T: Any>(
    override val name: String,
    override val description: String,
    override val runnable: (SlashCommandInteractionEvent.(T) -> Unit)?,
    override val threaded: Boolean,
    clazz: KClass<T>
): ICommand<T> {

    override val autocomplete: MutableMap<String, CommandAutoCompleteInteractionEvent.() -> List<Command.Choice>> = mutableMapOf()
    override val argsFactory = CommandArgsFactory(clazz)
}

class UserCommand(val name: String, val runnable: UserContextInteractionEvent.() -> Unit)
class MessageCommand(val name: String, val runnable: MessageContextInteractionEvent.() -> Unit)

class EmptyArgs
