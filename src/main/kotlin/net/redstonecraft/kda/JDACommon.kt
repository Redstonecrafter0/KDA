package net.redstonecraft.kda

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.sharding.ShardManager
import net.redstonecraft.kda.commands.updateKDACommands

interface JDACommon {
    fun getGuildById(id: String): Guild?
    fun updateSlashCommands(guild: Guild? = null)
}

class JDACommonJDAImpl(private val jda: JDA): JDACommon {

    override fun getGuildById(id: String): Guild? {
        return jda.getGuildById(id)
    }

    override fun updateSlashCommands(guild: Guild?) {
        jda.updateKDACommands(guild)
    }
}

class JDACommonShardManagerImpl(private val jda: ShardManager): JDACommon {

    override fun getGuildById(id: String): Guild? {
        return jda.getGuildById(id)
    }

    override fun updateSlashCommands(guild: Guild?) {
        jda.updateKDACommands(guild)
    }
}
