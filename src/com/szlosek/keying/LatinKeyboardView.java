/*
 * Copyright (C) 2008-2009 Google Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.szlosek.keying;

import android.content.Context;
import android.inputmethodservice.Keyboard;
import android.inputmethodservice.KeyboardView;
import android.inputmethodservice.Keyboard.Key;
import android.util.AttributeSet;
import android.util.Log;

import java.lang.Character;
import java.lang.CharSequence;

public class LatinKeyboardView extends KeyboardView {

	static final int KEYCODE_OPTIONS = -100;

	public LatinKeyboardView(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	public LatinKeyboardView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
	}
	
	public void onDetachedFromWindow() {
		Log.d("Keying", "LatinKeyboardView.onDetachedFromWindow");
	}

	/*
	@Override
	public void setOnKeyboardActionListener(KeyboardView.OnKeyboardActionListener listener) {
		super(listener);
	}
	*/

	/*
	Now that I figured out how to bypass the "double-tap does letter swap" (it was using android:codes),
	I can bypass this, and do my own long press detection
	*/
	@Override
	protected boolean onLongPress(Key key) {
		Log.d("Keying", "onLongPress");
		// Do nothing
		return super.onLongPress(key);
/*
		CharSequence cs;
		// Obey alternative keys from the XML
		if (key.codes[0] == Keyboard.KEYCODE_CANCEL) {
			getOnKeyboardActionListener().onKey(KEYCODE_OPTIONS, null);
			return true;
		} else {
			if (key.popupCharacters != null && key.popupCharacters.length() > 0) {
				getOnKeyboardActionListener().onKey(
					Character.codePointAt(key.popupCharacters, 0), null);
				return true;
			} else {
				return super.onLongPress(key);
			}
		}
*/
	}


	@Override
	public void swipeRight() {
		Log.d("Keying", "swipeRight2");
		/*
		if (mCompletionOn) {
			pickDefaultCandidate();
		}
		*/
	}

/*
	@Override
	public void swipeLeft() {
		Log.d("Keying", "swipeLeft2");
		((Keying) getOnKeyboardActionListener()).handleBackspace();
	}

	@Override
	public void swipeDown() {
		Log.d("Keying", "swipeDown2");
		((Keying) getOnKeyboardActionListener()).handleClose();
	}

	@Override
	public void swipeUp() {
		Log.d("Keying", "swipeUp2");
	}
*/

}
