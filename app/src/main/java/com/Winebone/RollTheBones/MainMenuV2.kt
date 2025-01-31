package com.Winebone.RollTheBones

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.*
import android.widget.TextView
import androidx.appcompat.app.ActionBar
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.motion.widget.MotionLayout
import androidx.core.math.MathUtils
import com.Winebone.RollTheBones.dicepool.DieTypesManager
import com.google.android.material.tabs.TabLayout
import kotlin.math.sign

private const val POOLS_TAB_POSITION = 0
@SuppressLint
private const val DICE_TAB_POSITION = 1

/**
 * Copyright (c) 2021-2025 Michael Usher
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of
 * the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

/**
 * Main menu activity. The menu has a tab showing saved pools and a tab showing custom die types.
 * Tabs can be selected with the tab list at the top or by swiping left/right. There is a "+" button
 * at the bottom which creates a new item based on the active tab. In the default state the action
 * bar is not shown. If deletion is queued up in the tab, the action bar will be shown with "cancel"
 * and "confirm deletion" buttons, and text showing how many things would be deleted.
 */
class MainMenuV2 : AppCompatActivity() {
    /**
     * Represents state of an individual item in the list: would it be deleted if queued deletion
     * was confirmed.
     */
    enum class ForDeleteState {
        Selected,
        NotSelected
    }

    /**
     * Represents whether deletion is queued in a tab. Iff any item has ForDeleteState of Selected
     * then the tab should have state Ready.
     */
    enum class DeletionStage {
        Normal,
        Ready
    }

    private lateinit var tabs: TabLayout
    private lateinit var mainMenuLayout: MotionLayout

    private var currentMenuTab = POOLS_TAB_POSITION
    private lateinit var tabFragments: Array<MainMenuTabFragParent>
    private lateinit var gestureDetector: GestureDetector
    private var selectedCountText: TextView? = null

