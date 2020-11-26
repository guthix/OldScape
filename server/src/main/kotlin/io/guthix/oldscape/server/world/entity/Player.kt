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

import io.guthix.oldscape.server.PersistentProperty
import io.guthix.oldscape.server.PersistentPropertyHolder
import io.guthix.oldscape.server.event.Event
import io.guthix.oldscape.server.event.EventHolder
import io.guthix.oldscape.server.event.PublicMessageEvent
import io.guthix.oldscape.server.net.game.out.*
import io.guthix.oldscape.server.plugin.EventHandler
import io.guthix.oldscape.server.task.Task
import io.guthix.oldscape.server.template.CS2Template
import io.guthix.oldscape.server.template.VarbitTemplate
import io.guthix.oldscape.server.template.VarpTemplate
import io.guthix.oldscape.server.world.World
import io.guthix.oldscape.server.world.entity.interest.*
import io.guthix.oldscape.server.world.entity.intface.IfComponent
import io.guthix.oldscape.server.world.map.Tile
import io.guthix.oldscape.server.world.map.dim.TileUnit
import io.guthix.oldscape.server.world.map.dim.floors
import io.guthix.oldscape.server.world.map.dim.tiles
import io.netty.channel.ChannelFuture
import io.netty.channel.ChannelHandlerContext
import java.util.concurrent.ConcurrentLinkedQueue

