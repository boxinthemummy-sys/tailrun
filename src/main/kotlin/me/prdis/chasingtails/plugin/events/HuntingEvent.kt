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

package me.prdis.chasingtails.plugin.events

import me.prdis.chasingtails.plugin.enums.DamageResult
import me.prdis.chasingtails.plugin.objects.ChasingtailsImpl.gamePlayers
import me.prdis.chasingtails.plugin.objects.ChasingtailsImpl.isGameHalted
import me.prdis.chasingtails.plugin.objects.ChasingtailsImpl.stopGame
import me.prdis.chasingtails.plugin.objects.ChasingtailsConsts.plugin
import me.prdis.chasingtails.plugin.objects.ChasingtailsConsts.server
import me.prdis.chasingtails.plugin.objects.ChasingtailsImpl.gamePlayer
import me.prdis.chasingtails.plugin.objects.ChasingtailsImpl.initEndSpawn
import me.prdis.chasingtails.plugin.objects.ChasingtailsImpl.lastLocation
import me.prdis.chasingtails.plugin.objects.GamePlayer
import net.kyori.adventure.text.Component.text
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.title.Title
import org.bukkit.*
import org.bukkit.entity.*
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import java.time.Duration

/**
 * @author Paradise Dev Team
 */

object HuntingEvent : Listener {
    @EventHandler
    fun PlayerInteractEvent.onInteract() {
        val gamePlayer = player.gamePlayer

        when {
            isGameHalted -> isCancelled = true
            action.isRightClick && item?.type == Material.DIAMOND && gamePlayer != null && gamePlayer.master == null -> {
                handleDiamondInteraction(gamePlayer)
            }

            item?.type == Material.END_CRYSTAL && player.world.environment == World.Environment.THE_END -> {
                isCancelled = true
            }

            item?.type == Material.COMPASS && gamePlayer?.master != null -> isCancelled = true
            gamePlayer?.isTemporaryDeath == true -> isCancelled = true
        }
    }

    private fun PlayerInteractEvent.handleDiamondInteraction(gamePlayer: GamePlayer) {
        val target = gamePlayer.target.offlinePlayer
        val location = target.location ?: target.lastLocation

        if (location != null) {
            if (player.location.world == location.world) {
                player.launchParticles(location)
                player.playSound(player.location, Sound.BLOCK_NOTE_BLOCK_BIT, 1F, 1F)
                item?.amount--
            } else {
                player.sendActionBar(text(" -- 추적 오류: 플레이어와 같은 월드에 있지 않습니다. -- ", NamedTextColor.RED))
            }
        } else {
            player.sendActionBar(text(" -- 추적 오류: 플레이어의 위치를 찾을 수 없습니다. -- ", NamedTextColor.RED))
        }
    }

    private fun Player.launchParticles(location: Location) {
        val eye = eyeLocation
        val offset = location.add(0.0, 1.5, 0.0).toVector().subtract(eye.toVector()).normalize()

        repeat(10) {
            server.scheduler.runTaskLater(plugin, Runnable {
                repeat(15) { distance ->
                    world.spawnParticle(
                        Particle.SOUL_FIRE_FLAME,
                        eye.clone().add(offset.clone().multiply(distance * 0.5)),
                        1,
                        0.0,
                        0.0,
                        0.0,
                        0.0
                    )
                }
            }, 4L)
        }

//        plugin.launch {
//            repeat(10) {
//                repeat(15) { distance ->
//                    world.spawnParticle(
//                        Particle.SOUL_FIRE_FLAME,
//                        eye.clone().add(offset.clone().multiply(distance * 0.5)),
//                        1,
//                        0.0,
//                        0.0,
//                        0.0,
//                        0.0
//                    )
//                }
//                delay(200) // 200ms in tick? = 4 ticks / Thanks!
//            }
//        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    fun EntityDamageByEntityEvent.onDamageByEntity() {
        val gamePlayer = (entity as? Player)?.gamePlayer
        val attacker = when (val source = damager) {
            is Player -> source
            is Projectile -> source.shooter as? Player
            is Tameable -> source.owner as? Player
            is TNTPrimed -> source.source as? Player
            else -> null
        }?.gamePlayer ?: return

        if (attacker == gamePlayer) return

        if (isGameHalted) isCancelled = true
        else {
            if (gamePlayer != null) {
                val result = processDamage(gamePlayer, attacker)

                isCancelled = result == DamageResult.DISALLOW
                if (result == DamageResult.ALLOW_ONLY_KNOCKBACK) damage = 0.0
            }
        }
    }

