package com.Winebone.RollTheBones

import android.app.AlertDialog
import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import com.Winebone.RollTheBones.dicepool.DieTypesManager

/**
 * Copyright (c) 2021-2025 Michael Usher
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of
 * the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

/**
 * Dialog fragment for a popup which is shown when a saved pool is opened, and that pool contains
 * a placeholder die type. This is checked when opening the pool in the rolling activity or the
 * config activity (by way of RollArea). Having a placeholder type indicates when the pool was
 * loaded there was a die type name in it that the die types manager did not recognize, which is to
 * say the pool was saved  with a die type which has since been deleted (and no type with the same
 * name has been created). If multiple die types are missing, this popup will be shown several times
 * in a row.
 *
 * The popup contains text specifying the name of the missing die type. The user has the option to
 * either close the pool and go to the main menu, or replace the die type with one that does exist.
 * The replacement does not overwrite the saved pool, just changes it as loaded into the activity.
 * The change can be saved from there.
 *
 * Replacing the die type replaces all instances of it in the pool, but does not affect any other
 * pools with the same placeholder. Note that if the type is replaced and the pool saved with the
 * replacement, creating a new die type later with the original name will not undo the replacement.
 *
 * missingDieName: Name of the missing die type
 * exitHandler: Called when the user selects the "Exit to Menu" button, so this should finish the
 *              current activity and launch the menu.
 * replaceHandler: Called when the user selects the "Replace" button. Will be passed the die name
 *                 currently selected in the spinner. Should call ReplaceDieType on the pool.
 */
class MissingDieDialog(val missingDieName: String,
                       val exitHandler: () -> Unit,
                       val replaceHandler: (String) -> Unit): DialogFragment() {
    private lateinit var dieSelector: Spinner

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        //Make the alert builder
        var builder = AlertDialog.Builder(requireActivity())
        builder.setTitle(getString(R.string.missing_die_dialogue_name))
        super.setCancelable(false) //use super as setCancelable is overridden to prevent changes

        //Inflate the layout
        var inflater = requireActivity().layoutInflater
        var dialogueLayout = inflater.inflate(R.layout.missing_die_dialogue_layout, null)
        builder.setView(dialogueLayout)

        //Set the first block of text in the popup, which identifies the missing die.
        dialogueLayout.findViewById<TextView>(R.id.missingDialogueMessage2).text =
            requireContext().getString(R.string.missing_die_dialogue_message, missingDieName)
        //The second block of text identifies the options the user has. It is fixed wrt the die type,
        //so it's specified in the layout.

        //Create the spinner to select a replacement die type. Spinner shows the names of all
        //existing die types, with built-in types first.
        var diceAdapter = ArrayAdapter(requireContext(), R.layout.array_adapter_item,
            DieTypesManager.EnnumerateDice())
        dieSelector = dialogueLayout.findViewById<Spinner>(R.id.missingDialogueDieSelector)
        dieSelector.adapter = diceAdapter

        //Build the buttons
        builder.setNeutralButton(getString(R.string.missing_die_dialogue_exit_button), ::onNeutralResponse)
        builder.setPositiveButton(getString(R.string.missing_die_dialogue_replace_die_button), ::onPositiveResponse)
        return builder.create()
    }

    /**
     * User must respond one way or the other; can't just cancel out to the activity.
     * Returns: false
     */
    override fun isCancelable(): Boolean {
        return false
    }

    /**
     * Trying to make the popup cancelable wouldn't work anyway
     */
    override fun setCancelable(cancelable: Boolean) {
        throw Exception("Can't set cancelable on missing die dialogue")
    }

    /**
     * User has pressed the "Exit to Menu" button, call the handler for that that we were given.
     * dialogue: This dialogue. Ignored.
     * whichButton: Which button was pressed. Ignored.
     */
    @Suppress("UNUSED_PARAMETER")
    private fun onNeutralResponse(dialogue: DialogInterface, whichButton: Int) {
        exitHandler()
    }

    /**
     * User has pressed the "Replace" button. Retrieve the die type name that's currently selected
     * in the spinner and pass it to the handler we were given.
     * dialogue: This dialogue. Ignored.
     * whichButton: Which button was pressed. Ignored.
     */
    @Suppress("UNUSED_PARAMETER")
    private fun onPositiveResponse(dialogue: DialogInterface, whichButton: Int) {
        val selection = dieSelector.selectedItem as String
        replaceHandler(selection)
    }
}