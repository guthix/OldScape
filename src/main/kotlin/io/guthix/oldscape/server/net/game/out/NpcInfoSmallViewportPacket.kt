/**
 * This file is part of Guthix OldScape.
 *
 * Guthix OldScape is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Guthix OldScape is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Foobar. If not, see <https://www.gnu.org/licenses/>.
 */
package io.guthix.oldscape.server.net.game.out

import io.guthix.buffer.BitBuf
import io.guthix.buffer.toBitMode
import io.guthix.oldscape.server.dimensions.TileUnit
import io.guthix.oldscape.server.dimensions.tiles
import io.guthix.oldscape.server.net.game.OutGameEvent
import io.guthix.oldscape.server.net.game.VarShortSize
import io.guthix.oldscape.server.world.NpcList
import io.guthix.oldscape.server.world.entity.CharacterVisual
import io.guthix.oldscape.server.world.entity.Npc
import io.guthix.oldscape.server.world.entity.NpcVisual
import io.guthix.oldscape.server.world.entity.Player
import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext

class NpcInfoSmallViewportPacket(
    private val player: Player,
    private val npcs: NpcList
) : OutGameEvent, CharacterInfoPacket() {
    override val opcode = 7

    override val size = VarShortSize

    override fun encode(ctx: ChannelHandlerContext): ByteBuf {
        val buf = ctx.alloc().buffer()
        val bitBuf = buf.toBitMode()
        localNpcUpdate(bitBuf)
        externalNpcUpdate(bitBuf)
        val byteBuf = bitBuf.toByteMode()
        for (npc in player.npcManager.localNpcs) {
            if (npc.visual.updateFlags.isNotEmpty()) {
                updateLocalNpcVisual(npc, byteBuf)
            }
        }
        return buf
    }

    fun localNpcUpdate(buf: BitBuf): BitBuf {
        buf.writeBits(value = player.npcManager.localNpcs.size, amount = 8)
        val removals = mutableListOf<Npc>()
        for (npc in player.npcManager.localNpcs) {
            when {
                npc.visual.updateFlags.isNotEmpty() -> {
                    buf.writeBoolean(true)
                    buf.writeBits(value = 0, amount = 2)
                }
                npc.visual.movementType == CharacterVisual.MovementUpdateType.WALK -> {
                    buf.writeBoolean(true)
                    buf.writeBits(value = 1, amount = 2)
                    buf.writeBits(value = getDirectionWalk(npc), amount = 3)
                    buf.writeBoolean(npc.visual.updateFlags.isNotEmpty())
                }
                npc.visual.movementType == CharacterVisual.MovementUpdateType.RUN -> {
                    buf.writeBoolean(true)
                    buf.writeBits(value = 2, amount = 2)
                    buf.writeBits(value = getDirectionWalk(npc), amount = 3)
                    buf.writeBits(value = getDirectionWalk(npc), amount = 3) //TODO Needs to send second step
                    buf.writeBoolean(npc.visual.updateFlags.isNotEmpty())
                }
                npc.index == -1 || npc.visual.movementType == CharacterVisual.MovementUpdateType.TELEPORT
                    || !player.pos.isInterestedIn(npc.pos) -> {
                    buf.writeBoolean(true)
                    buf.writeBits(value = 3, amount = 2)
                    removals.add(npc)
                }
                else -> buf.writeBoolean(false)
            }
        }
        player.npcManager.localNpcs.removeAll(removals) //TODO make this more efficient
        return buf
    }

    private fun needsAdd(npc: Npc) = player.pos.isInterestedIn(npc.pos) && !player.npcManager.localNpcs.contains(npc)

    fun getRespectiveLocation(npcTile: TileUnit, playerTile: TileUnit): TileUnit {
        var loc = npcTile - playerTile
        if (loc < 0.tiles) {
            loc += INTEREST_SIZE
        }
        return loc
    }

    fun externalNpcUpdate(buf: BitBuf): BitBuf {
        var npcsAdded = 0
        for (npc in npcs) { // TODO optimize and use surrounding npcs
            if (npcsAdded > 16) break
            if (needsAdd(npc)) {
                buf.writeBits(value = npc.index, amount = 15)
                buf.writeBits(value = npc.orientation, amount = 3) //TODO fix rotation
                buf.writeBits(value = getRespectiveLocation(npc.pos.x, player.pos.x).value, amount = 5)
                buf.writeBits(value = getRespectiveLocation(npc.pos.y, player.pos.y).value, amount = 5)
                buf.writeBoolean(false) // Is teleport
                buf.writeBits(value = npc.id, amount = 14)
                buf.writeBoolean(npc.visual.updateFlags.isNotEmpty())
                player.npcManager.localNpcs.add(npc)
                npcsAdded++
            }
        }
        return buf
    }

    private fun getDirectionWalk(localNpc: Npc): Int {
        val dx = localNpc.pos.x - localNpc.lastPos.x
        val dy = localNpc.pos.y - localNpc.lastPos.y
        return getDirectionType(dx, dy)
    }

    private fun getDirectionType(dx: TileUnit, dy: TileUnit) = movementOpcodes[2 - dy.value][dx.value + 2]

    private fun updateLocalNpcVisual(npc: Npc, maskBuf: ByteBuf) {
        var mask = 0
        npc.visual.updateFlags.forEach { update ->
            mask = mask or update.mask
        }
        maskBuf.writeByte(mask)
        npc.visual.updateFlags.forEach { updateType ->
            if (npc.visual.updateFlags.contains(updateType)) {
                updateType.encode(maskBuf, npc.visual)
            }
        }
    }

    class UpdateType(
        priority: Int,
        mask: Int,
        val encode: ByteBuf.(im: NpcVisual) -> Unit
    ) : CharacterVisual.UpdateType(priority, mask)

    companion object {
        private val movementOpcodes = arrayOf(
            intArrayOf(0, 1, 2),
            intArrayOf(3, -1, 4),
            intArrayOf(5, 6, 7)
        )

        val graphic = UpdateType(0, 0x4) { player ->
            //TODO
        }

        val orientation = UpdateType(0, 0x1) { player ->
            //TODO
        }

        val hit = UpdateType(0, 0x40) { player ->
            //TODO
        }

        val transform = UpdateType(0, 0x8) { player ->
            //TODO
        }

        val lockTurnToCharacter = UpdateType(0, 0x10) { player ->
            //TODO
        }

        val animation = UpdateType(0, 0x20) { player ->
            //TODO
        }

        val shout = UpdateType(0, 0x2) { player ->
            //TODO
        }
    }
}