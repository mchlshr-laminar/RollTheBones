package com.Winebone.RollTheBones.dicepool

import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.graphics.drawable.Drawable
import android.net.Uri
import android.util.TypedValue
import androidx.appcompat.content.res.AppCompatResources
import com.Winebone.RollTheBones.R
import com.Winebone.RollTheBones.TextDieFace
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.util.*

private const val DEFAULT_TYPE_INDEX = 1
private const val CUSTOM_TYPES_FILE = "customDice"
private const val FIELD_SEPARATOR = "\u001F"

/**
 * Copyright (c) 2021-2025 Michael Usher
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of
 * the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

/**
 * Singleton to handle and provide the available die types for the application, and the files for custom types.
 * All public functions here require that the manager has been initialized first and will through an
 * exception if it hasn't been. Therefore any activity that uses the die types manager should call
 * the InitializeManager function in its OnCreate method, before attempting to access any die types.
 * If the manager has already been initialized this call will update the theme on dice that have
 * already been loaded, but will not re-load the dice.
 *
 * The built-in die types are defined here and will be built when the manager is initialized.
 *
 * Custom die type are enumerated in a list file, and each custom type has a file defining its data.
 * The list file is saved as "customDice" in the app-specific data directory.
 * Each line of the list file has the format dieName FIELD_SEPARATOR die file name.
 * The file for each custom type is in the app-specific directory, and its name is from a random UUID.
 * Custom die files are formatted as:
 *      min value SEP max value SEP [(first face drawable URI or "") SEP (second face drawable URI or "") ...]
 */
object DieTypesManager {
    //The built-in types are D4, D6, D8, D10, D12, and D20. These prototypes will be built into
    //DieTypes during the manager initialization function.
    val builtInPrototypes = arrayOf<DiePrototype>(
        DiePrototype("D4", 1, 4, intArrayOf(
            R.drawable.ic_d4_1, R.drawable.ic_d4_2, R.drawable.ic_d4_3, R.drawable.ic_d4_4)),
        DiePrototype("D6", 1, 6, intArrayOf(
            R.drawable.d6_1, R.drawable.d6_2, R.drawable.d6_3,
            R.drawable.d6_4, R.drawable.d6_5, R.drawable.d6_6)),
        DiePrototype("D8", 1, 8, intArrayOf(
            R.drawable.ic_d8_1, R.drawable.ic_d8_2, R.drawable.ic_d8_3, R.drawable.ic_d8_4,
            R.drawable.ic_d8_5, R.drawable.ic_d8_6, R.drawable.ic_d8_7, R.drawable.ic_d8_8)),
        DiePrototype("D10", 1, 10, intArrayOf(
            R.drawable.ic_d10_1, R.drawable.ic_d10_2, R.drawable.ic_d10_3, R.drawable.ic_d10_4,
            R.drawable.ic_d10_5, R.drawable.ic_d10_6, R.drawable.ic_d10_7, R.drawable.ic_d10_8,
            R.drawable.ic_d10_9, R.drawable.ic_d10_10)),
        DiePrototype("D12", 1, 12, intArrayOf(
            R.drawable.ic_d12_1, R.drawable.ic_d12_2, R.drawable.ic_d12_3, R.drawable.ic_d12_4,
            R.drawable.ic_d12_5, R.drawable.ic_d12_6, R.drawable.ic_d12_7, R.drawable.ic_d12_8,
            R.drawable.ic_d12_9, R.drawable.ic_d12_10, R.drawable.ic_d12_11, R.drawable.ic_d12_12
        )),
        DiePrototype("D20", 1, 20, intArrayOf(
            R.drawable.ic_d20_1, R.drawable.ic_d20_2, R.drawable.ic_d20_3, R.drawable.ic_d20_4,
            R.drawable.ic_d20_5, R.drawable.ic_d20_6, R.drawable.ic_d20_7, R.drawable.ic_d20_8,
            R.drawable.ic_d20_9, R.drawable.ic_d20_10, R.drawable.ic_d20_11, R.drawable.ic_d20_12,
            R.drawable.ic_d20_13, R.drawable.ic_d20_14, R.drawable.ic_d20_15, R.drawable.ic_d20_16,
            R.drawable.ic_d20_17, R.drawable.ic_d20_18, R.drawable.ic_d20_19, R.drawable.ic_d20_20))
    )

