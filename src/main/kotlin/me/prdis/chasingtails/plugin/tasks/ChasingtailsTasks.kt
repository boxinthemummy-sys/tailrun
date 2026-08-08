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

package me.prdis.chasingtails.plugin.tasks

import me.prdis.chasingtails.plugin.objects.ChasingtailsConsts.plugin
import me.prdis.chasingtails.plugin.objects.ChasingtailsConsts.server
import me.prdis.chasingtails.plugin.objects.ChasingtailsImpl.gamePlayers
import net.kyori.adventure.text.Component.text
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Sound
import kotlin.math.roundToInt

/**
 * @author Paradise Dev Team
 */

object ChasingtailsTasks {
    private val masterComponent = text("주인(")

    fun startTasks() {
        distancingTask()
        gameTick()
        heartbeatTask()
    }

    fun stopTasks() {
        server.scheduler.cancelTasks(plugin)
    }

    private fun gameTick() {
        server.scheduler.runTaskTimer(plugin, Runnable {
            gamePlayers.forEach {
                val player = it.player
                val master = it.master

                if (it.isTemporaryDeath) {
                    val value = (--it.tempDeathDuration).coerceAtLeast(-1).also { value ->
                        it.tempDeathDuration = value
                    }

                    if (value == 0) {
                        it.revive()
                    } else if (value != -1) {
                        it.deathTimerUpdate(value)

                        if (master == null) {
                            it.player.sendActionBar(
                                text("부활까지 ", NamedTextColor.GOLD).append(
                                        text(
                                            String.format("%.1f", value / 20.0),
                                            NamedTextColor.YELLOW
                                        )
                                    ).append(text("초 남았습니다!", NamedTextColor.GOLD))
                            )
                        }
                    }
                }

                if (master != null) {
                    val masterLocation = master.player.location
                    player.compassTarget = masterLocation

                    if (player.location.world != master.player.world) {
                        player.sendActionBar(
                            masterComponent.color(NamedTextColor.RED)
                                .append(text(master.player.name, master.color))
                                .append(text(")과 같은 차원에 존재하지 않습니다!", NamedTextColor.RED))
                        )
                    } else if (it.farAwayWithMaster) {
                        player.sendActionBar(
                            masterComponent.color(NamedTextColor.RED)
                                .append(text(master.player.name, master.color))
                                .append(text(")과의 거리가 너무 멉니다!", NamedTextColor.RED))
                        )
                    } else {
                        player.sendActionBar(
                            masterComponent.color(NamedTextColor.GOLD)
                                .append(text(master.player.name, master.color)).append(
                                    text(
                                        ")과의 거리: ", NamedTextColor.GOLD
                                    )
                                ).append(
                                    text(
                                        "${
                                            it.player.location.distance(masterLocation).roundToInt()
                                        }m", NamedTextColor.WHITE
                                    )
                                )
                        )
                    }
                }
            }
        }, 0, 1)
    }

    private fun heartbeatTask() {
        server.scheduler.runTaskTimer(plugin, Runnable {
            gamePlayers.filter { it.master == null }.forEach {
                val target = it.target.player
                val player = it.player

                if (player.location.world == target.location.world) {
                    val dist = player.location.distance(target.location)
                    if (dist <= 50.0) {
                        val volume = when (dist) {
                            in 0.0..10.0 -> 1.0F
                            in 10.0..20.0 -> 0.8F
                            in 20.0..30.0 -> 0.6F
                            in 30.0..40.0 -> 0.4F
                            in 40.0..50.0 -> 0.2F
                            else -> 0F // Impossible
                        }

                        target.sendActionBar(
                            text(
                                "[ ♥ ]", NamedTextColor.RED
                            ).decorate(TextDecoration.BOLD)
                        )

                        server.scheduler.runTaskLater(plugin, Runnable {
                            target.sendActionBar(text(""))
                        }, 4L)

                        target.playSound(
                            target.location, Sound.ENTITY_WARDEN_HEARTBEAT, volume, 1.0F
                        )
                    }
                }

            }
        }, 0, 20)
    }

    private fun distancingTask() {
        server.scheduler.runTaskTimer(plugin, Runnable {
            gamePlayers.forEach {
                val master = it.master?.player

                if (master != null && !it.isTemporaryDeath) { // 주인이 있고, 임시 사망 상태가 아닐 때
                    val player = it.player

                    if (player.world != master.world || player.location.distance(master.location) > 50) { // 주인과 다른 차원에 있거나 거리가 50칸 이상일 때
                        player.damage(1.0)
                        it.farAwayWithMaster = true
                    } else {
                        it.farAwayWithMaster = false
                    }
                }
            }
        }, 0, 10)
    }
}