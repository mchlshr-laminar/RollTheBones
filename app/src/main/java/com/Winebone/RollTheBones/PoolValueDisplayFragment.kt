package com.Winebone.RollTheBones

import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.view.*
import android.widget.FrameLayout
import androidx.fragment.app.Fragment
import com.Winebone.RollTheBones.dicepool.*

private const val DEFAULT_POOL_COLUMNS = 3

/**
 * Copyright (c) 2021-2025 Michael Usher
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of
 * the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

/**
 * Ancestor class of fragments for displaying dice pool values. This is above the parameterized
 * parent class PoolValueDisplayFragment<T>, as instances of that with different type parameters are
 * different classes, so they can't share pointers.
 */
abstract class PoolDisplayParent : Fragment() {
    protected var dragEnabled = false
    protected var numColumns = DEFAULT_POOL_COLUMNS
    var afterDisplayBuiltCallback: (() -> Unit)? = null

    /**
     * Pass in the DicePoolValue this fragment displays, as well as whether drag-and-drop is
     * enabled in this context and how many columns to display dice in. This is used by the
     * companion object factory method.
     * setValue: The pool value this fragment is displaying
     * setAllowDrag: Enable drag and drop? True in the configuration activity.
     * setNumColumns: The number of columns dice are displayed in. Relevant to fragments displaying
     *                dice pools.
     */
    protected abstract fun applyArguments(setValue: DicePoolValue, setAllowDrag: Boolean, setNumColumns: Int)

    /**
     * Consumers of pool value display fragments may wish to get a pointer to the value that frag
     * displays, without needing to know which specific type it's displaying. However, the actual
     * value member is stored in PoolValueDisplayFragment as type T, for convenience within the
     * fragments. This function is therefore overridden by PoolValueDisplayFragment to return that
     * value cast up to DicePoolValue.
     * Returns: Value the fragment is displaying, as a DicePoolValue
     */
    abstract fun valueAsDPV(): DicePoolValue

    /**
     * Induce the DicePoolValue this fragment displays to generate a random result. Any child pools
     * will generate values within that, and the pool values will send specific results to the
     * setResults function on their display fragments.
     * Returns: The final result value of the whole pool.
     */
    abstract fun Roll(): Int

    /**
     * When a DicePoolValue generates a result, it calls this for its display fragment. The fragment
     * will implement this to update its display, if necessary.
     * result: The results object from the fragment's value
     */
    abstract fun setResults(result: DicePoolResult)

    /**
     * A callback may be set to be called after the display has been built. This is used by the top-
     * level pool list for generating a result when the activity is started, after the display has
     * been fully built. In service of that, descendant fragments will have this set to report back
     * to their parent that they (and their own descendants) have been fully built.
     *
     * Also used to cause the display to scroll to a new fragment after it has been built.
     *
     * An afterDisplayBuiltCallback will only be used once; it will be cleared after use.
     */
    protected fun runAfterDisplayBuiltCallback() {
        afterDisplayBuiltCallback?.invoke()
        afterDisplayBuiltCallback = null
    }

    companion object {
        /**
         * Generates a display fragment for the specific type of dice pool value to be displayed.
         * Passes it its value and returns it as a PoolDisplayParent.
         */
        @JvmStatic
        fun newInstance(setValue: DicePoolValue, setAllowDrag: Boolean,
                setNumColums: Int = DEFAULT_POOL_COLUMNS): PoolDisplayParent {
            var result: PoolDisplayParent? = null
            if(setValue is DicePool) result = DicePoolDisplay()
            else if(setValue is FixedNumber) result = FixedNumDisplayFragment()
            else if(setValue is PoolList) result = PoolListDisplayFragment()
            else throw Exception("Unexpected PoolDisplayParent subclass")
            result.applyArguments(setValue, setAllowDrag, setNumColums)
            return result
        }
    }

}

/**
 * Parent class for display fragments. The value should be of the specific type that's being
 * displayed, so there's a one-to-one correspondence between parameterizations of this and specific
 * subclasses for pool value types. However there is still a lot of common logic that uses the
 * value, but does not need to know which specific type it is.
 * T: What type of DicePoolValue is this fragment displaying?
 */
abstract class PoolValueDisplayFragment<T: DicePoolValue> : PoolDisplayParent() {
    lateinit var value: T
        protected set
    protected var defaultBackground: Int = 0
    protected var highlightBackground: Int = 0
    protected var highlightElement: View? = null  //Highlight element accepts drag and drop, and
                                                  //is highlighted when one is hovering over it.
    protected var dragHandleElement: View? = null
    private lateinit var fragmentView: FrameLayout