    lateinit var builtInTypes: Array<DieType>

    /**
     * Returns an array of all the custom types in the custom type map
     */
    @Suppress("UNUSED_PARAMETER")
    var customTypes: Array<DieType>
        get() {
            checkInitialization()
            return customTypesMap.values.toTypedArray()
        }
        private set(value) {}

    /**
     * Returns the number of custom types
     */
    val customTypeCount: Int
        get() {
            checkInitialization()
            return customTypesMap.size
        }

    private var builtInTypesMap = HashMap<String, DieType>()
    private var customTypesMap = HashMap<String, DieType>()
    private var placeholderTypeMap = HashMap<String, DieType>()

    private var customDieFileNames = HashMap<String, String>()
    //RecyclerView needs a consistently-ordered list of the custom types, and map.entries doesn't
    //guarantee that. Note that the ordering may not remain consistent when dice are deleted, but
    //the recycler should do a full refresh (notifyDataSetChanged) there anyway.
    private var customTypeNames = ArrayList<String>()


    private var hasBeenInitialized = false
    private var currentIconTint: Int? = null

    /**
     * The background which will surround the face value in generic (non-customized) faces of
     * custom dice.
     */
    @Suppress("UNUSED_PARAMETER")
    var defaultFaceOutline: Drawable
        get() {
            checkInitialization()
            return _defaultFaceOutline
        }
        private set(value) {}
    private lateinit var _defaultFaceOutline: Drawable

    /**
     * If the die types manager has not been initialized, does so now. Loads faces for the built-in
     * types and creates the DieType objects for them. Loads the custom types that have been saved.
     * Applies the current theme to dice whether or not initialization has previously been done.
     * context: Activity context is needed for loading drawables and loading saved custom dice.
     */
    fun InitializeManager(context: Context) {
        if(!hasBeenInitialized) doInitialization(context)
        applyThemeToDice(context)
    }

    /**
     * Called from InitializeManager if initialization hasn't been done to do the actual loading.
     * Creates die types from the built-in die type prototypes, loads the list of custom types,
     * then loads and deserializes each custom type on that list. Sets a flag that initialization
     * has been done so we don't repeat this work each time an activity launches.
     */
    private fun doInitialization(context: Context) {
        hasBeenInitialized = true

        //Load the generic face image
        _defaultFaceOutline = AppCompatResources.getDrawable(context, R.drawable.ic_generic_die_outline)!!

        //Load built-in types
        builtInTypes = builtInPrototypes.map(
            { proto: DiePrototype -> proto.CreateDie(context) }
        ).toTypedArray()

        for(type: DieType in builtInTypes) {
            builtInTypesMap[type.name] = type
        }

        //Load the list of custom types
        try {
            loadCustomDiceList(context)
        }
        catch (exception: Exception) {
            showListLoadError(context, exception.message)
            customTypesMap.clear()
            return
        }

        //Load each custom type on the list
        var diceToHide = LinkedList<String>()
        for(dieName in customDieFileNames.keys) {
            try{
                loadCustomDie(context, dieName)
            }
            catch (exception: Exception) {
                showDieLoadError(context, dieName, exception.message)
                diceToHide.add(dieName)
            }
        }
        for(badDieName in diceToHide) {
            //Any custom dice that had load errors should not be available in the map/list
            customTypesMap.remove(badDieName)
        }

        //Generate the list of names of custom types
        for(customType in customTypesMap.values) {
            customTypeNames.add(customType.name)
        }
    }

    /**
     * Updates the face drawables of die types to match the current UI theme. Affects the faces of
     * built-in types and the generic faces of custom types. Custom die faces with a custom image
     * set will be unchanged.
     * context: Application or activity context is required to load the theme.
     */
    private fun applyThemeToDice(context: Context) {
        if(context.theme == null) return

        var typedValue = TypedValue()
        context.theme.resolveAttribute(R.attr.myIconTint, typedValue, true)
        val newIconTint: Int = typedValue.data

        if(newIconTint == currentIconTint) return
        currentIconTint = newIconTint

        tintDieList(builtInTypes, newIconTint)
        tintDieList(customTypes, newIconTint)
    }

