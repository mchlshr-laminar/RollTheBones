@file:Suppress("FunctionName")

package com.Winebone.RollTheBones.dicepool

import android.graphics.drawable.Drawable
import com.Winebone.RollTheBones.DicePoolDisplay
import com.Winebone.RollTheBones.PoolDisplayParent
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/**
 * Copyright (c) 2021-2025 Michael Usher
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of
 * the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

/**
 * Parent class for things that can be in dice pools, namely groups of dice, numbers, or lists of
 * other dice pool values.
 */
@Suppress("FunctionName")
@Serializable
sealed class DicePoolValue {
    //Maintain a pointer to the fragment that displays this value, i.e. that has this dice pool value as its value.
    @Transient var displayFragment: PoolDisplayParent? = null
    @Transient var parentList: PoolList? = null

    /**
     * Generate a result from this dice pool value.
     * updateDisplay: If true, the linked fragment should have its setResults function called.
     * Returns: object representing the result, including the numeric value and, if relevant, a list
     *          of drawables for die faces.
     */
    abstract fun Evaluate(updateDisplay: Boolean = false): DicePoolResult

    /**
     * Generates a result object representing how this pool value should be shown in configuration
     * settings and menus.
     * Returns: DicePoolResult with the value that would occur if all dice landed on their maximum
     *          result. The drawables will be the config face drawables for the dice.
     */
    abstract fun GetConfigResults(): DicePoolResult

    /**
     * Generates an equivalent DicePoolValue. This is a deep copy in that any child pool values will
     * also be duplicated, but the die types and operations it points to will be the same objects
     * and it will not have any display fragments linked.
     * Returns: the duplicate dice pool value.
     */
    abstract fun Duplicate(): DicePoolValue //Does not copy displayFragment

    /**
     * In this dice pool value or any of its children, replaces all dice of one type with another
     * type. The new type is acquired from the die types manager, so if the names are the same this
     * has the effect of refreshing that type. This is used when a dice pool has a die type that
     * can't be found (i.e. has been deleted), or when a custom die type is altered.
     * oldTypeName: Die types with this name will be replaced.
     * newTypeName: The dice will be replaced with a die of this name acquired from the die type
     *              manager.
     * Returns: True if any dice with the specified name were found, false otherwise.
     */
    abstract fun ReplaceDieType(oldTypeName: String, newTypeName: String): Boolean

    /**
     * Looks for a placeholder die type in this pool value and its children. Used because placeholder
     * types will need to be replaced before a pool can be shown.
     * Returns: the first placeholder type found.
     */
    abstract fun FindFirstPlaceholder(): DieType?

    /**
     * Show the config die faces with the alpha set low, to show when the pool has not been rolled
     * yet.
     */
    open fun ShowGhostedConfigDice() = Unit

    companion object {

        /**
         * Generates and returns a dice pool to show if no other pool is specified. Will contain a
         * pool list of the plus operation which contains a single pool with one of the default
         * die type.
         */
        fun ConstructDefaultList(): PoolList {
            var list = PoolList()
            list.addPool(DicePool(DieTypesManager.DefaultType(), 1))
            return list
        }
    }
}

/**
 * Dice pool value for a group of dice of the same type
 */
@Serializable
class DicePool(var typeOfDie: DieType, var quantity: Int = 1): DicePoolValue() {

    /**
     * Increase the number of dice in the pool
     * additionalQuantity: how much to increase the pool size by
     * Returns: number of dice in the pool after incrementing
     */
    fun IncDice(additionalQuantity: Int = 1): Int {
        assert(additionalQuantity >= 0)
        quantity += additionalQuantity
        return quantity
    }

    /**
     * Decreases the number of dice in the pool. The size cannot go below 0.
     * removedQuantity: how much to decrease the pool size by
     * Returns: number of dice in the pool after decrementing
     */
    fun DecDice(removedQuantity: Int = 1): Int {
        assert(removedQuantity >= 0)
        quantity = Math.max(0, quantity - removedQuantity)
        return quantity
    }

