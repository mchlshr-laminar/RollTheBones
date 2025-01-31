package com.Winebone.RollTheBones.dicepool

import com.Winebone.RollTheBones.PoolDisplayParent

/**
 * Copyright (c) 2021-2025 Michael Usher
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of
 * the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

/**
 * Stores data for dice and pools being dragged in the pool configuration activity. Will be put in
 * the local state of the drag event.
 *
 * Either draggedPool or draggedDie should be set, and the other should be null. draggedDie is set
 * if an individual die is being dragged, everything else uses draggedPool.
 */
data class DiceDragData(
    val draggedPool: DicePoolValue?, //draggedPool xor draggedDie is not null
    val draggedDie: DieType?,
    val sourcePool: PoolDisplayParent?, //Null means dragged from drawer. When dragging an existing
                                        //pool, sourcePool points to the actual pool object that's
                                        //currently in the list, while draggedPool is a duplicate of
                                        //it that can be inserted elsewhere.
    val operation: Operation? = null) //Operation is null when dragging from an existing pool.
                                      //When dragging from the drawer, it will set this.
