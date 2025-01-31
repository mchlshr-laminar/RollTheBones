package com.Winebone.RollTheBones

import android.animation.ValueAnimator
import android.content.Intent
import android.os.Bundle
import android.view.DragEvent
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.math.MathUtils
import androidx.core.view.marginBottom
import com.Winebone.RollTheBones.dicepool.DiceDragData
import com.Winebone.RollTheBones.dicepool.DieTypesManager
import com.Winebone.RollTheBones.dicepool.PoolList

/**
 * Copyright (c) 2021-2025 Michael Usher
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of
 * the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

/**
 * Activity for building a dice pool. The screen is divided into two sections: at the top is an area
 * showing the current setup of the pool (using a RollArea fragment) and at the bottom is a drawer
 * with things that can be added to the pool (a DieTypesDrawer fragment). Things can be dragged from
 * the drawer into the pool, or dragged around in the pool, or dragged out of the pool and dropped
 * in the drawer.
 *
 * In this activity, the roll button in the RollArea frag will launch the RollingActivity and pass
 * it the current pool configuration.
 *
 * May receive through the ActivityArguments mechanism:
 * pool: PoolList object for the initial setup of the pool
 * poolName: String, the name that a pool being loaded was saved as. Displayed in the action bar,
 *           and will be the default name in the save dialogue.
 * If no arguments are to be passed, ActivityArguments.SetIdentifier must be called on the intent
 * prior to launching this activity.
 */
