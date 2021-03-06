/*
 * Copyright 2018-2021 Guthix
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.guthix.oldscape.server.world.entity.interest

import io.guthix.oldscape.dim.*
import io.guthix.oldscape.server.net.game.ZoneOutGameEvent
import io.guthix.oldscape.server.net.game.out.*
import io.guthix.oldscape.server.world.World
import io.guthix.oldscape.server.world.entity.Loc
import io.guthix.oldscape.server.world.entity.Obj
import io.guthix.oldscape.server.world.entity.Player
import io.guthix.oldscape.server.world.entity.Projectile
import io.guthix.oldscape.server.world.map.Tile
import io.guthix.oldscape.server.world.map.Zone
import io.netty.channel.ChannelFuture

internal class SceneManager {
    lateinit var middleZone: Zone

    val baseX: ZoneUnit get() = middleZone.x - RANGE

    val baseY: ZoneUnit get() = middleZone.y - RANGE

    val zones: Array<Array<Zone?>> = Array(SIZE.value) {
        arrayOfNulls(SIZE.value)
    }

    val changes: Array<Array<MutableList<ZoneOutGameEvent>>> = Array(SIZE.value) {
        Array(SIZE.value) {
            mutableListOf()
        }
    }

    fun reloadRequired(curZone: Zone): Boolean = abs(middleZone.x - curZone.x) > UPDATE_RANGE ||
        abs(middleZone.y - curZone.y) > UPDATE_RANGE

    fun checkReload(curZone: Zone, map: World, xteas: Map<Int, IntArray>, player: Player) {
        if (reloadRequired(curZone)) {
            val oldZone = middleZone
            middleZone = curZone
            val iXteas = getInterestedXteas(xteas)
            player.ctx.write(RebuildNormalPacket(iXteas, curZone.x, curZone.y))
            unsubscribeZones(player)
            subscribeZones(oldZone, player, map)
        }
    }

    fun getInterestedXteas(xteas: Map<Int, IntArray>): List<IntArray> {
        val interestedXteas = mutableListOf<IntArray>()
        val onTutorialIsland = onTutorialIsland(middleZone.x.inMapsquares, middleZone.y.inMapsquares)
        for (msX in middleZone.x.startMapInterest..middleZone.x.endMapInterest) {
            for (msY in middleZone.y.startMapInterest..middleZone.y.endMapInterest) {
                if (!onTutorialIsland ||
                    msY.value != 49 && msY.value != 149 && msY.value != 147 && msX.value != 50 &&
                    (msX.value != 49 || msY.value != 47)
                ) {
                    val id = (msX.value shl 8) or msY.value
                    val xtea = xteas[id] ?: error("Could not find XTEA for id $id.")
                    interestedXteas.add(xtea)
                }

            }
        }
        return interestedXteas
    }


    private fun subscribeZones(oldZone: Zone, player: Player, world: World) {
        val prevPacketCache = changes.copyOf()
        changes.forEach { it.forEach(MutableList<ZoneOutGameEvent>::clear) }
        ((middleZone.x - RANGE)..(middleZone.x + RANGE)).forEachIndexed { i, zoneX ->
            ((middleZone.y - RANGE)..(middleZone.y + RANGE)).forEachIndexed { j, zoneY ->
                val zone = world.getZone(middleZone.floor, zoneX, zoneY)
                zones[i][j] = zone
                zone?.let {
                    zone.playersLoaded.add(player)
                    val prevLocalX = (zone.x - (oldZone.x - RANGE))
                    val prevLocalY = (zone.y - (oldZone.y - RANGE))
                    if (middleZone.floor == oldZone.floor && prevLocalX in REL_RANGE && prevLocalY in REL_RANGE) {
                        changes[i][j].addAll(prevPacketCache[prevLocalX.value][prevLocalY.value]) // move packet cache
                    } else {
                        addInterestPackets(zone)
                    }
                }
            }
        }
    }

    private fun unsubscribeZones(player: Player) {
        zones.forEachIndexed { _, arrayOfZones ->
            arrayOfZones.forEachIndexed { _, zone ->
                zone?.playersLoaded?.remove(player)
            }
        }
    }

    private fun addInterestPackets(zone: Zone) {
        zone.groundObjects.forEach { (tile, objMap) ->
            objMap.values.forEach { objList ->
                objList.forEach { obj ->
                    addObject(tile, obj)
                }
            }
        }
        zone.addedLocs.forEach { (_, loc) ->
            addChangeLoc(loc)
        }
        zone.deletedLocs.forEach { (_, loc) ->
            delLoc(loc)
        }
    }

    internal fun addObject(tile: Tile, obj: Obj) {
        changes[(tile.x.inZones - baseX).value][(tile.y.inZones - baseY).value].add(
            ObjAddPacket(obj.id, obj.quantity, tile.x.relativeZone, tile.y.relativeZone)
        )
    }

    internal fun removeObject(tile: Tile, obj: Obj) {
        changes[(tile.x.inZones - baseX).value][(tile.y.inZones - baseY).value].add(
            ObjDelPacket(obj.id, tile.x.relativeZone, tile.y.relativeZone)
        )
    }

    internal fun addChangeLoc(loc: Loc) {
        changes[(loc.pos.x.inZones - baseX).value][(loc.pos.y.inZones - baseY).value].add(
            LocAddChangePacket(
                loc.id, loc.type, loc.orientation, loc.pos.x.relativeZone, loc.pos.y.relativeZone
            )
        )
    }

    internal fun delLoc(loc: Loc) {
        changes[(loc.pos.x.inZones - baseX).value][(loc.pos.y.inZones - baseY).value].add(
            LocDelPacket(loc.type, loc.orientation, loc.pos.x.relativeZone, loc.pos.y.relativeZone)
        )
    }

    internal fun addProjectile(projectile: Projectile) {
        changes[(projectile.start.x.inZones - baseX).value][(projectile.start.y.inZones - baseY).value].add(
            MapProjanimPacket(
                projectile.id,
                projectile.startHeight,
                projectile.endHeight,
                if (projectile.target is Player) projectile.target.index + 32769 else projectile.target.index + 1,
                projectile.angle,
                projectile.steepness,
                projectile.delay,
                projectile.lifetimeClientTicks,
                projectile.target.pos.x - projectile.start.x,
                projectile.target.pos.y - projectile.start.y,
                projectile.start.x.relativeZone,
                projectile.start.y.relativeZone
            )
        )
    }

    internal fun clear(player: Player) {
        changes.forEachIndexed { x, yPacketList ->
            yPacketList.forEachIndexed { y, _ ->
                player.ctx.write(UpdateZoneFullFollowsPacket(x.zones.inTiles, y.zones.inTiles))
            }
        }
    }

    internal fun initialize(world: World, player: Player) {
        middleZone = world.getZone(player.pos) ?: error("Could not find $player on the map.")
        ((middleZone.x - RANGE)..(middleZone.x + RANGE)).forEachIndexed { i, zoneX ->
            ((middleZone.y - RANGE)..(middleZone.y + RANGE)).forEachIndexed { j, zoneY ->
                val zone = world.getZone(middleZone.floor, zoneX, zoneY)
                zones[i][j] = zone
                zone?.let {
                    zone.playersLoaded.add(player)
                    addInterestPackets(zone)
                }
            }
        }
    }

    internal fun synchronize(world: World, xteas: Map<Int, IntArray>, player: Player): List<ChannelFuture> {
        val futures = mutableListOf<ChannelFuture>()
        val pZone = world.getZone(player.pos) ?: error("Could not find $player on the map.")
        checkReload(pZone, world, xteas, player)
        changes.forEachIndexed { x, yPacketList ->
            yPacketList.forEachIndexed { y, packetList ->
                if (packetList.size == 1) {
                    futures.add(player.ctx.write(UpdateZonePartialFollowsPacket(x.zones.inTiles, y.zones.inTiles)))
                    futures.add(player.ctx.write(packetList.first()))
                } else if (packetList.size > 1) {
                    futures.add(
                        player.ctx.write(
                            UpdateZonePartialEnclosedPacket(x.zones.inTiles, y.zones.inTiles, packetList.toList())
                        )
                    )
                }
            }
        }
        return futures
    }

    internal fun postProcess(): Unit = changes.forEach { it.forEach(MutableList<ZoneOutGameEvent>::clear) }

    companion object {
        val SIZE: ZoneUnit = 13.zones

        val REL_RANGE: ZoneUnitRange = (0.zones until SIZE)

        val RANGE: ZoneUnit = SIZE / 2.zones

        val UPDATE_RANGE: ZoneUnit = RANGE - PlayerManager.RANGE.inZones

        private val ZoneUnit.startMapInterest get() = (this - RANGE).inMapsquares

        private val ZoneUnit.endMapInterest get() = (this + RANGE).inMapsquares

        private fun onTutorialIsland(x: MapsquareUnit, y: MapsquareUnit) =
            ((x.value == 48 || x.value == 49) && y.value == 48)
                || (x.value == 48 && y.value == 148)
    }
}