    /**
     * Evaluate for a DicePool means rolling each of the dice in the pool. If the display is being
     * updated and there is a display fragment, it will be updated to show the drawables for the
     * generated dice rolls.
     */
    override fun Evaluate(updateDisplay: Boolean): DicePoolResult {
        var results = DicePoolResult()
        for(r in (1..quantity)) {
            var nextResult = typeOfDie.Roll()
            results.AddResult(nextResult)
        }

        if(updateDisplay && displayFragment != null) displayFragment!!.setResults(results)

        return results
    }

    /**
     * For a dice pool, the config result has all the dice at their maximum value.
     */
    override fun GetConfigResults(): DicePoolResult {
        var dieConfig: DieRollResult = typeOfDie.GetConfigResult()
        var poolConfig = DicePoolResult()
        for(r in (1..quantity)) {
            poolConfig.AddResult(dieConfig)
        }
        poolConfig.description = quantity.toString() + "d" + typeOfDie.name
        return poolConfig
    }

    override fun Duplicate(): DicePoolValue {
        return DicePool(typeOfDie, quantity)
    }

    /**
     * For an individual pool, if it has the die type specified to be replaced, retrieve and use the
     * replacement type from the die type manager, then return true. Otherwise do nothing and return
     * false.
     */
    override fun ReplaceDieType(oldTypeName: String, newTypeName: String): Boolean {
        //If this pool's die type has the name oldTypeName, gets a die named newTypeName from the
        //die type manager, replace the type in the pool, and return true. Otherwise return false.
        if(typeOfDie.name != oldTypeName) return false

        var newDieType = DieTypesManager.GetTypeByName(newTypeName)
        typeOfDie = newDieType
        return true
    }

    /**
     * Within an individual pool, the first placeholder pool is either this one (if the die type is
     * a placeholder) or nothing.
     */
    override fun FindFirstPlaceholder(): DieType? {
        return if(typeOfDie.isPlaceholder) { typeOfDie } else { null }
        //Shouldn't need to check DieTypesManager for whether this is still a placeholder; when the
        //placeholder was replaced the loaded pool would be updated. If this is a pool stored in an
        //activity instead, it wouldn't have any placeholders anyway.
    }

    override fun ShowGhostedConfigDice() {
        //Call the function in the display fragment to actually change the display
        (displayFragment as? DicePoolDisplay)?.ShowGhostedConfigDice()
    }
}

/**
 * Dice pool value for a constant integer value
 */
@Serializable
class FixedNumber(var number: Int = 0): DicePoolValue() {

    /**
     * For a fixed value, evaluating returns that value.
     */
    override fun Evaluate(updateDisplay: Boolean): DicePoolResult {
        var results = DicePoolResult(number, number.toString())

        if(updateDisplay && displayFragment != null) displayFragment!!.setResults(results)

        return results
    }

    /**
     * Since the value is fixed, config result is the same as evaluation.
     */
    override fun GetConfigResults(): DicePoolResult {
        return Evaluate()
    }

    override fun Duplicate(): DicePoolValue {
        return FixedNumber(number)
    }

    /**
     * Fixed value has no dice to replace, so just return false.
     */
    override fun ReplaceDieType(oldTypeName: String, newTypeName: String): Boolean {
        return false //Returns false as there are no dice in this pool value
    }

    /**
     * Fixed value has no dice which could be placeholders, so return null.
     */
    override fun FindFirstPlaceholder(): DieType? {
        return null //Returns null as there are no dice in this pool value
    }
}

/**
 * Pool value for a list of sub-values, whose results are combined by an operation. The result of a
 * list means applying that operation to the series of results obtained from the sub-values.
 */
