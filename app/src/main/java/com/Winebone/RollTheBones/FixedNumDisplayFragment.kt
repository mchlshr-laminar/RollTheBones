package com.Winebone.RollTheBones

import android.os.Bundle
import android.view.DragEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import com.Winebone.RollTheBones.dicepool.DiceDragData
import com.Winebone.RollTheBones.dicepool.DicePoolResult
import com.Winebone.RollTheBones.dicepool.FixedNumber

/**
 * Copyright (c) 2021-2025 Michael Usher
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of
 * the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

/**
 * PoolValueDisplayFragment for a FixedNumber. Number is displayed as text. In the config activity
 * it has an outline and a drag handle to the left.
 *
 * Accepts other FixedNumber pools dragged from elsewhere, which changes the number to the dropped
 * pool's number.
 */
class FixedNumDisplayFragment: PoolValueDisplayFragment<FixedNumber>() {
    private lateinit var numberField: TextView

    /**
     * PoolValueDisplayFragment function to construct the contents of the fragment. Sets the text of
     * the fixedNumText TextView to the FixedNumber's value.
     *
     * The highlight element is the top-level fragment view.
     * The drag handle element has ID fixedNumDragHandle, to the left of the text view.
     */
    override fun buildView(
        inflater: LayoutInflater,
        fragmentView: FrameLayout,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ) {
        var numberLayout = inflater.inflate(R.layout.fragment_fixed_num_display, fragmentView, false)
        highlightElement = numberLayout
        numberField = numberLayout.findViewById<TextView>(R.id.fixedNumText)
        fragmentView.addView(numberLayout)
        updateDisplayText()

        if(dragEnabled) {
            //Show the outline and drag handle
            numberLayout.findViewById<View>(R.id.fixedNumFragDragBox)?.visibility = View.VISIBLE
            dragHandleElement = numberLayout.findViewById(R.id.fixedNumDragHandle)
            dragHandleElement?.visibility = View.VISIBLE
        }
    }

    /**
     * This pool fragment will have no child fragments, so once its own layout has been inflated and
     * built there's nothing further to build. Therefore the afterDisplayBuiltCallback can be run.
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        runAfterDisplayBuiltCallback() //No sub-fragments to worry about here
    }

    /**
     * Sets the contents of the TextView based on the number in the FixedNumber pool value.
     */
    private fun updateDisplayText() {
        numberField.text = value.number.toString()
    }

    /**
     * FixedNumber always evaluates to the same result, return that number. Does not need to account
     * for display updating as that doesn't change for a FixedNumber.
     */
    override fun Roll(): Int {
        return value.number
    }

    /**
     * Does nothing, as a FixedNumber does not need to change its display when a new result is
     * generated.
     */
    override fun setResults(result: DicePoolResult) {
        //no-op
    }

    /**
     * A FixedNumber accepts other FixedNumbers, dragged from the drawer or elsewhere in the pool.
     */
    override fun acceptsDraggedObject(dragData: DiceDragData): Boolean {
        return dragData.draggedPool is FixedNumber
    }

    /**
     * When another FixedNumber pool is dropped here, update this fragment's value to the dropped
     * pool's number.
     */
    override fun draggedObjectDropped(view: View, event: DragEvent, dragData: DiceDragData): Boolean {
        if(dragData.draggedPool !is FixedNumber) return false //Should already be checked by acceptsDrawerObject

        value.number = dragData.draggedPool.number
        updateDisplayText()
        return true
    }

}