class PoolConfiguration : AppCompatActivity(),
    OnDropAcceptedHandlingActivity,
    RollArea.PoolNameDisplayingActivity {

    private var dividerGrabOffset: Float = 0f
    private lateinit var dividerView: View
    private lateinit var rollAreaFrag: RollArea

    //When re-showing the drawer after hiding, this is where it should be.
    private var cachedDividerMargin: Int = 0

    //Duration of animation to show/hide the drawer. Set to medium animation time.
    private var marginAnimDuration: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(null)
        setContentView(R.layout.activity_pool_configuration)
        DieTypesManager.InitializeManager(this)
        marginAnimDuration = resources.getInteger(android.R.integer.config_mediumAnimTime).toLong()

        //Use a custom action bar element
        setSupportActionBar(findViewById(R.id.configActivityToolbar))

        //Handler to adjust how much of the screen the drawer takes up when the user drags the divider
        dividerView = findViewById(R.id.frameDivider)
        dividerView.setOnTouchListener(::onDividerTouch)
        dividerView.setOnDragListener(::handleDragEvent)

        //Set up roll button action to go to the rolling activity. Callback returns false to prevent
        //the RollArea from trying to roll dice in this activity.
        rollAreaFrag = supportFragmentManager.findFragmentById(R.id.configRollFragContainer) as RollArea
        rollAreaFrag.rollActionCallback = fun(v: View): Boolean {
                goToRollingActivity()
                return false
            }

        //If this was launched to edit an existing pool, retrieve that. Otherwise start with an
        //empty pool. If the process was killed in the background, the current configuration will
        //also be stored here.
        var pool: PoolList =
            ActivityArguments.RetrieveArgument<PoolList>(intent, "pool") ?: PoolList()
        var poolName: String? = ActivityArguments.RetrieveArgument<String>(intent, "poolName")
        rollAreaFrag.setPoolList(pool, true)
        rollAreaFrag.poolName = poolName
    }

    /**
     * Home button is a hamburger menu button to go to the main menu (activity MainMenuV2).
     */
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if(item.itemId == android.R.id.home) {
            rollAreaFrag.goToMainMenu()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    /**
     * When the activity finishes, clear any arguments it received.
     */
    override fun onPause() {
        super.onPause()

        if(isFinishing) {
            ActivityArguments.ClearArgument(intent, "pool")
            ActivityArguments.ClearArgument(intent, "poolName")
        }
    }

    /**
     * Store the current state of the pool in ActivityArguments. This only works if this activity's
     * intent had an identifier in it before the activity was launched.
     */
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        //Overwrite old arguments if the configuration has been changed
        ActivityArguments.StoreArgument(
            intent,
            "pool",
            rollAreaFrag.poolFragment!!.value.Duplicate())
        ActivityArguments.StoreArgument(intent, "poolName", rollAreaFrag.poolName)
    }

    /**
     * Handler for sliding the top edge of the drawer up and down the screen. Attached to
     * dividerView as the touch event listener.
     * v: Should be dividerView
     * event: Touch event from Android
     * Returns: True, unless v somehow wasn't dividerView.
     */
    private fun onDividerTouch(v: View, event: MotionEvent): Boolean {
        if(v !== dividerView){
            return false
        }

        if(event.action == MotionEvent.ACTION_DOWN) {
            //When the user starts sliding the divider, record the vertical position within the
            //divider where the user touched. Divider will be moved to maintain that vertical offset
            //relative to the changing finger position.
            dividerGrabOffset = event.y
        }
        else if(event.action == MotionEvent.ACTION_MOVE) {
            //When the user's finger moves, adjust the bottom margin of the divider to maintain
            //relative position.
            //event.y is how far from the top of dividerView the finger currently is.
            //dividerView.marginBottom - event.y as margin would put the top of the divider
            //    at the finger
            //Add dividerGrabOffset to that. Moves the top of the divider up by the distance above
            //    the finger it was when the drag started.
            val targetMargin: Int = (dividerView.marginBottom - event.y + dividerGrabOffset).toInt()
            //Clamp margin so the divider doesn't go off the top/bottom of the container its in.
            val maxMargin: Int = (dividerView.parent as View).height - dividerView.height
            setDividerBottomMargin(MathUtils.clamp(targetMargin, 0, maxMargin))
        }
        return true
    }

    /**
     * DragEvent handler attached to the drawer/pool divider. Will temporarily hide the drawer when
     * an object is being dragged from the drawer. Does not hide the drawer when dragging from the
     * pool, as dragging to the drawer is drawer is how things are deleted.
     * view: Should be dividerView
     * event: DragEvent from Android
     * Returns: True when a drag event from the drawer begins (to register for further updates),
     *          false otherwise.
     */
    @Suppress("UNUSED_PARAMETER")
    fun handleDragEvent(view: View, event: DragEvent): Boolean {
        //Hide the drawer when dragging from the drawer
        if(event.localState !is DiceDragData ||
            (event.localState as DiceDragData).sourcePool != null) {
                //If this isn't dragging a DicePool object we don't care about it.
                //If this is dragging from the pool (sourcePool != null) we don't want to hide the
                //drawer, so don't handle that drag event.
                cachedDividerMargin = 0
                return false
        }

        if(event.action == DragEvent.ACTION_DRAG_STARTED) {
            //A dice pool object is being dragged from the pool. Set the bottom margin of the divider
            //to 0 (to hide the drawer) and cache the margin to restore when the drag ends.
            cachedDividerMargin = dividerView.marginBottom
            animateDividerBottomMargin(0, marginAnimDuration)
            return true
        }

        if(event.action == DragEvent.ACTION_DRAG_ENDED) {
            //A drag from the drawer has ended. Restore the bottom margin from before it began.
            animateDividerBottomMargin(cachedDividerMargin, marginAnimDuration)
        }

        return false
    }

    /**
     * Animate changing the vertical position of the pool/drawer divider. Used to show/hide the
     * drawer when dragging. Position will change from its current position to a target position
     * over a set duration.
     * toMargin: Target bottom margin for the divider
     * duration: Time to animate over
     */
    private fun animateDividerBottomMargin(toMargin: Int, duration: Long) {
        var animator = ValueAnimator.ofInt(dividerView.marginBottom, toMargin)
        animator.duration = duration
        animator!!.addUpdateListener(
            { valueAnimator -> setDividerBottomMargin(valueAnimator.animatedValue as Int) })
        animator.start()
    }

    /**
     * Sets the position of the pool/drawer divider based on bottom margin. Used for sliding the
     * divider, and in an update listener for the show/hide animation.
     * margin: Bottom margin to set on the divider
     */
    private fun setDividerBottomMargin(margin: Int) {
        var lp = dividerView.layoutParams as ConstraintLayout.LayoutParams
        lp.bottomMargin = margin
        dividerView.layoutParams = lp
    }

    /**
     * OnDropAcceptedHandlingActivity function. Dice pool fragments will call this when something is
     * dropped into them (after creation of the fragment's elements). Scrolls the roll are fragment
     * to the newly-created pool value fragment.
     * intoView: View where the drop landed. RollArea will scroll to this.
     */
    override fun DropAccepted(intoView: View) {
        rollAreaFrag.ScrollToView(intoView, cachedDividerMargin)
    }

    /**
     * RollArea.PoolNameDisplayingActivity function. Called by the roll area when the pool is saved.
     * Sets the action bar title to the name of the pool.
     * name: The name the pool was saved with
     */
    override fun DisplayPoolName(name: String) {
        supportActionBar?.title = name
    }

    /**
     * Launch the rolling activity and pass it the pool in its current state. Attached in the
     * RollArea callback for when the roll button is pressed (rollActionCallback).
     */
    private fun goToRollingActivity() {
        var intent = Intent(this, RollingActivity::class.java)

        var pools: PoolList = (rollAreaFrag.poolFragment?.value?.Duplicate() ?: PoolList()) as PoolList
        ActivityArguments.StoreArgument(intent, "pool", pools)
        ActivityArguments.StoreArgument(intent, "poolName", rollAreaFrag.poolName)
        ActivityArguments.StoreArgument(intent, "rollOnOpen", true);

        startActivity(intent)
    }
}