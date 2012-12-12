Why
====

I'm thrifty, with an LG Optimus V in my hand. It's got a 3.2" screen which makes typing on the standard Android keyboard a frustrating process. So I'm modifying the SoftKeyboard sample project, and experimenting with ways to arrange the keys differently such that they can be made larger.

View screenshot.png for a preview. Though, it's slightly out of date from what I describe below.

Ideas
====

The stock keyboard contains as many as 10 keys on a row. This is too many for my fingers to handle, so this project has only 7 keys per row.

To do this, I've doubled up on some letters. "q" and "w" are on the same key, same for "ui", "op", "as" and "kl". Long-press those (longer than 200ms) to get the secondary letter. Also, the "#" switches to symbols.

Just for the record: Also thought of having 2 spacebars, normal that obeys most logical completion, and another that ignores and leaves word as typed.

To Do
====

* The suggestions portion of the UI needs some major love. Copy style from built-in android keyboard
* Use long-press codes as a tree of possible predictions ... to maximize effectiveness of condensed keys

Done For now
====

* Finish modifying layout of the symbols keys.

Nah
====

* Try the alternative method of accessing keys that are hidden by the right side of the screen.

Bugs
====

* Suggestions should backtrack to the previous word characters (regex) and them use that for suggestions, not the letter you just typed after backspacing.
* Backspace should operate from current cursor position. There are edge cases where it doesn't.

Building
=====

Follow the instructions here for existing projects:

https://developer.android.com/tools/projects/projects-cmdline.html

Notes
====

Example project had some quirkiness. Namely regarding the caps lock key. Or maybe I introduced those bugs. Either way:

Thinking that when the keyboard first shows, it should check the shift key state for the current input control (view?). I have an internal boolean for isShifted, which I should set to the cursor shift key state.
