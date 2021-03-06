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
package io.guthix.oldscape.server.world.entity

import io.guthix.oldscape.dim.TileUnit
import io.guthix.oldscape.dim.floors
import io.guthix.oldscape.dim.tiles
import io.guthix.oldscape.server.event.Event
import io.guthix.oldscape.server.event.EventHolder
import io.guthix.oldscape.server.event.PublicMessageEvent
import io.guthix.oldscape.server.plugin.EventHandler
import io.guthix.oldscape.server.task.Task
import io.guthix.oldscape.server.task.TaskHolder
import io.guthix.oldscape.server.task.TaskType
import io.guthix.oldscape.server.world.World
import io.guthix.oldscape.server.world.entity.interest.InterestUpdateType
import io.guthix.oldscape.server.world.entity.interest.MovementInterestUpdate
import io.guthix.oldscape.server.world.entity.interest.PlayerManager
import io.guthix.oldscape.server.world.map.Tile
import io.guthix.oldscape.server.world.map.Zone
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.atan2
import kotlin.reflect.KProperty

abstract class Character(open val index: Int) : Entity, EventHolder, TaskHolder {
    abstract val size: TileUnit

    override val sizeX: TileUnit get() = size

    override val sizeY: TileUnit get() = size

    abstract override var pos: Tile

    abstract var zone: Zone

    abstract var spawnPos: Tile

    var lastPos: Tile = Tile(0.floors, 3234.tiles, 3222.tiles)

    var publicMessage: PublicMessageEvent? = null

    var followPosition: Tile = lastPos.copy()

    var interacting: Character? = null

    var sequence: Sequence? = null

    var spotAnimation: SpotAnimation? = null

    var shoutMessage: String? = null

    var movementType: MovementInterestUpdate = MovementInterestUpdate.STAY

    var path: MutableList<Tile> = mutableListOf()

    open var inRunMode: Boolean = false

    var teleportLocation: Tile? = null

    internal abstract val updateFlags: SortedSet<out InterestUpdateType>

    override val events: ConcurrentLinkedQueue<EventHandler<Event>> = ConcurrentLinkedQueue()

    override val tasks: MutableMap<TaskType, MutableSet<Task>> = mutableMapOf()

    internal val postTasks = mutableListOf<() -> Unit>()

    fun processTasks() {
        while (true) {
            while (events.isNotEmpty()) events.poll().handle()
            val resumed = tasks.values.flatMap { routineList -> routineList.toList().map(Task::run) } // TODO optimize
            if (resumed.all { !it } && events.isEmpty()) break // TODO add live lock detection
        }
    }

    override val properties: MutableMap<KProperty<*>, Any?> = mutableMapOf()

    fun teleport(to: Tile) {
        teleportLocation = to
        path = mutableListOf()
    }

    abstract fun scheduleMovedEvent(world: World)

    abstract fun moveZone(from: Zone, to: Zone)

    fun move(world: World) {
        lastPos = pos
        val lastZone = zone
        when {
            teleportLocation != null -> {
                doTeleport()
                postMovement(lastZone, world)
            }
            path.isNotEmpty() -> {
                doStep()
                postMovement(lastZone, world)
            }
        }
    }

    private fun postMovement(lastZone: Zone, world: World) {
        zone = world.getZone(pos) ?: error("Player at pos $pos not in zone.")
        if (lastZone != zone) moveZone(lastZone, zone)
        scheduleMovedEvent(world)
    }

    private fun doTeleport() {
        movementType = MovementInterestUpdate.TELEPORT
        addTemporaryMovementFlag()
        pos = teleportLocation!!
        followPosition = pos.copy(x = pos.x - 1.tiles) // TODO make follow location based on collision masks
        addPostTask { teleportLocation = null }
    }

    private fun doStep() {
        pos = when {
            inRunMode -> when {
                path.size == 1 -> {
                    movementType = MovementInterestUpdate.WALK
                    addTemporaryMovementFlag()
                    followPosition = pos
                    path.removeAt(0)
                }
                path.size > 1 && pos.withInDistanceOf(path[1], 1.tiles) -> { // running corners
                    movementType = MovementInterestUpdate.WALK
                    followPosition = path.removeAt(0)
                    path.removeAt(0)
                }
                else -> {
                    movementType = MovementInterestUpdate.RUN
                    followPosition = path.removeAt(0)
                    path.removeAt(0)
                }
            }
            else -> {
                movementType = MovementInterestUpdate.WALK
                followPosition = pos
                path.removeAt(0)
            }
        }
        orientation = getOrientation(followPosition, pos)
    }