    /**
     * Applies a tint to the relevant face images of a list of die types. Skips any custom faces
     * (i.e. faces that are loaded from a URI). Used to apply UI theme to dice.
     * diceList: The list of die types to apply tint to the faces of.
     * tint: The tint color to apply.
     */
    private fun tintDieList(diceList: Array<DieType>, tint: Int) {
        for(die in diceList) {
            for(resultInd in (0 until die.faceDrawables.size)) {
                //Skip this face if its from an image file
                if(die.faceDrawableUris?.get(resultInd) != null) continue

                die.faceDrawables[resultInd].setTint(tint)
            }
            if(die.configFaceDrawable is TextDieFace) die.configFaceDrawable.setTint(tint)
        }
    }

    /**
     * Loads a saved custom die type. Called during initialization after the list of custom types
     * has been loaded. The loaded type will be put into the customTypesMap. Throws an exception if
     * the file for the die type is not found or is malformed.
     * context: Application or activity context is required to load types from files.
     * name: The name of the custom type to be loaded
     */
    private fun loadCustomDie(context: Context, name: String) {
        val filename = customDieFileNames[name]
        if(filename == null) throw Exception("Die \"" + name + "\" not found")

        //Open file
        var dieFile = File(context.filesDir, filename)
        if(!dieFile.exists()) throw Exception("File for die \"" + name + "\" is missing")
        val dieData = dieFile.readText().split(FIELD_SEPARATOR)
        if(dieData.size < 2) throw Exception("Die \"" + name +"\" is missing data")

        //Basic values
        val minValue = dieData[1].toInt()
        val maxValue = dieData[2].toInt()
        if(minValue > maxValue) throw Exception("Minimum value is above maximum in die \"" + name +"\"")

        //Load custom faces
        var faceDrawableUris = arrayOfNulls<Uri>(maxValue-minValue+1)
        var setFaces = arrayOfNulls<Drawable>(maxValue-minValue+1)
        for(dataIndex in (3 until dieData.size)) {
            val uriString: String = dieData[dataIndex]
            val faceLoadResult = loadFaceDrawable(context, uriString)
            if(faceLoadResult != null) {
                faceDrawableUris[dataIndex-3] = faceLoadResult.first
                setFaces[dataIndex-3] = faceLoadResult.second
            }
        }

        //Get generic drawables where there's no custom faces. Determine config drawable.
        var faces = GenerateFaces(minValue, maxValue, setFaces)
        var configFace = GenerateConfigFace(minValue, maxValue, setFaces)
        var newDie = DieType(name, minValue, maxValue, faces, configFace)
        newDie.faceDrawableUris = faceDrawableUris
        customTypesMap[name] = newDie
    }

    /**
     * Loads an image from a URI string, presumably a custom face of a custom die type.
     * context: Application or activity context is required to open the input stream.
     * uriString: The URI of the image to load, as a string
     * Returns: the URI object and the drawable. If an error occurs, returns null.
     */
    private fun loadFaceDrawable(context: Context, uriString: String): Pair<Uri, Drawable>? {
        if(uriString == "") return null

        try {
            var faceUri = Uri.parse(uriString)
            var inputStream = context.contentResolver.openInputStream(faceUri)
            var result = Drawable.createFromStream(inputStream, faceUri.toString())
            if(result != null) return Pair(faceUri, result)
        }
        catch (exception: Exception) {
            return null
        }
        return null
    }

    /**
     * Displays an error that occurred while loading the list of custom dice.
     * context: activity context is required to display popups
     * message: Technical error message or message from an exception. Will display "Unknown Error"
     *          if this is not specified.
     */
    private fun showListLoadError(context: Context, message: String?) {
        val dispMessage = message ?: "Unknown Error"

        var messageBuilder = AlertDialog.Builder(context)
        messageBuilder.setTitle(context.getString(R.string.problem_loading_dice_popup_name))
        messageBuilder.setMessage(context.getString(R.string.problem_loading_dice_list_message, dispMessage))
        messageBuilder.setPositiveButton(context.getString(R.string.popup_button_ok_no_action), null)
        messageBuilder.create().show()
    }

