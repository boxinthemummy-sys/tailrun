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

import me.prdis.chasingtails.plugin.objects.ChasingtailsConsts.server
import me.prdis.chasingtails.plugin.objects.ChasingtailsImpl.gamePlayers
import me.prdis.chasingtails.plugin.objects.ChasingtailsImpl.scoreboard
import net.kyori.adventure.text.Component.text
import net.kyori.adventure.text.ComponentLike
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import org.bukkit.Color
import org.bukkit.EntityEffect
import org.bukkit.Material
import org.bukkit.OfflinePlayer
import org.bukkit.attribute.Attribute
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Display
import org.bukkit.entity.Mob
import org.bukkit.entity.Player
import org.bukkit.entity.TextDisplay
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.LeatherArmorMeta
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType

/**
 * @author Paradise Dev Team
 */

class GamePlayer(val player: Player) {
    val uniqueId = player.uniqueId

    val offlinePlayer: OfflinePlayer
        get() = server.getOfflinePlayer(player.uniqueId)

    var target: GamePlayer = this
        get() = master?.target ?: field
    var master: GamePlayer? = null

    var farAwayWithMaster = false

    val isTemporaryDeath
        get() = tempDeathDuration > 0

    var deathTimer: TextDisplay? = null

    var lastGameDamageData: Pair<GamePlayer, Long>? = null
    var tempDeathDuration = -1

    val team
        get() = scoreboard.getPlayerTeam(offlinePlayer)
    val color: TextColor
        get() {
            val team = team ?: return NamedTextColor.WHITE
            if (!team.hasColor()) return NamedTextColor.WHITE
            return team.color()
        }
    val koreanColor: String
        get() = ChasingtailsImpl.mappedColors[color] ?: "하얀색"

    fun revive() {
        deathTimer?.remove()
        deathTimer = null

        player.playEffect(EntityEffect.PROTECTED_FROM_DEATH)
        player.addPotionEffects(
            listOf(
                PotionEffect(PotionEffectType.RESISTANCE, 20 * 5, 1, false, false, false),
                PotionEffect(PotionEffectType.REGENERATION, 20 * 45, 1, false, false, false),
                PotionEffect(PotionEffectType.FIRE_RESISTANCE, 20 * 40, 0, false, false, false)
            )
        )

        sendMessage(text("부활하셨습니다!"))

        val masterLocation = master?.player?.location

        if (masterLocation != null) {
            initializeAsSlave()
            player.teleport(masterLocation)
        }
    }

    fun deathTimerUpdate(value: Int) {
        val deathTimer = deathTimer ?: player.world.spawn(player.location.add(0.0, 2.3, 0.0), TextDisplay::class.java) {
            it.isPersistent = true
            it.alignment = TextDisplay.TextAlignment.CENTER
            it.billboard = Display.Billboard.CENTER
            player.addPassenger(it)
        }.also { deathTimer = it }

        deathTimer.text(
            text("부활까지 ${String.format("%.1f", value / 20.0)}초", NamedTextColor.YELLOW)
        )
    }

    fun enslave(slave: GamePlayer, inherited: Boolean = false) {
        target = slave.target
        slave.master = this

        equipSlaveArmor(slave.player)

        if (!inherited) {
            gamePlayers.forEach {
                if (it.master == slave) enslave(it, true)
            }

            slave.initializeAsSlave()

            slave.player.playEffect(EntityEffect.PROTECTED_FROM_DEATH)
            slave.player.sendMessage(
                text("처치당하셨습니다! 앞으로 ").append(text(player.name, color))
                    .append(text("님의 꼬리가 됩니다.", NamedTextColor.WHITE))
            )

            slave.player.inventory.addItem(ItemStack(Material.COMPASS))

            sendMessage(
                text("목표를 처치하는데 성공하셨습니다! ").append(text(slave.player.name, slave.color)).append(text("님이 꼬리가 됩니다."))
            )
        }

        scoreboard.getPlayerTeam(slave.offlinePlayer)?.unregister()
        scoreboard.getPlayerTeam(offlinePlayer)?.addEntry(slave.player.name)

        val slaves = gamePlayers.count { it.master == this }
        player.getAttribute(Attribute.MAX_HEALTH)?.baseValue = (20 - (slaves * 2)).coerceAtLeast(1).toDouble()

        slave.deathTimer?.remove()
    }

    fun sendMessage(message: ComponentLike) = player.sendMessage(message)
    fun alert(message: String) = sendMessage(text(message, NamedTextColor.RED))

    fun temporaryDeath(duration: Int) {
        player.apply {
            health = getAttribute(Attribute.MAX_HEALTH)?.value ?: 20.0
            foodLevel = 20
            saturation = 3F
            exhaustion = 0F

            world.getNearbyLivingEntities(location, 30.0).forEach {
                if (it is Mob) {
                    if (it.target?.uniqueId == uniqueId) it.target = null
                }
            }
        }

        tempDeathDuration = duration
        farAwayWithMaster = false
    }

    fun equipSlaveArmor(slave: Player) {
        slave.inventory.apply {
            helmet = ItemStack(Material.LEATHER_HELMET).applyColorToArmor()
            chestplate = ItemStack(Material.LEATHER_CHESTPLATE).applyColorToArmor()
            leggings = ItemStack(Material.LEATHER_LEGGINGS).applyColorToArmor()
            boots = ItemStack(Material.LEATHER_BOOTS).applyColorToArmor()
        }
    }

    private fun initializeAsSlave() {
        player.apply {
            health = 10.0
            getAttribute(Attribute.MAX_HEALTH)?.baseValue = 10.0

            foodLevel = 20
            saturation = 3F
            exhaustion = 0F
        }
    }

    private fun ItemStack.applyColorToArmor() = apply {
        editMeta {
            if (it is LeatherArmorMeta) it.setColor(Color.fromRGB(color.value()))
            it.isUnbreakable = true
            it.addItemFlags(*ItemFlag.entries.toTypedArray())
            it.addEnchant(Enchantment.BINDING_CURSE, 1, true)
        }
    }

    override fun equals(other: Any?) = other is GamePlayer && other.uniqueId == uniqueId
    override fun hashCode() = uniqueId.hashCode()
}
