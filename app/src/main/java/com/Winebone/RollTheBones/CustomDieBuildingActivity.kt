package com.Winebone.RollTheBones

import android.content.DialogInterface
import android.content.Intent
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.HapticFeedbackConstants
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.*
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.Winebone.RollTheBones.dicepool.DieType
import com.Winebone.RollTheBones.dicepool.DieTypesManager
import java.util.*

private const val FIELD_SEPARATOR = "\u001F"

/**
 * Copyright (c) 2021-2025 Michael Usher
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of
 * the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

/**
 * Activity for creating or altering custom dice. Contains EditText fields for the die name, minimum
 * result, and maximum result. Also contains an expandable list of tiles for setting custom images
 * to use for faces of the die, and a button to save the die.
 *
 * May receive through the ActivityArguments mechanism:
 * die: DieType for an existing custom die to edit.
 */
class CustomDieBuildingActivity : AppCompatActivity(), ActivityResultCallback<Uri>, TextWatcher {
    private lateinit var nameInput: EditText
    private lateinit var minInput: EditText
    private lateinit var maxInput: EditText
    private lateinit var saveButton: Button
    private lateinit var customFaceArea: HorizontalScrollView
    private lateinit var customFaceList: LinearLayout
    private lateinit var faceListCollapseArrow: ImageView
    private lateinit var noFacesBanner: View
    private lateinit var validationPopupBuilder: AlertDialog.Builder
    private var imageRetriever = registerForActivityResult(ActivityResultContracts.OpenDocument(), this)
    private var awaitingImageForFace: Int? = null
    private val faceListOpen: Boolean
        get() { return customFaceArea.visibility == View.VISIBLE }

    //Populated if making changes to an existing die type, rather than creating a new one.
    private var editedDie: DieType? = null
    //Custom faces are stored according to the result value they're for rather than an index into the
    //list of faces. Thus if the result range is changed after a face is selected, the image will
    //still be used for the same value. Note that this means there may be face images stored in the
    //activity that aren't in the current range of face values.
    private var faceImageForResult = HashMap<Int, Pair<Uri?, Drawable>>()

    /**
     * Lifecycle event to set up the activity
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_custom_die_building)
        DieTypesManager.InitializeManager(this)

        //Find the custom toolbar view
        setSupportActionBar(findViewById(R.id.customDieActivityToolbar))

        //Find views
        nameInput = findViewById(R.id.editTextDieName)
        minInput = findViewById(R.id.editNumDieMin)
        maxInput = findViewById(R.id.editNumDieMax)
        customFaceArea = findViewById(R.id.customFaceArea)
        customFaceList = customFaceArea.findViewById(R.id.customFaceList)
        noFacesBanner = findViewById(R.id.noFacesBanner)

        //Changing min/max value invalidates the current list of custom faces
        minInput.addTextChangedListener(this)
        maxInput.addTextChangedListener(this)

        //Save handler
        saveButton = findViewById<Button>(R.id.customDieSaveButton)
        saveButton.setOnClickListener(
            fun(v: View) { onSaveButtonClick() }
        )

        //Custom face list expand/collapse handler
        var faceDivider = findViewById<View>(R.id.customFacesDivider)
        faceListCollapseArrow = faceDivider.findViewById(R.id.faceListCollapseArrow)
        faceDivider.setOnClickListener(
            fun(v: View) { toggleFaceListVisibility() }
        )

        //Initialize the popup builder for showing validation results
        validationPopupBuilder = AlertDialog.Builder(this)
        validationPopupBuilder.setPositiveButton(getString(R.string.popup_button_ok_no_action), null)

        //Determine if we're editing an existing die or creating a new one
        editedDie = ActivityArguments.RetrieveArgument<DieType>(intent, "die")
        if(editedDie != null) {
            //Show the name of the edited die in the toolbar
            supportActionBar?.title = getString(R.string.edit_custom_die_toolbar_title, editedDie!!.name)

            //Put it in the name field and prevent the user from changing it
            //Could make that possible in the future
            nameInput.setText(editedDie!!.name)
            nameInput.isEnabled = false

            //Set min and max fields from the currently saved values
            minInput.setText(editedDie!!.minResult.toString())
            maxInput.setText(editedDie!!.maxResult.toString())

            //Insert any existing custom face images into the face image map
            for(result in ((editedDie!!.minResult)..(editedDie!!.maxResult))) {
                val faceDrawable: Drawable = editedDie!!.faceDrawableForResult(result)
                val faceDrawableUri: Uri? = editedDie!!.faceDrawableUriForResult(result)
                if(faceDrawable !is TextDieFace) {
                    faceImageForResult[result] = Pair(faceDrawableUri, faceDrawable)
                    //Checking the type is kind of a hack, but I doubt I'll use TextDieFace for anything else
                }
            }
        }

        //If activity was killed in the background when picking an image, restore state
        awaitingImageForFace = savedInstanceState?.getInt("awaitingImageForFace")
        var faceUrisState = savedInstanceState?.getStringArrayList("faceUris")
        if(faceUrisState != null) {
            for(faceData in faceUrisState) {
                var fields = faceData.split(FIELD_SEPARATOR)
                if(fields.size < 2 || fields[1] == "") continue

                val forResult = fields[0].toIntOrNull() ?: continue
                if(faceImageForResult[forResult]?.first?.toString() != fields[1]) {
                    //cached state is not the same as the saved die
                    var uri = Uri.parse(fields[1])
                    setFaceFromUri(forResult, uri)
                }
            }
        }
    }

    /**
     * Toggle whether or not the list of custom faces is visible.
     */
    private fun toggleFaceListVisibility() {
        if(faceListOpen) hideFaceList()
        else showFaceList()
    }

