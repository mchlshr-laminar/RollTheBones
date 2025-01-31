package com.Winebone.RollTheBones

import android.os.Bundle
import android.view.DragEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentTransaction
import com.Winebone.RollTheBones.dicepool.*

//Children of this list are displayed in a linear layout, but there is one element (the drag handle)
//in that layout before any of those children.
private val LAYOUT_LIST_OFFSET = 1

/**
 * Copyright (c) 2021-2025 Michael Usher
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of
 * the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

/**
 * PoolValueDisplayFragment for a PoolList. Displays the fragments for the list's children one above
 * another, with an ImageView for the operator in between them. In the config activity it has a drag
 * handle at the top and an icon for the operator at the top left. Has an outline in both contexts.
 * This does not accept dropped pool values through the default mechanism unless there are no
 * children in the list, but any pool value (except for ancestors of this list) can be dropped
 * before or after an existing child, or in the list directly if it's empty. If a value from the
 * drawer is dropped before/after a child and the drawer is currently set to an operation other than
 * the one this list uses, then the dropped value and child it was dropped next to will be put into
 * a new sub-list (using the operation from the drawer), and that child will be replaced in this
 * list with the sub-list.
 *
 * The highlight element has ID emptyListDropArea and is below the linear layout of child fragments.
 *     Note that this is not used for dropping before/after existing children.
 * The drag handle element has ID listFragDragHandle and is the first element in the linear layout
 *     for child fragments.
 */
class PoolListDisplayFragment: PoolValueDisplayFragment<PoolList>() {
    private lateinit var subFragLayout: LinearLayout
    private var hasSubFrags = false
    private var nextLayoutIndex = LAYOUT_LIST_OFFSET
    //Map from list_item_layout instance to the trailing operator image view directly in that layout.
    //We can't use findViewById on the list_item_layout view for it after the fragment it contains
    //is built. This is because, if the fragment is a sub-list, it will have list items that also
    //have a trailing operator image. These would have the same ID and are earlier in the hierarchy.
    private var operatorImages = HashMap<View, View>()
    //Keep track of the number of child fragments which have been created, but have not yet finished
    //building their display.
    private var subfragsLeftToBuild = 0

    /**
     * PoolValueDisplayFragment function to construct the contents of the fragment. For each child
     * value of the PoolList this fragment is for, inflates list_item_layout, which contains:
     *     -A FrameLayout to display the fragment for the child value
     *     -Empty views before and after the FrameLayout for dropping values before/after this child.
     *         These are only visible in the config activity.
     *     -An ImageView at the end to display the operator for this PoolList
     * Creates a new fragment for each child, and puts it in the FrameLayout mentioned above. For
     * each list_item_layout but the last, makes the operator image visible.
     */
    override fun buildView(
        inflater: LayoutInflater,
        fragmentView: FrameLayout,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ) {
        var fragLayout = inflater.inflate(R.layout.fragment_pool_list_display, fragmentView, false) as ConstraintLayout
        subFragLayout = fragLayout.findViewById(R.id.listFragmentLayout)
        fragmentView.addView(fragLayout)

        if(dragEnabled) {
            dragHandleElement = fragLayout.findViewById(R.id.listFragDragHandle)
            dragHandleElement!!.visibility = View.VISIBLE

            highlightElement = fragLayout.findViewById<View>(R.id.emptyListDropArea)
            highlightElement!!.visibility = View.VISIBLE

            //This is the icon in the upper left, not the interstitial operators
            var operatorIcon = fragLayout.findViewById<ImageView>(R.id.listFragOperatorIcon)
            operatorIcon.setImageResource(value.operatorDrawableId)
        }

        //Make the fragments for each thing in the list
        for(subPool: DicePoolValue in value.poolsArray) {
            addSubPoolDisplay(subPool)
        }
    }

    /**
     * Adds a DicePoolValue to this PoolList at a given location. Creates a new fragment for that
     * value and adds it to the display layout. Checking for whether this would create a loop should
     * be done before calling this.
     * newSubPool: The pool value to add to this list.
     * index: Position in the PoolList to add the new value
     * scrollToPool: If true, then after the new fragment is built the display will scroll to the
     *               new fragment.
     */
    private fun AddSubPool(newSubPool: DicePoolValue, index: Int? = null, scrollToPool: Boolean = false) {
        var insertionIndex = index ?: value.size
        value.addPool(insertionIndex, newSubPool)
        addSubPoolDisplay(newSubPool, insertionIndex, scrollToPool)
    }