    @SuppressLint("InflateParams")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_menu_v2)
        DieTypesManager.InitializeManager(this)

        //Find elements
        tabs = findViewById(R.id.mainMenuTabs)
        mainMenuLayout = findViewById(R.id.mainMenuLayout)

        //Gesture detector to change tabs based on swiping
        gestureDetector = GestureDetector(this, FlingListener())

        //Set up the action bar
        setSupportActionBar(findViewById(R.id.mainMenuActionBar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeActionContentDescription(
            R.string.activity_toolbar_cancel_deletion_icon_content_description)
        supportActionBar?.setDisplayOptions(ActionBar.DISPLAY_SHOW_CUSTOM,
            ActionBar.DISPLAY_SHOW_CUSTOM or ActionBar.DISPLAY_SHOW_TITLE)
        var actionBarLayoutParams = ActionBar.LayoutParams(Gravity.END)
        var actionBarCustomView = layoutInflater.inflate(R.layout.action_bar_text, null)
        supportActionBar?.setCustomView(actionBarCustomView, actionBarLayoutParams)
        selectedCountText = supportActionBar?.customView?.findViewById(R.id.selectedCountText)

        //Restore the selected tab, if the activity was killed
        savedInstanceState?.getInt("tabSelected")?.apply {
            tabs.getTabAt(this)?.select()
            currentMenuTab = this
        }

        //Set up handler to change the displayed tab when selecting from the tab list
        tabs.addOnTabSelectedListener( object: TabLayout.OnTabSelectedListener {
            override fun onTabReselected(tab: TabLayout.Tab?) {  } //no-op
            override fun onTabUnselected(tab: TabLayout.Tab?) {  } //no-op

            override fun onTabSelected(tab: TabLayout.Tab?) {
                if(tab == null) return
                goToTabForPosition(tab.position)
            }
        })

        //Handler for the "+" button
        var addButton = findViewById<View>(R.id.mainMenuAddButton)
        addButton.setOnClickListener(::addButtonHandler)

        //Set up callbacks in the tab fragments, to update the action bar state
        tabFragments = arrayOf(
            supportFragmentManager.findFragmentById(R.id.mainMenuPoolsTabFragment) as MainMenuTabFragParent,
            supportFragmentManager.findFragmentById(R.id.mainMenuDiceTabFragment) as MainMenuTabFragParent)
        for(frag in tabFragments) {
            frag.deleteStagedCallback = ::showActionBar
            frag.deleteUnstagedCallback = ::hideActionBar
            frag.forDeleteCountChangedCallback = ::updateActionBarText
        }

        //Set the initially displayed tab fragment
        goToTabForPosition(currentMenuTab)
    }

    /**
     * Put the "confirm deletion" option in the action bar. It should be displayed as a button.
     */
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        if(menu != null) {
            menuInflater.inflate(R.menu.main_menu_action_bar, menu)
            return true
        }
        return false
    }

    /**
     * Handle action bar buttons. The home button is an X which cancels any queued deletion. The
     * "confirm deletion" button will finalize deletion of the selected items in the tab.
     */
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when(item.itemId) {
            android.R.id.home -> {
                tabFragments[currentMenuTab].unStageDeletion()
                true
            }
            R.id.mainMenuConfirmDelete -> {
                tabFragments[currentMenuTab].confirmDeletion()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    /**
     * Change the visible tab fragment and set the currentMenuTab (index of the currently selected
     * tab). Animates the transition: fragments will slide either left or right based on whether the
     * newly selected tab has a higher or lower index than the currently selected one.
     * tabPosition: Tab index of the tab fragment to show.
     */
    private fun goToTabForPosition(tabPosition: Int) {
        var shownFragment = tabFragments[tabPosition]
        var transaction = supportFragmentManager.beginTransaction()

        if(tabPosition > currentMenuTab) {
            //Slide right to left
            transaction.setCustomAnimations(
                R.anim.enter_right, R.anim.exit_left, R.anim.enter_right, R.anim.exit_left)
        }
        else {
            //Slide left to right
            transaction.setCustomAnimations(
                R.anim.enter_left, R.anim.exit_right, R.anim.enter_left, R.anim.exit_right)
        }

        //Show the newly-selected tab
        transaction.show(shownFragment)

        //Hide all other tabs
        for(frag in tabFragments) {
            if(frag !== shownFragment) {
                transaction.hide(frag)
                frag.onTabHidden()
            }
        }

        transaction.commit()
        currentMenuTab = tabPosition
    }

    /**
     * If the menu activity goes to the background, the current tab needs to unstage any deletion
     * (and do anything else it wants to do when it becomes hidden)
     */
    override fun onPause() {
        super.onPause()
        tabFragments[currentMenuTab].onTabHidden()
    }

    /**
     * If the activity is killed in the background, save which tab was selected so that can be
     * restored.
     */
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        outState.putInt("tabSelected", tabs.selectedTabPosition)
    }

    /**
     * Change the text shown in the action bar, which describes how many items are selected for
     * deletion. This is put in the forDeleteCountChangedCallback of the tab fragments.
     * selectedCount: How many items are selected. Passed as the quantity when retrieving the plural
     *                resource.
     * selectedTextResource: ID of a plural resource, which should be along the lines of "X pools
     *                       selected".
     */
    private fun updateActionBarText(selectedCount: Int, selectedTextResource: Int) {
        selectedCountText?.text = resources.getQuantityString(selectedTextResource, selectedCount, selectedCount)
    }

    /**
     * Change MotionLayout state to make the action bar visible. This is put in the
     * deleteStagedCallback of the tab fragments.
     */
    private fun showActionBar() {
        mainMenuLayout.transitionToState(R.id.menuShowActionBar)
    }

    /**
     * Change MotionLayout state to make the action bar invisible. This is put in the
     * deleteUnstagedCallback of the tab fragments.
     */
    private fun hideActionBar() {
        mainMenuLayout.transitionToState(R.id.menuNoActionBar)
        selectedCountText?.text = ""
    }

    /**
     * Handler for pressing the "+" button at the bottom. Defers to a sub-handler in the active tab,
     * which should launch an activity to make a new item.
     */
    private fun addButtonHandler(v: View) {
        tabFragments[currentMenuTab].addButtonPressed()
    }

    /**
     * In the main body of the screen, the recycler will consume touch events before the activity's
     * onTouchEvent is tried. Therefore we check for fling in touch event dispatch, but still
     * dispatch the event down to children.
     *
     * It seems like onInterceptTouchEvent is what is intended to be used for cases like this, but
     * that's not a method activities have so I'm not sure how to use it here.
     *
     * ev: Motion event being dispatched
     */
    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        gestureDetector.onTouchEvent(ev!!)
        return super.dispatchTouchEvent(ev)
    }

    /**
     * Gesture listener implementation to pass to gestureDetector. Listens for left/right fling.
     * When detected, changes the active tab to the one immediately to the left/right of the current
     * one.
     */
    inner class FlingListener: GestureDetector.SimpleOnGestureListener() {
        override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
            //If the gesture was more vertical than horizontal, disregard it
            if(Math.abs(velocityY) > Math.abs(velocityX)) return false

            val tabChange: Int = -1 * sign(velocityX).toInt() //Only move by one tab at a time
            val targetTab = MathUtils.clamp(tabs.selectedTabPosition + tabChange, 0, tabs.tabCount)
            if(targetTab != tabs.selectedTabPosition) {
                tabs.getTabAt(targetTab)?.select()
                return true
            }
            return false
        }
    }
}