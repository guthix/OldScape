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

import io.guthix.oldscape.server.template.type.SequenceTemplate
import io.guthix.oldscape.server.template.type.tickDuration
import mu.KotlinLogging

private val logger = KotlinLogging.logger { }

data class Sequence(private val template: SequenceTemplate) {
    val id: Int get() = template.id

    val duration: Int? = template.tickDuration
}