    fun turnTo(entity: Entity) {
        setOrientation(entity)
        addOrientationFlag()
    }

    fun turnToLock(char: Character?) {
        interacting = char
        char?.let { setOrientation(char) }
        addTurnToLockFlag()
    }


    object SequenceTask : TaskType

    fun animate(animationId: Int) {
        val anim = Sequence(animationId)
        addSequenceFlag()
        sequence = anim
        cancelTasks(SequenceTask)
        addTask(SequenceTask) {
            val duration = anim.duration ?: throw IllegalStateException(
                "Can't start routine because sequence does not exist."
            )
            wait(ticks = duration)
            sequence = null
        }
    }

    fun stopAnimation() {
        sequence = null
        addSequenceFlag()
        cancelTasks(SequenceTask)
    }

    object SpotAnimTask : TaskType

    fun spotAnimate(spotAnimId: Int, height: Int, delay: Int = 0): Unit =
        spotAnimate(SpotAnimation(spotAnimId, height, delay))

    fun spotAnimate(spotAnim: SpotAnimation) {
        addSpotAnimationFlag()
        spotAnimation = spotAnim
        cancelTasks(SpotAnimTask)
        addTask(SpotAnimTask) {
            wait(ticks = spotAnim.delay)
            val duration = spotAnim.duration ?: throw IllegalStateException(
                "Can't start routine because spot animation or sequence does not exist."
            )
            wait(ticks = duration)
            spotAnimation = null
        }
    }

    fun stopSpotAnimation() {
        spotAnimation = null
        addSpotAnimationFlag()
        cancelTasks(SpotAnimTask)
    }

    fun shout(message: String) {
        publicMessage = null
        shoutMessage = message
        addShoutFlag()
        cancelTasks(ChatTask)
        addTask(ChatTask) {
            wait(ticks = PlayerManager.MESSAGE_DURATION - 1)
            addPostTask { shoutMessage = null }
        }
    }

    protected object ChatTask : TaskType

    internal val hitMarkQueue: MutableList<HitMark> = mutableListOf()

    internal val healthBarQueue: MutableList<HealthBarUpdate> = mutableListOf()

    object HitTask : TaskType

    fun addHitMark(hitMark: HitMark) {
        addHitUpdateFlag()
        hitMarkQueue.add(hitMark)
    }

    fun updateHealthBar(update: HealthBarUpdate) {
        addHitUpdateFlag()
        healthBarQueue.add(update)
    }

    override fun postProcess() {
        super.postProcess()
        updateFlags.clear()
        hitMarkQueue.clear()
        healthBarQueue.clear()
        postTasks.forEach { it.invoke() }
        postTasks.clear()
        movementType = MovementInterestUpdate.STAY
    }

    protected abstract fun addTemporaryMovementFlag(): Boolean

    protected abstract fun addOrientationFlag(): Boolean

    protected abstract fun addTurnToLockFlag(): Boolean

    protected abstract fun addSequenceFlag(): Boolean

    protected abstract fun checkSequenceFlag(): Boolean

    protected abstract fun addSpotAnimationFlag(): Boolean

    protected abstract fun addHitUpdateFlag(): Boolean

    protected abstract fun addShoutFlag(): Boolean

    fun addPostTask(task: () -> Unit) {
        postTasks.add(task)
    }

    fun getOrientation(prev: Tile, new: Tile): Int = getOrientation(new.x - prev.x, new.y - prev.y)

    fun getOrientation(dx: TileUnit, dy: TileUnit): Int = moveDirection[2 - dy.value][dx.value + 2]

    protected fun setOrientation(entity: Entity) {
        val dx = (pos.x.value + (sizeX.value.toDouble() / 2)) -
            (entity.pos.x.value + (entity.sizeX.value.toDouble() / 2))
        val dy = (pos.y.value + (sizeY.value.toDouble() / 2)) -
            (entity.pos.y.value + (entity.sizeY.value.toDouble() / 2))
        if (dx.toInt() != 0 || dy.toInt() != 0) orientation = (atan2(dx, dy) * 325.949).toInt() and 0x7FF
    }

    companion object {
        private val moveDirection = arrayOf(
            intArrayOf(768, 768, 1024, 1280, 1280),
            intArrayOf(768, 768, 1024, 1280, 1280),
            intArrayOf(512, 512, -1, 1536, 1536),
            intArrayOf(256, 256, 0, 1792, 1792),
            intArrayOf(256, 256, 0, 1792, 1792)
        )
    }
}