package net.redstonecraft.kda

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.requests.GatewayIntent
import net.dv8tion.jda.api.sharding.DefaultShardManagerBuilder
import net.dv8tion.jda.api.sharding.ShardManager
import org.slf4j.LoggerFactory
import kotlin.concurrent.thread

abstract class DiscordBotLauncher(private val intents: Collection<GatewayIntent>) {

    constructor(vararg intents: GatewayIntent) : this(intents.toList())

    companion object {
        val logger = LoggerFactory.getLogger(DiscordBotLauncher::class.java)
    }

    private lateinit var jda: JDA
    private lateinit var shardManager: ShardManager

    fun main(args: Array<String>): Any? {
        if ("-h" in args || "--help" in args || args.isEmpty()) {
            println("""
                Help: command [options] token
                ======================
                Options:
                    -h  --help            | Show this help.
                    -s  --sharded         | Start the bot sharded with optional shard range (min-max/total).
                    -uc --update-commands | Update the bots commands globally or for a specific guild when setting the guild afterwards.
                ======================
            """.trimIndent())
            return null
        }
        val token = args.last()
        val sharded = "-s" in args || "--sharded" in args
        val shards = try {
            if (sharded) {
                val (range, totalS) = args[args.indexOf("-s").let { if (it == -1) args.indexOf("--sharded") else it } + 1].split("/", limit = 2)
                val (minS, maxS) = range.split("-", limit = 2)
                val total = totalS.toIntOrNull()
                val min = minS.toIntOrNull()
                val max = maxS.toIntOrNull()
                if (total == null || min == null || max == null) {
                    null
                } else if (min > max || max >= total) {
                    logger.warn("Invalid shard range.")
                    null
                } else {
                    total to min..max
                }
            } else {
                null
            }
        } catch (_: IndexOutOfBoundsException) {
            null
        }
        val updateCommands = "-uc" in args || "--update-commands" in args
        val guildId = try {
            if (updateCommands) {
                args[args.indexOf("-uc").let { if (it == -1) args.indexOf("--update-commands") else it } + 1]
            } else {
                null
            }
        } catch (_: IndexOutOfBoundsException) {
            null
        }
        Runtime.getRuntime().addShutdownHook(thread(false, name = "Shutdown Hook") {
            logger.info("Shutting down.")
            if (::jda.isInitialized) {
                shutdown(jda)
                while (jda.status != JDA.Status.SHUTDOWN) {
                    Thread.sleep(50)
                }
            }
            if (::shardManager.isInitialized) {
                shutdown(shardManager)
                shardManager.shards.forEach {
                    while (it.status != JDA.Status.SHUTDOWN) {
                        Thread.sleep(50)
                    }
                }
            }
        })
        return if (!sharded) {
            jda = startNormal(token)
            if (updateCommands) {
                jda.awaitReady()
                setupCommands(guildId, JDACommonJDAImpl(jda))
            }
            jda
        } else if (shards == null) {
            shardManager = startSharded(token)
            if (updateCommands) {
                shardManager.shards.forEach { it.awaitReady() }
                setupCommands(guildId, JDACommonShardManagerImpl(shardManager))
            }
            shardManager
        } else {
            shardManager = startShards(token, shards.second, shards.first)
            if (updateCommands) {
                shardManager.shards.forEach { it.awaitReady() }
                setupCommands(guildId, JDACommonShardManagerImpl(shardManager))
            }
            shardManager
        }
    }

    private fun setupCommands(guildId: String?, jda: JDACommon) {
        if (guildId != null) {
            val guild = jda.getGuildById(guildId)
            if (guild != null) {
                jda.updateSlashCommands(guild)
                logger.info("Queued update of slash-commands for guild ${guild.name}.")
            } else {
                logger.error("Guild not found. Not updated Slash-Commands.")
            }
        } else {
            jda.updateSlashCommands()
            logger.info("Queued update of slash-commands globally.")
        }
    }

    fun startNormal(token: String): JDA {
        val builder = JDABuilder.create(token, intents)
        start(builder)
        return builder.build()
    }

    fun startSharded(token: String): ShardManager {
        val builder = DefaultShardManagerBuilder.create(token, intents)
        start(builder)
        return builder.build()
    }

    fun startShards(token: String, range: IntRange, total: Int): ShardManager {
        val builder = DefaultShardManagerBuilder.create(token, intents)
        start(builder)
        builder.setShards(range.first, range.last)
        builder.setShardsTotal(total)
        return builder.build()
    }

    open fun start(builder: JDABuilder) {}
    open fun start(builder: DefaultShardManagerBuilder) {}

    open fun shutdown(jda: JDA) {}
    open fun shutdown(shardManager: ShardManager) {}

}
