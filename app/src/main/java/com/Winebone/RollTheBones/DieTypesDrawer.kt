package com.Winebone.RollTheBones

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.fragment.app.Fragment
import com.Winebone.RollTheBones.dicepool.*

//Size of the text used as a drag shadow for fixed values
private const val SHADOW_TEXT_SIZE = 128f

/**
 * Copyright (c) 2021-2025 Michael Usher
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of
 * the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

/**
 * Fragment used in the config activity to show things which can be dragged into the pool. This
 * comprises three sections: built-in die types, custom die types, and fixed values. The first two
 * of these are horizontally scrolling lists of the die types, and can be expanded/collapsed by
 * tapping on their headers. The last section has an EditText to enter the value, and a drag handle
 * to drag that value to the pool.
 *
 * In the upper right right is a spinner to select the operator to use when dragging into the pool.
 */
class DieTypesDrawer : Fragment(), AdapterView.OnItemSelectedListener {
    private lateinit var customDiceLayout: LinearLayout
    private lateinit var noCustomDiceBanner: ConstraintLayout
    private lateinit var fixedValInput: EditText
    private var currentOperation: Operation = Operation.DefaultOperation

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?
                              ): View? {
        // Inflate the layout for this fragment
        var view = inflater.inflate(R.layout.fragment_die_types_drawer, container, false)

        //Set up options in the operation spinner
        var operationSpinner = view.findViewById<Spinner>(R.id.drawerOpSelector)
        operationSpinner.adapter = OperationSpinnerAdapter(requireContext(),
            R.layout.operation_spinner_item_layout, R.layout.operation_spinner_dropdown_layout,
            Operation.SelectableOperations)
        operationSpinner.onItemSelectedListener = this

        //Add the built-in dice
        var builtInDiceLayout = view.findViewById<LinearLayout>(R.id.drawerBuiltInDiceLayout)
        for(dieType: DieType in DieTypesManager.builtInTypes) {
            addDieToSegment(builtInDiceLayout, dieType)
        }

        //Find layout for custom dice; will be built in onResume as they may change
        customDiceLayout = view.findViewById<LinearLayout>(R.id.drawerCustomDiceLayout)
        noCustomDiceBanner = view.findViewById<ConstraintLayout>(R.id.drawerNoDiceBanner)

        //Set up the fixed value input
        fixedValInput = view.findViewById<EditText>(R.id.fixedValInput)
        var fixedValDragBox = view.findViewById<ImageView>(R.id.fixedValDragHandle)
        fixedValDragBox.setOnTouchListener(
            fun(v: View, event: MotionEvent): Boolean {
                if(event.action == MotionEvent.ACTION_DOWN) {
                    beginNumberDrag(v)
                    return true
                }
                return false
            }
        )

        //Handler for dragging things from the pool
        view.setOnDragListener(::handleDieDrag)

        //Handle segment visibility toggle
        for(ids in arrayOf(Triple(R.id.drawerBuiltInDiceHeaderLayout, R.id.drawerBuiltInDiceCollapseIcon, R.id.drawerBuiltInDiceArea),
                           Triple(R.id.drawerCustomDiceHeaderLayout, R.id.drawerCustomDiceCollapseIcon, R.id.drawerCustomDiceContainer))) {
            var headerLayout = view.findViewById<View>(ids.first)
            var collapseIcon = view.findViewById<ImageView>(ids.second)
            var segmentArea = view.findViewById<View>(ids.third)

            headerLayout.setOnClickListener(
                fun(v: View) { toggleSegmentVisibility(segmentArea, collapseIcon) }
            )
        }

        return view
    }

    /**
     * Custom dice may have been created while this activity was in the background, so reload that
     * list.
     */
    override fun onResume() {
        super.onResume()

        customDiceLayout.removeAllViews()
        noCustomDiceBanner.visibility = if(DieTypesManager.customTypeCount == 0) { View.VISIBLE }
                                        else { View.GONE }
        for(dieType: DieType in DieTypesManager.customTypes) {
            addDieToSegment(customDiceLayout, dieType)
        }
    }

    /**
     * Display-building helper function to create a tile for a die type and put it in one of the
     * drawer sections (built-in dice or custom dice). Tile is inflated from die_drawer_item_layout.
     * The die can be dragged either by long pressing on the tile, or touching the drag handle.
     * segment: Layout of the segment to add the die to
     * die: Die to add a tile for
     */
    @SuppressLint("ClickableViewAccessibility")
    fun addDieToSegment(segment: LinearLayout, die: DieType) {
        var inflater = requireActivity().layoutInflater

        //Inflate the tile's layout and set the image & name in it.
        var newDieImageContainer = inflater.inflate(R.layout.die_drawer_item_layout, segment, false)
        var newDieImg = newDieImageContainer.findViewById<ImageView>(R.id.dieImage)
        newDieImg.setImageDrawable(die.GetConfigResult().faceDrawable)
        newDieImg.setIntrinsicDimensionRatio()
        var dieNameText = newDieImageContainer.findViewById<TextView>(R.id.dieNameText)
        dieNameText.text = die.name

        //Add it to the section
        segment.addView(newDieImageContainer)

        //Long click on any part of the tile starts dragging the die
        var longClickListener = View.OnLongClickListener(
            fun (v: View): Boolean {
                beginDieDrag(die, newDieImg)
                return true
            }
        )
        newDieImageContainer.setOnLongClickListener(longClickListener)

        //Touching the drag handle starts the drag
        var dragHandle = newDieImageContainer.findViewById<ImageView>(R.id.dieDragHandle)
        dragHandle.setOnTouchListener(
            fun(v: View, event: MotionEvent): Boolean {
                if(event.action == MotionEvent.ACTION_DOWN) {
                    beginDieDrag(die, newDieImg)
                    return true
                }
                return false
            })
    }

    /**
     * Toggle whether one of the dice sections is visible or collapsed. Hides/shows the view
     * containing the sections, and sets the rotation on the collapse arrow icon. Current state is
     * inferred based on the view's current visibility (gone vs. anything else).
     * segmentArea: View for the section
     * collapseIcon: Arrow icon indicating whether the section is expanded/collapsed
     */
    fun toggleSegmentVisibility(segmentArea: View, collapseIcon: ImageView) {
        if(segmentArea.visibility == View.GONE) {
            segmentArea.visibility = View.VISIBLE
            collapseIcon.rotation = 90f
        }
        else {
            segmentArea.visibility = View.GONE
            collapseIcon.rotation = 0f
        }
    }

    /**
     * Begins dragging a die from the drawer. Will be called from the long press handler of each
     * die type tile, and also from the touch event handler of their drag handlers (when the event
     * is ACTION_DOWN).
     * die: The type of die to drag
     * view: View to build the drag shadow from, which should be the ImageView from the tile showing
     *       the die.
     */
    fun beginDieDrag(die: DieType, view: View) {
        var shadowBuilder = View.DragShadowBuilder(view)
        var data = DiceDragData(null, die, null, currentOperation)
        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        view.startDragAndDrop(null, shadowBuilder, data, 0)
    }

    /**
     * Handle pools and dice dragged from elsewhere. Ignores objects dragged from the drawer (i.e.
     * that have no source pool). When an object is dragged from the pool and dropped in the drawer,
     * this will accept the drop but not do anything with it. This will cause the object to be
     * removed from the pool, but not placed back in it anywhere.
     * view: View handling the drag event
     * event: DragEvent from the Android framework
     * Returns: False for drag events originating in the drawer.
     *          For drags from the pool, returns true on ACTION_DRAG_STARTED (to register for further
     *          updates) and ACTION_DROP to accept the drop.
     */
    fun handleDieDrag(view: View, event: DragEvent): Boolean {
        if(event.localState is DiceDragData) {
            if((event.localState as DiceDragData).sourcePool != null) {
                if (event.action == DragEvent.ACTION_DRAG_STARTED || event.action == DragEvent.ACTION_DROP) {
                    return true
                }
            }
        }
        return false
    }

    /**
     * Start dragging a fixed number from the drawer. The value dragged will be taken from the
     * EditText in the fixed number section; if the text there cannot be converted to an integer
     * then no drag will begin. The drag shadow will be from a TextView created here, with its text
     * being the value that's dragged.
     * view: View initiating the drag (will be the drag handle for the fixed value input)
     * Returns: True if a drag event is started, false otherwise
     */
    fun beginNumberDrag(view: View): Boolean {
        var number = fixedValInput.text.toString().toIntOrNull()
        if(number == null) return false
        var poolValue = FixedNumber(number)

        var shadowView = TextView(requireContext())
        shadowView.textSize = SHADOW_TEXT_SIZE
        shadowView.text = number.toString()
        shadowView.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        shadowView.layout(0, 0, shadowView.measuredWidth, shadowView.measuredHeight)

        var shadowBuilder = View.DragShadowBuilder(shadowView)
        var data = DiceDragData(poolValue, null, null, currentOperation)
        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        view.startDragAndDrop(null, shadowBuilder, data, 0)
        return true
    }

    /**
     * Adapter for the spinner for selecting the operator to drag objects with. Has separate resources
     * for the selected operator and operators in the dropdown, but both must be ImageViews at the
     * top level. The activity tracks which operator is currently selected through the
     * OnItemSelectedListener interface.
     */
    class OperationSpinnerAdapter(
        private var _context: Context,
        val _resource: Int,
        val _dropdownResource: Int,
        val _objects: Array<Operation>
        ): ArrayAdapter<Operation>(_context, _resource, _objects) {

        /**
         * Make a view for the currently selected operator.
         * position: Index into the list of operations
         * convertView: May be an existing view to convert to a different operation
         * parent: Not used here
         * Returns: The view to use
         */
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            return createView(false, position, convertView)
        }

        /**
         * Make a view for an operator in the dropdown list
         * position: Index into the list of operations
         * convertView: May be an existing view to convert to a different operation
         * parent: Not used here
         * Returns: The view to use
         */
        override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
            return createView(true, position, convertView)
        }

        /**
         * Common logic for getView and getDropDownView to build/convert the view. This is the same
         * in these cases except for the resource used.
         * forDropdown: True to use the resource for operators in the dropdown, false to use the
         *              resource for the selected operation.
         * position: Index into the list of operations
         * convertView: May be an existing view to convert to a different operation
         * Returns: The view to use
         */
        private fun createView(forDropdown: Boolean, position: Int, convertView: View?): View {
            var view = convertView
            if(view == null) {
                val resourceToInflate = if(forDropdown) { _dropdownResource } else { _resource }
                var layoutInflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
                view = layoutInflater.inflate(resourceToInflate, null)
            }

            val opDrawableId = _objects[position].operatorDrawableId
            (view as ImageView).setImageResource(opDrawableId)
            view.contentDescription = context.getString(R.string.operator_selection_content_description, _objects[position].operatorName)
            return view
        }
    }

    /**
     * OnItemSelectedListener function to track which operator is selected in the spinner. Should
     * be set as the onItemSelectedListener for the spinner. Retrieves the operator from the
     * adapter based on position.
     * parent: Spinner view
     * view: idk
     * position: Position of the selected operator
     * id: I guess it has an ID too
     */
    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
        var selectedOp = parent?.adapter?.getItem(position) as? Operation
        if(selectedOp != null) currentOperation = selectedOp
    }

    /**
     * OnItemSelectedListener function for if (somehow) no operation becomes selected. Set the
     * operation to use when dragging to the default operation from the Operation companion object.
     * parent: Spinner view
     */
    override fun onNothingSelected(parent: AdapterView<*>?) {
        currentOperation = Operation.DefaultOperation
    }

    companion object {
        /**
         * Factory method for DieTypesDrawer. Currently kinda pointless.
         */
        @JvmStatic
        fun newInstance() = DieTypesDrawer()
    }
}