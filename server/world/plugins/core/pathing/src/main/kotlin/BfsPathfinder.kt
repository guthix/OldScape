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
package io.guthix.oldscape.server.core.pathing

import io.guthix.oldscape.dim.TileUnit
import io.guthix.oldscape.dim.tiles
import io.guthix.oldscape.server.world.World
import io.guthix.oldscape.server.world.map.Tile
import kotlin.math.abs

private const val MAX_QUEUE_LENGTH = 4096
private val SEARCH_SIZE = 104.tiles
private val ALTERNATIVE_ROUTE_RANGE = 10.tiles
private const val MAX_ALTERNATIVE_PATH = 99

fun breadthFirstSearch(
    start: Tile,
    dest: Destination,
    moverSize: TileUnit,
    findAlternative: Boolean,
    map: World
): MutableList<Tile> {
    val pathBaseX = start.x - (SEARCH_SIZE / 2.tiles)
    val pathBaseY = start.y - (SEARCH_SIZE / 2.tiles)
    var endX = dest.x
    var endY = dest.y
    if (dest.reached(start.x, start.y, moverSize)) { //Already at location
        return emptyList<Tile>().toMutableList()
    }
    val directions = Array(SEARCH_SIZE.value) { IntArray(SEARCH_SIZE.value) }
    val distances = Array(SEARCH_SIZE.value) { IntArray(SEARCH_SIZE.value) { Int.MAX_VALUE } }

    fun canFindPath(start: Tile, dest: Destination, moverSize: TileUnit): Boolean {
        val bufferX = IntArray(MAX_QUEUE_LENGTH)
        val bufferY = IntArray(MAX_QUEUE_LENGTH)
        var currentIndex = 0
        var nextIndex = 0
        bufferX[nextIndex] = start.x.value
        bufferY[nextIndex] = start.y.value
        nextIndex++
        var curGraphX: TileUnit
        var curGraphY: TileUnit
        while (currentIndex != nextIndex) { // While path is not found
            val curX = bufferX[currentIndex].tiles
            val curY = bufferY[currentIndex].tiles
            currentIndex = (currentIndex + 1) and 0xFFF
            curGraphX = curX - pathBaseX
            curGraphY = curY - pathBaseY
            if (dest.reached(curX, curY, moverSize)) {
                endX = curX
                endY = curY
                return true
            }
            val nextDistance = distances[curGraphX.value][curGraphY.value] + 1
            if (curGraphX > 0.tiles && directions[curGraphX.value - 1][curGraphY.value] == 0
                && map.canWalkWest(start.floor, curX, curY, moverSize)
            ) {
                bufferX[nextIndex] = curX.value - 1
                bufferY[nextIndex] = curY.value
                nextIndex = (nextIndex + 1) and 0xFFF
                directions[curGraphX.value - 1][curGraphY.value] = Direction.EAST.mask
                distances[curGraphX.value - 1][curGraphY.value] = nextDistance
            }
            if (curGraphX < SEARCH_SIZE - 1.tiles && directions[curGraphX.value + 1][curGraphY.value] == 0
                && map.canWalkEast(start.floor, curX, curY, moverSize)
            ) {
                bufferX[nextIndex] = curX.value + 1
                bufferY[nextIndex] = curY.value
                nextIndex = (nextIndex + 1) and 0xFFF
                directions[curGraphX.value + 1][curGraphY.value] = Direction.WEST.mask
                distances[curGraphX.value + 1][curGraphY.value] = nextDistance
            }
            if (curGraphY > 0.tiles && directions[curGraphX.value][curGraphY.value - 1] == 0 &&
                map.canWalkSouth(start.floor, curX, curY, moverSize)
            ) {
                bufferX[nextIndex] = curX.value
                bufferY[nextIndex] = curY.value - 1
                nextIndex = (nextIndex + 1) and 0xFFF
                directions[curGraphX.value][curGraphY.value - 1] = Direction.NORTH.mask
                distances[curGraphX.value][curGraphY.value - 1] = nextDistance
            }
            if (curGraphY < SEARCH_SIZE - 1.tiles && directions[curGraphX.value][curGraphY.value + 1] == 0
                && map.canWalkNorth(start.floor, curX, curY, moverSize)
            ) {
                bufferX[nextIndex] = curX.value
                bufferY[nextIndex] = curY.value + 1
                nextIndex = (nextIndex + 1) and 0xFFF
                directions[curGraphX.value][curGraphY.value + 1] = Direction.SOUTH.mask
                distances[curGraphX.value][curGraphY.value + 1] = nextDistance
            }
            if (curGraphX > 0.tiles && curGraphY > 0.tiles && directions[curGraphX.value - 1][curGraphY.value - 1] == 0
                && map.canWalkSouthWest(start.floor, curX, curY, moverSize)
            ) {
                bufferX[nextIndex] = curX.value - 1
                bufferY[nextIndex] = curY.value - 1
                nextIndex = (nextIndex + 1) and 0xFFF
                directions[curGraphX.value - 1][curGraphY.value - 1] = Direction.NORTH.mask or Direction.EAST.mask
                distances[curGraphX.value - 1][curGraphY.value - 1] = nextDistance
            }
            if (curGraphX < SEARCH_SIZE - 1.tiles && curGraphY > 0.tiles
                && directions[curGraphX.value + 1][curGraphY.value - 1] == 0
                && map.canWalkSouthEast(start.floor, curX, curY, moverSize)
            ) {
                bufferX[nextIndex] = curX.value + 1
                bufferY[nextIndex] = curY.value - 1
                nextIndex = (nextIndex + 1) and 0xFFF
                directions[curGraphX.value + 1][curGraphY.value - 1] = Direction.NORTH.mask or Direction.WEST.mask
                distances[curGraphX.value + 1][curGraphY.value - 1] = nextDistance
            }
            if (curGraphX > 0.tiles && curGraphY < SEARCH_SIZE - 1.tiles
                && directions[curGraphX.value - 1][curGraphY.value + 1] == 0
                && map.canWalkNorthWest(start.floor, curX, curY, moverSize)
            ) {
                bufferX[nextIndex] = curX.value - 1
                bufferY[nextIndex] = curY.value + 1
                nextIndex = (nextIndex + 1) and 0xFFF
                directions[curGraphX.value - 1][curGraphY.value + 1] = Direction.SOUTH.mask or Direction.EAST.mask
                distances[curGraphX.value - 1][curGraphY.value + 1] = nextDistance
            }
            if (curGraphX < SEARCH_SIZE - 1.tiles && curGraphY < SEARCH_SIZE - 1.tiles
                && directions[curGraphX.value + 1][curGraphY.value + 1] == 0
                && map.canWalkNorthEast(start.floor, curX, curY, moverSize)
            ) {
                bufferX[nextIndex] = curX.value + 1
                bufferY[nextIndex] = curY.value + 1
                nextIndex = (nextIndex + 1) and 0xFFF
                directions[curGraphX.value + 1][curGraphY.value + 1] = Direction.SOUTH.mask or Direction.WEST.mask
                distances[curGraphX.value + 1][curGraphY.value + 1] = nextDistance
            }
        }
        return false
    }

    fun findAlternativeDestination(dest: Destination) {
        var lowCost = Integer.MAX_VALUE
        var lowDist = Integer.MAX_VALUE
        for (x in dest.x - ALTERNATIVE_ROUTE_RANGE..dest.x + ALTERNATIVE_ROUTE_RANGE) {
            for (y in dest.y - ALTERNATIVE_ROUTE_RANGE..dest.y + ALTERNATIVE_ROUTE_RANGE) {
                val localX = x - pathBaseX
                val localY = y - pathBaseY
                if (localX >= SEARCH_SIZE || localY >= SEARCH_SIZE || localX < 0.tiles || localY < 0.tiles
                    || distances[localX.value][localY.value] >= MAX_ALTERNATIVE_PATH
                ) {
                    continue
                }
                val dx = abs((dest.x - x).value)
                val dy = abs((dest.y - y).value)
                val cost = dx * dx + dy * dy
                if (cost < lowCost || (cost == lowCost && distances[localX.value][localY.value] < lowDist)) {
                    lowCost = cost
                    lowDist = distances[localX.value][localY.value]
                    endY = y
                    endX = x
                }
            }
        }
    }

    val foundDestination = canFindPath(start, dest, moverSize)
    if (!foundDestination && findAlternative) {
        findAlternativeDestination(dest)
    }
    if (start.x == endX && start.y == endY) { //Alternative destination is the same as current location
        return emptyList<Tile>().toMutableList()
    }

    // Trace back the path from destination to the start using the direction masks
    var traceX = endX
    var traceY = endY
    var direction: Int
    val path = mutableListOf<Tile>()
    while (traceX != start.x || traceY != start.y) {
        direction = directions[(traceX - pathBaseX).value][(traceY - pathBaseY).value]
        path.add(Tile(dest.floor, traceX, traceY))
        if (direction and Direction.EAST.mask != 0) {
            traceX++
        } else if (direction and Direction.WEST.mask != 0) {
            traceX--
        }
        if (direction and Direction.NORTH.mask != 0) {
            traceY++
        } else if (direction and Direction.SOUTH.mask != 0) {
            traceY--
        }
    }
    return path.asReversed()
}