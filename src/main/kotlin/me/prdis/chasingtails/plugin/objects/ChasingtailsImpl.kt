/*
 * Copyright (C) 2026 Paradise Dev Team
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *  
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *  
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>
 */

package me.prdis.chasingtails.plugin.objects

import me.prdis.chasingtails.plugin.config.ChasingtailsConfig
import me.prdis.chasingtails.plugin.config.GamePlayerData
import me.prdis.chasingtails.plugin.events.GameManageEvent
import me.prdis.chasingtails.plugin.events.HuntingEvent
import me.prdis.chasingtails.plugin.objects.ChasingtailsConsts.plugin
import me.prdis.chasingtails.plugin.objects.ChasingtailsConsts.server
import me.prdis.chasingtails.plugin.tasks.ChasingtailsTasks
import net.kyori.adventure.text.Component.text
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.*
import org.bukkit.attribute.Attribute
import org.bukkit.entity.Player
import org.bukkit.entity.TextDisplay
import org.bukkit.event.HandlerList
import org.bukkit.scoreboard.Scoreboard
import java.util.*
import kotlin.random.Random

/**
 * @author Paradise Dev Team
 */

object ChasingtailsImpl {
    var isRunning = false
    var isGameHalted = false

    val gamePlayers = LinkedList<GamePlayer>()

    val scoreboard = server.scoreboardManager.mainScoreboard

    val Player.gamePlayer
        get() = gamePlayers.find { it.uniqueId == uniqueId }

    var OfflinePlayer.lastLocation: Location?
        get() = plugin.config.getLocation("last_location.${uniqueId}")
        set(value) {
            plugin.config.set("last_location.${uniqueId}", value)
        }

    var initEndSpawn: Location? = null


    val mappedColors = hashMapOf<NamedTextColor, String>(
        Pair(NamedTextColor.LIGHT_PURPLE, "핑크색"),
        Pair(NamedTextColor.RED, "빨간색"),
        Pair(NamedTextColor.GOLD, "주황색"),
        Pair(NamedTextColor.YELLOW, "노란색"),
        Pair(NamedTextColor.GREEN, "초록색"),
        Pair(NamedTextColor.BLUE, "파란색"),
        Pair(NamedTextColor.DARK_BLUE, "남색"),
        Pair(NamedTextColor.DARK_PURPLE, "보라색")
    )

    fun startGame() {
        if (!isRunning) {
            isRunning = true
            plugin.config.set("isRunning", true)
            plugin.saveConfig()

            scoreboard.initScoreboard()

            val innerGamePlayers = server.onlinePlayers.toTypedArray().filter { it.gameMode != GameMode.SPECTATOR }

            gamePlayers.addAll(innerGamePlayers.map { GamePlayer(it) }.shuffled())

            server.worlds.forEach(ChasingtailsImpl::manageWorld)

            gamePlayers.forEachIndexed { index, gamePlayer ->
                gamePlayer.target = gamePlayers[(index + 1) % gamePlayers.size]

                val player = gamePlayer.player

                scoreboard.getTeam("team$index")?.addPlayer(player)

                player.exp = 0F

                val x = Random.nextInt(-750, 750)
                val z = Random.nextInt(-750, 750)

                player.teleportAsync(
                    Location(
                        player.world,
                        x + 0.5,
                        player.world.getHighestBlockYAt(x, z) + 1.0,
                        z + 0.5,
                    )
                )

                player.playerListName(text(player.name, NamedTextColor.WHITE))
                player.restoreDefaults()
            }

            gamePlayers.forEach { gamePlayer ->
                gamePlayer.notifyTeam()
            }

            ChasingtailsTasks.startTasks()
        } else {
            isGameHalted = !allPlayerExists()
        }

        server.pluginManager.registerEvents(HuntingEvent, plugin)
        server.pluginManager.registerEvents(GameManageEvent, plugin)
    }

    fun stopGame() {
        HandlerList.unregisterAll(HuntingEvent)
        HandlerList.unregisterAll(GameManageEvent)

        ChasingtailsTasks.stopTasks()

        for (teamIndex in 0 until 8) {
            scoreboard.getTeam("team${teamIndex}")?.unregister()
        }

        gamePlayers.forEach { it.player.restoreDefaults() }
        server.offlinePlayers.forEach { it.lastLocation = null }
        plugin.config.set("last_location", null)

        server.worlds.forEach { world ->
            world.entities.filterIsInstance<TextDisplay>().forEach {
                it.remove()
            }
        }

        gamePlayers.clear()

        ChasingtailsConfig.resetGameStatus()

        isRunning = false
        plugin.config.set("isRunning", false)
        plugin.saveConfig()
    }

