
package com.alanszlosek.keying;

import android.inputmethodservice.InputMethodService;
import android.inputmethodservice.Keyboard;
import android.inputmethodservice.KeyboardView;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

/**
 * A different take on an on-screen keyboard for those whose dislike auto-correct
 * and have difficulty aiming their thumbs.
 *
 * Created using the android SoftKeyboard example as a starting point
 */
public class Keying extends InputMethodService implements KeyboardView.OnKeyboardActionListener {
    // Starting over
    private long mKeyDownStart = 0;
    private long longPressThreshold = 150;
    /*
    SYMBOL KEYBOARD LAYOUT
    7 8 9 +  '[ "] @#
    4 5 6 -_ () `~ $%
    1 2 3 *  ,  :; ^&
    0 . = /\ ?  !| D
     */
    private String mappings = new String("qwuiopasklzx'[\"]@#-_()`~$%:;^&/\\!|");


    private KeyboardView mInputView;

    private StringBuilder mComposing = new StringBuilder();
    private boolean mPredictionOn = false;

    // Not entirely certain I need LatinKeyboard class yet ... it seems to use super() a lot
    private LatinKeyboard mSymbolsKeyboard;
    private LatinKeyboard mQwertyKeyboard;

    // Pointer to the current keyboard being used
    private LatinKeyboard mCurKeyboard;

    private String mWordSeparators;

    // CALLBACKS ARE ROUGHLY IN THE ORDER I'VE SEEN THEM CALLED

    // Called by the system when the service is first created
    @Override
    public void onCreate() {
        super.onCreate();
        debug("onCreate");

        mWordSeparators = getResources().getString(R.string.word_separators);
    }

    // Initialize UI here. Called when service is first created and after config changes
    @Override
    public void onInitializeInterface() {
        debug("onInitializeInterface");

        // Re-create keyboards to reflect (possibly new) display width
        mQwertyKeyboard = new LatinKeyboard(this, R.xml.qwerty);
        mSymbolsKeyboard = new LatinKeyboard(this, R.xml.symbols);
    }

    // Upon this call you know that getCurrentInputBinding() and getCurrentInputConnection() return valid objects.
    @Override
    public void onBindInput() {
        debug("onBindInput");

    }

    /**
     * Called to inform the input method that text input has started in an editor.
     * You should use this callback to initialize the state of your input to match
     * the state of the editor given to it.
     */
    @Override public void onStartInput(EditorInfo attribute, boolean restarting) {
        super.onStartInput(attribute, restarting);
        debug("onStartInput");

        // Initialize based on type of text we're editing
        switch (attribute.inputType & EditorInfo.TYPE_MASK_CLASS) {
            case EditorInfo.TYPE_CLASS_NUMBER:
            case EditorInfo.TYPE_CLASS_DATETIME:
                mCurKeyboard = mSymbolsKeyboard;
                break;

            case EditorInfo.TYPE_CLASS_PHONE:
                mCurKeyboard = mSymbolsKeyboard;
                break;

            case EditorInfo.TYPE_CLASS_TEXT:
                mCurKeyboard = mQwertyKeyboard;
                // Check whether the keyboard should start out shifted
                updateShiftKeyState(attribute);
                break;

            default:
                mCurKeyboard = mQwertyKeyboard;
                updateShiftKeyState(attribute);
        }

        // Update the label on the enter key, depending on what the application
        // says it will do.
        mCurKeyboard.setImeOptions(getResources(), attribute.imeOptions);
    }

    /**
     * Create and return the view hierarchy used for the input area (such as a soft keyboard).
     * This will be called once, when the input area is first displayed
     */
    @Override
    public View onCreateInputView() {
        debug("onCreateInputView");
        mInputView = (KeyboardView) getLayoutInflater().inflate(R.layout.input, null);
        mInputView.setOnKeyboardActionListener(this);
        mInputView.setKeyboard(mCurKeyboard);
        mInputView.setPreviewEnabled(false);
        return mInputView;
    }

    @Override
    public void onStartInputView(EditorInfo attribute, boolean restarting) {
        super.onStartInputView(attribute, restarting);
        debug("onStartInputView");

        mInputView.closing();
        // Apply the selected keyboard to the input view.
        mInputView.setKeyboard(mCurKeyboard);

        // Reset composing buffer
        mComposing.setLength(0);
    }

    /**
     * Called to inform the input method that text input has finished in the last editor.
     * The default implementation uses the InputConnection to clear any active composing text.
     */
    @Override
    public void onFinishInput() {
        super.onFinishInput();
        debug("onFinishInput");

        // Necessary?
        if (mInputView != null) {
            mInputView.closing();
        }
    }

