package com.Winebone.RollTheBones

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.view.*
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.core.math.MathUtils
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentTransaction
import androidx.lifecycle.Lifecycle
import com.Winebone.RollTheBones.dicepool.*
import com.google.android.material.floatingactionbutton.FloatingActionButton

private const val ARG_INIT_DESCRIPTION = "initDescription"
private const val EDGE_SCROLL_MIN_DISTANCE = 15
private const val EDGE_SCROLL_MAX_DISTANCE = 75
private const val EDGE_SCROLL_INTERVAL = 100.toLong()
private const val MIN_SCALE = 0.2f
private const val MAX_SCALE = 1f
private const val SCROLL_CLEARANCE = 128

/**
 * Copyright (c) 2021-2025 Michael Usher
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of
 * the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

/**
 * Fragment which displays a pool/pool list. Used as part of the configuration activity and the
 * rolling activity. Shows a PoolList with a PoolListDisplayFragment and all its children. Contains
 * functionality to save the pool, set it as the startup pool, or roll the pool. Also contains
 * functionality to scroll up and down when a pool object is being dragged near the top or bottom of
 * the fragment, and to scroll to a specific view.
 *
 * The PoolList fragment will be built according to which activity this is in, i.e. it will either
 * have drag and drop enabled or not.
 */
class RollArea : Fragment() {
    private lateinit var poolsLayout: LinearLayout
    private lateinit var scrollArea: ScrollView
    private lateinit var rollButton: FloatingActionButton
    private var allowDragRemoval = false
    private lateinit var scaleDetector: ScaleGestureDetector
    private var scrollAnimDuration: Long = 0 //Duration of the animation to scroll to a view. Will be
                                             //set to the medium animation time.

    /**
     * Called when the roll button is pressed, before generating a result. This is used by the
     * configuration activity to prevent attempting to generate results there and switch to the
     * rolling activity first.
     * View parameter: The roll button
     * Returns: If this returns false, a result will not be generated and afterRollCallback will not
     *          be called
     */
    var rollActionCallback: ((View) -> Boolean)? = null

    /**
     * Called when the roll button is pressed, after a result is generated. Used by the rolling
     * activity to update the text showing the total rolled.
     * Int parameter: The numeric total result of rolling the pool.
     */
    var afterRollCallback: ((Int) -> Unit)? = null

    /**
     * Called after the PoolList fragment and all its descendants have had their display built. Used
     * by the rolling activity to make an initial roll after the activity opens.
     */
    var afterDisplayBuiltCallback: (() -> Unit)? = null

    //The fragment displaying the top-level pool list fragment
    var poolFragment: PoolListDisplayFragment? = null
        private set

    //The name the pool is saved as. If this is in an activity that displays the pool name (which,
    //it will be) informs the activity.
    var poolName: String? = null
        set(value) {
            field = value
            if(activity is PoolNameDisplayingActivity && value != null) {
                (activity as PoolNameDisplayingActivity).DisplayPoolName(value)
            }
        }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreateView(
            inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        // Inflate the layout for this fragment
        var view = inflater.inflate(R.layout.fragment_roll_area, container, false)
        scrollAnimDuration = resources.getInteger(android.R.integer.config_mediumAnimTime).toLong()

        //Find views and build the display
        rollButton = view.findViewById<FloatingActionButton>(R.id.roll_button)
        poolsLayout = view.findViewById(R.id.poolsLayout)
        scrollArea = view.findViewById(R.id.rollAreaScroll)
        if(poolFragment != null) buildPoolDisplay()

        //Set up the handler for the roll button and for pinch zooming.
        var clickListener = View.OnClickListener(::onRollButtonClick)
        rollButton.setOnClickListener(clickListener)
        scaleDetector = ScaleGestureDetector(requireContext(), ScaleHandler(poolsLayout))
        scrollArea.setOnTouchListener(::touchListener)

        //Set up handlers for edge scrolling
        for (id in intArrayOf(R.id.bottomScrollActivator, R.id.topScrollActivator)) {
            var activatorElement = view.findViewById<View>(id)
            activatorElement.setOnDragListener(ScrollHandler(this, id == R.id.topScrollActivator))
        }

        setHasOptionsMenu(true)
        return view
    }