@Serializable
class PoolList(val operation: Operation = Operation.DefaultOperation): DicePoolValue() {
    private var subPools: MutableList<DicePoolValue> = mutableListOf()
    @Transient val operatorText = operation.operatorText
    @Transient val operatorDrawableId = operation.operatorDrawableId

    /**
     * Number of sub-pools in this list
     */
    val size: Int
        get() { return subPools.size }

    /**
     * Converts the list of sub-pools to an array
     */
    val poolsArray: Array<DicePoolValue>
        get() { return subPools.toTypedArray() }

    init {
        //When initialized, the sub-pools need a pointer back to their parent
        for(subPool: DicePoolValue in subPools) {
            subPool.parentList = this
        }
    }

    /**
     * For a pool list, evaluation means evaluating each sub-element and combining those results
     * using the list's operation.
     */
    override fun Evaluate(updateDisplay: Boolean): DicePoolResult {
        var results = DicePoolResult()
        for(subPool in subPools) {
            //Generate results for the sub-pools and combine them according to the lists operation
            var subPoolResults: DicePoolResult = subPool.Evaluate(updateDisplay)
            results = results.Concatenate(subPoolResults, operatorText, operation.opFunction)
        }

        if(updateDisplay && displayFragment != null) displayFragment!!.setResults(results)

        return results
    }

    /**
     * Adds the given sub-pool to the end of the list
     * newPool: pool to add
     */
    fun addPool(newPool: DicePoolValue) {
        addPool(subPools.size, newPool)
    }

    /**
     * Adds the given sub-pool to the list. Existing sub-pools after the insertion location will
     * shift later. Does not update display fragments.
     * index: location in the list to insert the pool at
     * newPool: pool to add
     */
    fun addPool(index: Int, newPool: DicePoolValue) {
        newPool.parentList = this
        subPools.add(index, newPool)
    }

    /**
     * Removes the sub-pool at the specified index. Does not update display fragments.
     * index: index to remove at
     * Returns: the pool that was removed
     */
    fun removeAt(index: Int): DicePoolValue {
        var removed: DicePoolValue = subPools.removeAt(index)
        removed.parentList = null
        return removed
    }

    /**
     * Returns the index of a pool in the list, or -1 if it's not in the list. A list shouldn't have
     * the same sub-pool more than once, but if it did the index of the first occurence would be
     * returned.
     * pool: Dice pool value to find, by reference comparison
     * Returns: index of the pool
     */
    fun indexOf(pool: DicePoolValue): Int {
        return subPools.indexOf(pool)
    }

    /**
     * Returns the sub-pool at the given index
     */
    operator fun get(index: Int): DicePoolValue {
        return subPools[index]
    }

    /**
     * For a pool list, the config result is obtained by getting the config result for each sub-
     * element and combining them with the list's operation.
     */
    override fun GetConfigResults(): DicePoolResult {
        var total = 0
        var description = ""
        var afterFirst = false
        for(pool: DicePoolValue in subPools) {
            var subResult: DicePoolResult = pool.GetConfigResults()

            if(afterFirst) {
                total = operation.apply(total, subResult.total)
                description = description + operatorText + subResult.description
            }
            else {
                total = subResult.total
                description = subResult.description
            }

            afterFirst = true
        }

        return DicePoolResult(total, description)
    }

    override fun Duplicate(): DicePoolValue {
        //Creates a list with the same operation, then populates it with duplicates of this list's
        //sub-pools
        var duplicateList = PoolList(operation)
        for(child: DicePoolValue in subPools) {
            var duplicateChild = child.Duplicate()
            duplicateChild.parentList = duplicateList
            duplicateList.subPools.add(duplicateChild)
        }
        return duplicateList
    }

    /**
     * Attempts die replacement in all of the sub-pools. If any of them return that a replacement
     * was made, returns true.
     */
    override fun ReplaceDieType(oldTypeName: String, newTypeName: String): Boolean {
        //
        var replacementMade = false
        for(pool in subPools) {
            replacementMade = pool.ReplaceDieType(oldTypeName, newTypeName) or replacementMade
        }
        return replacementMade
    }

