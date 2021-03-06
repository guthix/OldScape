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
package io.guthix.oldscape.server.core.loc

import io.guthix.oldscape.server.core.loc.event.LocReachedEvent
import io.guthix.oldscape.server.core.pathing.DestinationLocation
import io.guthix.oldscape.server.core.pathing.breadthFirstSearch
import io.guthix.oldscape.server.event.EventBus
import io.guthix.oldscape.server.event.LocClickEvent
import io.guthix.oldscape.server.plugin.invalidMessage
import io.guthix.oldscape.server.task.NormalTask
import io.guthix.oldscape.server.world.map.Tile

on(LocClickEvent::class).then {
    val loc = world.getLoc(id, player.pos.floor, x, y) ?: invalidMessage(
        "Could not find location at ${Tile(player.pos.floor, x, y)}."
    )
    val destination = DestinationLocation(loc, world)
    player.path = breadthFirstSearch(player.pos, destination, player.size, true, world)
    player.path.lastOrNull()?.let { (_, x, y) -> player.setMapFlag(x, y) }
    player.cancelTasks(NormalTask)
    player.addTask(NormalTask) {
        wait { destination.reached(player.pos.x, player.pos.y, player.size) }
        EventBus.schedule(LocReachedEvent(loc, player, world))
    }
}