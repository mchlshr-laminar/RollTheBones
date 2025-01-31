package com.Winebone.RollTheBones

import android.content.Context
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.constraintlayout.motion.widget.MotionLayout
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView

/**
 * Copyright (c) 2021-2025 Michael Usher
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of
 * the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

/**
 * Ancestor class for fragments for the tabs in the main menu. This class is for common logic that
 * does not depend on the type of object the tab is for. There is an intermediate class
 * (MainMenuTabFragment<T>) parameterized for the type of view holder the tab's Recycler uses, then
 * the individual tab classes inherit from that (there shouldn't be a case where two tabs inherit
 * from the same parameterization of the intermediate class, as the view holder type is from the tab
 * class).
 *
 * Classes with different parameterized types count as separate classes and therefore can't share
 * pointers as parent classes, hence the un-parameterized ancestor class.
 *
 * The tabs use a recycler view to show a list of what they're for. An item can be loaded by tapping
 * it in the list, or multiple items can be selected and deleted. If there are no items in the list,
 * element emptyListBanner will be shown instead (so subclasses must set that in onCreate). There is
 * also a handler for when the "+" button in the main menu is pressed while this tab is active.
 *
 * There are currently tabs for pools (MainMenuPoolsTabFragment) and dice (MainMenuDiceTabFragment).
 */
abstract class MainMenuTabFragParent: Fragment() {
    protected lateinit var tabListRecycler: RecyclerView

    //Called when the tab moves from the normal state to the queued-for-deletion state. The main
    //menu activity sets a function to show the action bar here. The action bar has the confirm
    //deletion and cancel deletion buttons.
    var deleteStagedCallback: (()->Unit)? = null

    //Called when the tab moves from the queued-for-deletion state to the normal state. The main
    //menu activity sets a function to hide the action bar here.
    var deleteUnstagedCallback: (()->Unit)? = null

    //Called when the number of items selected for deletion changes. The main menu sets a function
    //to update the text in the action bar.
    var forDeleteCountChangedCallback: ((count: Int, textResource: Int) -> Unit)? = null //Second argument will be selectedTextResource
    protected var itemsForDelete = HashMap<String, Int>() //item name to adapter position
    protected var deleteStage = MainMenuV2.DeletionStage.Normal
    protected abstract val BatchDeletionFunction: (Context, Set<String>) -> HashMap<String?, String>
    protected abstract val collectiveNounResource: Int //String resource
    protected abstract val deletedTextResource: Int //Plural resource with count argument
    protected abstract val selectedTextResource: Int //Plural resource with count argument
    protected var emptyListBanner: View? = null

    /**
     * Items to show may have changed while this is in the background. Determine whether we need to
     * show the banner for having nothing to show and refresh the recycler.
     */
    override fun onResume() {
        super.onResume()
        setEmptyListBannerVisibility()
        tabListRecycler.adapter?.notifyDataSetChanged()
    }

    /**
     * When this tab is no longer shown, any deletion that was queued up for items in that tab
     * no longer makes sense, so un-queue that deletion.
     */
    open fun onTabHidden() {
        unStageDeletion()
    }

    /**
     * Determines whether the tab needs to show a banner saying there's nothing there, then sets the
     * visibility of the element for it. Will be shown when the adapter in the tab's recycler has an
     * item count of 0.
     */
    private fun setEmptyListBannerVisibility() {
        val itemCount = tabListRecycler.adapter?.itemCount ?: 0
        val visibility = if(itemCount == 0) { View.VISIBLE } else { View.GONE }
        emptyListBanner?.visibility = visibility
    }

    /**
     * If the tab is not currently in the state for queued up deletion, moves to that state. Invokes
     * the callback to show the action bar. Does not set any specific items for deletion. This does
     * not need to inform the adapter of anything, as no items are changing here. If this happens
     * because an item was selected for deletion, the selection handler will inform the adapter.
     *
     * Does nothing if deletion is already staged.
     */
    open fun stageDeletion() {
        if(deleteStage != MainMenuV2.DeletionStage.Normal) return
        deleteStagedCallback?.invoke()
        deleteStage = MainMenuV2.DeletionStage.Ready
    }

    /**
     * If the tab is currently in the state where item deletion is staged, sets it back to the
     * normal state. Clears the list of items for delete and informs the recycler that they've been
     * updated. Invokes the callback to hide the action bar.
     *
     * Does nothing if deletion is not staged.
     */
    open fun unStageDeletion() {
        if(deleteStage != MainMenuV2.DeletionStage.Ready) return
        deleteUnstagedCallback?.invoke()
        deleteStage = MainMenuV2.DeletionStage.Normal

        //Update list display so items are not shown as selected for deletion
        var poolAdapter = tabListRecycler.adapter
        for(unDeletedPool in itemsForDelete) {
            if(unDeletedPool.value != RecyclerView.NO_POSITION) {
                poolAdapter?.notifyItemChanged(unDeletedPool.value)
            }
        }
        itemsForDelete.clear()
    }