    /**
     * Displays an error that occurred while loading the file for an individual custom die. The user
     * will be presented with the option to either do nothing, or attempt to delete that die.
     * context: activity context is required to display popups
     * dieName: The name of the die for which this error occurred
     * message: Technical error message or message from an exception. Will display "Unknown Error"
     *          if this is not specified.
     */
    private fun showDieLoadError(context: Context, dieName: String, message: String?) {
        val dispMessage = message ?: "Unknown Error"

        var messageBuilder = AlertDialog.Builder(context)
        messageBuilder.setTitle(context.getString(R.string.problem_loading_dice_popup_name))
        messageBuilder.setMessage(context.getString(
            R.string.problem_loading_dice_individual_message, dieName, dispMessage))
        messageBuilder.setPositiveButton(context.getString(R.string.popup_button_ok_no_action), null)
        messageBuilder.setNegativeButton(
            context.getString(R.string.problem_loading_dice_popup_button_delete_die),
            DialogInterface.OnClickListener(
            fun (d: DialogInterface, whichButton: Int) {
                loadErrorDelete(context, dieName)
            }))
        messageBuilder.create().show()
    }

    /**
     * Delete a custom die type which had an error while loading. If another error occurs while
     * deleting, another popup will be shown, but no further attempt to delete will be made.
     * context: activity context is required to display popups and write to files
     * dieName: The name of the die to delete
     */
    private fun loadErrorDelete(context: Context, dieName: String) {
        val deleteResults = DeleteCustomDie(context, dieName)
        if(!deleteResults.first) {
            var messageBuilder = AlertDialog.Builder(context)
            messageBuilder.setTitle(context.getString(R.string.problem_deleting_dice_popup_name))
            messageBuilder.setMessage(
                context.getString(R.string.problem_deleting_dice_popup_message, dieName, deleteResults.second))
            messageBuilder.setPositiveButton(context.getString(R.string.popup_button_ok_no_action), null)
            messageBuilder.create().show()
        }
    }

    /**
     * Loads the master list of custom die types from the list file. Each individual die will be
     * loaded after this. If no list file exists, creates one but leaves the list of custom types
     * empty. Throws an Exception if the file could not be read or is malformed, or an IOException
     * if creating the file failed.
     * context: Activity or application context is required to load from files.
     */
    private fun loadCustomDiceList(context: Context) {
        var customTypesFile = File(context.filesDir, CUSTOM_TYPES_FILE)
        if(!customTypesFile.exists()) {
            if(!customTypesFile.createNewFile()) throw IOException("Could not create custom dice file")
            return
        }

        for(dieLine in customTypesFile.readLines()) {
            val dieFileData = dieLine.split(FIELD_SEPARATOR)
            if(dieFileData.size != 2) throw Exception("Custom dice list is corrupted")
            customDieFileNames[dieFileData[0]] = dieFileData[1]
        }
    }

    /**
     * Returns the default die type to be used in the default pool (which is loaded if nothing else
     * is specified). This should be a D6. Probably ought to be a property instead of a function.
     */
    fun DefaultType(): DieType {
        checkInitialization()

        return builtInTypes[DEFAULT_TYPE_INDEX]
    }

    /**
     * Finds a die type by name. Looks first for a built-in type, then a custom type, then an
     * existing placeholder type for that name. If none of these are found then a new placeholder
     * type will be created for that name, and that will be returned.
     * name: The name of the die type to search for.
     * Returns: The die of that name, or a placeholder for it
     */
    fun GetTypeByName(name: String): DieType {
        checkInitialization()

        val returnType = builtInTypesMap[name] ?: customTypesMap[name] ?: placeholderTypeMap[name]
        if(returnType != null) return returnType

        return makeNewPlaceholder(name)
    }

