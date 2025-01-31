package com.Winebone.RollTheBones

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.DragEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.animation.doOnEnd
import androidx.core.animation.doOnRepeat
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.Lifecycle
import com.Winebone.RollTheBones.dicepool.DiceDragData
import com.Winebone.RollTheBones.dicepool.DicePool
import com.Winebone.RollTheBones.dicepool.DicePoolResult
import java.util.*

private const val DEFAULT_POOL_COLUMNS = 3

/**
 * Copyright (c) 2021-2025 Michael Usher
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of
 * the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

/**
 * Sets the dimension ratio constraint on an image view (which is the ratio of width to height) to
 * match the intrinsic dimensions of the image. This allows the image view to be scaled by another
 * constraint without introducing empty space around the actual image.
 */
fun ImageView.setIntrinsicDimensionRatio() {
    (layoutParams as ConstraintLayout.LayoutParams).dimensionRatio =
        drawable.intrinsicWidth.toString() + ":" + drawable.intrinsicHeight.toString()
}

/**
 * PoolValueDisplayFragment to display a DicePool, i.e. a group of one type of die. Dice are displayed
 * in rows, with each row having at most n dice in it, where n is the number of columns passed to
 * the factory method. If this is displayed in the config activity, it will have a border and an
 * icon displaying the type of die.
 *
 * Will accept individual dice dragged from the drawer or other pools, which increments the size of
 * the pool by one. Will also accept whole pools dragged from elsewhere with the same die type. As
 * well as dragging the whole pool, individual dice can be dragged out of the pool. If that dragged
 * die is accepted elsewhere, the size of the pool will be decremented by one. If this results in
 * the pool being empty, it will be removed from its parent list as though the whole pool had been
 * dragged.
 */
@Suppress("FunctionName")
class DicePoolDisplay : PoolValueDisplayFragment<DicePool>() {
    private var nextColumn: Int = 0
    private lateinit var poolRows: LinearLayout

    //Maintain linear lists of the image rows and individual images, for removing images and rows,
    //and updating the drawables in the images for a result.
    private var rowsList = LinkedList<LinearLayout>()
    private var imagesList = LinkedList<ImageView>()
    private var lastResults: DicePoolResult? = null

    private var rollAnimDuration: Long = 0 //Will be set to the short animation duration.
    //Map of in-progress transitions for dice images changing to new drawables. These are added by
    //animateImageDrawable when the animation begins, and removed by an ObjectAnimator callback when
    //the animation is done. Key is the dice image being changed. Value is the ObjectAnimator doing
    //the animation and the target drawable the image will show after the transition.
    private var imageAnimators = HashMap<ImageView, Pair<ObjectAnimator, Drawable>>()