    /**
     * Creates a list_item_layout and new fragment for a given PoolValue. Adds the new fragment to
     * the fragment manager, putting it in the list_item_layout. Adds the list_item_layout to the
     * linear layout displaying this list, and updates operator image visibility.
     *
     * This does not affect the children of the PoolList this fragment is for; it is a helper function
     * for AddSubPool and for initial display generation from an existing PoolList.
     *
     * newPool: The pool value to add to the display
     * index: The position to add the new fragment at, as an index into the PoolList. If null the new
     *        fragment will be added at the end.
     * scrollToPool: If true, then after the new fragment is built the display will scroll to the
     *               new fragment. Note that this uses the afterDisplayBuiltCallback, so it should
     *               not be set to true during initial display generation.
     */
    private fun addSubPoolDisplay(newPool: DicePoolValue, index: Int? = null, scrollToPool: Boolean = false) {
        var layoutIndex = index?.plus(LAYOUT_LIST_OFFSET) ?: nextLayoutIndex //index is index into value.subPools; layout may have leading elements

        //Create the item layout that will contain the new fragment
        var itemLayout = activity?.layoutInflater?.inflate(R.layout.list_item_layout,
            subFragLayout,
            false) as LinearLayout
        var operatorImg = itemLayout.findViewById<ImageView>(R.id.itemTrailingOperator)
        operatorImg.setImageResource(value.operatorDrawableId)
        operatorImages[itemLayout] = operatorImg

        //Add drag handlers to the new item
        if(dragEnabled) {
            for(id in intArrayOf(R.id.beforeItem, R.id.afterItem)) {
                var dragArea = itemLayout.findViewById<View>(id)
                dragArea.visibility = View.VISIBLE

                var dragListener = View.OnDragListener(
                    fun(v: View, event: DragEvent): Boolean {
                        return handleDropSegmentDrag(newPool, id == R.id.afterItem, v, event)
                    }
                )
                dragArea.setOnDragListener(dragListener)
            }
        }

        //Find the frame to put the new fragment into
        var itemArea = itemLayout.findViewById<FrameLayout>(R.id.itemArea)
        itemArea.id = View.generateViewId() //Needs unique ID for fragment transaction to find it
        subFragLayout.addView(itemLayout, layoutIndex)

        //Update operator visibility
        if(hasSubFrags) {
            if (layoutIndex == nextLayoutIndex) {
                //Inserting at the end; show operator on prior fragment
                var priorItem: LinearLayout = subFragLayout.getChildAt(layoutIndex-1) as LinearLayout
                //Item should exist there if the list already has subfrags
                var priorOperatorImg = operatorImages[priorItem]
                priorOperatorImg?.visibility = View.VISIBLE
            } else {
                //Inserting before the end; show operator on this fragment
                operatorImg.visibility = View.VISIBLE
            }
        }


        //Add the fragment to the item layout
        var fragMan = activity?.supportFragmentManager as FragmentManager
        var transaction: FragmentTransaction = fragMan.beginTransaction()

        //If there's an existing fragment for the pool, use that. Otherwise make a new one
        var newSubFrag = newPool.displayFragment ?: PoolDisplayParent.newInstance(newPool, dragEnabled, numColumns)
        newSubFrag.afterDisplayBuiltCallback = ::onSubfragBuildComplete
        transaction.add(itemArea.id, newSubFrag)

        //Scroll to the new fragment
        if(scrollToPool) {
            afterDisplayBuiltCallback = fun () {
                scrollToSubfrag(newSubFrag)
            }
        }

        //Indicate that there's another fragment who's display is not finished building
        subfragsLeftToBuild++
        transaction.commit()

        //Record-keeping
        hasSubFrags = true
        nextLayoutIndex++
    }

