// =====================================================================
// Spiral - a frequency shifter in a feedback loop
//
// A pitch shifter multiplies every partial; a frequency shifter adds the same
// number of Hz to each, so harmonics stop being harmonics. Put it in a loop, and
// each trip round moves the sound further: `shift` is how far, `delay` how long a
// trip takes, `feedback` how many trips it survives. That is the barber pole, the
// sound that keeps climbing and never gets anywhere. `ring` fades over to ring
// modulation at the same frequency, which goes both ways at once.
//
//   Ndef(\tone, { Spiral.ar(SinOsc.ar([220, 221]) * Decay2.kr(Impulse.kr(0.5), 0.01, 0.5) * 0.3, \spiral) }).play;
//   p = Spiral(Ndef(\tone), \spiral);
//   p.gui;
//   p.shift = 40; p.feedback = 0.8; p.delay = 0.08;
//   p.sides = 1;                        // the sides go opposite ways
//   p.preset = 'barber pole';
//
// The knob for `shift` is fine near 0 and coarse at the ends, since 0.5 Hz and
// 500 Hz are both things you want. A loop of a block or less is not possible
// without LocalIn: the shortest delay is about 1.4 ms at 48 kHz and 64 samples.
// =====================================================================

Spiral : FxParamUnit {
	*controlName { ^\spiral }
	*hasTail { ^true }
	*displayClass { ^SpiralView }
	*guiClass { ^FxDeviceGUI }
	*panelClass { ^SpiralPanel }
	*windowSize { ^Point(960, 580) }

	// the pitch family's lime: what the shifter adds, the note's trail round the loop
	*identityColour { ^FxPalette.identity(\pitch) }

	*params {
		var shift = this.knob(\shift, -2000, 2000, \lin, 30, "Hz", "shift",
			"how far each trip moves every partial, in Hz: up, or down below 0", section: \shift, dec: 2, size: \big);
		shift[\spec].warp = FxCubeWarp(shift[\spec]);
		^[
			shift,
			this.knob(\sides, -1, 1, \lin, 0, "", "sides", "the sides apart: at 50 the right one stands still, at 100 it goes the opposite way", box: \pct, section: \shift),
			this.knob(\ring, 0, 1, \lin, 0, "", "ring", "over to ring modulation: up and down at once", box: \pct, section: \shift),
			this.knob(\driftDepth, 0, 1, \lin, 0, "", "depth", "how far the shift wanders, as a part of itself", box: \pct, section: \drift, size: \small),
			this.menu(\driftShape, FxLfo.shapes, \noise, "shape", "how the shift wanders", \drift),
			this.knob(\driftRate, 0.01, 12, \exp, 0.3, "Hz", "rate", "how quickly it wanders", section: \drift, size: \small),

			this.knob(\feedback, 0, 1.05, \lin, 0.6, "", "feedback", "how many trips it survives - past 100, it builds to a roar", box: \pct, section: \loop, size: \big),
			this.knob(\delay, 0.0015, 2, \exp, 0.08, "s", "delay", "how long a trip round the loop takes", box: \ms, lag: 0.15, section: \loop, size: \big),
			this.knob(\damp, 200, 20000, \exp, 12000, "Hz", "lo pass", "a low pass in the loop: climbing notes die out at the top", section: \loop, dec: 0, size: \small),
			this.knob(\loCut, 20, 4000, \exp, 40, "Hz", "hi pass", "a high pass in the loop: falling notes die out at the bottom", section: \loop, dec: 0, size: \small),
			this.knob(\drive, 0, 24, \lin, 0, "dB", "drive", "how hard the loop saturates: it holds a long loop up", section: \loop, dec: 1, size: \small, bipolar: false),
			this.knob(\width, 0, 2, \lin, 1, "", "width", "how far apart the sides are", box: \pct, section: \output),
			this.mixParams(\output, 0.5),
			// appended 2026-11-08: the drift on Tidal's cycle (FxLfo's sync)
			this.menu(\driftSync, FxLfo.syncNames, \free, "sync",
				"the drift free, at its rate - or one period every so many cycles of Tidal's clock", \drift)
		]
	}

	// one page: the shifter, the loop, the drift, then the output card
	*devicePages {
		^[
			\spiral -> [
				(title: "shift", keys: [\shift, \sides, \ring], colour: \identity),
				(title: "loop", keys: [\delay, \feedback, [\damp, \loCut], \drive]),
				(title: "drift", keys: [[\driftDepth, \driftShape], [\driftRate, \driftSync]])
			]
		]
	}

	// what does nothing in the current settings, dimmed
	*isActive { |key, unit|
		var fb = unit.get(\feedback) ? 0, depth = unit.get(\driftDepth) ? 0;
		^switch (key,
			// no feedback: one trip, nothing goes round, so nothing waits or saturates
			\delay, { fb > 0 },
			\drive, { fb > 0 },
			\driftShape, { depth > 0 },
			\driftSync, { depth > 0 },
			// on Tidal's clock the rate is the cycle's
			\driftRate, { depth > 0 and: { unit.get(\driftSync) == \free } },
			{ true }
		)
	}

	// parameters renamed on 2026-11-08 so each is a free Tidal word (old -> new):
	// old presets, sessions and code still reach them
	*renamed { ^(spread: \sides) }

	// what a Tidal pattern may move through SuperDirtGUI, event by event
	*tidalParams {
		^#[\shift, \sides, \ring, \driftDepth, \driftRate, \driftSync, \feedback, \delay, \damp, \drive, \width, \mix]
	}

	*usesTidalClock { ^true }

	*presetList {
		^[
			\default, (),
			'barber pole', (shift: 24, feedback: 0.92, delay: 0.12, damp: 8000, mix: 0.6),
			'falling', (shift: -18, feedback: 0.88, delay: 0.16, loCut: 90, mix: 0.6),
			'opposite ways', (shift: 45, sides: 1, feedback: 0.8, delay: 0.09, mix: 0.6),
			'detuned air', (shift: 1.2, sides: 1, feedback: 0.3, delay: 0.01, mix: 0.5),
			'phasing', (shift: 0.4, feedback: 0.75, delay: 0.004, mix: 0.5),
			'bell ring', (shift: 320, ring: 1, feedback: 0.55, delay: 0.25, mix: 0.5),
			'alien radio', (shift: 700, feedback: 0.7, delay: 0.03, driftDepth: 0.3, driftRate: 2, drive: 6, mix: 0.7),
			'roar', (shift: 12, feedback: 1.03, delay: 0.04, drive: 12, damp: 5000, loCut: 80, mix: 0.6)
		]
	}

	// where a partial is after some trips, in Hz, as the graph takes it: a
	// display's business, nothing filtered, nothing wandering
	*climb { |freq, shift, trips| ^freq + (shift * trips) }

	*process { |sig, p, ctx|
		var n = sig.size;
		var loop = FxLoop(n, 2.1);
		var drift = FxLfo.kr(p[\driftShape], p[\driftRate], 0, p[\driftSync]);
		var y = loop.read(p[\delay]);
		var x = sig + (y * p[\feedback]);
		var gain = p[\drive].dbamp;
		var wet = n.collect { |i|
			// on the right, spread moves it towards the opposite direction
			var side = (i % 2) * p[\sides] * -2 + 1;
			var hz = p[\shift] * (1 + (drift * p[\driftDepth])) * side;
			var shifted = FreqShift.ar(x[i], hz);
			var ringed = x[i] * SinOsc.ar(hz.abs);
			shifted + ((ringed - shifted) * p[\ring])
		};
		wet = HPF.ar(LPF.ar(wet, p[\damp]), p[\loCut]);
		wet = LeakDC.ar(wet);
		loop.write((wet * gain).tanh / gain.sqrt);
		ctx[\extras] = [drift];
		^FxKit.width(wet, p[\width])
	}
}