    /**
     * Set up the toolbar menu options. Has options to save the pool, set the pool as the startup
     * pool, and go to the main menu.
     */
    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.roll_area_options, menu)
    }

    /**
     * Handle toolbar menu items being selected. Has options to save the pool, set the pool as the
     * startup pool, and go to the main menu.
     */
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when(item.itemId) {
            R.id.optionMenuSave -> {
                saveDialog()
                true
            }
            R.id.optionMenuLoad -> {
                goToMainMenu()
                true
            }
            R.id.optionsMenuSetStartup -> {
                setStartupPool()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    /**
     * Called from the save menu option. Shows a dialog to set the name of the pool, validates the
     * name, confirms overwriting an existing pool if necessary, then attempts to save the pool.
     * If the pool has a name (i.e. if it's been saved already or was an existing pool that was
     * loaded) the name entry field will initially be populated with that name.
     * Flow is:
     * saveDialog -> onSaveAccepted --------------------------\           /--> <error>
     *           \-> <cancel>   \  \---> confirmSaveOverwrite --> savePool --> <done>
     *                           \-> <Validation failed>    \--> <cancel>
     **/
    private fun saveDialog() {
        if(poolFragment == null) return

        var builder = AlertDialog.Builder(requireContext())
        builder.setTitle(getString(R.string.save_pool_name_entry_popup_title))

        //var nameEntry = EditText(requireContext())
        var nameEntry = layoutInflater.inflate(R.layout.save_pool_name_entry_field, null) as EditText
        //nameEntry.inputType = InputType.TYPE_CLASS_TEXT
        nameEntry.setText(poolName)
        //nameEntry.filters = arrayOf<InputFilter>(InputFilter.LengthFilter(60))
        //nameEntry.setSelectAllOnFocus(true)
        //nameEntry.contentDescription = getString(R.string.save_pool_name_entry_content_description)
        builder.setView(nameEntry)

        builder.setNegativeButton(getString(R.string.popup_button_cancel_no_action), null)
        builder.setPositiveButton(getString(R.string.popup_button_save_item), DialogInterface.OnClickListener(
            fun (dialogue: DialogInterface, whichButton: Int) {
                onSaveAccepted(nameEntry.text.toString())
            }
        ))

        builder.create().show()
    }

    /**
     * User has accepted the save dialog and entered a name. First validates the name. If validation
     * fails, displays the issues in a popup. If it succeeds, proceeds to either confirming
     * overwriting an existing pool (if necessary) or straight to saving the pool.
     * saveName: Text entered in the name entry dialog.
     */
    private fun onSaveAccepted(saveName: String) {
        val validationResult = validateSaveName(saveName)

        if(!validationResult.first) {
            //Validation failed, show a popup
            var builder = AlertDialog.Builder(requireContext())
            builder.setTitle(getString(R.string.problem_saving_pool_popup_name))
            builder.setMessage(getString(R.string.pool_validation_header) + " " + validationResult.second)
            builder.setPositiveButton(getString(R.string.popup_button_ok_no_action), null)
            builder.create().show()
            scrollArea.performHapticFeedback(HapticFeedbackConstants.REJECT)
        }
        else if(saveName != poolName
            && SavedPoolManager.HasSavedPool(requireContext().applicationContext, saveName)) {
            //Validation succeeded, but there's an existing pool with that name. Confirm overwrite.
            //Note that this isn't called if the name to save with matches the name this pool already
            //has.
            confirmSaveOverwrite(saveName)
        }
        else {
            //Validation succeeded, save the pool.
            savePool(saveName)
        }
    }

    /**
     * Checks whether a name is valid for saving a pool. To be valid, the name must not be empty,
     * and must not contain the character used to separate fields in the list file.
     * saveName: Proposed name for the pool
     * Returns: (True, "") if validation succeeded
     *          (False, <validation message>) if validation failed
     */
    private fun validateSaveName(saveName: String): Pair<Boolean, String> {
        if(saveName == "") return Pair(false, getString(R.string.pool_validation_issue_no_name))
        if(saveName.contains(POOL_FIELD_SEPARATOR)) {
            return Pair(false, getString(R.string.pool_validation_issue_illegal_char))
        }
        return Pair(true, "")
    }

    /**
     * Called if the user attempts to save a pool with the name of another pool that already exists.
     * Shows a popup to confirm they want to overwrite it. If they accept, saves the pool. If they
     * don't, exits without doing anything.
     * saveName: The name to save the pool with.
     */
    private fun confirmSaveOverwrite(saveName: String) {
        var builder = AlertDialog.Builder(requireContext())
        builder.setTitle(getString(R.string.confirm_overwrite_pool_popup_name))
        builder.setMessage(getString(R.string.confirm_overwrite_pool_popup_message, saveName))
        builder.setNegativeButton(getString(R.string.popup_button_cancel_no_action), null)
        builder.setPositiveButton(getString(R.string.popup_button_overwrite_existing_saved_item), DialogInterface.OnClickListener(
            fun (dialogue: DialogInterface, whichButton: Int) {
                savePool(saveName)
            }
        ))
        builder.create().show()
    }

    /**
     * Saves the pool. Assumes all checks have already been done. If an error occurs while saving,
     * displays a popup showing it. Does haptic feedback: CONFIRM if saving worked, REJECT if there
     * was an error.
     * saveName: Name to save the pool under.
     */
    private fun savePool(saveName: String) {
        var saveResult: Pair<Boolean, String> =
            SavedPoolManager.SavePool(requireContext().applicationContext,
                saveName,
                poolFragment!!.value)

        if(saveResult.first) {
            //Saving was successful.
            scrollArea.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            poolName = saveName
        }
        else {
            //Saving failed, show a dialog with the error.
            scrollArea.performHapticFeedback(HapticFeedbackConstants.REJECT)
            var builder = AlertDialog.Builder(requireContext())
            builder.setTitle(getString(R.string.problem_saving_pool_popup_name))
            builder.setMessage(getString(R.string.problem_saving_pool_popup_message) + " " + saveResult.second)
            builder.setPositiveButton(getString(R.string.popup_button_ok_no_action), null)
            builder.create().show()
        }
    }

    /**
     * Launch the main menu activity.
     * allowReturn: If false, this activity will be finished and, if the menu was previously open,
     *              will clear the activity stack to go to that instance instead of creating a new
     *              one. This is used if the pool couldn't be opened properly, in particular if the
     *              pool has placeholder dice that were not replaced.
     */
    fun goToMainMenu(allowReturn: Boolean = true) {
        var intent = Intent(requireContext(), MainMenuV2::class.java)
        if(!allowReturn) {
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            activity?.finish()
        }
        startActivity(intent)
    }

    /**
     * Attempts to set this pool as the pool to open when the application starts. If there's an
     * error doing that, shows a popup with that error message instead.
     */
    private fun setStartupPool() {
        var poolToSave = poolFragment?.value
        var saveResult =
            if(poolToSave != null) {
                SavedPoolManager.SetInitialPool(requireContext().applicationContext, poolToSave)
            }
            else {
                Pair(false, "No pool to save")
            }

        if(!saveResult.first) {
            //Saving pool failed, show a dialog with the error.
            var builder = AlertDialog.Builder(requireContext())
            builder.setTitle(getString(R.string.problem_saving_pool_popup_name))
            builder.setMessage(getString(R.string.problem_saving_startup_pool_popup_message) + " " + saveResult.second)
            builder.setPositiveButton(getString(R.string.popup_button_ok_no_action), null)
            builder.create().show()
        }
    }

    /**
     * Sets the top-level pool list to display. If the display has already been built, rebuilds it.
     * If the pool has placeholder dice, shows the MissingDieDialog popup to try to replace them.
     * pools: PoolList to show
     * setAllowDrag: If set, update the flag in this fragment for whether drag and drop is enabled.
     *               The pool list fragment will be built according to that.
     */
    fun setPoolList(pools: PoolList, setAllowDrag: Boolean? = null) {
        allowDragRemoval = setAllowDrag ?: allowDragRemoval
        setPoolCheckPlaceholders(pools)
    }

    /**
     * Helper for setPoolList. Checks whether a pool has any placeholder dice. If it does, launch the
     * popup to replace them. If it doesn't, display the pool.
     * If the popup is displayed and the user elects not to replace the placeholder, goes back to
     * the main menu.
     * pools: PoolList to display
     */
    private fun setPoolCheckPlaceholders(pools: PoolList) {
        var nextPlaceholder = pools.FindFirstPlaceholder()
        if(nextPlaceholder == null) {
            //No placeholders, display the pool
            setPoolNoPlaceholders(pools)
            return
        }

        var missingDialog = MissingDieDialog(nextPlaceholder.name,
                fun() { goToMainMenu(false) },
                fun(dieName: String) { setPoolReplacePlaceholder(pools, nextPlaceholder, dieName) })
        missingDialog.show(requireActivity().supportFragmentManager, null)
    }

    /**
     * Helper for setPoolList->setPoolCheckPlaceholders. Called if a replacement is selected for a
     * placeholder that was found. Replace the placeholder with a type from the manager, then
     * goes back to setPoolCheckPlaceholder to check for more placeholders.
     * pools: PoolList to be displayed
     * placeholder: The first placeholder type found in the list
     * newDieName: Name of the type to replace the placeholder with.
     */
    private fun setPoolReplacePlaceholder(pools: PoolList, placeholder: DieType, newDieName: String) {
        pools.ReplaceDieType(placeholder.name, newDieName)
        setPoolCheckPlaceholders(pools)
    }

    /**
     * Helper for setPoolList->setPoolCheckPlaceholders. Called if there are no placeholders in the
     * pool or they've all been replaced. Sets the passed pool as the pool this area displays. If
     * display has already been built, calls buildPoolDisplay to rebuild it.
     * pools: PoolList to display.
     */
    private fun setPoolNoPlaceholders(pools: PoolList) {
        var oldPoolFragment: PoolListDisplayFragment? = poolFragment
        poolFragment = PoolDisplayParent.newInstance(pools, allowDragRemoval) as PoolListDisplayFragment
        if(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) buildPoolDisplay(oldPoolFragment)
    }

    /**
     * Builds/rebuilds the display to show the current pool. Helper for both setPoolDisplay and
     * onCreateView. If there's an existing pool display fragment, removes it. If there's a callback
     * for after display is built, passes that to the top level of the new display fragment as its
     * afterDisplayBuiltCallback.
     * oldDisplay: Existing pool display fragment to remove
     */
    private fun buildPoolDisplay(oldDisplay: PoolListDisplayFragment? = null) {
        var fragMan = activity?.supportFragmentManager as FragmentManager
        var transaction: FragmentTransaction = fragMan.beginTransaction()

        if(oldDisplay != null) transaction.remove(oldDisplay)
        transaction.add(poolsLayout.id, poolFragment!!)
        poolFragment!!.afterDisplayBuiltCallback = this.afterDisplayBuiltCallback

        transaction.commit()
    }

    /**
     * Handler for pressing the roll button. First calls the roll action callback if present. If
     * that returns false, exits without doing anything else. Otherwise, generates a result (calls
     * Roll) and calls the after roll callback, passing it the value of the result.
     * view: The roll button
     */
    private fun onRollButtonClick(view: View) {
        rollButton.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        var rollPool: Boolean = rollActionCallback?.invoke(view) ?: true
        if(rollPool) {
            var sum =Roll()
            afterRollCallback?.invoke(sum)
        }
    }

    /**
     * Make the top-level pool list generate a new result. Die images will be updated by members of
     * the DicePoolValue hierarchy informing their display fragments of the result during their
     * Evaluate calls.
     * Returns: Total result of the roll
     */
    fun Roll(): Int {
        rollButton.setImageResource(R.drawable.ic_roll_icon)
        if(poolFragment != null) return poolFragment!!.Roll()
        return 0
    }

    /**
     * Class to handle scrolling the view up and down when something is being dragged near the top
     * or bottom of the view. Should be attached as the on drag listener for invisible activator views
     * near the top and bottom of the scrollable area. Scrolling will occur when the dragged object is
     * within on of those views, and the speed of scrolling is proportional to its depth into that
     * view.
     *
     * Scrolling does not occur when the object is dragged in from outside the scrollable area (i.e.
     * from above for the top activator or below for the bottom one). This operates according to a
     * state machine:
     * <drag started> ---> WAITING ---> INITIAL_ENTRY ---> SCROLLING ---> OUT_OF_BOUNDS
     *                       /\               |                /\              |
     *                       |               \/                |---------------|
     *                       |-----------SUPPRESSED
     *
     * -When the dragged object first enters the activator (DragEvent.ACTION_DRAG_ENTERED), the state
     *     goes to INITIAL_ENTRY.
     * -During the DragEvent.ACTION_DRAG_LOCATION that follows immediately, this checks whether the
     *     dragged object entered from the scrollable area or outside (based on the event's vertical
     *     location and the forScrollingUp flag). If it's from outside, the state goes to SUPPRESSED,
     *     otherwise it goes to SCROLLING.
     * -Scrolling occurs when in the SCROLLING state.
     * -On a DragEvent.ACTION_DRAG_EXITED event, if the state was SCROLLING it goes to OUT_OF_BOUNDS.
     *     If it was SUPPRESSED it goes back to WAITING, to test direction the next time it enters.
     * -On a DragEvent.ACTION_DRAG_ENTERED event from the OUT_OF_BOUNDS state, go back to SCROLLING
     *     without checking direction.
     *
     * This exists because the user may wish to drag something to a part of the dice pool which is
     * currently off screen, and they can't manually scroll while dragging the object. However we
     * don't want to start scrolling immediately when something's dragged from the drawer into the
     * pool, thus the SUPPRESSED state. OUT_OF_BOUNDS is separate from WAITING as dragging something
     * further than the edge of the pool area then back in probably does still indicate a desire to
     * scroll.
     *
     * rollFragment: The fragment this is in, i.e. the view to scroll. Could've been done as an
     *               inner class instead.
     * forScrollingUp: Set to true for the listener attached to the activator at the top. Dragging
     *                 into that activator scrolls up, and the "depth" referenced above means distance
     *                 from the bottom of the view. If false, the activator will cause scrolling down
     *                 and "depth" is distance from the top.
     */
    private class ScrollHandler(val rollFragment: RollArea, val forScrollingUp: Boolean): View.OnDragListener {
        enum class ActivatorState {
            WAITING, INITIAL_ENTRY, SCROLLING, SUPPRESSED, OUT_OF_BOUNDS
        }

        private val scrollDirection = if(forScrollingUp) -1 else 1
        private val scrollDistanceRange = EDGE_SCROLL_MAX_DISTANCE - EDGE_SCROLL_MIN_DISTANCE
        private var state = ActivatorState.WAITING
        private var scrollDepth: Float = 0f

        /**
         * Handle DragEvents from Android. Updates the state machine based on events, and causes
         * scrolling if the state requires. See the ScrollHandler class comment for a description of
         * the state machine.
         * view: The scroll activator view
         * event: DragEvent from Android
         * Returns: True for ACTION_DRAG_STARTED events, to receive later events
         *          True for ACTION_DRAG_ENTERED events, to receive ACTION_DRAG_LOCATION events
         *              while the drag is in the activator.
         *          False otherwise (does not consume drops)
         */
        override fun onDrag(view: View?, event: DragEvent?): Boolean {
            if(!rollFragment.allowDragRemoval) return false //Don't scroll if drag and drop isn't enabled.
            if(view == null || event == null) return false //Shouldn't happen, here for type inference
            if(event.localState !is DiceDragData) return false //Something else being dragged

            if(event.action == DragEvent.ACTION_DRAG_ENDED) {
                //The drag has ended. Reset the state machine for the next drag. No further action
                //needed
                state = ActivatorState.WAITING
                return false
            }
            else if(event.action == DragEvent.ACTION_DRAG_STARTED) {
                //A drag has started. State machine begins in WAITING state, and we want to receive
                //later events for this drag. No further action needed.
                state = ActivatorState.WAITING
                return true
            }

            //While the drag is in the activator view, determine its "depth" into the activator. This
            //is a value on [0,1] representing the portion of the way from the bottom of the view to
            //the top for the scroll-up activator, or from the top to the bottom for the scroll-down
            //activator.
            if(event.action == DragEvent.ACTION_DRAG_LOCATION) {
                scrollDepth = MathUtils.clamp(event.y / view.height, 0f, 1f)
                if(forScrollingUp) scrollDepth = 1 - scrollDepth
            }

            var returnValue = false //Most events return false
            when (state) {
                ActivatorState.WAITING -> {
                    //When the drag enters the activator in the WAITING state, the first LOCATION
                    //event should test whether to suppress or scroll. Also return true in order to
                    //receive the LOCATION events.
                    if(event.action == DragEvent.ACTION_DRAG_ENTERED) {
                        returnValue = true
                        state = ActivatorState.INITIAL_ENTRY
                    }
                }
                ActivatorState.INITIAL_ENTRY -> {
                    if(event.action == DragEvent.ACTION_DRAG_LOCATION) {
                        //Determine whether we want to scroll on the first LOCATION event. If we do
                        //go to the SCROLLING state and start the scrolling runnable. Otherwise go
                        //to the SUPPRESSED state.
                        var enteredFromBottom = (event.y * 2) > view.height
                        var shouldScroll = (enteredFromBottom == forScrollingUp)

                        if(shouldScroll) {
                            state = ActivatorState.SCROLLING
                            rollFragment.scrollArea.post(Runnable { -> continueScrolling() })
                        } else {
                            state = ActivatorState.SUPPRESSED
                        }
                    }
                }
                ActivatorState.SCROLLING -> {
                    if(event.action == DragEvent.ACTION_DRAG_EXITED) {
                        //Exited from SCROLLING state: go to OUT_OF_BOUNDS.
                        state = ActivatorState.OUT_OF_BOUNDS
                    }
                }
                ActivatorState.SUPPRESSED -> {
                    if(event.action == DragEvent.ACTION_DRAG_EXITED) {
                        //Exited from SUPPRESSED state: go back to WAITING.
                        state = ActivatorState.WAITING
                    }
                }
                ActivatorState.OUT_OF_BOUNDS -> {
                    if(event.action == DragEvent.ACTION_DRAG_ENTERED) {
                        //Entered from OUT_OF_BOUNDS state: go to SCROLLING state and start the
                        //scrolling runnable.
                        state = ActivatorState.SCROLLING
                        rollFragment.scrollArea.post(Runnable { -> continueScrolling() })
                        returnValue = true
                    }
                }
            }
            return returnValue
        }

        /**
         * Called periodically if we are scrolling. Initially called in a runnable when entering the
         * SCROLLING state. If we're still in the Scrolling state, start the next scroll animation
         * and post the next call to this, delayed for after that animation finishes. If we're not
         * in the SCROLLING state do nothing; this ends the chain of scrolling runnables.
         *
         * The distance scrolled during the next animation is based on the depth calculated in the
         * most recent ACTION_DRAG_LOCATION event. If depth is 0 then the distance will be
         * EDGE_SCROLL_MIN_DISTANCE. If depth is 1 distance is EDGE_SCROLL_MAX_DISTANCE. The
         * duration of the animation, i.e. the interval on which this is run, is
         * EDGE_SCROLL_INTERVAL.
         */
        fun continueScrolling() {
            if(state == ActivatorState.SCROLLING) {
                var scrollAmount = (scrollDistanceRange * scrollDepth) + EDGE_SCROLL_MIN_DISTANCE
                val targetY = (rollFragment.scrollArea.scrollY + (scrollDirection * scrollAmount)).toInt()

                var animator = ObjectAnimator.ofInt(rollFragment.scrollArea, "scrollY", targetY)
                animator.duration = EDGE_SCROLL_INTERVAL
                animator.start()

                rollFragment.scrollArea.postDelayed(Runnable { -> continueScrolling() }, EDGE_SCROLL_INTERVAL)
            }
        }
    }

    /**
     * Scroll the roll area to show a specific child view. If any part of that view is within a given
     * clearance of the top or bottom of the roll area, scroll the roll area so the top of that view
     * is below the top of the roll area by that clearance. If the view is on screen, does nothing.
     * target: View to scroll to
     * bottomMargin: Additional clearance at the bottom of the roll area we want the view to be
     *               above. This is used because this may be called when the drawer is hidden, and
     *               we want the view to remain visible once the drawer is shown again.
     */
    fun ScrollToView(target: View, bottomMargin: Int = 0) {
        val scrollY = findVerticalOffsetFromAncestor(target, scrollArea)
        if(scrollY == -1) return //View is not in the scroll area

        val goesOffScreen = (scrollY - SCROLL_CLEARANCE) < scrollArea.scrollY ||
                (scrollY + target.height + bottomMargin + SCROLL_CLEARANCE) > (scrollArea.scrollY + scrollArea.height)
        if(goesOffScreen) {
            var animator = ObjectAnimator.ofInt(scrollArea, "scrollY", scrollY - SCROLL_CLEARANCE)
            animator.duration = scrollAnimDuration
            animator.start()
        }
    }

    /**
     * Determine how far down vertically the top of one view is from the top of an ancestor view.
     * This is calculated as the sum of "top" values in the ancestry chain from the target view up
     * to (but not including) the specified ancestor.
     * target: View to find the vertical offset of
     * relativeTo: Ancestor to find vertical offset from
     * Returns: Distance from top edge of ancestor to top edge of target. If target is not a
     *          descendant of relativeTo, returns -1.
     */
    private fun findVerticalOffsetFromAncestor(target: View, relativeTo: View): Int {
        var result = 0
        var ancestor: View? = target
        while(ancestor != relativeTo && ancestor != null) {
            result += ancestor.top
            ancestor = ancestor.parent as? View
        }
        return if(ancestor == relativeTo) { result } else { -1 }
    }

    /**
     * Gesture handler to zoom the roll area based on pinching. Should be passed to a
     * ScaleGestureDetector. That ScaleGestureDetector should have its onTouchEvent method called in
     * the RollArea's touch listener. Adjusts the scale of the passed view (which will be the pools
     * layout) based on the detected scale gesture amount. Scale will not go above MAX_SCALE or
     * MIN_SCALE.
     */
    private class ScaleHandler(val scaledView: View): ScaleGestureDetector.SimpleOnScaleGestureListener() {

        /**
         * Called when the gesture detector detects a scale gesture. Does the scaling.
         * detector: The gesture detector this is in
         * Returns: Should return true, unless this isn't actually in a detector
         */
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            if(detector == null) return false

            val inputScale: Float = detector.scaleFactor * scaledView.scaleX
            val setScale: Float = MathUtils.clamp(inputScale, MIN_SCALE, MAX_SCALE)
            scaledView.scaleX = setScale
            scaledView.scaleY = setScale
            return true
        }
    }

    /**
     * Attached to the roll area's scroll view as the touch listener. Calls the scroll area's base
     * onTouchEvent to preserve the ability to scroll, but also calls the scale detector's
     * onTouchEvent.
     * view: The scroll area
     * event: MotionEvent from Android
     */
    @Suppress("UNUSED_PARAMETER")
    private fun touchListener(view: View, event: MotionEvent): Boolean {
        //Use "or"; we don't want to short-circuit as both of these handlers should be run
        return scrollArea.onTouchEvent(event) or scaleDetector.onTouchEvent(event)
    }

    /**
     * Interface which may be implemented by activities containing a RollArea. If the activity
     * containing this RollArea implements this, it will be informed of what the name of the pool in
     * the RollArea is.
     */
    interface PoolNameDisplayingActivity {
        /**
         * Called when the name of the pool in the RollArea changes, i.e. when its saved.
         * name: The new name of the pool.
         */
        fun DisplayPoolName(name: String)
    }

    companion object {
        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @param initDescription Deprecated and ignored.
         * @return A new instance of fragment RollArea.
         */
        @JvmStatic
        fun newInstance(initDescription: String) =
            RollArea().apply {
                arguments = Bundle().apply {
                    putString(ARG_INIT_DESCRIPTION, initDescription)
                }
            }
    }
}