    /**
     * Set up the fragment for display. Individual display fragments should not override
     * onCreateView, instead their type-specific setup will be in buildView, which is called from
     * here.
     */
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        defaultBackground = Color.TRANSPARENT
        var highlightValue = TypedValue()
        requireActivity().theme.resolveAttribute(R.attr.colorControlHighlight, highlightValue, true)
        highlightBackground = highlightValue.data

        //Call type-specific setup
        fragmentView = inflater.inflate(R.layout.fragment_pool_value_display, container, false)
                as FrameLayout
        buildView(inflater, fragmentView, container, savedInstanceState)

        //Set up drag handling
        (highlightElement ?: fragmentView).setOnDragListener(::handleDragEvent)
        if(dragEnabled && dragHandleElement != null) dragHandleElement?.setOnTouchListener(::dragHandleGrabHandler)
        return fragmentView
    }

    /**
     * Called during onCreateView to do any setup specific to a type of pool display fragment. The
     * highlightElement and dragHandleElement should be assigned here.
     * inflater: from inflater parameter of onCreateView
     * fragmentView: the container view inflated in onCreateView. Type-specific things should be put
     *     inside this.
     * container: Parent of fragmentView, as passed to onCreateView
     * savedInstanceState: as passed to onCreateVView
     */
    abstract fun buildView(inflater: LayoutInflater, fragmentView: FrameLayout, container: ViewGroup?,
                           savedInstanceState: Bundle?)

    /**
     * Called in the PoolDisplayParent factory method to set the DicePoolValue the created fragment
     * shows, and some other parameters.
     * setValue: DicePoolValue this fragment will display
     * setAllowDrag: True if being created for the config activity, so the fragment can be dragged
     *               and dropped.
     * setNumColumns: How many columns to show dice in
     */
    override fun applyArguments(setValue: DicePoolValue, setAllowDrag: Boolean, setNumColumns: Int) {
        @Suppress("UNCHECKED_CAST")
        value = setValue as T
        value.displayFragment = this
        dragEnabled = setAllowDrag
        numColumns = setNumColumns
    }

    override fun valueAsDPV(): DicePoolValue {
        //PoolDisplayParent function to allow retrieving the value from pointers of that type.
        return value
    }

    /**
     * Handler for dragging this pool value elsewhere. In the config activity, this is set as the
     * onTouch handler for the drag handler element.
     * view: the drag handle element
     * event: the motion event
     */
    private fun dragHandleGrabHandler(view: View, event: MotionEvent): Boolean {
        if(event.action != MotionEvent.ACTION_DOWN) return false

        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        return beginDrag(fragmentView)
    }

    /**
     * Begin a drag event to drag this pool elsewhere. The drag shadow will be the whole view for the
     * fragment.
     * view: View that the touch event to initiate dragging came from.
     */
    protected open fun beginDrag(view: View): Boolean {
        if(!dragEnabled) return false

        //Use a duplicate, as the drop event adds to the target before ended event removes
        var dragData = DiceDragData(value.Duplicate(), null, this, null)
        var shadowBuilder = View.DragShadowBuilder(fragmentView)
        view.startDragAndDrop(null, shadowBuilder, dragData, 0)
        return true
    }

    /**
     * Handle an incoming drag event, either from a pool fragment or the drawer. Only pays
     * attention to events which have DiceDragData in their local state (i.e. they're for dragging
     * dice pool stuff) and only if drag and drop is enabled (i.e. in the config activity). Note
     * that the pool list fragment has a separate handler for dropping in between children of the
     * list.
     *
     * If the drag event is dragging this fragment's value, this fragment will either remove itself
     * from its parent or do nothing, depending on the result of the event.
     *
     * If the drag event is from the drawer or another fragment and has a value this fragment can
     * accept, will highlight the drop area when hovering and accept the element when dropping.
     *
     * view: View to which the drag handler is attached, presumably the fragment view
     * event: Drag event from drag and drop framework
     * Returns: For drag started events, true means this handler wants to receive further updates.
     *          For drop events, true means the dragged object has been accepted and the event is
     *          consumed.
     */
    private fun handleDragEvent(view: View, event: DragEvent): Boolean {
        if(!dragEnabled) return false
        if(event.localState !is DiceDragData) return false
        var dragData = event.localState as DiceDragData

        if(dragData.sourcePool === this) return handleDragFromFragment(view, event, dragData)
        else return handleDragFromElsewhere(view, event, dragData)
    }

    /**
     * Handle drag events that originate from this fragment. Registers for further updates when the
     * drag begins. Discards the event if the pool is dropped on itself. If the event ends
     * elsewhere, either removes this pool from its parent or does nothing.
     * view: View to which the drag handler is attached, presumably the fragment view
     * dragData: contains the pool being dragged, i.e. this fragment's value
     * event: Drag event from drag and drop framework
     * Returns: As per handleDragEvent.
     */
    private fun handleDragFromFragment(view: View, event: DragEvent, dragData: DiceDragData): Boolean {
        if(event.action == DragEvent.ACTION_DRAG_STARTED) return true
        else if(event.action == DragEvent.ACTION_DROP) return false //Do nothing if dropping a fragment on itself
        else if(event.action == DragEvent.ACTION_DRAG_ENDED) return dragFromFragEnded(view, event, dragData)
        return false
    }

    /**
     * Helper for handleDragFromFragment for when the drag event ends (somewhere other than inside
     * this fragment). If the drag was accepted there, this pool will be removed from its parent
     * list.
     * view: View to which the drag handler is attached, presumably the fragment view
     * dragData: contains the pool being dragged, i.e. this fragment's value
     * event: Drag event from drag and drop framework
     * Returns: As per handleDragEvent.
     */
    protected open fun dragFromFragEnded(view: View, event: DragEvent, dragData: DiceDragData): Boolean {
        if(!event.result || !dragEnabled) return false

        val parentFrag = value.parentList?.displayFragment as? PoolListDisplayFragment
        if(parentFrag == null) return false

        //Removal can't happen within the DragEvent, as that results in a
        //concurrent modification exception
        view.post(Runnable({ -> parentFrag.RemoveSubPool(value) }))
        return true
    }

    /**
     * Handler for a drag event originating in a different pool fragment or the drawer. When the
     * drag event begins, determines whether this pool can accept its contents being dropped here
     * and registers for further updates if it can. Updates the background of the highlight element
     * when the drag enters/leaves the fragment. If the drag is dropped here, makes a fine decision
     * whether to accept it. If it is accepted, reports that to the activity and makes any necessary
     * updates.
     * view: View to which the drag handler is attached, presumably the fragment view
     * dragData: contains the object being dragged, either a dice pool or a die.
     * event: Drag event from drag and drop framework
     * Returns: As per handleDragEvent.
     */
    private fun handleDragFromElsewhere(view: View, event: DragEvent, dragData: DiceDragData): Boolean {
        var returnValue = false
        if(event.action == DragEvent.ACTION_DRAG_STARTED) {
            returnValue = acceptsDraggedObject(dragData)
        }
        else if(event.action == DragEvent.ACTION_DRAG_ENTERED) {
            highlightElement?.setBackgroundColor(highlightBackground)
            returnValue = true
        }
        else if(event.action == DragEvent.ACTION_DRAG_EXITED || event.action == DragEvent.ACTION_DRAG_ENDED) {
            highlightElement?.setBackgroundColor(defaultBackground)
            returnValue = true
        }
        else if(event.action == DragEvent.ACTION_DROP) {
            returnValue = draggedObjectDropped(view, event, dragData)
            if(returnValue) reportDropAccepted(this.fragmentView)
        }
        return returnValue
    }

    /**
     * Determines whether a dice pool object dragged from elsewhere can be accepted if dropped into
     * this fragment. By default always returns false; subclasses should override if they actually
     * want to accept anything.
     * dragData: contains the object being dragged, either a dice pool or a die.
     * Returns: True if this fragment can accept the dragged value, and wants to track the drag.
     */
    protected open fun acceptsDraggedObject(dragData: DiceDragData): Boolean {
        return false
    }

    /**
     * Handler for when a dragged object from elsewhere is dropped in this fragment. Makes the final
     * decision on accepting the dragged object, then does any updates needed to accept the object.
     * By default returns false and does nothing; subclasses should override this if they want to
     * actually accept objects.
     * view: View to which the drag handler is attached, presumably the fragment view
     * dragData: contains the object being dragged, either a dice pool or a die.
     * event: Drag event from drag and drop framework
     * Returns: True to accept the dragged object, false otherwise.
     */
    protected open fun draggedObjectDropped(view: View, event: DragEvent, dragData: DiceDragData): Boolean {
        return false
    }

    /**
     * When a dropped object is accepted, informs the activity that that has happened. This is used
     * by the configuration activity to scroll to the location was dropped at.
     * intoView: Where was the drop accepted? Either the fragment view, or the area for dropping
     *           between children of a list.
     */
    protected fun reportDropAccepted(intoView: View?) {
        if(intoView == null) return
        if(activity is OnDropAcceptedHandlingActivity) {
            (activity as OnDropAcceptedHandlingActivity).DropAccepted(intoView)
        }
    }
}

/**
 * Implemented by activities (i.e. the config activity) that want to know when pool fragments they
 * contain accept dropped objects.
 */
interface OnDropAcceptedHandlingActivity {
    fun DropAccepted(intoView: View)
}