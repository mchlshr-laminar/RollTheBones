package com.Winebone.RollTheBones

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.constraintlayout.motion.widget.MotionLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.Winebone.RollTheBones.dicepool.SavedPoolManager

/**
 * Copyright (c) 2021-2025 Michael Usher
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of
 * the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

/**
 * Main menu tab for saved pool configurations. Pools are shown in one column. Tapping an item opens
 * that pool for rolling. Each list item also has an edit icon, which will open that pool for editing.
 * The plus button will open the pool config activity for a new pool.
 */
class MainMenuPoolsTabFragment : MainMenuTabFragment<MainMenuPoolsTabFragment.SavedPoolViewHolder>() {
    override val BatchDeletionFunction: (Context, Set<String>) -> HashMap<String?, String>
        get() = SavedPoolManager::DeletePools
    override val collectiveNounResource: Int
        get() = R.string.pools_collective_noun
    override val deletedTextResource: Int
        get() = R.plurals.pools_deleted_text
    override val selectedTextResource: Int
        get() = R.plurals.pools_selected_text

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        var fragmentView = inflater.inflate(R.layout.fragment_main_menu_pools_tab, container, false)
        emptyListBanner = fragmentView.findViewById(R.id.menuNoSavedPoolsBanner)

        //Set up the recycler
        tabListRecycler = fragmentView.findViewById<RecyclerView>(R.id.savedPoolsRecycler)
        tabListRecycler.layoutManager = LinearLayoutManager(requireActivity())
        tabListRecycler.adapter = SavedPoolAdapter(requireContext().applicationContext)

        return fragmentView
    }

    /**
     * Open the pool configuration activity for building a new pool
     */
    override fun addButtonPressed() {
        var intent = Intent(requireContext(), PoolConfiguration::class.java)
        ActivityArguments.SetIdentifier(intent) //If the activity is killed in the background, it
                                                //needs an identifier to store the pool state
        startActivity(intent)
    }

    /**
     * Open an existing pool, either in the rolling activity or the config activity.
     * listItem: View holder currently bound to the pool to open
     * edit: If true, open the pool in the config activity.
     *       If false, open it in the rolling activity.
     */
    override fun itemLoadHandler(listItem: SavedPoolViewHolder, edit: Boolean) {
        if(listItem.forPool == null) return //View holder was not bound to anything
        val poolName = listItem.forPool!!

        //Load the pool from the manager
        val loadResult = SavedPoolManager.GetSavedPool(requireContext(), poolName)
        if(loadResult.first == null) {
            showLoadError(loadResult.second)
            return
        }

        //Determine which activity to launch
        var targetActivity = if(edit) { PoolConfiguration::class.java } else { RollingActivity::class.java }
        var intent = Intent(requireContext(), targetActivity)

        //Store the pool for the activity to retrieve
        ActivityArguments.StoreArgument(intent, "pool", loadResult.first)
        ActivityArguments.StoreArgument(intent, "poolName", poolName)

        //Launch the activity
        startActivity(intent)
    }

    /**
     * Show an error that occurred while trying to load a saved pool. Shows the error in a popup.
     * errorMessage: Exception message or other technical error message.
     */
    private fun showLoadError(errorMessage: String) {
        var builder = AlertDialog.Builder(requireActivity())
        builder.setTitle(getString(R.string.problem_loading_pool_popup_name))
        builder.setMessage(getString(R.string.problem_loading_pool_popup_message) + " " + errorMessage)
        builder.setPositiveButton(getString(R.string.popup_button_ok_no_action), null)
        builder.create().show()
    }

    /**
     * View holder for saved pools shown in the list. Shows the name of the pool and an icon to edit
     * that pool.
     */
    class SavedPoolViewHolder(itemLayout: MotionLayout): MainMenuTabFragParent.MenuTabViewHolder(
            itemLayout, R.id.loadItemNormal, R.id.loadItemForDelete) {
        val nameText: TextView = itemView.findViewById(R.id.loadItemPoolName)
        val editIcon: ImageView = itemView.findViewById(R.id.loadItemEditIcon)
        var forPool: String? = null

        /**
         * Get the name of the pool this view holder is bound to
         */
        override val itemName: String?
            get() = forPool
    }

    /**
     * Recycler adapter for saved pools
     */
    inner class SavedPoolAdapter(private var poolManagerContext: Context)
        : MainMenuTabFragment<SavedPoolViewHolder>.MenuTabAdapter() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SavedPoolViewHolder {
            var itemView = layoutInflater.inflate(
                R.layout.load_list_item_layout,
                parent,
                false) as MotionLayout
            itemView.currentState

            var holder = SavedPoolViewHolder(itemView)
            applyHandlers(holder)
            holder.editIcon.setOnClickListener(
                //Edit button launches the config activity instead of the roll activity
                fun(v: View) {
                    itemLoadHandler(holder, true)
                })

            return holder
        }

        /**
         * Binds a view holder to a saved pool, based on position. Sets the name text. Sets the
         * MotionLayout state based on whether the pool is queued for deletion.
         * holder: View holder to bind
         * position: Index into the consistently-ordered list of saved pools that the pool manager
         *           maintains.
         */
        override fun onBindViewHolder(holder: SavedPoolViewHolder, position: Int) {
            val poolName = SavedPoolManager.GetPoolName(poolManagerContext, position)
            val isChangingPool = holder.forPool != poolName

            val targetDeletionState: MainMenuV2.ForDeleteState = getItemDeletionState(poolName)
            holder.setForDeletionDisplay(targetDeletionState, !isChangingPool)

            if(isChangingPool) {
                holder.nameText.text = poolName
                holder.forPool = poolName
            }
        }

        /**
         * Number of saved pools as reported by the manager.
         */
        override fun getItemCount(): Int {
            return SavedPoolManager.GetNumPools(poolManagerContext)
        }

    }

    companion object {
        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @return A new instance of fragment MainMenuPoolsTabFragment.
         */
        @JvmStatic
        fun newInstance() =
            MainMenuPoolsTabFragment()
    }
}