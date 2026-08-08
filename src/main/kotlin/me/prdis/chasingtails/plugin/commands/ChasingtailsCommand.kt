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

package me.prdis.chasingtails.plugin.commands

import me.prdis.chasingtails.plugin.config.ChasingtailsConfig
import me.prdis.chasingtails.plugin.objects.ChasingtailsConsts.commandManager
import me.prdis.chasingtails.plugin.objects.ChasingtailsConsts.server
import me.prdis.chasingtails.plugin.objects.ChasingtailsImpl.isRunning
import me.prdis.chasingtails.plugin.objects.ChasingtailsImpl.startGame
import me.prdis.chasingtails.plugin.objects.ChasingtailsImpl.stopGame
import net.kyori.adventure.text.Component.text
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.GameMode
import org.incendo.cloud.parser.standard.IntegerParser
import org.incendo.cloud.permission.PredicatePermission

/**
 * @author Paradise Dev Team
 */

object ChasingtailsCommand {
    fun registerCommands() {
        registerChasingTails()
    }

    private fun registerChasingTails() {
        val ct = commandManager.commandBuilder("chasingtails", { "A chasing tails command." }, "ct")
            .permission(PredicatePermission.of { ctx -> ctx.source().isOp })

        commandManager.command(ct.handler { ctx ->
            val sender = ctx.sender().source()
            sender.sendMessage(
                text(
                    """
                    꼬리잡기 (chasing-tails) 플러그인
                    by Paradise Dev Team
                    
                    도움말
                    - 게임 시작: /chasingtails start
                    - 게임 종료: /chasingtails stop

                    ※ /ct 로 축약 사용 가능합니다.
                """.trimIndent()
                )
            )
        })

        commandManager.command(ct.literal("start").handler { ctx ->
            val sender = ctx.sender().source()
            val maxPlayers = ChasingtailsConfig.getMaxPlayers()
            val onlinePlayers = server.onlinePlayers.filter { it.gameMode != GameMode.SPECTATOR }

            if (onlinePlayers.size !in 3..maxPlayers) {
                // Don't be dumb. The condition is correct.
                sender.sendMessage(text("서버 내 관전 중이지 않은 플레이어의 수가 3 ~ ${maxPlayers}명 이어야 합니다.", NamedTextColor.RED))
                return@handler
            }

            if (!isRunning) {
                server.broadcast(text("\n".repeat(100)))

                startGame()
                server.broadcast(text("게임을 시작합니다.", NamedTextColor.GREEN))
            } else {
                sender.sendMessage(text("이미 게임이 진행중입니다.", NamedTextColor.RED))
            }
        })

        commandManager.command(ct.literal("stop").handler { ctx ->
            val sender = ctx.sender().source()

            if (isRunning) {
                stopGame()
                sender.sendMessage(text("게임을 종료합니다.", NamedTextColor.GREEN))
            } else {
                sender.sendMessage(text("게임이 진행중이지 않습니다.", NamedTextColor.RED))
            }
        })

        // Many of you have asked to have a maxPlayers configuration option, and finally it's here.
        val configBuilder = ct.literal("config")
        commandManager.command(configBuilder.handler { ctx ->
            val sender = ctx.sender().source()
            val maxPlayers = ChasingtailsConfig.getMaxPlayers()

            sender.sendMessage(
                text(
                    """
                    꼬리잡기 플러그인 설정

                    현재 최대 플레이어 수: $maxPlayers
                    - 최대 플레이어 수 변경: /chasingtails config maxPlayers <3 ~ 8 사이의 숫자>
                """.trimIndent()
                )
            )
        })

        commandManager.command(
            configBuilder.literal("maxPlayers").required("maxPlayers", IntegerParser.integerParser(3, 8))
                .handler { ctx ->
                    val sender = ctx.sender().source()
                    val maxPlayers = ctx.get<Int>("maxPlayers")

                    ChasingtailsConfig.setMaxPlayers(maxPlayers)
                    sender.sendMessage(text("최대 플레이어 수를 $maxPlayers(으)로 설정했습니다.", NamedTextColor.GREEN))
                })
    }
}