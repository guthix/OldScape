/**
 * This file is part of Guthix OldScape.
 *
 * Guthix OldScape is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Guthix OldScape is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Foobar. If not, see <https://www.gnu.org/licenses/>.
 */
package io.guthix.oldscape.server.net.state.game

import io.guthix.oldscape.server.net.state.IsaacRandom
import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.MessageToByteEncoder

class GameEncoder(private val encodeCipher: IsaacRandom) : MessageToByteEncoder<OutGameEvent>() {
    override fun encode(ctx: ChannelHandlerContext, msg: OutGameEvent, out: ByteBuf) {
        val packet = msg.encode(ctx)
        out.writeByte(packet.opcode + encodeCipher.nextInt())
        when(packet.type) {
            GamePacket.PacketSize.VAR_SHORT -> out.writeShort(packet.payload.readableBytes())
            GamePacket.PacketSize.VAR_BYTE -> out.writeByte(packet.payload.readableBytes())
            else -> { }
        }
        out.writeBytes(packet.payload)
    }
}