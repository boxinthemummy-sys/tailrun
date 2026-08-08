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

import me.prdis.chasingtails.plugin.ChasingtailsPlugin
import org.incendo.cloud.execution.ExecutionCoordinator
import org.incendo.cloud.paper.PaperCommandManager
import org.incendo.cloud.paper.util.sender.PaperSimpleSenderMapper
import org.incendo.cloud.paper.util.sender.Source

object ChasingtailsConsts {
    val plugin = ChasingtailsPlugin.instance
    val server = plugin.server

    private val executionCoordinator = ExecutionCoordinator.simpleCoordinator<Source>()
    val commandManager = PaperCommandManager.builder(PaperSimpleSenderMapper.simpleSenderMapper())
        .executionCoordinator(executionCoordinator)
        .buildOnEnable(plugin)
}