class Player internal constructor(
    val uid: Int,
    var priority: Int,
    var ctx: ChannelHandlerContext,
    val username: String,
    val clientSettings: ClientSettings,
    override val persistentProperties: MutableMap<String, Any>,
    private val playerManager: PlayerManager,
    private val npcManager: NpcManager,
    private val sceneManager: SceneManager,
    private val energyManager: EnergyManager,
    private val contextMenuManager: ContextMenuManager,
    private val varpManager: VarpManager,
    private val statManager: StatManager,
    private val interfaceManager: TopInterfaceManager,
) : Character(playerManager.index), Comparable<Player>, EventHolder, PersistentPropertyHolder {
    override var pos: Tile by PersistentProperty { Tile(0.floors, 3235.tiles, 3222.tiles) }

    override var orientation: Int by PersistentProperty { 0 }

    val gender: Gender by PersistentProperty { Gender.MALE }

    val colours: Colours by PersistentProperty { Colours(0, 0, 0, 0, 0) }

    val style: Style by PersistentProperty {
        Style(
            hair = 0,
            beard = 10,
            torso = 18,
            arms = 26,
            legs = 36,
            hands = 33,
            feet = 42
        )
    }

    val animations: StanceSequences by PersistentProperty {
        StanceSequences(
            stand = 808,
            turn = 823,
            walk = 819,
            turn180 = 820,
            turn90CW = 821,
            turn90CCW = 822,
            run = 824
        )
    }

    var rights: Int by PersistentProperty { 2 }

    var isLoggingOut: Boolean = false

    val contextMenu: Array<String> get() = contextMenuManager.contextMenu

    val topInterface: TopInterfaceManager get() = interfaceManager

    internal val scene: SceneManager get() = sceneManager

    val stats: StatManager get() = statManager

    var nameModifiers: Array<String> = arrayOf("", "", "")

    var isSkulled: Boolean = false

    val prayerIcon: Int = -1

    val combatLevel: Int = 126

    val equipmentSet: PlayerManager.EquipmentSet = PlayerManager.EquipmentSet(mutableMapOf())

    override var inRunMode: Boolean = super.inRunMode
        set(value) {
            field = value
            updateFlags.add(PlayerInfoPacket.movementCached)
        }

    override val size: TileUnit = 1.tiles

    var weight: Int
        get() = energyManager.weight
        set(value) {
            energyManager.weight = value
        }

    var energy: Int
        get() = energyManager.energy
        set(value) {
            energyManager.energy = value
        }

    override val updateFlags = sortedSetOf<PlayerUpdateType>()

    override val events: ConcurrentLinkedQueue<EventHandler<Event>> = ConcurrentLinkedQueue()

    override fun processTasks() {
        while (true) {
            while (events.isNotEmpty()) events.poll().handle()
            val resumed = tasks.values.flatMap { routineList -> routineList.toList().map(Task::run) } // TODO optimize
            if (resumed.all { !it } && events.isEmpty()) break // TODO add live lock detection
        }
    }

    fun initialize(world: World) {
        playerManager.initialize(world, this)
        sceneManager.initialize(world, this)
        val xteas = sceneManager.getInterestedXteas(world.xteas)
        ctx.write(InterestInitPacket(world.players, this, xteas, pos.x.inZones, pos.y.inZones))
        updateFlags.add(PlayerInfoPacket.appearance)
        updateFlags.add(PlayerInfoPacket.orientation)
        updateFlags.add(PlayerInfoPacket.nameModifiers)
        topInterface.initialize()
        contextMenuManager.initialize(this)
        statManager.initialize(world, this)
        energyManager.initialize(this)
    }

    internal fun synchronize(world: World): List<ChannelFuture> {
        val futures = mutableListOf<ChannelFuture>()
        futures.addAll(topInterface.synchronize(this))
        futures.addAll(contextMenuManager.synchronize(this))
        futures.addAll(varpManager.synchronize(this))
        futures.addAll(statManager.synchronize(world, this))
        futures.addAll(energyManager.synchronize(this))
        futures.addAll(sceneManager.synchronize(world, world.xteas, this))
        futures.addAll(npcManager.synchronize(world, this))
        futures.addAll(playerManager.synchronize(world, this))
        ctx.flush()
        return futures
    }

    override fun postProcess() {
        super.postProcess()
        topInterface.postProcess()
        contextMenuManager.postProcess()
        varpManager.postProcess()
        statManager.postProcess()
        energyManager.postProcess()
        sceneManager.postProcess()
    }

    fun openTopInterface(id: Int, modalSlot: Int? = null, moves: Map<Int, Int> = mutableMapOf()): TopInterfaceManager {
        val movedChildren = mutableMapOf<Int, IfComponent>()
        ctx.write(IfOpentopPacket(id))
        for ((fromSlot, toSlot) in moves) {
            movedChildren[toSlot] = topInterface.children[fromSlot] ?: continue
            ctx.write(IfMovesubPacket(topInterface.id, fromSlot, id, toSlot))
        }
        topInterface.modalSlot?.let { curModalSlot ->
            modalSlot?.let { newModalSlot ->
                if (topInterface.modalOpen && curModalSlot != newModalSlot) {
                    ctx.write(IfMovesubPacket(topInterface.id, curModalSlot, id, newModalSlot))
                }
            }
        }
        topInterface.id = id
        topInterface.modalSlot = modalSlot
        topInterface.children = movedChildren
        return topInterface
    }

    fun talk(message: PublicMessageEvent) {
        publicMessage = message
        shoutMessage = null
        updateFlags.add(PlayerInfoPacket.chat)
        cancelTasks(ChatTask)
        addTask(ChatTask) {
            wait(ticks = PlayerManager.MESSAGE_DURATION - 1)
            addPostTask { publicMessage = null }
        }
    }

    fun senGameMessage(message: String) {
        ctx.write(MessageGamePacket(0, false, message))
    }

    fun updateAppearance() {
        updateFlags.add(PlayerInfoPacket.appearance)
    }

    fun updateVarp(template: VarpTemplate, value: Int): Unit = varpManager.updateVarp(template, value)

    fun updateVarbit(template: VarbitTemplate, value: Int): Unit = varpManager.updateVarbit(template, value)

    fun runClientScript(id: CS2Template, vararg args: Any) {
        ctx.write(RunclientscriptPacket(id, *args))
    }

    fun setMapFlag(x: TileUnit, y: TileUnit) {
        ctx.write(SetMapFlagPacket(x - sceneManager.baseX.inTiles, y - sceneManager.baseY.inTiles))
    }

    override fun addOrientationFlag(): Boolean = updateFlags.add(PlayerInfoPacket.orientation)

    override fun addTurnToLockFlag(): Boolean = updateFlags.add(PlayerInfoPacket.lockTurnTo)

    override fun addSequenceFlag(): Boolean = updateFlags.add(PlayerInfoPacket.sequence)

    override fun checkSequenceFlag(): Boolean = updateFlags.contains(PlayerInfoPacket.sequence)

    override fun addSpotAnimationFlag(): Boolean = updateFlags.add(PlayerInfoPacket.spotAnimation)

    override fun addHitUpdateFlag(): Boolean = updateFlags.add(PlayerInfoPacket.hit)

    override fun addShoutFlag(): Boolean = updateFlags.add(PlayerInfoPacket.shout)

    internal fun stageLogout() {
        isLoggingOut = true
        events.clear()
        tasks.clear()
        ctx.writeAndFlush(LogoutFullPacket())
    }

    override fun compareTo(other: Player): Int = when {
        priority < other.priority -> -1
        priority > other.priority -> 1
        else -> 0
    }

    fun clearMap(): Unit = sceneManager.clear(this)
}