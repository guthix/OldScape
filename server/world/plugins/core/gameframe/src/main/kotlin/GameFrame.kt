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
package io.guthix.oldscape.server.core.gameframe

import io.guthix.oldscape.cache.config.EnumConfig
import io.guthix.oldscape.server.template.Enums
import io.guthix.oldscape.server.world.entity.Player

enum class GameFrame(val interfaceId: Int, val enum: Map<EnumConfig.Component, EnumConfig.Component>) {
    FIXED(interfaceId = 548, enum = Enums.GAMEFRAME_FIXED_1129),
    RESIZABLE_BOX(interfaceId = 161, enum = Enums.GAMEFRAME_RESIZABLE_BOX_1130),
    RESIZABLE_LINE(interfaceId = 164, enum = Enums.GAMEFRAME_RESIZABLE_LINE_1131),
    BLACK_SCREEN(interfaceId = 165, enum = Enums.GAMEFRAME_RESIZABLE_BLACKSCREEN_1132);

    companion object {
        fun findByInterfaceId(id: Int): GameFrame = values().first { it.interfaceId == id }
    }
}

private val modalKey: EnumConfig.Component = EnumConfig.Component(interfaceId = 161, slot = 15)

fun Player.changeGameFrame(gameFrame: GameFrame) {
    val fromEnum = GameFrame.findByInterfaceId(topInterface.id).enum
    val toEnum = gameFrame.enum
    val moves = mutableMapOf<Int, Int>()
    for ((fromKey, fromValue) in fromEnum) {
        val toValue = toEnum[fromKey]
        if (toValue != null) {
            moves[fromValue.slot] = toValue.slot
        }
    }
    openTopInterface(gameFrame.interfaceId, toEnum[modalKey]?.slot, moves)
}