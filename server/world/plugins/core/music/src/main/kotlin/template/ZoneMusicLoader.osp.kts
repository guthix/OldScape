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
package io.guthix.oldscape.server.core.music.template

import io.guthix.oldscape.dim.floors
import io.guthix.oldscape.dim.mapsquares
import io.guthix.oldscape.server.core.music.ZoneMusic
import io.guthix.oldscape.server.core.music.musicTrack
import io.guthix.oldscape.server.event.WorldInitializedEvent
import io.guthix.oldscape.server.readYaml

on(WorldInitializedEvent::class).then {
    val zoneMusic: Map<String, ZoneMusic> = readYaml("/Music.yaml")
    zoneMusic.values.forEach { (floor, msX, msY, musicId) ->
        val zones = world.getZones(floor.floors, msX.mapsquares, msY.mapsquares)
        zones.forEach { zoneY ->
            zoneY.forEach { zone ->
                zone?.musicTrack = musicId
            }
        }
    }
    logger.info { "Loaded ${zoneMusic.size} mapsquare music assignments" }
}