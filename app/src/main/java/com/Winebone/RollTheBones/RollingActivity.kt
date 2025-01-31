package com.Winebone.RollTheBones

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.Winebone.RollTheBones.dicepool.DieTypesManager
import com.Winebone.RollTheBones.dicepool.PoolList
import com.Winebone.RollTheBones.dicepool.SavedPoolManager

/**
 * Copyright (c) 2021-2025 Michael Usher
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of
 * the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

/**
 * Activity for rolling a pool of dice. The main body of the activity is a RollArea fragment. Also
 * displays the total of the last pool roll at the top of the screen.
 *
 * May receive through the ActivityArguments mechanism:
 * pool: PoolList to display and roll. If not set, the default pool will be retrieved from the saved
 *       pool manager.
 * poolName: String, the name that a pool being loaded was saved as. Displayed in the action bar,
 *           and will be the default name in the save dialogue.
 * rollOnOpen: Boolean, if true roll the pool after the view has been built. Otherwise the pool will
 *             initially show config faces and a zero result.
 * If no arguments are passed, ActivityArguments.SetIdentifier must be called on the intent prior to
 * launching this activity.
 */
class RollingActivity : AppCompatActivity() {
    private lateinit var rollAreaFrag: RollArea
    private lateinit var resultTextView: TextView
    private lateinit var activityToolbar: androidx.appcompat.widget.Toolbar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(null)
        setContentView(R.layout.activity_rolling)
        DieTypesManager.InitializeManager(this)

        //Uses a custom action bar element
        setSupportActionBar(findViewById(R.id.rollingActivityToolbar))

        //Set the after-roll callback in the roll area to update the total text.
        rollAreaFrag = supportFragmentManager.findFragmentById(R.id.rollingRollFragContainer) as RollArea
        resultTextView = findViewById<TextView>(R.id.resultText)
        activityToolbar = findViewById(R.id.rollingActivityToolbar)
        rollAreaFrag.afterRollCallback = ::setSumText

        //If the arguments say to do so, set up a call back to roll the dice immediately after
        //building the roll area display. If an initial roll is not being done, show the config dice
        //ghosted.
        var rollOnOpen: Boolean = ActivityArguments.RetrieveArgument<Boolean>(intent,
            "rollOnOpen") ?: false
        if(rollOnOpen) rollAreaFrag.afterDisplayBuiltCallback = ::doInitialRoll
        else rollAreaFrag.afterDisplayBuiltCallback = ::ghostConfigDice

        //Retrieve the pool to display, or the default pool if non was passed.
        val pool: PoolList = ActivityArguments.RetrieveArgument<PoolList>(intent, "pool")
            ?: SavedPoolManager.GetInitialPool(this.applicationContext).second
        var poolName: String? = ActivityArguments.RetrieveArgument<String>(intent, "poolName")
        rollAreaFrag.setPoolList(pool)
        rollAreaFrag.poolName = poolName
    }

    /**
     * Function to roll the dice immediately after opening the activity. Will be set into the
     * afterDisplayBuiltCallback of the roll area if an initial roll is desired.
     */
    private fun doInitialRoll() {
        setSumText(rollAreaFrag.Roll());
    }

    /**
     * Show the config face for all the dice, with the alpha set low. Will be set into the
     * afterDisplayBuiltCallback of the roll area if an initial roll is not desired.
     */
    private fun ghostConfigDice() {
        rollAreaFrag?.poolFragment?.value?.ShowGhostedConfigDice()
    }

    /**
     * Add a menu item to the toolbar: in addition to the options from RollArea, this activity has
     * an option to open this pool to edit in the configuration activity. It's displayed as a button
     * if there's room.
     */
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        if(menu != null) {
            menuInflater.inflate(R.menu.rolling_activity_options, menu)
            return true
        }
        return false
    }

    /**
     * Handler for selecting menu options in the toolbar. Home button is a hamburger menu icon to go
     * to the main menu. Edit icon opens this pool in the config activity.
     */
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when(item.itemId) {
            R.id.optionsMenuEdit -> {
                goToConfigActivity()
                return true
            }
            android.R.id.home -> {
                rollAreaFrag.goToMainMenu()
                return true
            }
            else -> {
                return super.onOptionsItemSelected(item)
            }
        }
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
     * Displays the numeric total result of a pool roll. Is set in RollArea as a callback for after
     * a roll is done (afterRollCallback).
     * sum: Pool roll result.
     */
    private fun setSumText(sum: Int) {
        resultTextView.text = sum.toString()
        activityToolbar.title = getString(R.string.rolling_activity_toolbar_alt_title)
    }

    /**
     * Launch the configuration activity and pass it the current pool.
     */
    private fun goToConfigActivity() {
        var intent = Intent(this, PoolConfiguration::class.java)

        var pools: PoolList = (rollAreaFrag.poolFragment?.value?.Duplicate() ?: PoolList()) as PoolList
        ActivityArguments.StoreArgument(intent, "pool", pools)
        ActivityArguments.StoreArgument(intent, "poolName", rollAreaFrag.poolName)

        startActivity(intent)
    }
}