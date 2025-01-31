package com.Winebone.RollTheBones

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.motion.widget.MotionLayout
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.Winebone.RollTheBones.dicepool.DieType
import com.Winebone.RollTheBones.dicepool.DieTypesManager

private const val CUSTOM_DICE_LIST_COLUMNS = 2

/**
 * Copyright (c) 2021-2025 Michael Usher
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of
 * the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

/**
 * Main menu tab to show custom dice which have been saved. The dice will be shown in two columns of
 * tiles (per the CUSTOM_DICE_LIST_COLUMNS constant), and each tile shows the config face of the die
 * and its name. Loading a die will open the CustomDieBuildingActivity to edit that die, which
 * allows changing faces and values but not the name. The plus button opens the same activity but
 * for a new die.
 */
class MainMenuDiceTabFragment : MainMenuTabFragment<MainMenuDiceTabFragment.CustomDieViewHolder>() {
    override val BatchDeletionFunction: (Context, Set<String>) -> HashMap<String?, String>
        get() = DieTypesManager::DeleteCustomDice
    override val collectiveNounResource: Int
        get() = R.string.dice_collective_noun
    override val deletedTextResource: Int
        get() = R.plurals.dice_deleted_text
    override val selectedTextResource: Int
        get() = R.plurals.dice_selected_text

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        var fragmentView = inflater.inflate(R.layout.fragment_main_menu_dice_tab, container, false)
        emptyListBanner = fragmentView.findViewById(R.id.menuNoDiceBanner)

        //Set up the recycler
        tabListRecycler = fragmentView.findViewById<RecyclerView>(R.id.customDiceRecycler)
        tabListRecycler.layoutManager = GridLayoutManager(requireActivity(), CUSTOM_DICE_LIST_COLUMNS)
        tabListRecycler.adapter = CustomDieAdapter(requireContext().applicationContext)

        return fragmentView
    }

    /**
     * Create a new die: launch the die building activity without loading an existing die.
     */
    override fun addButtonPressed() {
        editDie(null)
    }

    /**
     * Handler for tapping on dice in the list (when there isn't a delete queued). Opens the custom
     * die building activity to edit that die.
     * listItem: View holder currently bound to the die to edit
     * edit: Ignored. Opening a die is always for editing.
     */
    override fun itemLoadHandler(listItem: CustomDieViewHolder, edit: Boolean) {
        if(listItem.forDie == null) return //View holder was not bound to anything
        editDie(listItem.forDie)
    }

    /**
     * Launch the custom die building activity.
     * die: The die type to edit, or null to build a new die.
     */
    private fun editDie(die: DieType? = null) {
        var intent = Intent(requireContext(), CustomDieBuildingActivity::class.java)

        if(die != null) ActivityArguments.StoreArgument(intent, "die", die)

        startActivity(intent)
    }

    /**
     * View holder for a custom die in the list. Contains the die type, a text view showing its name,
     * and an image view showing its config face.
     */
    class CustomDieViewHolder(itemView: MotionLayout): MainMenuTabFragParent.MenuTabViewHolder(
        itemView, R.id.customDieNormal, R.id.customDieForDelete) {

        var icon = itemView.findViewById<ImageView>(R.id.customDieIcon)
        var nameText = itemView.findViewById<TextView>(R.id.customDieNameText)
        var forDie: DieType? = null

        /**
         * Get the name of the die this view holder is bound to
         */
        override val itemName: String?
            get() = forDie?.name
    }

    /**
     * Recycler adapter for custom dice
     */
    inner class CustomDieAdapter(private var dieManagerContext: Context)
        : MainMenuTabFragment<CustomDieViewHolder>.MenuTabAdapter() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CustomDieViewHolder {
            var itemView = layoutInflater.inflate(
                R.layout.custom_die_item_layout,
                parent,
                false) as MotionLayout
            var holder = CustomDieViewHolder(itemView)
            applyHandlers(holder)
            return holder
        }

        /**
         * Retrieve a die type from the manager based on position. Sets the icon and name text in
         * the view holder, and sets the MotionLayout state based on whether the die is queued for
         * deletion.
         * holder: View holder to bind
         * position: Index into the consistently-ordered list of custom dice that the die types
         *           manager maintains.
         */
        override fun onBindViewHolder(holder: CustomDieViewHolder, position: Int) {
            var newDieType: DieType = DieTypesManager.GetCustomTypeByPosition(position)
            val isChangingDie = newDieType !== holder.forDie

            val targetDeletionState: MainMenuV2.ForDeleteState =
                getItemDeletionState(newDieType.name)
            holder.setForDeletionDisplay(targetDeletionState, !isChangingDie)

            if(!isChangingDie) return
            holder.forDie = newDieType
            holder.nameText.text = newDieType.name
            holder.icon.setImageDrawable(newDieType.GetConfigResult().faceDrawable)
        }

        /**
         * Number of custom die types as reported by the die types manager.
         */
        override fun getItemCount(): Int {
            return DieTypesManager.customTypeCount
        }

    }

    companion object {
        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @return A new instance of fragment MainMenuDiceTabFragment.
         */
        @JvmStatic
        fun newInstance() =
            MainMenuDiceTabFragment()
    }
}