    /**
     * Determines whether or not a specific item has been selected for deletion, based on whether
     * it's in the list. Returns a value from the ForDeleteState enum.
     * itemName: Name of the item to look for
     * Returns: Selected if it is queued for deletion, NotSelected otherwise.
     */
    protected fun getItemDeletionState(itemName: String): MainMenuV2.ForDeleteState {
        if(itemsForDelete.contains(itemName)) return MainMenuV2.ForDeleteState.Selected
        else return MainMenuV2.ForDeleteState.NotSelected
    }

    /**
     * If deletion is queued up, deletes all items that are selected, then puts the tab back in the
     * normal state. If deletion is not queued, does nothing. If errors occur during deletion, show
     * a popup for them. Also show a toast for the number of items that were successfully deleted.
     */
    fun confirmDeletion() {
        if(deleteStage != MainMenuV2.DeletionStage.Ready) return

        var deletionErrors = BatchDeletionFunction(
            requireContext().applicationContext, itemsForDelete.keys)
        showDeletionErrors(deletionErrors)

        //Show toast for number of deleted items
        var count = deletionErrors.size
        //Null key means error in updating the list file, not an individual item
        if(deletionErrors.containsKey(null)) count--
        //Number of items deleted is number intended for deletion minus number that had errors
        count = itemsForDelete.size - count
        if(count > 0) showDeletionToast(count)

        //Return to normal list state. Show the empty list banner if all items have been deleted.
        unStageDeletion()
        setEmptyListBannerVisibility()
        tabListRecycler.adapter?.notifyDataSetChanged()
        if(deletionErrors.size == 0) {
            tabListRecycler.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        }
        else {
            tabListRecycler.performHapticFeedback(HapticFeedbackConstants.REJECT)
        }
    }

    /**
     * Show an errors that occurred while trying to delete the items that were queued for deletion.
     * They will be shown in popups. Errors for individual items are only shown if there's no error
     * for the list file.
     * deletionResults: Map containing errors that occurred. Key null is for errors writing the list
     *                  file. Other keys are the name of the item that had an error. The value is
     *                  the technical error message.
     */
    private fun showDeletionErrors(deletionResults: HashMap<String?, String>) {
        if(deletionResults[null] != null) {
            //Error writing the list
            showDeletionListError(deletionResults[null]!!)
            return
        }

        if(!deletionResults.isEmpty()) {
            //Error deleting some of the individual items
            showDeletionErrorList(deletionResults)
        }

    }

    /**
     * Show a popup for an error rewriting the list file while attempting to delete items.
     * message: Message from the exception, or other technical error message
     */
    private fun showDeletionListError(message: String) {
        var builder = AlertDialog.Builder(requireActivity())
        builder.setTitle(getString(R.string.problem_deleting_items_popup_name))
        builder.setMessage(getString(R.string.problem_deleting_item_list_message, getString(collectiveNounResource)) + message)
        builder.setPositiveButton(getString(R.string.popup_button_ok_no_action), null)
        builder.create().show()
    }

    /**
     * Show a popup for errors deleting individual items.
     * messages: As per showDeletionErrors. Should not have a null key at this point, as if there
     *           was then showDeletionListError would've been called instead.
     */
    private fun showDeletionErrorList(messages: HashMap<String?, String>) {
        var combinedMessage = ""
        for(errorPool in messages) {
            if(errorPool.key == null) continue
            combinedMessage += errorPool.key + ": " + errorPool.value + "\n"
        }

        var builder = AlertDialog.Builder(requireActivity())
        builder.setTitle(getString(R.string.problem_deleting_items_popup_name))
        builder.setMessage(getString(R.string.problem_deleting_item_individual_message, getString(collectiveNounResource)) +
                "\n" + combinedMessage)
        builder.setPositiveButton(getString(R.string.popup_button_ok_no_action), null)
        builder.create().show()

    }

    /**
     * Show a toast telling how many items were successfully deleted.
     * deleteCount: How many items were deleted
     */
    private fun showDeletionToast(deleteCount: Int) {
        val deletionText = resources.getQuantityString(deletedTextResource, deleteCount, deleteCount)
        val duration = Toast.LENGTH_SHORT
        var toast = Toast.makeText(requireContext().applicationContext, deletionText, duration)
        toast.show()
    }

    /**
     * The main menu activity will call this function on the currently-displayed tab when the "+"
     * button at the bottom of the screen is pressed. Individual tab fragments must implement this
     * to launch the appropriate activity to create a new instance of what that tab's for.
     */
    abstract fun addButtonPressed()