    @EventHandler
    fun EntityDamageEvent.onDamage() {
        val gamePlayer = (entity as? Player)?.gamePlayer

        if (gamePlayer?.isTemporaryDeath == true && cause != EntityDamageEvent.DamageCause.ENTITY_ATTACK) {
            isCancelled = true
        }

        if (isGameHalted) isCancelled = true
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    fun PlayerDeathEvent.onDeath() {
        val gamePlayer = entity.gamePlayer

        isCancelled = true

        player.world.entities.filterIsInstance<Monster>().forEach { monster ->
            if (monster.target == player) monster.target = null
        }

        if (gamePlayer != null) {
            val master = gamePlayer.master

            val killer = player.killer?.gamePlayer ?: run {
                gamePlayer.lastGameDamageData?.takeIf { (_, time) ->
                    System.currentTimeMillis() - time < (1000 * 60)
                }?.first
            }

            val killerMaster = (killer?.master ?: killer).takeIf { gamePlayer != it }

            if (gamePlayer == killerMaster?.target && master == null) {
                if (gamePlayer.isTemporaryDeath) gamePlayer.tempDeathDuration = -1

                if (gamePlayers.filter { it.master == null }.size == 2) {
                    server.showTitle(
                        Title.title(
                            text(""), text(killerMaster.player.name, killerMaster.color).append(
                                text(
                                    "님이 우승하셨습니다!", NamedTextColor.WHITE
                                )
                            ), Title.Times.times(Duration.ofSeconds(0), Duration.ofSeconds(5), Duration.ofSeconds(0))
                        )
                    )

                    server.broadcast(text("게임을 종료합니다.", NamedTextColor.GREEN))
                    stopGame()

                    return
                }

                killerMaster.enslave(gamePlayer)
            } else {
                if (master != null) {
                    gamePlayer.temporaryDeath(20 * 30)

                    gamePlayers.forEach {
                        if (it.master == gamePlayer.master || it == gamePlayer.master) {
                            it.sendMessage(
                                text(
                                    "${gamePlayer.player.name}이(가) 사망했습니다. (30초 후 리스폰)", NamedTextColor.RED
                                )
                            )
                        }
                    }
                } else {
                    if (player.world.environment == World.Environment.THE_END) {
                        initEndSpawn?.let { spawn -> player.teleportAsync(spawn) }
                    }

                    gamePlayer.temporaryDeath(120 * 20)
                    gamePlayer.sendMessage(
                        text("사망하셨습니다! ", NamedTextColor.RED).append(
                            text(
                                "현 시점으로부터 2분 뒤에 부활합니다.", NamedTextColor.WHITE
                            )
                        )
                    )
                }
            }
        }

        player.addPotionEffect(PotionEffect(PotionEffectType.BLINDNESS, 40, 0, false, false, false))
    }

    private const val DAMAGEABLE_ALERT = "타겟 플레이어와 그 플레이어의 꼬리, 그리고 자신의 꼬리 이외의 플레이어는 공격할 수 없습니다!"

    private fun processDamage(damaged: GamePlayer, damager: GamePlayer): DamageResult {
        if (damager.isTemporaryDeath) { // 공격자가 임시 사망 상태
            damager.alert("리스폰 대기 상태에서는 공격할 수 없습니다!")
            return DamageResult.DISALLOW
        } else {
            if (damaged.isTemporaryDeath) { // 공격받은 이가 임시 사망 상태
                return if (damager.target == damaged) { // 헌터 또는 그의 꼬리가 대상을 때릴 때
                    DamageResult.ALLOW
                } else if (damaged.target == damager.master) { // 대상이 헌터의 꼬리를 떄릴 때
                    DamageResult.ALLOW_ONLY_KNOCKBACK
                } else {
                    damager.alert("해당 플레이어는 리스폰 대기 상태입니다!")

                    DamageResult.DISALLOW
                }
            } else {
                if (damaged == damager.target) { // 공격받은 이가 공격자 (+꼬리)의 타겟일 경우
                    damaged.lastGameDamageData = damager to System.currentTimeMillis()

                    return DamageResult.ALLOW
                } else if (damaged.master != null) { // 공격받은 이가 꼬리일 경우
                    return DamageResult.ALLOW
                } else if (damaged.target == (damager.master ?: damager)) { // 공격받은 이의 타겟이 공격자일 경우 (쫓기는 상황)
                    return DamageResult.ALLOW_ONLY_KNOCKBACK
                } else if (damaged == damager.master) { // 공격한 이가 꼬리이고 공격자가 주인일 경우
                    damager.alert("당신의 주인은 공격할 수 없습니다! 혹시 하극상을 시도했나요?")

                    return DamageResult.DISALLOW
                } else {
                    damager.alert(DAMAGEABLE_ALERT)
                    return DamageResult.DISALLOW
                }
            }
        }
    }
}