    @Override
    public void onFinishInputView(boolean finishingInput) {
        super.onFinishInputView(finishingInput);
    }

    // After this method, getCurrentInputBinding() and getCurrentInputConnection()
    // will no longer return valid objects.
    @Override
    public void onUnbindInput() {
        debug("onUnbindInput");
    }

    @Override
    public void onDestroy() {
        debug("onDestroy");
    }

    /**
     * Override this to intercept key down events before they are processed by the application.
     * If you return true, the application will not process the event itself.
     * If you return false, the normal application processing will occur as if the IME
     * had not seen the event at all.
     */
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        debug("onKeyDown");
        switch (keyCode) {
            case KeyEvent.KEYCODE_BACK:
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
        }

        return super.onKeyDown(keyCode, event);
    }

    // Sibling to onKeyDown()
    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        debug("onKeyUp");
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
            mInputView.setShifted(caps != 0);
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

    // should delete 2 words/second if long pressing
    public void handleBackspace() {
        final int length = mComposing.length();
        if (length > 0) {
            mComposing.delete(length - 1, length);
            getCurrentInputConnection().setComposingText(mComposing, 1);
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
        if (currentKeyboard == mQwertyKeyboard) {
            debug("handleShift,qwerty");
            // Toggle current shift state
            mInputView.setShifted(!mInputView.isShifted());

        } else if (currentKeyboard == mSymbolsKeyboard) {
            debug("handleShift,symbols");
            //mInputView.setKeyboard(mSymbolsShiftedKeyboard);

        }
        /*
        else if (currentKeyboard == mSymbolsShiftedKeyboard) {
            debug("handleShift,symbolsShifted");
            mInputView.setKeyboard(mSymbolsKeyboard);
        }
        */
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
        } else {
            getCurrentInputConnection().commitText( String.valueOf((char) primaryCode), 1);
        }
    }

    public void handleClose() {
        commitTyped();
        requestHideSelf(0);
        mInputView.closing();
    }

    private String getWordSeparators() {
        return mWordSeparators;
    }

    public boolean isWordSeparator(int code) {
        String separators = getWordSeparators();
        return separators.contains(String.valueOf((char)code));
    }


    // OnKeyboardActionListener CALLBACKS

    /**
     * NOTE: Don't change the current keyboard during press events.
     * Wait until the key has been released or the app will crash.
     */
    public void onPress(int primaryCode) {
        debug("onPress");

        // Track when this key was pressed so we can determine short or long press in onRelease()
        mKeyDownStart = System.currentTimeMillis();
    }

    // Ignore this callback for most keys. We'll use onPress and onRelease
    // to handle long and short press detection.
    // This is still handy because it fires repeatedly when delete is held down
    public void onKey(int primaryCode, int[] keyCodes) {
        debug(String.format("onKey %c %d", (char)primaryCode, primaryCode));
        if (primaryCode == Keyboard.KEYCODE_DELETE) {
            handleBackspace();

            // Show a menu or somethin'
        } else if (primaryCode == Keyboard.KEYCODE_MODE_CHANGE && mInputView != null) {
            Keyboard current = mInputView.getKeyboard();
            if (current == mSymbolsKeyboard) {
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
    }

    public void onRelease(int primaryCode) {
        long diff;
        int i;
        int code;

        if (
            primaryCode == Keyboard.KEYCODE_DELETE
            ||
            primaryCode == Keyboard.KEYCODE_CANCEL
            ||
            primaryCode == Keyboard.KEYCODE_MODE_CHANGE
        ) {
            // We ignore these special keys
            debug(String.format("onRelease code %d", primaryCode));

        } else if (primaryCode == Keyboard.KEYCODE_SHIFT) {
            handleShift();

        } else {
            // Detect long or short press here
            diff = System.currentTimeMillis() - mKeyDownStart;
            debug(String.format("onRelease after %d ms", diff));
            if (diff > longPressThreshold) {
                // Does the pressed key have more than 1 character associated with it?
                i = mappings.indexOf(primaryCode);
                if (i == -1) {
                    // Nope, pass the character code through

                    code = primaryCode;
                } else {
                    // Yes, pass the sibling character code
                    code = mappings.codePointAt( i+1 );
                }
            } else {
                code = primaryCode;
            }


            if (isWordSeparator(code)) {
                debug("onRelease,isWordSeparator");
                // Handle separator
                if (mComposing.length() > 0) {
                    commitTyped();
                }
                sendKey(code);
                updateShiftKeyState(getCurrentInputEditorInfo());
            } else {
                handleCharacter( code );
            }
        }
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


    @Override
    public void swipeRight() {
        debug("swipeRight");
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
