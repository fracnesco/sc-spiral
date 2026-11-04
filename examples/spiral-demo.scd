// Spiral - a walkthrough, one block at a time
//
// Evaluate each ( ... ) block in turn with the cursor inside it. This is
// an editor document, not a file to .load.

// ---- 1. a sound, and Spiral driven from its Ndef
(
s.boot;
)

(
Ndef(\tone, { Spiral.ar(SinOsc.ar([220, 221]) * Decay2.kr(Impulse.kr(0.5), 0.01, 0.5) * 0.3, \spiral) }).play;
~p = Spiral(Ndef(\tone), \spiral);
~p.gui;
)

// ---- 2. settings: each is a property, each moves the window
(
~p.shift = 40; ~p.feedback = 0.8; ~p.delay = 0.08;
~p.spread = 1;                     // the sides go opposite ways
~p.preset = 'barber pole';
)

// ---- 3. every preset; evaluate one at a time
~p.preset = \default;
~p.preset = 'barber pole';
~p.preset = \falling;
~p.preset = 'opposite ways';
~p.preset = 'detuned air';
~p.preset = \phasing;
~p.preset = 'bell ring';
~p.preset = 'alien radio';
~p.preset = \roar;

// ---- 4. settings kept, and carried
(
~p.saveAsPreset("demo");
~p.copySettings;
Spiral.presetNames.postln;
Spiral.removePreset("demo");
)

// ---- 5. the same effect with a node of its own, on the main out
(
~own = Spiral.onBus(0, 2);
~own.pasteSettings;
~own.enabled = true;
~own.gui;
)

(
~own.free;
)

// ---- 6. tidy up
(
~p.free;
Ndef(\tone).clear;
)
