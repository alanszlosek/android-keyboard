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

import android.inputmethodservice.InputMethodService;
import android.inputmethodservice.Keyboard;
import android.inputmethodservice.KeyboardView;
import android.text.method.MetaKeyKeyListener;
import android.util.Log;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.CompletionInfo;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Example of writing an input method for a soft keyboard.  This code is
 * focused on simplicity over completeness, so it should in no way be considered
 * to be a complete soft keyboard implementation.  Its purpose is to provide
 * a basic example for how you would get started writing an input method, to
 * be fleshed out as appropriate.
 */
public class Keying extends InputMethodService implements KeyboardView.OnKeyboardActionListener {
	// Starting over
	private long mKeyDownStart = 0;
	private String mappings = new String("qwuiopasklzx");

	static final boolean DEBUG = false;
	/**
	* This boolean indicates the optional example code for performing
	* processing of hard keys in addition to regular text generation
	* from on-screen interaction.  It would be used for input methods that
	* perform language translations (such as converting text entered on
	* a QWERTY keyboard to Chinese), but may not be used for input methods
	* that are primarily intended to be used for on-screen text entry.
	*/
	static final boolean PROCESS_HARD_KEYS = true;

	private KeyboardView mInputView;
	private CandidateView mCandidateView;
	private CompletionInfo[] mCompletions;

	private StringBuilder mComposing = new StringBuilder();
	private boolean mPredictionOn;
	private boolean mCompletionOn;
	private boolean mShifted;
	private long mLastShiftTime;
	private long mMetaState;

	// Not entirely certain I need LatinKeyboard class yet ... it seems to use super() a lot
	private LatinKeyboard mSymbolsKeyboard;
	private LatinKeyboard mSymbolsShiftedKeyboard;
	private LatinKeyboard mQwertyKeyboard;

	private LatinKeyboard mCurKeyboard;

	private String mWordSeparators;
    
	/**
	* Main initialization of the input method component.  Be sure to call
	* to super class.
	*/
	@Override
	public void onCreate() {
		super.onCreate();
		debug("onCreate");
		mWordSeparators = getResources().getString(R.string.word_separators);
	}

	// For UI init.Called after the service is first created and after a configuration change happens
	@Override
	public void onInitializeInterface() {
		debug("onInitializeInterface");
		
		// Re-create keyboards to reflect (possibly new) display width
		mQwertyKeyboard = new LatinKeyboard(this, R.xml.qwerty);
		mSymbolsKeyboard = new LatinKeyboard(this, R.xml.symbols);
		mSymbolsShiftedKeyboard = new LatinKeyboard(this, R.xml.symbols_shift);
	}

/*
	// Return true to use our own input box
	@Override
	public boolean onEvaluateInputViewShown() {
		return false; //true;
	}
*/

	/**
	* Called by the framework when your view for creating input needs to
	* be generated.  This will be called the first time your input method
	* is displayed, and every time it needs to be re-created such as due to
	* a configuration change.
	*/
	@Override
	public View onCreateInputView() {
		debug("onCreateInputView");
		mInputView = (KeyboardView) getLayoutInflater().inflate(R.layout.input, null);
		mInputView.setOnKeyboardActionListener(this);
		mInputView.setKeyboard(mQwertyKeyboard);
		return mInputView;
	}

	/**
	* Called by the framework when your view for showing candidates needs to
	* be generated, like {@link #onCreateInputView}.
	*/
	@Override
	public View onCreateCandidatesView() {
		debug("onCreateCandidatesView");
		mCandidateView = new CandidateView(this);
		mCandidateView.setService(this);
		return mCandidateView;
	}
	
	@Override
	public void onBindInput() {
		debug("onBindInput");
	}

