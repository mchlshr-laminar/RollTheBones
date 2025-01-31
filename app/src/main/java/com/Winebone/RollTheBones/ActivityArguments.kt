package com.Winebone.RollTheBones

import android.content.Intent
import com.Winebone.RollTheBones.dicepool.DicePoolValue
import java.util.*

/**
 * Copyright (c) 2021-2025 Michael Usher
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of
 * the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

/**
 * Singleton for passing objects between activities in the application. When building the intent to
 * launch an activity, use this singleton to store objects for it to retrieve. The intent will have
 * a UUID string extra added for identifying the objects intended for it, and the object will be
 * stored in a map with a key combining the UUID and the argument name. If an activity is going to
 * retrieve objects from this, it must have the UUID in the intent before the activity is launched.
 *
 * Each public function here takes an Intent object, which is the Intent for the activity which will
 * receive the arguments.
 */
object ActivityArguments {
    const val IDENTIFIER_EXTRA = "IdentifierExtra"

    private var argStore = HashMap<String, Any?>()

    /**
     * Adds the UUID extra to an intent without adding any arguments. This is used if an activity is
     * being launched without any arguments, but it may need to store arguments for itself after being
     * launched, which is to recover state after a process is killed.
     */
    fun SetIdentifier(intent: Intent) {
        //GetIdentifier causes the identifier to be created if it doesn't exist, but for this
        //function's purpose we don't care what it is.
        GetIdentifier(intent)
    }

    /**
     * Stores an argument for an activity. If the intent doesn't have the UUID extra yet, one will be
     * generated for it. If this argument for this intent has previously been stored, the old value
     * will be overwritten.
     * name: The argument name
     * argument: The object to store
     */
    fun <T> StoreArgument(intent: Intent, name: String, argument: T) {
        val identifier = GetIdentifier(intent)
        argStore[key(identifier, name)] = argument
    }

    /**
     * Retrieves an argument for an activity. If that argument is a DicePoolValue, returns a duplicate
     * of the stored value, to avoid changes to the object the activity makes altering the stored
     * value.
     *
     * Consider changing back to a system where arguments are cleared after being recovered; this
     * would require re-storing the arguments in onSaveInstanceState.
     *
     * T is the type of the argument the activity is looking for.
     * name: The name of the argument
     * Returns: Null if no argument of that name was stored for the activity, or the value that was
     *          found is not the right type (not type T).
     */
    fun <T> RetrieveArgument(intent: Intent, name: String): T? {
        val identifier = GetIdentifier(intent)
        val argument = argStore[key(identifier, name)] as? T?
        if(argument is DicePoolValue) return argument.Duplicate() as T
        else return argument
    }

    /**
     * Remove an argument of a given name for a given intent from the map. Activities should call
     * this when they finish to avoid leaving their arguments around.
     *
     * Consider changing the structure so this could clear all arguments for an intent.
     *
     * name: The name of the argument to remove for this intent
     */
    fun ClearArgument(intent: Intent, name: String) {
        val identifier = GetIdentifier(intent)
        argStore.remove(key(identifier, name))
    }

    /**
     * Gets the UUID string for an intent. If that intent does not yet have one stored, a UUID will
     * be generated and added to the intent. Otherwise the one already in the intent will be returned.
     * Returns: UUID string for the intent & its activity.
     */
    private fun GetIdentifier(intent: Intent): String {
        var identifier = intent.getStringExtra(IDENTIFIER_EXTRA)
        if(identifier.isNullOrEmpty()) {
            identifier = UUID.randomUUID().toString()
            intent.putExtra(IDENTIFIER_EXTRA, identifier)
        }
        return identifier
    }

    /**
     * Get the map key that would be used for an argument for an activity. This will have the format
     * of <UUID string>_<argument name>.
     * identifier: UUID string
     * name: Argument name
     * Returns: Key into argStore where the argument would be stored
     */
    private fun key(identifier: String, name: String): String {
        return identifier + "_" + name
    }
}