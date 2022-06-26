package net.redstonecraft.kda.test

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.OnlineStatus
import net.dv8tion.jda.api.entities.Activity
import net.dv8tion.jda.api.events.message.MessageDeleteEvent
import net.dv8tion.jda.api.interactions.commands.Command
import net.dv8tion.jda.api.interactions.components.Modal
import net.dv8tion.jda.api.interactions.components.buttons.Button
import net.dv8tion.jda.api.interactions.components.selections.SelectMenu
import net.dv8tion.jda.api.interactions.components.text.TextInput
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle
import net.redstonecraft.kda.DiscordBotLauncher
import net.redstonecraft.kda.commands.Description
import net.redstonecraft.kda.commands.commandManager
import net.redstonecraft.kda.components.componentInteractionManager
import net.redstonecraft.kda.events.eventManager
import kotlin.random.Random

fun main(args: Array<String>) {
    TestBotLauncher().main(args)
}

class TestBotLauncher: DiscordBotLauncher(*enumValues()) {

    override fun start(builder: JDABuilder) {
        builder.setStatus(OnlineStatus.ONLINE)
        builder.setActivity(Activity.playing("Testing"))
        builder.commandManager {
            command<TestArgs>("test", "test command") { (it) ->
                reply(it).addActionRow(SelectMenu.create("testmenu:hello")
                    .addOption("Hi", "hi")
                    .addOption("Hello", "hello")
                    .build()
                ).queue()
            }.autocomplete(TestArgs::test) {
                val value = Random.nextLong()
                listOf(Command.Choice(value.toString(), value))
            }

            userCommand("Greet") {
                replyModal(Modal.create("testmodal:greet", "Greeting").addActionRow(
                    TextInput.create("text", "Text", TextInputStyle.SHORT).build()).build()
                ).queue()
            }

            messageCommand("Count words") {
                reply("${target.contentRaw.split(" ").size} Words").addActionRow(Button.danger("testbutton:delete", "Delete Message")).queue()
            }

            messageCommand("Delete") {
                reply("Delete").addActionRow(Button.danger("testbutton:delete:${target.id}", "Confirm")).queue()
            }
        }
        builder.componentInteractionManager {
            button("testbutton:") {
                reply(it).queue()
            }

            button("testbutton:delete:") {
                deferReply(false).flatMap { replyHook -> editButton(button.asDisabled()).flatMap { _ -> messageChannel.retrieveMessageById(it).flatMap { msg -> msg.delete().flatMap { replyHook.editOriginal("deleted") } } } }.queue()
            }

            selectMenu("testmenu:") {
                deferReply(false).flatMap { hook -> editSelectMenu(selectMenu.asDisabled()).flatMap { _ -> hook.editOriginal("${it}\n\n${values.joinToString("\n")}") } }.queue()
            }

            modal("testmodal:") {
                reply("$modalId\n$it\n\n${values.joinToString("\n") { i -> "${i.id}=${i.asString}" }}").queue()
            }
        }
        builder.eventManager {
            on<MessageDeleteEvent> {
                if (isFromGuild) {
                    channel.sendMessage("deleted msg").queue()
                }
            }
        }
    }

    override fun shutdown(jda: JDA) {
        jda.presence.setStatus(OnlineStatus.OFFLINE)
    }
}

data class TestArgs(@Description("test argument") val test: String)