    /**
     * Creates a placeholder die type for a die name and puts it into the placeholder map. These
     * placeholder types are loaded into pools when they specify die types that have been deleted
     * (i.e. contain a die type name that has no real die type). They cannot be rolled or shown in
     * the config activity, but are used by the pool to mark that the types must be replaced if the
     * pool is to be loaded.
     * name: The die type name to make a placeholder for
     * Returns: The placeholder type that was created and put into the placeholder type map
     */
    private fun makeNewPlaceholder(name: String): DieType {
        var newPlaceholder = DieType(name, 0, 0, arrayOf(), null, true)
        placeholderTypeMap[name] = newPlaceholder
        return newPlaceholder
    }

    /**
     * Determines whether a custom die type has been saved by a given name. Note that this does not
     * check whether there's a built-in type of that name, or if a placeholder type for that name
     * has been created.
     * name: Name of the custom die type to look for
     * Returns: True if there's a custom type of that name, false otherwise.
     */
    fun HasCustomType(name: String): Boolean {
        checkInitialization()

        return customTypesMap.containsKey(name)
    }

    /**
     * Gets a custom die type based on its position in the consistently-ordered list of custom types.
     * This is used by the menu RecyclerView to get the value for a given adapter position.
     * position: Index in the custom types list to get the die type for
     * Returns: Die type at that index. Does not check index bounds.
     */
    fun GetCustomTypeByPosition(position: Int): DieType {
        checkInitialization()

        val dieName = customTypeNames[position]
        return customTypesMap[dieName]!!
    }

    /**
     * Saves a custom die type. If it's a new type, adds it to the list file and creates a new file
     * for the type. Puts/overwrites the type in the custom type map and saves its data to the file
     * for the type. If any pools have been loaded which contain this die type (or a placeholder for
     * this type's name) they will be updated to use the newly saved type.
     * context: Application or activity context is required for saving to files.
     * die: The die type to be saved. The name to save it under is retrieved from the type itself.
     * Returns: (True, "") if saving was successful
     *          (False, <message>) if the die was not saved successfully. This occurs if there's an
     *                           exception while writing to a file or the die's name is also the name
     *                           of a built-in type. The second value is a technical error message
     *                           describing the issue that occurred.
     */
    fun SaveCustomType(context: Context, die: DieType): Pair<Boolean, String> {
        checkInitialization()

        if(builtInTypesMap.containsKey(die.name)) {
            return Pair(false, context.getString(R.string.custom_die_save_builtin_name))
        }

        try{
            val filename = customDieFileNames[die.name] ?: UUID.randomUUID().toString()
            if(!customDieFileNames.containsKey(die.name)) {
                //If this die isn't in the list file (i.e. it's a new type) then add it and write a
                //new line for it at the end of the list file.
                var listFile = File(context.filesDir, CUSTOM_TYPES_FILE)
                var listWriter = FileWriter(listFile, true)
                val line = die.name + FIELD_SEPARATOR + filename
                listWriter.appendLine(line)
                listWriter.close()
                customDieFileNames[die.name] = filename
                customTypeNames.add(die.name)
            }

            //Update the type in the map. If there was already a type or placeholder by that name,
            //update any pools containing the type.
            var poolsNeedUpdate = customTypesMap.containsKey(die.name) || placeholderTypeMap.containsKey(die.name)
            customTypesMap[die.name] = die
            if(poolsNeedUpdate) SavedPoolManager.DieTypeUpdated(die.name)

            //Write the file for the die type
            var dieFile = File(context.filesDir, filename)
            dieFile.createNewFile() //We rewrite the whole file anyway, so it doesn't matter if we recreate the file.
            var dieData: String = die.name + FIELD_SEPARATOR +
                    die.minResult.toString() + FIELD_SEPARATOR + die.maxResult.toString()
            if(die.faceDrawableUris != null) {
                //Add the URIs for custom faces to the file
                for(result in (die.minResult..die.maxResult)){
                    dieData += FIELD_SEPARATOR +
                            (die.faceDrawableUriForResult(result)?.toString() ?: "")
                }
            }
            dieFile.writeText(dieData)
            return Pair(true, "")
        }
        catch (exception: Exception) {
            return Pair(false, exception.message ?: "Error saving die")
        }
    }

