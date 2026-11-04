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
//   p.spread = 1;                        // the sides go opposite ways
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
	*windowSize { ^Point(860, 490) }

	*params {
		var shift = this.knob(\shift, -2000, 2000, \lin, 30, "Hz", "shift", "how far each trip moves every partial, in Hz", section: \shift, dec: 2);
		shift[\spec].warp = FxCubeWarp(shift[\spec]);
		^[
			shift,
			this.knob(\spread, -1, 1, \lin, 0, "", "spread", "the sides apart: at 100 they go opposite ways", box: \pct, section: \shift),
			this.knob(\ring, 0, 1, \lin, 0, "", "ring", "over to ring modulation: both directions at once", box: \pct, section: \shift),
			this.knob(\driftDepth, 0, 1, \lin, 0, "", "drift", "how far the shift wanders, as a part of itself", box: \pct, section: \shift),
			this.menu(\driftShape, FxLfo.shapes, \noise, "shape", "how it wanders", \shift),
			this.knob(\driftRate, 0.01, 12, \exp, 0.3, "Hz", "rate", "how quickly", section: \shift),

			this.knob(\feedback, 0, 1.05, \lin, 0.6, "", "feedback", "how many trips it survives - past 100, it builds to a roar", box: \pct, section: \loop),
			this.knob(\delay, 0.0015, 2, \exp, 0.08, "s", "delay", "how long a trip takes", box: \ms, lag: 0.15, section: \loop),
			this.knob(\damp, 200, 20000, \exp, 12000, "Hz", "lo pass", "a low pass in the loop: climbing notes die out at the top", section: \loop, dec: 0),
			this.knob(\loCut, 20, 4000, \exp, 40, "Hz", "hi pass", "a high pass in the loop: falling notes die out at the bottom", section: \loop, dec: 0),
			this.knob(\drive, 0, 24, \lin, 0, "dB", "drive", "how hard the loop saturates", section: \loop, dec: 1),
			this.knob(\width, 0, 2, \lin, 1, "", "width", "how far apart the sides are", box: \pct, section: \loop),
			this.mixParams(\loop, 0.5)
		]
	}

	*presetList {
		^[
			\default, (),
			'barber pole', (shift: 24, feedback: 0.92, delay: 0.12, damp: 8000, mix: 0.6),
			'falling', (shift: -18, feedback: 0.88, delay: 0.16, loCut: 90, mix: 0.6),
			'opposite ways', (shift: 45, spread: 1, feedback: 0.8, delay: 0.09, mix: 0.6),
			'detuned air', (shift: 1.2, spread: 1, feedback: 0.3, delay: 0.01, mix: 0.5),
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
		var drift = FxLfo.kr(p[\driftShape], p[\driftRate]);
		var y = loop.read(p[\delay]);
		var x = sig + (y * p[\feedback]);
		var gain = p[\drive].dbamp;
		var wet = n.collect { |i|
			// on the right, spread moves it towards the opposite direction
			var side = (i % 2) * p[\spread] * -2 + 1;
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