    /**
     * When a child fragment reports that it has completed building its display, decrement the count
     * of children which have not finished building. If that's the last child (i.e. all children have
     * finished), then run the afterDisplayBuiltCallback. This callback will either be:
     *     -Inform this list's parent list that the display is built
     *     -Generate an initial result (if this is the top-level list during the initial build for
     *         the rolling activity)
     *     -Scroll to the fragment that was being built (if this is adding a new child in the config
     *         activity)
     *
     * Note that this assumes all children to be built will be created (by addSubPoolDisplay) before
     * any of them has an opportunity to actually build their display, which will occur after control
     * leaves this fragment's onCreateView. For adding children in the config activity, it assumes
     * children are added one at a time, and the new child's display will be finished before any
     * additional children can be added.
     */
    private fun onSubfragBuildComplete() {
        subfragsLeftToBuild--
        if(subfragsLeftToBuild == 0) runAfterDisplayBuiltCallback()
    }

    /**
     * Induce the activity to scroll to a newly-created child of this list, by reporting that a drop
     * was accepted at that location. Needs to be done after the child's display is built, as the
     * full height of the child will not be known until then (scrolling actually happens if the
     * fragment that accepted a drop is partially offscreen).
     */
    private fun scrollToSubfrag(target: PoolDisplayParent) {
        reportDropAccepted(target.view)
    }

    /**
     * Removes a given child of this PoolList. Also removes the layout displaying that child's
     * fragment, and removes the fragment from the fragment manager. Updates operator image visibility
     * if necessary.
     * pool: The pool to find and remove. Identified by reference equality. If the pool is not found
     *       in this list, nothing will happen.
     */
    fun RemoveSubPool(pool: DicePoolValue) {
        var index = value.indexOf(pool)
        if(index != -1) RemoveSubPool(index)
    }

    /**
     * Removes the child of this PoolList at a given location. Also removes the layout displaying
     * that child's fragment, and removes the fragment from the fragment manager. Updates operator
     * image visibility if necessary.
     * index: Index into the PoolList of the child to remove.
     */
    fun RemoveSubPool(index: Int) {
        var layoutIndex = index + LAYOUT_LIST_OFFSET

        //Update operator visibility
        if(value.size > 1 && index == value.size - 1) {
            //Removing the last when there's something before it; hide the operator on that
            var penultimate = subFragLayout.getChildAt(layoutIndex - 1)
            //var opImg = penultimate.findViewById<ImageView>(R.id.itemTrailingOperator)
            var opImg = operatorImages[penultimate]
            opImg?.visibility = View.GONE
        }

        //Remove the item from the layout
        var removedPool = value.removeAt(index)
        operatorImages.remove(subFragLayout.getChildAt(layoutIndex))
        subFragLayout.removeViewAt(layoutIndex)

        //Remove the fragment from the fragment manager
        if(removedPool.displayFragment != null) {
            var fragMan = activity?.supportFragmentManager as FragmentManager
            var transaction: FragmentTransaction = fragMan.beginTransaction()

            transaction.remove(removedPool.displayFragment!!)
            removedPool.displayFragment = null

            transaction.commit()
        }

        //Record-keeping
        hasSubFrags = value.size > 0
        nextLayoutIndex--
    }

    /**
     * Induce the PoolList this is for to generate a random result. Fragments for descendant pool
     * values will be informed of the new result by the Evaluate calls in those pool values.
     * Returns: The total generated by this list, applying the appropriate operator to any generated
     *          sub-pool results.
     */
    override fun Roll(): Int {
        var result = value.Evaluate(true)
        return result.total
    }

    /**
     * Does nothing, as a pool list does not need to update anything in its own display when a result
     * is generated. The changes will all be in DicePoolDisplay fragments.
     */
    override fun setResults(result: DicePoolResult) {
        //no-op
    }