    /**
     * Deletes a custom die type by name. The type will be removed from the custom types map, list
     * file, and consistently ordered list of custom types. The file for the type will be deleted.
     * A placeholder type will be created for the type name, and any pools which contained the type
     * will have the placeholder inserted instead. Note that if an error occurs while writing to
     * files the type will still be removed from the type map in memory, so if this returns false it
     * does not mean the type could still be used.
     * context: Application or activity context is required for saving to files.
     * dieName: The name of the custom type to delete
     * Returns: (True, "") if the deletion was successful
     *          (False, <message>) if the deletion was not successful. This occurs if there was no
     *                custom type by the given name, the die file could not be deleted, or there was
     *                an exception while writing to files. The second value is a technical error
     *                message describing what occured.
     */
    fun DeleteCustomDie(context: Context, dieName: String): Pair<Boolean, String> {
        checkInitialization()

        val filename = customDieFileNames[dieName]
        if(filename == null) return Pair(false, "Die not found")

        customDieFileNames.remove(dieName)
        customTypesMap.remove(dieName)
        customTypeNames.remove(dieName)
        makeNewPlaceholder(dieName)
        SavedPoolManager.DieTypeUpdated(dieName) //Replace the type with a placeholder in loaded pools

        try {
            var dieFile = File(context.filesDir, filename)
            if(dieFile.exists() && !dieFile.delete()) return Pair(false, "File could not be deleted")
            rewriteListFile(context)
            return Pair(true, "")
        }
        catch (exception: Exception) {
            return Pair(false, exception.message ?: "Error deleting die")
        }
    }

    /**
     * Deletes several custom die types at once. Their files will be deleted, they will be removed
     * from the types map and list of custom types, and the list file will be rewritten without them.
     * Any type that's successfully deleted will have a placeholder type created, and all loaded
     * pools that contain that type will have the placeholder type inserted instead. Not that in this
     * function, if there's an error deleting a file for a given die name then that type will still
     * exist in the types map/list in memory.
     * context: Application or activity context is required for saving to files.
     * diceToRemove: Set of the names of custom types to delete
     * Returns: A map of any errors that occurred while deleting dice. Key null is used for an error
     *          while rewriting the list file. If an error occurs while deleting the file for a given
     *          die name, or no custom type of that name could be found, map(dieName) will contain
     *          the technical error message describing what occurred.
     */
    fun DeleteCustomDice(context: Context, diceToRemove: Set<String>): HashMap<String?, String> {
        checkInitialization()
        var errors = HashMap<String?, String>()
        var removedDice = HashSet<String>() //Not using diceToRemove to update lists and pools as some might fail

        for(dieName in diceToRemove) {
            try {
                //Can't remove dice that don't exist
                val filename = customDieFileNames[dieName]
                if(filename == null) {
                    errors[dieName] = "Die not found"
                    continue
                }

                //First delete the file for the die
                var dieFile = File(context.filesDir, filename)
                if(dieFile.exists() && !dieFile.delete()) {
                    errors[dieName] = "Die file could not be deleted"
                    continue
                }

                //If that's successful, remove it from the map and in-memory list of file names, and
                //mark that it has successfully been deleted.
                customDieFileNames.remove(dieName)
                customTypesMap.remove(dieName)
                removedDice.add(dieName) //Accumulate dice to remove from customTypeNames
            }
            catch (exception: Exception) {
                errors[dieName] = exception.message ?: "Error deleting die"
            }
        }

        //Filter the ordered list of names to remove those that were deleted successfully. Done here rather
        //than in the main loop to avoid multiple O(n) operations
        var filteredList: List<String> = customTypeNames.filterNot { dieName ->
                removedDice.contains(dieName) }
        if(filteredList is ArrayList<String>) customTypeNames = filteredList
        else customTypeNames = ArrayList<String>(filteredList)

        //Replace the removed die types with a placeholder in any pools that have been loaded.
        for(dieName in removedDice) {
            makeNewPlaceholder(dieName)
            SavedPoolManager.DieTypeUpdated(dieName)
        }

        try {
            //Rewrite the list file once, from the in-memory list of file names
            rewriteListFile(context)
        }
        catch (exception: Exception) {
            errors[null] = exception.message ?: "Error writing dice list file"
        }

        return errors
    }