    /**
     * Parent class for the view holders to be used by the recyclers in the tabs.
     */
    abstract class MenuTabViewHolder(var itemLayout: MotionLayout,
                                     private val normalStateId: Int, private val deleteStateId: Int):
        RecyclerView.ViewHolder(itemLayout) {
        var forDeleteState = MainMenuV2.ForDeleteState.NotSelected
            private set(value) {
                field = value
            }
        abstract val itemName: String?

        /**
         * Transitions this view holder to or from the state where it's selected for deletion. This
         * transitions the MotionLayout state its in and sets a flag. Note that the animated
         * transition isn't really working, as view holders are getting re-bound when items are
         * selected for deletion. Use MainMenuTabFragment.deleteItem or unDeleteItem to actually
         * select/deselect for deletion; this is for setting how it's displayed.
         * targetState: Selected for delete or not
         * withTransition: If false, the target MotionLayout state will be gone to directly. If true
         *                 then the animated transition will be used. Should be true if this is
         *                 happening for an item being selected/deselected, and false if the change
         *                 is because a ViewHolder is being bound to a different item.
         */
        fun setForDeletionDisplay(targetState: MainMenuV2.ForDeleteState, withTransition: Boolean) {
            if(targetState == forDeleteState) return
            forDeleteState = targetState

            val targetConstraintSet = when(targetState) {
                MainMenuV2.ForDeleteState.Selected -> deleteStateId
                else -> normalStateId
            }
            itemLayout.transitionToState(targetConstraintSet)
            if(!withTransition) itemLayout.setProgress(1f)
        }
    }
}

/**
 * Intermediate parent for fragments for tabs of the main menu. This contains common logic which
 * needs to handle a pointer of the type the tab is for, but does not depend on the specifics of
 * that type. Might be avoidable by using a pointer of type MainMenuTabFragParent.
 *
 * T is a view holder type for the tab's recycler view. It should contain the type the tab is for,
 * which is currently either dice or pools.
 */
abstract class MainMenuTabFragment<T: MainMenuTabFragParent.MenuTabViewHolder>
                                   : MainMenuTabFragParent() {

    /**
     * Tab fragments must implement this to handle launching an activity to use or edit one of the
     * items in the tab. Will be called from the click handler of item views (as applied by the
     * recycler adapter) if the tab does not currently have deletion queued.
     * listItem: View holder which currently contains the item to open
     * edit: If true, the user wants to edit the item rather than use it. This only applies to pools;
     *       dice are always being edited when they're opened.
     */
    protected abstract fun itemLoadHandler(listItem: T, edit: Boolean = false)

    /**
     * Toggle whether a given item is selected for deletion or not. If nothing is currently selected
     * and we select something, this will put the tab in deletion-queued state. If this is
     * deselecting the only item selected, deletion will be un-queued.
     *
     * The recycler adapter attaches this in the long-press handler for the item views. It will also
     * be called from their click handler if deletion is already queued.
     *
     * listItem: View holder containing the item to select/deselect
     */
    protected open fun deleteButtonHandler(listItem: T) {
        if(listItem.itemName == null) return

        if(itemsForDelete.contains(listItem.itemName)) unDeleteItem(listItem)
        else deleteItem(listItem)

        if(deleteStage == MainMenuV2.DeletionStage.Ready) {
            forDeleteCountChangedCallback?.invoke(itemsForDelete.size, selectedTextResource)
        }
    }

    /**
     * Selects an item for deletion and notifies the adapter to update how it's displayed. If
     * deletion is not yet queued in the tab, it will be now.
     * listItem: View holder containing the item to select
     */
    protected open fun deleteItem(listItem: T) {
        itemsForDelete[listItem.itemName!!] = listItem.adapterPosition
        stageDeletion()
        tabListRecycler.adapter?.notifyItemChanged(listItem.adapterPosition)
    }

    /**
     * Removes an item from the for-deletion list and notifies the adapter to update how it's
     * displayed. If that was the only item selected for deletion, un-queues deletion in the tab.
     * listItem: View holder containing the item to deselect
     */
    protected open fun unDeleteItem(listItem: T) {
        itemsForDelete.remove(listItem.itemName!!)
        if(itemsForDelete.size == 0) unStageDeletion()
        tabListRecycler.adapter?.notifyItemChanged(listItem.adapterPosition)
    }

    /**
     * Parent class for the adapters used in the recyclers in the tabs. The click and long press
     * handlers can be set with common logic, though pools also have an edit button which is handled
     * separately.
     */
    abstract inner class MenuTabAdapter(): RecyclerView.Adapter<T>() {

        /**
         * Apply the click and long press handlers to an item displayed in the tab. Will be called
         * in onCreateViewHolder. Functions called by the handlers are passed the view holder, so
         * they can retrieve the item that view holder is currently for.
         *
         * The long press handler will toggle whether an item is selected for deletion (by calling
         *     deleteButtonHandler).
         * The click handler's behavior depends on the tab's state. If deletion is not queued in the
         *     tab, the item will be opened (not for edit, for pools) with itemLoadHandler. If
         *     deletion is queued, this will also toggle whether the item is selected for deletion.
         */
        protected fun applyHandlers(listItem: T) {
            //Long press handler toggles deletion selection
            listItem.itemView.setOnLongClickListener(
                fun(v: View): Boolean {
                    deleteButtonHandler(listItem)
                    return true
                })

            //Click handler opens the item or toggles deletion selection
            listItem.itemView.setOnClickListener(
                fun (view: View) {
                    //If we're currently selecting items for delete, simple press does that instead of loading it
                    if(deleteStage == MainMenuV2.DeletionStage.Ready) {
                        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                        deleteButtonHandler(listItem)
                    }
                    else {
                        itemLoadHandler(listItem)
                    }
                })
        }
    }
}