	/**
	* This is the main point where we do our initialization of the input method
	* to begin operating on an application.  At this point we have been
	* bound to the client, and are now receiving all of the detailed information
	* about the target of our edits.
	*/
	@Override public void onStartInput(EditorInfo attribute, boolean restarting) {
		super.onStartInput(attribute, restarting);
		debug("onStartInput");

		// Reset our state.  We want to do this even if restarting, because
		// the underlying state of the text editor could have changed in any way.

		if (!restarting) {
			// Clear shift states.
			mMetaState = 0;
		}

		mPredictionOn = false;
		mCompletionOn = false;
		// eh?
		mCompletions = null;

		// We are now going to initialize our state based on the type of
		// text being edited.
		switch (attribute.inputType & EditorInfo.TYPE_MASK_CLASS) {
			case EditorInfo.TYPE_CLASS_NUMBER:
			case EditorInfo.TYPE_CLASS_DATETIME:
				// Numbers and dates default to the symbols keyboard, with
				// no extra features.
				mCurKeyboard = mSymbolsKeyboard;
				break;

			case EditorInfo.TYPE_CLASS_PHONE:
				// Phones will also default to the symbols keyboard, though
				// often you will want to have a dedicated phone keyboard.
				mCurKeyboard = mSymbolsKeyboard;
				break;

			case EditorInfo.TYPE_CLASS_TEXT:
				// This is general text editing.  We will default to the
				// normal alphabetic keyboard, and assume that we should
				// be doing predictive text (showing candidates as the
				// user types).
				mCurKeyboard = mQwertyKeyboard;
				mPredictionOn = true;

				// We now look for a few special variations of text that will
				// modify our behavior.
				int variation = attribute.inputType & EditorInfo.TYPE_MASK_VARIATION;
				if (variation == EditorInfo.TYPE_TEXT_VARIATION_PASSWORD ||
					variation == EditorInfo.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD) {
					// Do not display predictions / what the user is typing
					// when they are entering a password.
					mPredictionOn = false;
				}

				// Would love to show a slightly modified keyboard for email address, with @ in place of ,
				if (variation == EditorInfo.TYPE_TEXT_VARIATION_EMAIL_ADDRESS 
					|| variation == EditorInfo.TYPE_TEXT_VARIATION_URI
					|| variation == EditorInfo.TYPE_TEXT_VARIATION_FILTER) {
					// Our predictions are not useful for e-mail addresses
					// or URIs.
					mPredictionOn = false;
				}

				if ((attribute.inputType&EditorInfo.TYPE_TEXT_FLAG_AUTO_COMPLETE) != 0) {
					// If this is an auto-complete text view, then our predictions
					// will not be shown and instead we will allow the editor
					// to supply their own.  We only show the editor's
					// candidates when in fullscreen mode, otherwise relying
					// own it displaying its own UI.
					mPredictionOn = false;
					mCompletionOn = isFullscreenMode();
				}

				// We also want to look at the current state of the editor
				// to decide whether our alphabetic keyboard should start out
				// shifted.
				updateShiftKeyState(attribute);
				break;

			default:
				// For all unknown input types, default to the alphabetic
				// keyboard with no special features.
				mCurKeyboard = mQwertyKeyboard;
				updateShiftKeyState(attribute);
		}

		// Update the label on the enter key, depending on what the application
		// says it will do.
		mCurKeyboard.setImeOptions(getResources(), attribute.imeOptions);
	}

	@Override
	public void onStartInputView(EditorInfo attribute, boolean restarting) {
		super.onStartInputView(attribute, restarting);
		debug("onStartInputView");
		// Apply the selected keyboard to the input view.
		mInputView.setKeyboard(mCurKeyboard);
		//mInputView.closing();
		
		// Reset composing buffer
		mComposing.setLength(0);
		updateCandidates();
	}

	/**
	* Deal with the editor reporting movement of its cursor.
	*/
	@Override
	public void onUpdateSelection(int oldSelStart, int oldSelEnd, int newSelStart, int newSelEnd,
		int candidatesStart, int candidatesEnd) {
		super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd);
		debug(String.format("onUpdateSelection: %d %d %d %d %d %d", oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd));
		
		// Set composing text from here
		
		// If we move the cursor away from completion area, what to do?
		
