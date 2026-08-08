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
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package me.prdis.chasingtails.plugin.events

import com.destroystokyo.paper.event.player.PlayerTeleportEndGatewayEvent
import me.prdis.chasingtails.plugin.config.ChasingtailsConfig
import me.prdis.chasingtails.plugin.objects.ChasingtailsConsts.plugin
import me.prdis.chasingtails.plugin.objects.ChasingtailsConsts.server
import me.prdis.chasingtails.plugin.objects.ChasingtailsImpl
import me.prdis.chasingtails.plugin.objects.ChasingtailsImpl.gamePlayer
import me.prdis.chasingtails.plugin.objects.ChasingtailsImpl.initEndSpawn
import me.prdis.chasingtails.plugin.objects.ChasingtailsImpl.isGameHalted
import me.prdis.chasingtails.plugin.objects.ChasingtailsImpl.lastLocation
import me.prdis.chasingtails.plugin.objects.ChasingtailsImpl.scoreboard
import me.prdis.chasingtails.plugin.tasks.ChasingtailsTasks
import net.kyori.adventure.text.Component.text
import net.kyori.adventure.text.Component.translatable
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.entity.EntityTargetLivingEntityEvent
import org.bukkit.event.player.*

/**
 * @author Paradise Dev Team
 */

object GameManageEvent : Listener {

    @EventHandler
    fun PlayerJoinEvent.onJoin() {
        player.noDamageTicks = 0
        player.maximumNoDamageTicks = 0

        joinMessage(
            translatable("multiplayer.player.joined", player.displayName().color(null)).color(NamedTextColor.YELLOW)
        )

        player.playerListName(text(player.name).color(NamedTextColor.WHITE))
        server.scheduler.runTaskLater(plugin, Runnable {
            if (isGameHalted) {
                val check = ChasingtailsImpl.allPlayerExists()

                if (!check) {
                    player.sendMessage(
                        text(
                            "기존 게임 플레이 인원이 전부 접속되지 않아 현재 게임이 일시정지 되어있습니다.\n모든 플레이어 접속 시 자동 재개됩니다.", NamedTextColor.GRAY
                        )
                    )
                } else {
                    ChasingtailsImpl.resumeGame()
                }
            }
        }, 4L)

        val configGamePlayerUUIDs = ChasingtailsConfig.getGamePlayerDatas().keys
        val isGamePlayer = ChasingtailsImpl.isRunning &&
            (player.uniqueId.toString() in configGamePlayerUUIDs ||
             ChasingtailsImpl.gamePlayers.any { it.uniqueId == player.uniqueId })

        if (!isGamePlayer) {
            player.gameMode = GameMode.SPECTATOR
            player.sendMessage(text("현재 게임이 진행중이어 관전 상태로 전환되었습니다!", NamedTextColor.GRAY))
        }
    }

    @EventHandler
    fun PlayerQuitEvent.onQuit() {
        player.lastLocation = player.location

        quitMessage(
            translatable("multiplayer.player.left", player.displayName().color(null)).color(NamedTextColor.YELLOW)
        )

        val gamePlayer = player.gamePlayer
        if (gamePlayer != null && ChasingtailsImpl.isRunning) {
            ChasingtailsConfig.saveGamePlayers()

            if (!ChasingtailsImpl.isGameHalted) {
                ChasingtailsImpl.isGameHalted = true

                server.onlinePlayers.forEach {
                    it.sendMessage(
                        text("! ", NamedTextColor.YELLOW).decorate(TextDecoration.BOLD)
                            .append(
                                text(
                                    "플레이를 진행하는 유저가 접속을 종료하여 재접속 이전까지 일시적으로 게임을 중단합니다.",
                                    NamedTextColor.YELLOW
                                ).decoration(
                                    TextDecoration.BOLD,
                                    false
                                )
                            )
                    )
                }

                ChasingtailsTasks.stopTasks()
            }
        }
    }

    @EventHandler
    fun PlayerMoveEvent.onMove() {
        val gamePlayer = player.gamePlayer
        if (gamePlayer?.isTemporaryDeath == true && hasExplicitlyChangedPosition()) {
            isCancelled = true
        }

        if (isGameHalted) isCancelled = true
    }

    @EventHandler
    fun PlayerTeleportEndGatewayEvent.onPlayerTeleportEndGateway() {
        isCancelled = true
    }

    @EventHandler
    fun PlayerChangedWorldEvent.onChangedWorld() {
        server.onlinePlayers.filter {
            scoreboard.getTeam(player.name)?.entries?.contains(it.name) == true
        }.forEach {
            it.teleportAsync(player.location)
        }
    }

    @EventHandler
    fun PlayerPortalEvent.onPlayerPortal() {
        if (from.world.environment == World.Environment.NORMAL && to.world.environment == World.Environment.THE_END && initEndSpawn == null) {
            initEndSpawn = to

            return
        }

        if (from.world.environment == World.Environment.NORMAL && to.world.environment == World.Environment.NETHER) {
            to = Location(to.world, from.blockX.toDouble(), to.blockY.toDouble(), from.blockZ.toDouble())
        } else if (from.world.environment == World.Environment.NETHER && to.world.environment == World.Environment.NORMAL) {
            to = Location(to.world, from.blockX.toDouble(), to.blockY.toDouble(), from.blockZ.toDouble())
        }
    }

    @EventHandler
    fun PlayerKickEvent.onKick() {
        if (cause == PlayerKickEvent.Cause.FLYING_PLAYER) isCancelled = true
    }

    @EventHandler
    fun EntityTargetLivingEntityEvent.onTarget() {
        if (target is Player) {
            val gamePlayer = (target as Player).gamePlayer
            if (gamePlayer?.isTemporaryDeath == true) {
                isCancelled = true
            }

            if (isGameHalted) isCancelled = true
        }
    }

    @EventHandler
    fun BlockPlaceEvent.onPlace() {
        val gamePlayer = player.gamePlayer
        if (gamePlayer?.isTemporaryDeath == true) isCancelled = true
        if (isGameHalted) isCancelled = true
    }

    @EventHandler
    fun BlockBreakEvent.onBreak() {
        val gamePlayer = player.gamePlayer
        if (gamePlayer?.isTemporaryDeath == true) isCancelled = true
        if (isGameHalted) isCancelled = true
    }
}
