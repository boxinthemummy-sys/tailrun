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

package me.prdis.chasingtails.plugin

import me.prdis.chasingtails.plugin.commands.ChasingtailsCommand.registerCommands
import me.prdis.chasingtails.plugin.config.ChasingtailsConfig
import me.prdis.chasingtails.plugin.config.GamePlayerData
import me.prdis.chasingtails.plugin.objects.ChasingtailsImpl.isRunning
import me.prdis.chasingtails.plugin.objects.ChasingtailsImpl.startGame
import org.bukkit.configuration.serialization.ConfigurationSerialization
import org.bukkit.plugin.java.JavaPlugin

/**
 * @author Paradise Dev Team
 */

class ChasingtailsPlugin : JavaPlugin() {
    companion object {
        lateinit var instance: ChasingtailsPlugin
            private set
    }

    override fun onEnable() {
        instance = this

        ConfigurationSerialization.registerClass(GamePlayerData::class.java)

        isRunning = config.getBoolean("isRunning")

        if (isRunning) {
            startGame()
            logger.warning("게임이 현재 진행 중인 상황입니다. 종료를 원하시는 게 아닌 경우 \"/chasingtails start\" 명령어를 다시 입력하실 필요가 없습니다.")
            logger.warning("게임 자동 재개 대기중입니다.")
        }

        registerCommands()
    }

    override fun onDisable() {
        if (isRunning) {
            ChasingtailsConfig.saveGamePlayers()
        }
    }
}
