package com.Winebone.RollTheBones;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.AttributeSet;
import android.view.DragEvent;
import android.widget.EditText;

/**
 * Copyright (c) 2021-2025 Michael Usher
 *
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of
 * the MPL was not distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

/**
 * Subclass of EditText which does not interact with drag and drop events. This is used because the
 * base EditText class was trying to consume dice & dice pools that were dropped into it, but didn't
 * understand the data I was using and caused an exception.
 *
 * Done in Java because of idiosyncrasies in how Kotlin handles constructors. Specifically:
 * -Main Kotlin constructor has to invoke one of the parent constructors
 * -Secondary constructors have to invoke the primary constructor
 * This leaves it unclear how to implement the equivalent of each constructor in the (Java) parent
 * class: for instance, which constructor should the primary constructor call?
 *
 * Implements a corresponding constructor for each parent class constructor, and overrides the
 * onDragEvent function to do nothing.
 */
@SuppressLint("AppCompatCustomView")
public class EditTextNoDrag extends EditText {
    public EditTextNoDrag(Context context) {
        super(context);
    }

    public EditTextNoDrag(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public EditTextNoDrag(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @SuppressLint("C")
    public EditTextNoDrag(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @Override
    public boolean onDragEvent(DragEvent event) {
        return false;
    }
}