    /**
     * Rewrites the entire custom types list file based on the types currently stored in memory.
     * Exceptions must be handled by this function's consumers.
     * context: Application or activity context is required for saving to files.
     */
    private fun rewriteListFile(context: Context) {
        var listFile = File(context.filesDir, CUSTOM_TYPES_FILE)
        listFile.createNewFile()
        var listWriter = FileWriter(listFile)

        for(dieData in customDieFileNames) {
            val line = dieData.key + FIELD_SEPARATOR + dieData.value
            listWriter.appendLine(line)
        }
        listWriter.close()
    }

    /**
     * Generates an array of the names of all available die types (built-in + custom types).
     */
    fun EnnumerateDice(): Array<String> {
        checkInitialization()
        return (builtInTypes.map({ type -> type.name }) + customTypesMap.keys).toTypedArray()
    }

    /**
     * Generates an array of drawables to show as the faces of a custom die type. The resultant
     * array will have any custom images that have been selected for particular faces, and use the
     * default generic result images for any other faces.
     * Does not perform validation of min/max values.
     * minValue: The lowest face value on the die
     * maxValue: The highest face value on the die
     * setFaces: If null, all faces of the die will have a generic drawable. Otherwise, if the drawable
     *           in this list at index (f-minValue) is not null then that drawable will be used for the
     *           face with value f.
     * Returns: An array of drawables of size (maxValue-minValue+1), i.e. the number of faces the die
     *          has. array[f-minValue] contains the drawable for the face with value f.
     */
    fun GenerateFaces(minValue: Int, maxValue: Int,
                      setFaces: Array<Drawable?>? = null): Array<Drawable> {
        //Initialization is checked in GenerateGenericFace
        var result = LinkedList<Drawable>()

        for(faceVal in (minValue..maxValue)) {
            val index = faceVal-minValue
            var face = setFaces?.get(index) ?: GenerateGenericFace(faceVal)
            result.add(face)
        }
        return result.toTypedArray()
    }

    /**
     * Generates a generic drawable to show for the face of a die with a given value. The drawable
     * will be tinted according to the current theme.
     * faceVal: the result to show on this face
     * Returns: A TextDieFace drawable. This has a background drawable (from defaultFaceOutline) and
     *          displays the face value as text over that.
     */
    fun GenerateGenericFace(faceVal: Int): TextDieFace {
        //Initialization is checked in defaultFaceOutline property
        var face = TextDieFace(defaultFaceOutline, faceVal.toString())
        if(currentIconTint != null) face.setTint(currentIconTint!!)
        return face
    }

    /**
     * Generates a drawable to represent a die type in config activities and menus. If the minimum
     * result of the die is 1 or there is a custom drawable specified for the die's highest result,
     * then no separate config drawable will be generated. This means the die will be represented
     * by the drawable for the highest result. Otherwise (i.e. the highest result is a generic face
     * and the lowest result is not 1) a drawable will be generated that uses the generic face
     * outline and shows the range of possible results, e.g. "2-3".
     * minValue: the lowest result of this die
     * maxValue: the highest result of this die
     * setFaces: as per GenerateFaces, if this isn't null it contains any custom face drawables the
     *           die has.
     * Returns: Null if the die should be represented by its highest result drawable. Otherwise, a
     *          drawable showing the range of results over the generic face background.
     */
    fun GenerateConfigFace(minValue: Int, maxValue: Int,
                           setFaces: Array<Drawable?>? = null): Drawable? {
        //Initialization is checked in defaultFaceOutline
        if(minValue == 1 || setFaces?.last() != null) return null

        val faceText = minValue.toString() + "-" + maxValue.toString()
        var configFace = TextDieFace(defaultFaceOutline, faceText)
        if(currentIconTint != null) configFace.setTint(currentIconTint!!)
        return configFace
    }

    /**
     * Called at the start of each public function in the manager (except InitializeManager) to check
     * that initialization has been done. If it hasn't, throws an exception.
     */
    private fun checkInitialization() {
        if(!hasBeenInitialized) throw Exception("DieTypesManager must be initialized before use")
    }
}