    fun resumeGame() {
        isGameHalted = false

        scoreboard.initScoreboard()

        ChasingtailsConfig.getGamePlayerDatas().keys.forEach { uuidString ->
            val uuid = try {
                UUID.fromString(uuidString)
            } catch (_: IllegalArgumentException) {
                server.broadcast(text("플레이어 정보 변환에 실패하여 게임을 강제 종료합니다.", NamedTextColor.RED))
                stopGame()
                return
            }

            val player = server.getPlayer(uuid) ?: run {
                server.broadcast(text("변환하는 플레이어를 못하여 게임을 강제 종료합니다.", NamedTextColor.RED))
                stopGame()
                return
            }

            val gamePlayer = GamePlayer(player)
            gamePlayers.add(gamePlayer)
        }

        plugin.logger.info("Restoring game players: ${gamePlayers.map { it.uniqueId }}")

        gamePlayers.forEach {
            val isRestored = it.player.restoreGamePlayer()

            if (!isRestored) {
                server.broadcast(text("플레이어의 게임 진행 정보를 불러오지 못하여 게임을 강제 종료합니다.", NamedTextColor.RED))
                stopGame()
                return
            }

            it.notifyTeam()
        }

        server.broadcast(text("게임을 재개합니다.", NamedTextColor.GREEN))

        ChasingtailsTasks.startTasks()
    }

    fun Scoreboard.initScoreboard() {
        val colorList = listOf(
            NamedTextColor.LIGHT_PURPLE,
            NamedTextColor.RED,
            NamedTextColor.GOLD,
            NamedTextColor.YELLOW,
            NamedTextColor.GREEN,
            NamedTextColor.BLUE,
            NamedTextColor.DARK_BLUE,
            NamedTextColor.DARK_PURPLE,
        ).reversed()

        colorList.forEachIndexed { index, color ->
            getTeam("team$index")?.unregister()

            registerNewTeam("team$index").apply {
                color(color)
            }
        }
    }

    fun manageWorld(world: World) = world.apply {
        setGameRule(GameRules.IMMEDIATE_RESPAWN, true)
        setGameRule(GameRules.KEEP_INVENTORY, true)
        setGameRule(GameRules.SHOW_DEATH_MESSAGES, false)
        setGameRule(GameRules.SHOW_ADVANCEMENT_MESSAGES, false)
        difficulty = Difficulty.EASY
    }

    fun GamePlayer.notifyTeam() {
        sendMessage(
            text("당신의 팀: ", NamedTextColor.YELLOW).append(text(koreanColor, color))
        )

        sendMessage(
            text("당신의 타겟은 ", NamedTextColor.YELLOW).append(text(target.koreanColor, target.color))
                .append(text("입니다.", NamedTextColor.YELLOW))
        )
    }

    fun allPlayerExists(): Boolean {
        val configGamePlayers = ChasingtailsConfig.getGamePlayerDatas().keys
        val innerGamePlayers = server.onlinePlayers.toTypedArray().filter { it.gameMode != GameMode.SPECTATOR }
            .map { it.uniqueId.toString() }

        return configGamePlayers.all { it in innerGamePlayers }
    }

    fun Player.restoreGamePlayer(): Boolean {
        val data = plugin.config.get("chasingtails.${uniqueId}") as? GamePlayerData ?: run {
            plugin.componentLogger.error(text("Failed to get GamePlayerData for player $uniqueId"))
            return false
        }

        val gamePlayer = gamePlayer ?: run {
            plugin.componentLogger.error(text("Failed to get GamePlayer for player $uniqueId"))
            return false
        }

        val target = gamePlayers.find { it.uniqueId.toString() == data.target }
        if (target != null) gamePlayer.target = target

        gamePlayer.master = gamePlayers.find { it.uniqueId.toString() == data.master }

        val deathTimerEntity = server.worlds.flatMap { world -> world.entities }
            .firstOrNull { it.uniqueId.toString() == data.deathTimer }

        gamePlayer.deathTimer = deathTimerEntity as? TextDisplay
        gamePlayer.tempDeathDuration = data.tempDeathDuration

        gamePlayer.deathTimer?.let { deathTimer ->
            gamePlayer.player.addPassenger(deathTimer)
        }

        gamePlayer.master?.let {
            getAttribute(Attribute.MAX_HEALTH)?.baseValue = 10.0
        }

        val team = scoreboard.getTeam(data.teamName) ?: scoreboard.registerNewTeam(data.teamName)

        if (!team.hasColor()) {
            team.color(NamedTextColor.namedColor(data.teamColor))
        }

        team.addPlayer(this)

        if (gamePlayer.master != null) {
            gamePlayer.equipSlaveArmor(this)
        }

        return true
    }

    private fun Player.restoreDefaults() {
        inventory.clear()
        getAttribute(Attribute.MAX_HEALTH)?.baseValue = 20.0

        health = 20.0
        foodLevel = 20
        saturation = 3F
        exhaustion = 0F

        gameMode = GameMode.SURVIVAL
    }
}