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
package io.guthix.oldscape.server.world.entity.character.player

import io.guthix.oldscape.server.world.entity.Obj

class Inventory(
    val player: Player,
    val interfaceId: Int,
    val positionId: Int,
    val containerId: Int,
    val objs: Array<Obj?>
) {
    val maxSize = objs.size

    private var amountInInventory = objs.count { it != null }

    private val objsToUpdate = mutableMapOf<Int, Obj?>()

    fun addObject(obj: Obj) = addObject(objs.indexOfFirst { it == null }, obj)

    fun addObject(slot: Int, obj: Obj) {
        require(slot in 0 until maxSize && amountInInventory != maxSize)
        println("add object!")
        objs[slot] = obj
        objsToUpdate[slot] = obj
        amountInInventory++
    }

    fun removeObject(slot: Int) {
        val obj = objs[slot]
        objs[slot] = null
        objsToUpdate[slot] = obj
        amountInInventory--
    }

    fun update() {
        if(objsToUpdate.isNotEmpty()) {
            if(objsToUpdate.size == amountInInventory) {
                player.addFullInventory(interfaceId, positionId, containerId, objs.toList())
            } else {
                player.addPartialInventory(interfaceId, positionId, containerId, objsToUpdate)
            }
            objsToUpdate.clear()
        }
    }

    fun release() {
        player.releaseInvMemory(containerId)
    }
}