    /**
     * Drag event handler which is attached to the before/after drop sections of each list item
     * layout. Ignores any pool list being dragged which is a direct ancestor of this list, as dropping
     * that within this list would create a loop. Otherwise accepts any dice pool object that would
     * be dropped in the section. If an individual die is dropped here, a new DicePool will be
     * created for that die's type, with an initial size of 1.
     *
     * For ACTION_DRAG_STARTED: Return true to register for further updates (unless the dragged pool
     *     is this list or one of its ancestors).
     * For ACTION_DRAG_ENTERED, ACTION_DRAG_EXITED, and ACTION_DRAG_ENDED: Updates the element's
     *     background, to highlight it when the dragged element could be dropped.
     *     Returns true.
     * For ACTION_DROP: If the object was dragged with no operation specified (which occurs if it
     *     originates elsewhere in the list, rather than the drawer), or the operation matches this
     *     list's operation, inserts the new sub-pool into the PoolList. If there was a different
     *     operation specified, then the new pool will be put into a sub-list (using that operation)
     *     with the child it's dropped before/after, then that sub-list will be put in place of the
     *     existing child.
     *     Returns true if a new pool is inserted (which should always be the outcome).
     *
     * item: Which child value of the PoolList are we dropping before/after
     * isAfterItem: True if this is the handler for the after section, false if it's the before section.
     * dropSegmentView: The view for the area to drop into.
     * event: DragEvent from the Android framework.
     * Returns: As per event types listed above.
     */
    private fun handleDropSegmentDrag(item: DicePoolValue,
                                      isAfterItem: Boolean,
                                      dropSegmentView: View,
                                      event: DragEvent): Boolean {
        if(event.localState !is DiceDragData) return false //Something else is being dragged
        var dragData = event.localState as DiceDragData

        if(dragData.sourcePool === item.displayFragment || draggedPoolIsAncestor(dragData)) {
            //Can't drag an item to itself or a list into itself
            if(dragData.draggedDie == null) return false //Can drag a die out of a pool though
        }

        if(event.action == DragEvent.ACTION_DRAG_STARTED) {
            //These interstitial drop sections can accept any dice pool value (other than ancestor
            //lists).
            return true
        }
        else if(event.action == DragEvent.ACTION_DRAG_ENTERED) {
            //Update highlight to show drop could happen here
            dropSegmentView.setBackgroundColor(highlightBackground)
            return true
        }
        else if(event.action == DragEvent.ACTION_DRAG_EXITED || event.action == DragEvent.ACTION_DRAG_ENDED) {
            //Update highlight to show drop will no longer happen here
            dropSegmentView.setBackgroundColor(defaultBackground)
            return true
        }
        else if(event.action == DragEvent.ACTION_DROP) {
            if(dragData.operation == null || dragData.operation === value.operation)
                return handleMatchingOpDrop(item, isAfterItem, event, dragData)
            else
                return handleDifferentOpDrop(item, isAfterItem, event, dragData)
        }
        return false
    }

    /**
     * Determines whether a DragEvent is for dragging either this PoolList, or a PoolList that's a
     * direct ancestor of it. Used because a pool cannot accept itself or an ancestor being dropped
     * into it.
     * Note that this check may be unnecessary, as Android drag-and-drop does not appear to allow a
     * drag from one view to be handled by that view's descendants.
     * dragData: Data for the dice drag event
     * Returns: False if the drag data is not for a pool, or is for a pool that's not in this pool's
     *          ancestry.
     */
    private fun draggedPoolIsAncestor(dragData: DiceDragData): Boolean {
        if(dragData.draggedPool == null) return false
        if(dragData.sourcePool == null) return false

        //Iterate through parents and compare to the source pool for the event
        var ancestor: PoolList? = this.value
        while(ancestor != null) {
            if(ancestor.displayFragment === dragData.sourcePool) return true
            ancestor = ancestor.parentList
        }
        return false
    }

    /**
     * Case for handleDropSegmentDrag, when an object is dropped into a before/after item area, and
     * the dropped object either has no operation specified or its operation matches this list's.
     * Inserts the dropped value into this PoolList, at an index determined based on the child it
     * should be inserted before/after & whether it's before or after. If the value is an individual
     * die, will create a new pool of that die and insert that.
     * item: Which child value of the PoolList are we dropping before/after
     * isAfterItem: True if this is the handler for the after section, false if it's the before section.
     * event: DragEvent from the Android framework.
     * dragData: What's being dropped, as extracted from the event's local state.
     * Returns: False only if the drag data has no pool and no die, which should not happen.
     */
    private fun handleMatchingOpDrop(item: DicePoolValue, isAfterItem: Boolean,
                                    event: DragEvent, dragData: DiceDragData): Boolean {
        var insertionIndex = value.indexOf(item)
        if(isAfterItem) insertionIndex++
        if(dragData.draggedDie != null) {
            //Dragged item was an individual die, so create a pool for it and add that.
            AddSubPool(DicePool(dragData.draggedDie), insertionIndex, true)
            return true
        }
        else if(dragData.draggedPool != null) {
            //Add the dragged item to the list
            AddSubPool(dragData.draggedPool, insertionIndex, true)
            return true
        }
        return false
    }