    /**
     * Looks for a placeholder in each sub-pool in order. Returns the first placeholder any of
     * them return that isn't null. If they all return null, returns null.
     */
    override fun FindFirstPlaceholder(): DieType? {
        for(pool in subPools) {
            val subPoolResult = pool.FindFirstPlaceholder()
            if(subPoolResult != null) return subPoolResult
        }
        return null
    }

    /**
     * Iterate through the sub-elements and show ghosted config for each.
     */
    override fun ShowGhostedConfigDice() {
        //Ghost all the dice in the sub-pools
        for(pool in subPools) {
            pool.ShowGhostedConfigDice()
        }
    }
}

/**
 * Object storing the result from rolling a dice pool value (pool, list, or fixed number).
 */
class DicePoolResult() {
    var total: Int = 0 //Numeric total result
    var description: String = "" //String describing the result; currently used only in determining
                                 //whether a result is empty when concatenating.
    private var drawables: MutableList<Drawable> = mutableListOf() //Faces of the dice, if relevant
    private var isCompound: Boolean = false //Does this result have multiple components, i.e. is it
                                            //a pool with multiple dice or a list with multiple
                                            //elements. Affects description text generation.

    constructor(setTotal: Int, setDescription: String): this() {
        total = setTotal
        description = setDescription
    }

    /**
     * Adds another die result to this pool result. Adds the value from the new result to the total
     * and the result's drawable to the list of drawables to show. Assumes this is the result for a
     * DicePool. For combining results into the result for a PoolList, use Concatenate. FixedNumber
     * results set their value with the constructor
     * newResult: The dice roll to add to the pool results.
     */
    fun AddResult(newResult: DieRollResult) {
        total += newResult.number
        if(description == "") {
            description = newResult.number.toString()
        }
        else {
            //operator symbol is + as dice are always added within an individual pool.
            description += "+" + newResult.number.toString()
            isCompound = true
        }
        drawables.add(newResult.faceDrawable)
    }

    /**
     * Assuming this is the result for a DicePool, returns an array of the die face drawables for
     * the die results.
     * returns: the "drawables" list converted to an array
     */
    fun GetDrawables(): Array<Drawable> {
        return drawables.toTypedArray()
    }

    /**
     * Assumes this is a result for a PoolList. Adds another result, combining the value according
     * to the passed operation if there's already a value in the result. Output drawables list will
     * have the drawables from each sub-result.
     * other: Next result in the list, to place after all the ones already in this result
     * operatorText: symbol to place between the results in the list
     * operation: Operation to apply to the sub-results to get the total. Will be applied to
     *            (this.total, other.total) to get the total for the combined result (i.e. operation
     *            is applied to sub-elements of the list in order). Default op is addition.
     * returns: Result object for the "other" result appended after all of this result object using
     *          the specified operation.
     */
    fun Concatenate(other: DicePoolResult, operatorText: String = "+",
                    operation: (Int, Int) -> Int = fun(a: Int, b: Int): Int { return a + b },
                    ): DicePoolResult {
        if(this.description == "") return other //Nothing in this result, return the other
        else if(other.description == "") return this //Nothing in other result return this one

        var combinedResult = DicePoolResult()
        combinedResult.isCompound = true //Otherwise one of the early outs would've worked
        combinedResult.total = operation(this.total, other.total)

        var thisDescription = this.description
        if(this.isCompound) thisDescription = "(" + thisDescription + ")"
        var secondDescription = other.description
        if(other.isCompound) secondDescription = "(" + secondDescription + ")"
        combinedResult.description = thisDescription + operatorText + secondDescription

        combinedResult.drawables.addAll(this.drawables)
        combinedResult.drawables.addAll(other.drawables)

        return combinedResult
    }
}