		// If the current selection in the text view changes, we should
		// clear whatever candidate text we have.
		if (mComposing.length() > 0 && (newSelStart != candidatesEnd || newSelEnd != candidatesEnd)) {
			mComposing.setLength(0);
			updateCandidates();
			InputConnection ic = getCurrentInputConnection();
			if (ic != null) {
				ic.finishComposingText();
			}
		}
		
	}

	/**
	* This tells us about completions that the editor has determined based
	* on the current text in it.  We want to use this in fullscreen mode
	* to show the completions ourself, since the editor can not be seen
	* in that situation.
	*/
	@Override
	public void onDisplayCompletions(CompletionInfo[] completions) {
		debug("onDisplayCompletions");
		if (mCompletionOn) {
			mCompletions = completions;
			if (completions == null) {
				setSuggestions(null, false, false);
				return;
			}

			List<String> stringList = new ArrayList<String>();
			for (int i=0; i<(completions != null ? completions.length : 0); i++) {
				CompletionInfo ci = completions[i];
				if (ci != null) {
					stringList.add(ci.getText().toString());
				}
			}
			setSuggestions(stringList, true, true);
		}
	}
	
		/**
	* This is called when the user is done editing a field.  We can use
	* this to reset our state.
	*/
	@Override
	public void onFinishInput() {
		super.onFinishInput();
		debug("onFinishInput");

		// Clear current composing text and candidates.
		mComposing.setLength(0);
		
		// why not just clear them?
		updateCandidates();

		// We only hide the candidates window when finishing input on
		// a particular editor, to avoid popping the underlying application
		// up and down if the user is entering text into the bottom of
		// its window.
		setCandidatesViewShown(false);

		mCurKeyboard = mQwertyKeyboard;
		// necessary?
		/*
		if (mInputView != null) {
			mInputView.closing();
		}
		*/
	}
	
	@Override
	public void onFinishCandidatesView(boolean finishingInput) {
		super.onFinishCandidatesView(finishingInput);
		debug("onFinishCandidatesView");
	}
	
	@Override
	public void onUnbindInput() {
		debug("onUnbindInput");
	}
	
	@Override
	public void onDestroy() {
		debug("onDestroy");
	}


	/**
	* This translates incoming hard key events in to edit operations on an
	* InputConnection.  It is only needed when using the
	* PROCESS_HARD_KEYS option.
	*/
	private boolean translateKeyDown(int keyCode, KeyEvent event) {
		debug("translateKeyDown");
		mMetaState = MetaKeyKeyListener.handleKeyDown(mMetaState, keyCode, event);
		int c = event.getUnicodeChar(MetaKeyKeyListener.getMetaState(mMetaState));
		mMetaState = MetaKeyKeyListener.adjustMetaAfterKeypress(mMetaState);
		InputConnection ic = getCurrentInputConnection();
		if (c == 0 || ic == null) {
			return false;
		}

		boolean dead = false;

		if ((c & KeyCharacterMap.COMBINING_ACCENT) != 0) {
			dead = true;
			c = c & KeyCharacterMap.COMBINING_ACCENT_MASK;
		}

		if (mComposing.length() > 0) {
			char accent = mComposing.charAt(mComposing.length() -1 );
			int composed = KeyEvent.getDeadChar(accent, c);

			if (composed != 0) {
				c = composed;
				mComposing.setLength(mComposing.length()-1);
			}
		}

		//onKey(c, null);

		return true;
	}

	/**
	* Use this to monitor key events being delivered to the application.
	* We get first crack at them, and can either resume them or let them
	* continue to the app.
	*/
	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		debug("onKeyDown");
		switch (keyCode) {
			case KeyEvent.KEYCODE_BACK:
				// The InputMethodService already takes care of the back
				// key for us, to dismiss the input method if it is shown.
				// However, our keyboard could be showing a pop-up window
				// that back should dismiss, so we first allow it to do that.
				if (event.getRepeatCount() == 0 && mInputView != null) {
					if (mInputView.handleBack()) {
						return true;
					}
				}
				break;

			case KeyEvent.KEYCODE_DEL:
				// Special handling of the delete key: if we currently are
				// composing text for the user, we want to modify that instead
				// of let the application to the delete itself.
				if (mComposing.length() > 0) {
					//onKey(Keyboard.KEYCODE_DELETE, null);
					return true;
				}
				break;

			case KeyEvent.KEYCODE_ENTER:
				// Let the underlying text editor always handle these.
				return false;

			default:
				// For all other keys, if we want to do transformations on
				// text being entered with a hard keyboard, we need to process
				// it and do the appropriate action.
				if (PROCESS_HARD_KEYS) {
					if (keyCode == KeyEvent.KEYCODE_SPACE && (event.getMetaState()&KeyEvent.META_ALT_ON) != 0) {
						// A silly example: in our input method, Alt+Space
						// is a shortcut for 'android' in lower case.
						InputConnection ic = getCurrentInputConnection();
						if (ic != null) {
							// First, tell the editor that it is no longer in the
							// shift state, since we are consuming this.
							ic.clearMetaKeyStates(KeyEvent.META_ALT_ON);
							keyDownUp(KeyEvent.KEYCODE_A);
							keyDownUp(KeyEvent.KEYCODE_N);
							keyDownUp(KeyEvent.KEYCODE_D);
							keyDownUp(KeyEvent.KEYCODE_R);
							keyDownUp(KeyEvent.KEYCODE_O);
							keyDownUp(KeyEvent.KEYCODE_I);
							keyDownUp(KeyEvent.KEYCODE_D);
							// And we consume this event.
							return true;
						}
					}
					if (mPredictionOn && translateKeyDown(keyCode, event)) {
						return true;
					}
				}
		}

		return super.onKeyDown(keyCode, event);
	}

	/**
	* Use this to monitor key events being delivered to the application.
	* We get first crack at them, and can either resume them or let them
	* continue to the app.
	*/
	@Override
	public boolean onKeyUp(int keyCode, KeyEvent event) {

		debug("onKeyUp");
		// If we want to do transformations on text being entered with a hard
		// keyboard, we need to process the up events to update the meta key
		// state we are tracking.
		if (PROCESS_HARD_KEYS) {
			if (mPredictionOn) {
				mMetaState = MetaKeyKeyListener.handleKeyUp(mMetaState, keyCode, event);
			}
		}

		return super.onKeyUp(keyCode, event);
	}

	/**
	* Helper function to commit any text being composed in to the editor.
	*/
	private void commitTyped() {
		InputConnection inputConnection = getCurrentInputConnection();
		if (mComposing.length() > 0) {
			inputConnection.commitText(mComposing, 1);
			mComposing.setLength(0);
			updateCandidates();
		}
	}

	/**
	* Helper to update the shift state of our keyboard based on the initial
	* editor state.
	*/
	private void updateShiftKeyState(EditorInfo attr) {
		// think this is the cause of why it got stuck in caps-mode
		if (attr != null && mInputView != null && mQwertyKeyboard == mInputView.getKeyboard()) {
			int caps = 0;
			if (attr.inputType != EditorInfo.TYPE_NULL) {
				caps = getCurrentInputConnection().getCursorCapsMode(attr.inputType);
			}
			mShifted = (caps != 0);
			mInputView.setShifted(mShifted);
		}
	}

	/**
	* Helper to determine if a given character code is alphabetic.
	*/
	private boolean isAlphabet(int code) {
		if (Character.isLetter(code)) {
			return true;
		} else {
			return false;
		}
	}

	/**
	* Helper to send a key down / key up pair to the current editor.
	*/
	private void keyDownUp(int keyEventCode) {
		debug("keyDownUp");
		getCurrentInputConnection().sendKeyEvent( new KeyEvent(KeyEvent.ACTION_DOWN, keyEventCode));
		getCurrentInputConnection().sendKeyEvent( new KeyEvent(KeyEvent.ACTION_UP, keyEventCode));
	}

	/**
	* Helper to send a character to the editor as raw key events.
	*/
	private void sendKey(int keyCode) {
		debug(String.format("sendKey %d", keyCode));
		switch (keyCode) {
			case '\n':
				keyDownUp(KeyEvent.KEYCODE_ENTER);
				break;
			default:
				if (keyCode >= '0' && keyCode <= '9') {
					keyDownUp(keyCode - '0' + KeyEvent.KEYCODE_0);
				} else {
					getCurrentInputConnection().commitText(String.valueOf((char) keyCode), 1);
				}
				break;
		}
	}



	/**
	* Update the list of available candidates from the current composing
	* text.  This will need to be filled in by however you are determining
	* candidates.
	*/
	private void updateCandidates() {
		if (!mCompletionOn) {
			if (mComposing.length() > 0) {
				ArrayList<String> list = new ArrayList<String>();
				list.add(mComposing.toString());
				setSuggestions(list, true, true);
			} else {
				setSuggestions(null, false, false);
			}
		}
	}

	public void setSuggestions(List<String> suggestions, boolean completions, boolean typedWordValid) {
		if (suggestions != null && suggestions.size() > 0) {
			setCandidatesViewShown(true);
		} else if (isExtractViewShown()) {
			setCandidatesViewShown(true);
		}
		if (mCandidateView != null) {
			mCandidateView.setSuggestions(suggestions, completions, typedWordValid);
		}
	}

	public void handleBackspace() {
		final int length = mComposing.length();
		if (length > 0) {
			mComposing.delete(length - 1, length);
			getCurrentInputConnection().setComposingText(mComposing, 1);
			updateCandidates();
		} else {
			keyDownUp(KeyEvent.KEYCODE_DEL);
		}
		// Re-build composing text
		updateShiftKeyState(getCurrentInputEditorInfo());
	}

	// pretty certain this logic is failing somewhere ... got stuck in caps mode
	private void handleShift() {
		debug("handleShift");
		if (mInputView == null) {
			return;
		}

		Keyboard currentKeyboard = mInputView.getKeyboard();
		if (mQwertyKeyboard == currentKeyboard) {
			debug("handleShift,qwerty");
			// Alphabet keyboard
			checkToggleCapsLock();
			// Don't defer to InputView setting ... override it. Want to be able to start out first letter lowercase
			//mInputView.setShifted(mShifted || mInputView.isShifted());
			// do we need to do the following manually? won't the keypress toggle the state by itself?
			//mInputView.setShifted(mShifted);
		} else if (currentKeyboard == mSymbolsKeyboard) {
			mSymbolsKeyboard.setShifted(false);
			mInputView.setKeyboard(mSymbolsShiftedKeyboard);
			mSymbolsShiftedKeyboard.setShifted(true);
		} else if (currentKeyboard == mSymbolsShiftedKeyboard) {
			mSymbolsShiftedKeyboard.setShifted(false);
			mInputView.setKeyboard(mSymbolsKeyboard);
			mSymbolsKeyboard.setShifted(false);
		}
	}

	private void handleCharacter(int primaryCode) {
		debug(String.format("handleCharacter %c", (char)primaryCode));
		if (isInputViewShown()) {
			if (mInputView.isShifted()) {
				primaryCode = Character.toUpperCase(primaryCode);
			}
		}
		if (isAlphabet(primaryCode) && mPredictionOn) {
			mComposing.append((char) primaryCode);
			getCurrentInputConnection().setComposingText(mComposing, 1);
			// eh?
			updateShiftKeyState(getCurrentInputEditorInfo());
			updateCandidates();
		} else {
			getCurrentInputConnection().commitText( String.valueOf((char) primaryCode), 1);
		}
	}

	public void handleClose() {
		commitTyped();
		requestHideSelf(0);
		//mInputView.closing();
	}

	private void checkToggleCapsLock() {
		mShifted = !mShifted;
		debug("checkToggleCapsLock,toggling");
		/*
		long now = System.currentTimeMillis();
		if (mLastShiftTime + 800 > now) {
			debug("checkToggleCapsLock,toggling");
			mLastShiftTime = 0;
		} else {
			mLastShiftTime = now;
		}
		*/
	}

	private String getWordSeparators() {
		return mWordSeparators;
	}

	public boolean isWordSeparator(int code) {
		String separators = getWordSeparators();
		return separators.contains(String.valueOf((char)code));
	}

	public void pickDefaultCandidate() {
		pickSuggestionManually(0);
	}

	public void pickSuggestionManually(int index) {
		if (mCompletionOn && mCompletions != null && index >= 0
			&& index < mCompletions.length) {
			CompletionInfo ci = mCompletions[index];
			getCurrentInputConnection().commitCompletion(ci);
			if (mCandidateView != null) {
				mCandidateView.clear();
			}
			updateShiftKeyState(getCurrentInputEditorInfo());
		} else if (mComposing.length() > 0) {
			// If we were generating candidate suggestions for the current
			// text, we would commit one of them here.  But for this sample,
			// we will just commit the current text.
			commitTyped();
		}
	}
	
	// OnKeyboardActionListener callbacks


	// We're going to ignore these callbacks so we can trigger from onRelease
	// But this gets called repeatedly for long presses like delete
	public void onKey(int primaryCode, int[] keyCodes) {
		debug(String.format("onKey %c %d", (char)primaryCode, primaryCode));
		if (primaryCode == Keyboard.KEYCODE_DELETE) {
			handleBackspace();

		// Show a menu or somethin'
		} else if (primaryCode == Keyboard.KEYCODE_MODE_CHANGE && mInputView != null) {
			Keyboard current = mInputView.getKeyboard();
			if (current == mSymbolsKeyboard || current == mSymbolsShiftedKeyboard) {
				current = mQwertyKeyboard;
			} else {
				current = mSymbolsKeyboard;
			}
			mInputView.setKeyboard(current);
			if (current == mSymbolsKeyboard) {
				// shouldn't this check the input connection state?
				current.setShifted(false);
			}
		}
		return;
// Original:
/*
		if (isWordSeparator(primaryCode)) {
			// Handle separator
			if (mComposing.length() > 0) {
				commitTyped();
			}
			sendKey(primaryCode);
			updateShiftKeyState(getCurrentInputEditorInfo());
		} else if (primaryCode == Keyboard.KEYCODE_DELETE) {
			handleBackspace();
		} else if (primaryCode == Keyboard.KEYCODE_SHIFT) {
			handleShift();
		} else if (primaryCode == Keyboard.KEYCODE_CANCEL) {
			handleClose();
			return;

		// Show a menu or somethin'
		} else if (primaryCode == Keyboard.KEYCODE_MODE_CHANGE && mInputView != null) {
			Keyboard current = mInputView.getKeyboard();
			if (current == mSymbolsKeyboard || current == mSymbolsShiftedKeyboard) {
				current = mQwertyKeyboard;
			} else {
				current = mSymbolsKeyboard;
			}
			mInputView.setKeyboard(current);
			if (current == mSymbolsKeyboard) {
				current.setShifted(false);
			}
		} else {
			handleCharacter(primaryCode, keyCodes);
		}
*/

	}

	// Have never seen this called
	public void onText(CharSequence text) {
		debug("onText");
		InputConnection ic = getCurrentInputConnection();
		if (ic == null) return;
		ic.beginBatchEdit();
		if (mComposing.length() > 0) {
			commitTyped();
		}
		ic.commitText(text, 0);
		ic.endBatchEdit();
		updateShiftKeyState(getCurrentInputEditorInfo());
	}


	// Use this to detect long presses ourselves?
	public void onPress(int primaryCode) {
		debug("onPress");

/*
		if (primaryCode == Keyboard.KEYCODE_DELETE) {
			handleBackspace();
		} else 
*/
		if (primaryCode == Keyboard.KEYCODE_SHIFT) {
			handleShift();
		} else if (primaryCode == Keyboard.KEYCODE_CANCEL) {
			handleClose();
		} else {
			// Determine if we care about this key
			mKeyDownStart = System.currentTimeMillis();
		}

	}

	public void onRelease(int primaryCode) {
		long diff;
		int i;

		// We only care about long-presses on certain keys, and long-press is short: 200ms
		if (isWordSeparator(primaryCode)) {
			// this is dumb ... if cursor is in middle fo the string, this fails
			debug("onRelease,isWordSeparator");
			// Handle separator
			if (mComposing.length() > 0) {
				commitTyped();
			}
			sendKey(primaryCode);
			updateShiftKeyState(getCurrentInputEditorInfo());
		} else if (
			primaryCode == Keyboard.KEYCODE_DELETE
			||
			primaryCode == Keyboard.KEYCODE_SHIFT
			||
			primaryCode == Keyboard.KEYCODE_CANCEL
			||
			primaryCode == Keyboard.KEYCODE_MODE_CHANGE
		) {
			debug(String.format("onRelease code %d", primaryCode));
		} else {
			diff = System.currentTimeMillis() - mKeyDownStart;
			debug(String.format("onRelease after %d ms", diff));
			if (diff > 200) {
				// Do re-mappings
				i = mappings.indexOf(primaryCode);
				if (i == -1) {
					handleCharacter( primaryCode );
				} else {
					handleCharacter( mappings.codePointAt( i+1 ) );
				}
			} else {
				handleCharacter(primaryCode);
			}
		}
	}
	
	
	@Override
        public void swipeRight() {
		debug("swipeRight");
                if (mCompletionOn) {
                        pickDefaultCandidate();
                }
        }

        @Override
        public void swipeLeft() {
		debug("swipeLeft");
                handleBackspace();
        }

        @Override
        public void swipeDown() {
		debug("swipeDown");
                handleClose();
        }

        @Override
        public void swipeUp() {
        }


	public void debug(String message) {
		Log.d("Keying", message);
	}
}