    /**
     * Show the custom face list, by making its parent element visible and rotating the collapse
     * arrow icon. Will rebuild the list of views for each face.
     */
    private fun showFaceList() {
        customFaceArea.visibility = View.VISIBLE
        faceListCollapseArrow.rotation = 90f
        buildFaceList()
    }

    /**
     * Hide the custom face list, my making its parent element invisible and rotating the collapse
     * arrow icon.
     */
    private fun hideFaceList() {
        customFaceArea.visibility = View.GONE
        noFacesBanner.visibility = View.VISIBLE
        faceListCollapseArrow.rotation = 0f
    }

    /**
     * TextWatcher function. When the range values this die would produce changes, the list of views
     * for setting custom faces is no longer valid. This is handled by closing that list when the range
     * changes; when it is opened again the list will be rebuilt for the current range.
     * s: The text editable that was changed. The activity should only be registered for the min/max
     *    fields, and we do the same thing in either case.
     */
    override fun afterTextChanged(s: Editable?) {
        hideFaceList()
    }

    /**
     * TextWatcher function must be implemented, but is not needed here.
     */
    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
        /* no-op */
    }

    /**
     * TextWatcher function must be implemented, but is not needed here.
     */
    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
        /* no-op */
    }

    /**
     * Rebuilds the list of views for showing and selecting custom face images. Removes all current
     * views and creates new ones. For values that have a custom face: display it in the image view,
     * show the button to clear that face, remove the border, and set the text to the value of the
     * face. For other values: show a greyed-out generic face with the value and leave the text as
     * "select".
     *
     * This is currently being done each time the face list is opened. Would probably be better as a
     * RecyclerView.
     */
    private fun buildFaceList() {
        //Remove the old views
        customFaceList.removeAllViews()

        //If the current text in the min/max fields can't be interpreted as an actual range, show
        //the no-faces-banner instead of building views. That element is set to Visible in hideFaceList
        //so we don't need to do that here.
        val minResult = minInput.text.toString().toIntOrNull() ?: return
        val maxResult = maxInput.text.toString().toIntOrNull() ?: return
        if(minResult > maxResult) return

        //There are actual faces to show
        noFacesBanner.visibility = View.GONE //Hide the no face banner
        for(result in (minResult..maxResult)) {
            //Create the view for this result and add it to the list
            var resultItem = layoutInflater.inflate(R.layout.custom_face_list_item_layout, customFaceList, false)
            customFaceList.addView(resultItem)

            //Open image selector when it's clicked
            resultItem.setOnClickListener(
                fun(v: View) { pickImage(result) }
            )

            var resultText = resultItem.findViewById<TextView>(R.id.faceValue)
            var resultImage = resultItem.findViewById<ImageView>(R.id.faceImage)

            //Determin whether there's a custom face image for this result
            var setFaceImage: Drawable? = faceImageForResult[result]?.second
            if(setFaceImage == null) {
                //Set the face image to a generic face showing the value
                resultImage.setImageDrawable(DieTypesManager.GenerateGenericFace(result))
                resultImage.contentDescription = getString(R.string.default_face_image, result)
            }
            else {
                //Display the custom face
                resultImage.setImageDrawable(setFaceImage)
                resultImage.contentDescription = getString(R.string.custom_face_image, result)
                resultImage.imageTintList = null //Custom faces aren't tinted by the theme
                resultImage.alpha = 1f //Generic face is greyed out in the layout

                resultText.text = result.toString() //Show the face value in the bottom text instead of "select"
                resultItem.background = null //Remove the border

                //Set up and display the button to remove the custom face from this value
                var removeFaceButton = resultItem.findViewById<ImageView>(R.id.removeCustomFaceButton)
                removeFaceButton.visibility = View.VISIBLE
                removeFaceButton.setOnClickListener(
                    fun(v: View) { removeImage(result) }
                )
            }
        }
    }

    /**
     * Set up the toolbar menu. This activity has two menu entries:
     *     Discard changes: return to the menu without saving any changes. If the activity is for
     *         creating a new die this is labelled "Discard Die", otherwise it's "Discard Changes".
     *     Delete Die: Only visible if editing an existing die. Delete the edited die entirely.
     */
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        if(menu != null) {
            menuInflater.inflate(R.menu.custom_die_activity_options, menu)

            if(editedDie != null) {
                var discardOption = menu.findItem(R.id.optionsMenuDiscardDie)
                discardOption.title = getString(R.string.custom_die_activity_menu_discard_changes)

                var deleteOption = menu.findItem(R.id.optionsMenuDeleteDie)
                deleteOption.isVisible = true
            }

            return true
        }
        return false
    }

    /**
     * Handle toolbar menu option selection. See onCreateOptionsMenu for what options are there.
     * Also has custom handling of the home button, which is an X and functions the same as the
     * "discard changes" button.
     */
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when(item.itemId) {
            R.id.optionsMenuDiscardDie -> {
                finishAndLaunchMenu()
                return true
            }
            R.id.optionsMenuDeleteDie -> {
                if(editedDie == null) {
                    //This option shouldn't be visible if creating a new die, but if it is somehow
                    //selected just finish the activity.
                    finishAndLaunchMenu()
                }
                else {
                    onDeleteOptionClicked()
                }
                return true
            }
            android.R.id.home -> {
                finishAndLaunchMenu()
                return true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    /**
     * If the activity process is killed in the background (particularly likely when selecting a
     * custom face image) then state must be stored for restoring the activity. Specifically any
     * custom faces that had already been selected, and which face was being selected if that was
     * happening. The values in the entry fields appears to be handled internally.
     */
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        //If the image picker was open and we were waiting for a result, we'll need to recover which
        //face that result would be for. That can be stored as an integer.
        if(awaitingImageForFace != null) {
            outState.putInt("awaitingImageForFace", awaitingImageForFace!!)
        }

        //Any custom faces which had already been picked will need to be recovered. This will be
        //stored as an array of strings. Each string is for one face value, and has the format
        //<face value> FIELD_SEPARATOR <drawable URI string>. Note that these are not necessarily
        //in order.
        var faceUris = ArrayList<String>(faceImageForResult.map { faceForResult ->
            faceForResult.key.toString() + FIELD_SEPARATOR + (faceForResult.value.first?.toString() ?: "")
        })
        outState.putStringArrayList("faceUris", faceUris)
    }

    /**
     * Use the Android framework to launch an activity to pick an image in device storage. The image
     * picked is to be used as the face image for some result of this die. If an exception occurs
     * while trying to launch the picker, will show a popup saying it couldn't be opened.
     * forFace: What die result will the picked image be used for? This value will be stored until
     *          the picker activity sends a result back.
     */
    private fun pickImage(forFace: Int) {
        try {
            imageRetriever.launch(arrayOf("image/*"))
            awaitingImageForFace = forFace
        }
        catch(exception: Exception) {
            var alertBuilder = AlertDialog.Builder(this)
            alertBuilder.setMessage(getString(R.string.popup_could_not_select_image_for_custom_face))
            alertBuilder.setPositiveButton(getString(R.string.popup_button_ok_no_action), null)
            alertBuilder.create().show()
        }
    }

    /**
     * This will be called when the image picker activity is finished and sends back a result. That
     * result should be a URI for an image on the device. Load a drawable from that URI and put it
     * into the custom face map for the awaited value. Acquires persistent permission for the
     * application to use that image. Shows a popup if there's an error loading the image.
     * result: The URI returned by the picker
     */
    override fun onActivityResult(result: Uri?) {
        val forFace = awaitingImageForFace ?: return showImageLoadError()
        awaitingImageForFace = null
        if(result == null) return

        try {
            contentResolver.takePersistableUriPermission(result, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            setFaceFromUri(forFace, result)
            if(faceListOpen) buildFaceList()
            else toggleFaceListVisibility() //Always show the face list after receiving a face
        }
        catch (exception: Exception) {
            showImageLoadError()
        }
    }

    /**
     * Load the drawable from a URI and puts it into the custom face map. Called from activity
     * creation if needed and onActivityResult. Error handling must be done by consumers. If no
     * drawable is found, the face will not be set.
     * forFace: Die face value the drawable will be the face for
     * uri: URI to load the drawable from
     */
    private fun setFaceFromUri(forFace: Int, uri: Uri) {
        var inputStream = contentResolver.openInputStream(uri)
        var faceDrawable = Drawable.createFromStream(inputStream, uri.toString())
        if(faceDrawable != null) faceImageForResult[forFace] = Pair(uri, faceDrawable)
    }

    /**
     * Shows a popup indicating there was an error while trying to load a face drawable from a URI.
     */
    private fun showImageLoadError() {
        var alertBuilder = AlertDialog.Builder(this)
        alertBuilder.setMessage(getString(R.string.popup_selected_custom_face_image_not_loaded))
        alertBuilder.setPositiveButton(getString(R.string.popup_button_ok_no_action), null)
        alertBuilder.create().show()
    }

    /**
     * Removes the custom face image for a result. If the face list is currently being displayed,
     * immediately rebuild it so that face's view will not show a custom face.
     * forFace: Die face value to remove the drawable from
     */
    private fun removeImage(forFace: Int) {
        faceImageForResult.remove(forFace)
        if(faceListOpen) buildFaceList()
    }

    /**
     * Handler for the save button. Retrieves values from the input fields, then parses & validates
     * them. If validation is successful, saves the die and finishes the activity (to the main menu).
     * If it's unsuccessful, displays a popup with the validation errors. If the user is attempting
     * to save a new die with the name of a custom die that already exists, will show a popup to
     * confirm overwriting the existing die before saving.
     */
    private fun onSaveButtonClick() {
        val nameText = nameInput.text.toString()
        val minText = minInput.text.toString()
        val maxText = maxInput.text.toString()
        if(!validateInput(nameText, minText, maxText)) return

        if(editedDie == null && DieTypesManager.HasCustomType(nameText)) confirmOverwrite(nameText, minText, maxText)
        else saveDie(nameText, minText, maxText)
    }

    /**
     * Displays a popup to confirm overwriting an existing custom die with a new one. This occurs if
     * the name is the same but the activity was not opened as editing the existing die. If the user
     * confirms, the die will be saved and the activity finished.
     * nameText: The name of the die that would be overwritten.
     * minText: Text from the minimum value field. Needed for passing to saveDie if confirmed.
     * maxText: Text from the maximum value field. Needed for passing to saveDie if confirmed.
     */
    private fun confirmOverwrite(nameText: String, minText: String, maxText: String) {
        var confirmBuilder = AlertDialog.Builder(this)
        confirmBuilder.setMessage(getString(R.string.popup_save_die_type_confirm_overwrite, nameText))
        confirmBuilder.setNegativeButton(getString(R.string.popup_button_cancel_no_action), null)
        confirmBuilder.setPositiveButton(
            getString(R.string.popup_button_overwrite_existing_saved_item), DialogInterface.OnClickListener(
            fun(d: DialogInterface, whichButton: Int) {
                saveDie(nameText, minText, maxText)
            }))
        confirmBuilder.create().show()
    }

    /**
     * Saves the die, finishes the activity, and goes back to the main menu. Assumes all checks and
     * validation have been done.
     * nameText: Name to save the die under
     * minText: Text from the minimum value field
     * maxText: Text from the maximum value field
     */
    private fun saveDie(nameText: String, minText: String, maxText: String) {
        var dieToSave: DieType = buildDie(nameText, minText, maxText)
        var saveResult = DieTypesManager.SaveCustomType(this.applicationContext, dieToSave)
        if(saveResult.first) {
            saveButton.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            finishAndLaunchMenu()
        }
        else {
            var saveIssueBuilder = AlertDialog.Builder(this)
            saveIssueBuilder.setTitle(getString(R.string.problem_saving_die_popup_name))
            saveIssueBuilder.setMessage(getString(R.string.problem_saving_die_popup_message, saveResult.second))
            saveIssueBuilder.setPositiveButton(getString(R.string.popup_button_ok_no_action), null)
            saveIssueBuilder.create().show()
        }

    }

    /**
     * Handler for the "delete die" menu option. If the activity is not editing an existing die, this
     * does nothing. If it is, attempts to delete the edited die from the die types manager, then
     * closes the activity and returns to the main menu. If there's an error while deleting, shows
     * a popup for the error instead.
     */
    private fun onDeleteOptionClicked() {
        if(editedDie == null) return

        var deleteResults = DieTypesManager.DeleteCustomDie(this.applicationContext, editedDie!!.name)
        if(!deleteResults.first) {
            var messageBuilder = android.app.AlertDialog.Builder(this)
            messageBuilder.setTitle(getString(R.string.problem_deleting_dice_popup_name))
            messageBuilder.setMessage(
                getString(R.string.problem_deleting_dice_popup_message, editedDie!!.name, deleteResults.second))
            messageBuilder.setPositiveButton(getString(R.string.popup_button_ok_no_action), null)
            messageBuilder.create().show()
        }
        else {
            finishAndLaunchMenu()
        }
    }

    /**
     * Builds a DieType based on the values in input fields and custom faces that have been selected.
     * Used when saving the die, and assumes all checks and validation have already been done. Parses
     * values assuming they are properly formatted.
     * name: The name to give the die type
     * minText: Text from the minimum value field, to be parsed as an integer
     * maxText: Text from the maximum value field, to be parsed as an integer
     * Returns: New die type built from the entered data
     */
    private fun buildDie(name: String, minText: String, maxText: String): DieType {
        //Parse the numeric entry field values as integers
        val minResult = minText.toInt()
        val maxResult = maxText.toInt()

        //Build an array of null-or-custom-face, which is passed to the die types manager to
        //generate the complete list of face drawables.
        var setFaces = arrayOfNulls<Drawable>(maxResult - minResult + 1)
        var setFaceUris = arrayOfNulls<Uri>(maxResult - minResult + 1)
        for(result in (minResult..maxResult)) {
            setFaces[result-minResult] = faceImageForResult[result]?.second
            setFaceUris[result-minResult] = faceImageForResult[result]?.first
        }

        //Get a list of face drawables, which has any custom faces and generic face drawables for
        //any other face.
        var dieFaces: Array<Drawable> = DieTypesManager.GenerateFaces(minResult, maxResult, setFaces)
        var configFace: Drawable? = DieTypesManager.GenerateConfigFace(minResult, maxResult, setFaces)
        var result = DieType(name, minResult, maxResult, dieFaces, configFace)
        result.faceDrawableUris = setFaceUris
        return result
    }

    /**
     * Validates entry field data, to check that a properly formatted die could be built. Checks:
     * -Name field is populated
     * -Minimum and maximum are populated, and are actually integers.
     * -Minimum is less than or equal to maximum
     * If an issue is found, displays a popup with the validation messages.
     * name: Value from the name entry field
     * minText: Value from the minimum entry field
     * maxText: Value from the maximum entry field
     * Returns: True if all validation checks were good, false otherwise.
     */
    private fun validateInput(name: String, minText: String, maxText: String): Boolean {
        var validationIssues = LinkedList<String>()

        //Check it has a name
        if(name.isEmpty()) {
            validationIssues.add(getString(R.string.die_validation_no_name))
        }

        //Check min is good
        var checkRange = true
        if(minText.isEmpty()) {
            validationIssues.add(getString(R.string.die_validation_no_minimun_result))
            checkRange = false
        }
        else if(minText.toIntOrNull() == null) {
            validationIssues.add(getString(R.string.die_validation_specified_minimum_invalid))
            checkRange = false
        }

        //Check max is good
        if(maxText.isEmpty()) {
            validationIssues.add(getString(R.string.die_validation_no_maximum_result))
            checkRange = false
        }
        else if(maxText.toIntOrNull() == null) {
            validationIssues.add(getString(R.string.die_validation_specified_maximum_invalid))
            checkRange = false
        }

        //Check min <= max. Only do this if both min and max are good
        if(checkRange && minText.toInt() > maxText.toInt()) {
            validationIssues.add(getString(R.string.die_vallidation_min_above_max))
        }

        //If there were errors, show them
        if(validationIssues.size > 0) {
            showValidationMessage(validationIssues)
            return false
        }
        return true
    }

    /**
     * Display a popup for validation errors encountered trying to save the die.
     * issueMessages: List of validation error message strings
     */
    private fun showValidationMessage(issueMessages: LinkedList<String>) {
        var fullMessage = ""
        for(msg in issueMessages) {
            fullMessage += msg + "\n"
        }
        validationPopupBuilder.setMessage(fullMessage)
        validationPopupBuilder.create().show()
        saveButton.performHapticFeedback(HapticFeedbackConstants.REJECT)
    }

    /**
     * Clears the argument which may have been sent to the activity, then finishes the activity.
     * This should result in going to the menu, as that's where this is launched from.
     */
    private fun finishAndLaunchMenu() {
        ActivityArguments.ClearArgument(intent, "die")
        finish()
    }
}