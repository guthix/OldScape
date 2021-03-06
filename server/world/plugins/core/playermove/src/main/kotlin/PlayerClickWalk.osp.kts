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
package io.guthix.oldscape.server.core.playermove

import io.guthix.oldscape.dim.FloorUnit
import io.guthix.oldscape.dim.TileUnit
import io.guthix.oldscape.server.core.pathing.DestinationTile
import io.guthix.oldscape.server.core.pathing.breadthFirstSearch
import io.guthix.oldscape.server.event.MapClickEvent
import io.guthix.oldscape.server.event.MiniMapClickEvent
import io.guthix.oldscape.server.task.NormalTask
import io.guthix.oldscape.server.world.World
import io.guthix.oldscape.server.world.entity.Player
import io.guthix.oldscape.server.world.map.Tile

on(MiniMapClickEvent::class).then {
    player.startWalkingToTile(player.pos.floor, x, y, world)
}

on(MapClickEvent::class).then {
    player.startWalkingToTile(player.pos.floor, x, y, world)
}

fun Player.startWalkingToTile(floor: FloorUnit, x: TileUnit, y: TileUnit, world: World) {
    path = breadthFirstSearch(pos, DestinationTile(floor, x, y), size, true, world)
    path.lastOrNull()?.let { dest -> if (dest != Tile(floor, x, y)) setMapFlag(dest.x, dest.y) }
    cancelTasks(NormalTask)
}