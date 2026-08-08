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

package me.prdis.chasingtails.plugin.config

import me.prdis.chasingtails.plugin.objects.ChasingtailsConsts.plugin
import me.prdis.chasingtails.plugin.objects.ChasingtailsImpl.gamePlayers

/**
 * @author Paradise Dev Team
 */

object ChasingtailsConfig {
    fun getMaxPlayers(): Int {
        val maxPlayers = plugin.config.getInt("maxPlayers")
        return if (maxPlayers == 0 || maxPlayers > 8) {
            8
        } else maxPlayers
    }

    fun setMaxPlayers(maxPlayers: Int) {
        plugin.config.set("maxPlayers", maxPlayers)
        plugin.saveConfig()
    }

    fun getGamePlayerDatas(): MutableMap<String, GamePlayerData> {
        val ctSection = plugin.config.getConfigurationSection("chasingtails")
        val loadedGamePlayerDatas = mutableMapOf<String, GamePlayerData>()

        ctSection?.getKeys(false)?.forEach { key ->
            val data = ctSection.get(key)

            if (data != null && data as? GamePlayerData != null) {
                loadedGamePlayerDatas[key] = data
            }
        }

        return loadedGamePlayerDatas
    }

    fun saveGamePlayers() {
        gamePlayers.forEach { gamePlayer ->
            val player = gamePlayer.player
            val team = gamePlayer.team

            if (team != null) {
                plugin.config.set(
                    "chasingtails.${player.uniqueId}",

                    GamePlayerData(
                        team.name,
                        team.color().value(),
                        gamePlayer.target.uniqueId.also { println(it) }.toString(),
                        gamePlayer.master?.uniqueId?.toString() ?: "",
                        gamePlayer.deathTimer?.uniqueId?.toString() ?: "",
                        gamePlayer.tempDeathDuration
                    )
                )
            }
        }

        plugin.saveConfig()
    }

    fun resetGameStatus() {
        plugin.config.set("chasingtails", null)
        plugin.saveConfig()
    }
}