    /**
     * Case for handleDropSegmentDrag, when an object is dropped into a before/after item area, and
     * the dropped object has an operation specified that is different from this list's. First
     * removes the existing child that the object was dropped before/after. Then creates a new
     * PoolList containing the dropped object and the old child. Finally, inserts that new list at
     * the prior location of the old child. If the dropped object is an individual die, the new list
     * will use a pool of a single instance of that die, and the order of the members of the new list
     * will be based on whether this is a before section or an after section.
     * item: Which child value of the PoolList are we dropping before/after
     * isAfterItem: True if this is the handler for the after section, false if it's the before section.
     * event: DragEvent from the Android framework.
     * dragData: What's being dropped, as extracted from the event's local state.
     * Returns: true
     */
    private fun handleDifferentOpDrop(item: DicePoolValue, isAfterItem: Boolean,
                                      event: DragEvent, dragData: DiceDragData): Boolean {

        //Dragging an existing pool in the list should have null operation, and therefore will
        //use handleMatchingOpDrop.
        if(dragData.sourcePool != null)
            throw Exception("Pool dragged with operation")
        //This shouldn't be called with no operation specified.
        if(dragData.operation == null)
            throw Exception("Creating sublist requires operation")

        //Remove the existing item from the list; it will be added to the new sub-list.
        var insertionIndex = value.indexOf(item)
        RemoveSubPool(insertionIndex)

        var newList = PoolList(dragData.operation)
        var newPoolValue = dragData.draggedPool ?: DicePool(dragData.draggedDie!!, 1)
        if(isAfterItem) {
            //Dropping after existing list item, so existing list item is first in the sub-list
            newList.addPool(item.Duplicate())
            newList.addPool(newPoolValue)
        }
        else {
            //Dropping before existing list item, so new item is first in the sub-list
            newList.addPool(newPoolValue)
            newList.addPool(item.Duplicate())
        }

        //Add the sub-list where the existing item used to be.
        AddSubPool(newList, insertionIndex, true)
        return true
    }

    /**
     * Determines whether the list can accept an object dropped directly on the list. This accepts
     * the same things as the before/after sections (i.e. everything), but will only do so if the
     * list is empty.
     * dragData: What's being dropped, as extracted from the event's local state.
     * Returns: True if the dragged object can be dropped on the list
     */
    override fun acceptsDraggedObject(dragData: DiceDragData): Boolean {
        //Accept entries to the list directly iff the list is empty; otherwise the before/after
        //segments should be used.
        return value.size == 0 && !draggedPoolIsAncestor(dragData)
    }

    /**
     * Accepts an object that has been dropped on an empty list. The object will be inserted as the
     * first element of the list. An individual die that's dropped will be inserted as a dice pool
     * of that type of size 1. Does not make a new sublist if the object has a different operator,
     * as there's nothing else for the operator to act on.
     * view: The highlight element for the list, which is a view covering the whole area of the list.
     * event: DragEvent from the Android framework
     */
    override fun draggedObjectDropped(view: View, event: DragEvent, dragData: DiceDragData): Boolean {
        var newSubPool = dragData.draggedPool ?: DicePool(dragData.draggedDie!!, 1)
        AddSubPool(newSubPool)
        return true
    }

    /**
     * Override the default behavior for when a pool is dragged & accepted elsewhere. If this is the
     * top-level list of the pool (i.e. it has no parent list) then we can't remove this from its
     * parent. The presumed intent of dragging the top-level list out is to clear the pool, so in
     * that case just remove all the children of this list.
     *
     * If this is not the top-level list, revert to default behavior.
     * view: The drag handle view that started the drag
     * event: DragEvent from the Android framework
     * dragData: Will contain this pool value
     * Returns: True if the list was moved or cleared
     */
    override fun dragFromFragEnded(view: View, event: DragEvent, dragData: DiceDragData): Boolean {
        if(value.parentList != null) return super.dragFromFragEnded(view, event, dragData)
        else {
            if(!event.result || !dragEnabled) return false

            //Trying to delete the top-level list; instead just clear it
            while(hasSubFrags) RemoveSubPool(0)
            return true
            //todo: this can be more efficient
        }
    }
}