    /**
     * PoolValueDisplayFragment function to construct the contents of the fragment. Creates an image
     * view for each die in the pool. The the views will be initialized with the config faces of the
     * die type. If the activity generates a result on initialization, that must be done after the
     * display is built by way of the afterDisplayBuiltCallback.
     *
     * The highlight element (which accepts dice dragged from elsewhere) is the linear layout containing
     * the rows of dice images (id poolRows)
     * The drag handle element is at the top of the layout with ID poolDragHandle.
     */
    override fun buildView(inflater: LayoutInflater, fragmentView: FrameLayout, container: ViewGroup?,
                            savedInstanceState: Bundle?) {
        var layoutRoot: View = inflater.inflate(R.layout.fragment_dice_pool_display, fragmentView, true)
        poolRows = layoutRoot.findViewById<LinearLayout>(R.id.poolRows)
        highlightElement = poolRows
        rollAnimDuration = resources.getInteger(android.R.integer.config_shortAnimTime).toLong()

        if(dragEnabled) {
            //In activities where things can be edited, show the drag handle, outline, and icon for
            //the die type in the pool.
            dragHandleElement = layoutRoot.findViewById(R.id.poolDragHandle)
            dragHandleElement!!.visibility = View.VISIBLE

            var poolTypeIcon = layoutRoot.findViewById<ImageView>(R.id.poolTypeIcon)
            poolTypeIcon.setImageDrawable(value.typeOfDie.GetConfigResult().faceDrawable)

            var poolLayout = layoutRoot.findViewById<ConstraintLayout>(R.id.dicePoolLayout)
            poolLayout.background = ResourcesCompat.getDrawable(requireContext().resources,
                R.drawable.pool_frag_outline, null)
        }

        //Add the dice
        for(i in (1..value.quantity)) {
            addDieToView()
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
     * Increases the number of dice in the pool, and adds image views to the display for the new
     * dice. New dice images are initialized with the config face. New images are added to the end
     * of the last row. If that row is full, a new is created and they're added there.
     * number: How many new dice to add. Must be >= 0.
     */
    private fun addDie(number: Int = 1) {
        value.IncDice(number)
        for(i in (1..number)) {
            addDieToView()
        }
    }

    /**
     * Decreases the number of dice in the pool by 1, and removes one dice image is removed from the
     * end of the last row of images. If that row becomes empty, the row will be removed. If the
     * size of the pool is currently 0, no die will be removed. The die is also removed from the
     * linear list of dice images.
     * Returns: True if a die was removed, false otherwise (i.e. pool was empty).
     */
    private fun removeDie(): Boolean {
        if(value.quantity == 0) return false
        value.DecDice()
        imagesList.removeLast()

        nextColumn = (nextColumn - 1).mod(numColumns)
        if(nextColumn == 0) {
            //We're removing the last die in the row
            removeRow()
        }
        else {
            rowsList.last.removeViewAt(rowsList.last.childCount-1)
        }
        return true
    }

    /**
     * Adds one die image to the display. It will be added to the end of the last row if there's room.
     * If there isn't, a new row will be added and it will be added there. The image is also added
     * to the linear list of dice images.
     *
     * Note that this does not affect the number of dice in the DicePool this fragment is for; this
     * is a helper for addDie, and for the initial generation of the display for an existing DicePool.
     */
    private fun addDieToView() {
        if(nextColumn == 0) addRow() //Last row is full, add a new row

        //Create the image for the new die
        var newImageContainer = activity?.layoutInflater?.inflate(R.layout.die_image_layout, rowsList.last, false) as ConstraintLayout
        var newImage = newImageContainer.findViewById<ImageView>(R.id.dieImageImage)
        newImage.setImageDrawable(value.typeOfDie.GetConfigResult().faceDrawable)
        newImage.setOnLongClickListener(::beginDieDrag)
        newImage.setIntrinsicDimensionRatio()

        //Add the image to the last row, and to the linear list of images
        rowsList.last.addView(newImageContainer)
        imagesList.add(newImage)
        nextColumn = (nextColumn + 1) % numColumns
    }

    /**
     * Creates a new row for dice images and adds it to the display (as well as the list of rows).
     * This does not add any dice images or update the nextColumn counter; it should be called as a
     * helper for addDieToView.
     */
    private fun addRow() {
        var newRowContainer = activity?.layoutInflater?.inflate(R.layout.pool_row_layout, poolRows, false) as ConstraintLayout
        var newRow = newRowContainer.findViewById<LinearLayout>(R.id.poolRowContents)
        newRow.weightSum = numColumns.toFloat()
        rowsList.add(newRow) //Outer element is a ConstraintLayout
        poolRows.addView(newRowContainer)
    }

    /**
     * Removes the last row element from the display and list of rows. This does not update the nextColumn
     * counter or list of dice images, and does not check whether there are any rows; it should be
     * called as a helper for removeDie.
     */
    private fun removeRow() {
        poolRows.removeViewAt(poolRows.childCount-1)
        rowsList.removeLast()
    }

    /**
     * Induce the DicePool this fragment is for to generate a random result, then set the displayed
     * result from that. A dice pool has no child fragments, so if this is called directly it doesn't
     * need to tell the value object to inform fragments.
     * Returns: The sum of the dice rolled in this pool
     */
    override fun Roll(): Int {
        setResults(value.Evaluate())
        return lastResults!!.total
    }

    /**
     * Sets all the images in this pool to the config face, but with the alpha set to 0.3. Used to
     * show that the pool has not been rolled yet.
     */
    fun ShowGhostedConfigDice() {
        var drawable = value.typeOfDie.GetConfigResult().faceDrawable
        for(img: ImageView in imagesList) {
            setImageDrawable(img, drawable, true)
        }
    }

    /**
     * Updates the dice images to reflect the most recent result rolled. If no result has been rolled,
     * the images will be set to the config face drawables.
     * animate: If true, the images will fade out and back in, with the drawable being swapped while
     *          they're invisible. Each die image will have its transition delayed by a random amount
     *          so they don't all pulse simultaneously.
     */
    private fun updateImages(animate: Boolean = false) {
        if(lastResults == null) {
            var drawable = value.typeOfDie.GetConfigResult().faceDrawable
            for(img: ImageView in imagesList) {
                setImageDrawable(img, drawable)
            }
        }
        else {
            var newImages: Array<Drawable> = lastResults!!.GetDrawables()
            var index = 0
            for (img: ImageView in imagesList) {
                if(animate && lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                    animateImageDrawable(img, newImages[index])
                }
                else {
                    setImageDrawable(img, newImages[index])
                }
                index++
            }
        }
    }

    /**
     * Animates the transition of an image view from one drawable to another. The animation is a fade
     * from alpha = 1 to 0 to 1, with the drawable being swapped at the midpoint when the view is
     * invisible. The duration of the transition is the Android-configured short animation time, and
     * its beginning will be delayed by a random amount between 0 and the short animation time.
     * img: Which image view to update
     * newDrawable: The drawable that image view should show when the transition is complete.
     */
    private fun animateImageDrawable(img: ImageView, newDrawable: Drawable) {
        //If there is an animated transition already in progress for this image view, the first thing
        //to do is immediately end that transition (back to alpha = 1) and set the image to show what
        //it was going to show at the end of that transition.
        imageAnimators[img]?.apply {
            setImageDrawable(img, this.second)
            this.first.end()
        }

        //Create the animator, and put it in the map of animators so it can be ended (as above) if
        //another transition for this image is started.
        var animator = ObjectAnimator.ofFloat(img, "alpha",1f, 0f)
        imageAnimators[img] = Pair(animator, newDrawable)

        //Fade out, then reverse to fade back in.
        animator.repeatMode = ValueAnimator.REVERSE
        animator.repeatCount = 1
        animator.duration = rollAnimDuration/2
        animator.startDelay = (0..(rollAnimDuration/2)).random() //Delay by a random amount up to the
                                                                 //duration of the animation.

        //Swap the drawable at the midpoint, while it's invisible
        animator.doOnRepeat { valueAnimator -> setImageDrawable(img, newDrawable) }
        //When the animation is done, remove it from the map of in-progress transitions, so later
        //transitions don't need to worry about it.
        animator.doOnEnd { imageAnimators.remove(img) }
        animator.start()
    }

    /**
     * Immediately set an ImageView to show a new drawable, and update its dimension ration constraint
     * based on the drawable's intrinsic dimensions (ImageView's aspect ratio should match the
     * drawable's). Used for updating dice images.
     * img: The ImageView to update
     * newDrawable: The drawable it should display
     * ghosted: If true, the image will have 0.5 alpha
     */
    private fun setImageDrawable(img: ImageView, newDrawable: Drawable, ghosted: Boolean = false) {
        img.setImageDrawable(newDrawable)
        img.setIntrinsicDimensionRatio()

        //Set the alpha. 0.3 if ghosted, 1 if not.
        if(ghosted) img.alpha = 0.3f
        else img.alpha = 1f
    }

    /**
     * Called by the DicePool when a new result has been generated. Stores that as the last result
     * and updates the dice images (with animation) to show that result.
     * result: Result of the DicePool roll.
     */
    override fun setResults(result: DicePoolResult) {
        lastResults = result
        //Lifecycle check is a holdover from when the rolling activity was rolling the pool before
        //the display was fully built; currently the afterDisplayBuiltCallback is used to avoid that.
        if(lifecycle.currentState.isAtLeast(Lifecycle.State.CREATED)) updateImages(true)
    }

    /**
     * Override the default dragFromFragEnded behavior, as dragging from a DicePool may mean dragging
     * an individual die out as well as dragging the whole pool.
     *
     * If the drag was not accepted elsewhere, does nothing as per default implementation.
     * If it was the whole pool being dragged, or it was the only die in the pool, reverts to default
     *     behavior (i.e. removes the pool from its parent list).
     * Otherwise (dragging a die when there's more than one left), just decrement the size of the
     *     pool by one.
     */
    override fun dragFromFragEnded(view: View, event: DragEvent, dragData: DiceDragData): Boolean {
        //If the dragged object wasn't accepted elsewhere, do nothing.
        if(!event.result || !dragEnabled) return false

        if(dragData.draggedDie != null) {
            //Event from dragging a single die
            removeDie()

            //If there are no dice left, remove the pool
            if(value.quantity == 0) return super.dragFromFragEnded(view, event, dragData)
            else return true
        }
        else {
            //Event from dragging the whole pool; behavior as per default.
            return super.dragFromFragEnded(view, event, dragData)
        }
    }

    /**
     * A DicePool can accept drops of individual dice of the pool's type (dragged from another pool
     * or from the drawer), or an entire pool of the same type of die.
     */
    override fun acceptsDraggedObject(dragData: DiceDragData): Boolean {
        if(dragData.draggedDie == null) {
            //Dragging a pool value; must be a pool with the same die type
            if(dragData.draggedPool !is DicePool) return false
            return dragData.draggedPool.typeOfDie.name == value.typeOfDie.name
        }
        else {
            //Dragging a die; must have the same type
            return dragData.draggedDie.name == value.typeOfDie.name
        }
    }

    /**
     * An object has been dropped into this pool. At this point we should only be receiving events
     * for objects this pool can accept (per acceptsDraggedObject), so we don't actually check the
     * die type here. If an individual die is dropped, the pool size is incremented by one. If a
     * whole pool is dropped, all of that pool's dice will be added (i.e. increase the size of this
     * pool by the dropped pool's size). Note that the source pool will be removed by its own
     * dragFromFragEnded handler if the last die in that pool or the whole pool was dropped.
     */
    override fun draggedObjectDropped(view: View, event: DragEvent, dragData: DiceDragData): Boolean {
        if(dragData.draggedDie != null) {
            //Individual die dropped, increment size by 1
            addDie()
        }
        else {
            //Entire pool dropped, increase size by that pool's size
            if(dragData.draggedPool !is DicePool) throw Exception("Pool trying to accepted dropped non-pool")
            addDie(dragData.draggedPool.quantity)
        }
        return true
    }

    /**
     * Start dragging an individual die out of this pool. Will be set as the long click handler for
     * dice images.
     * view: The image view which was long-clicked to drag a die
     * Returns: True if a drag event is started (i.e. drag is enabled), false otherwise.
     */
    private fun beginDieDrag(view: View): Boolean {
        if(dragEnabled) {
            var shadowBuilder = View.DragShadowBuilder(view)
            var data = DiceDragData(null, value.typeOfDie, this)
            view.startDragAndDrop(null, shadowBuilder, data, 0)
            return true
        }
        return false
    }
}