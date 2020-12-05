/*
 * Copyright 2018-2020 Guthix
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
package io.guthix.oldscape.server.world.entity

import io.guthix.oldscape.server.ServerContext
import io.guthix.oldscape.server.template.HitBarTemplate

sealed class HealthBarUpdate(val id: Int) {
    val template: HitBarTemplate by lazy { ServerContext.hitbarTemplates[id] }
}

class StaticHealthBarUpdate(id: Int, val curHealth: Int, val maxHealth: Int, val delay: Int = 0) : HealthBarUpdate(id) {
    val barWidth: Int = ((curHealth.toDouble() / maxHealth) * template.width).toInt()
}

class DynamicHealthBarUpdate(
    id: Int,
    val startHealth: Int,
    val endHealth: Int,
    val maxHealth: Int,
    val decreaseSpeed: Int,
    val delay: Int = 0
) : HealthBarUpdate(id) {
    val startBarWidth: Int = ((startHealth.toDouble() / maxHealth) * template.width).toInt()
    val endBarWidth: Int = ((endHealth.toDouble() / maxHealth) * template.width).toInt()
}

class RemoveHealthBarUpdate(id: Int) : HealthBarUpdate(id)