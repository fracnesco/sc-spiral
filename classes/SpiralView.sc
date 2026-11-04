// =====================================================================
// SpiralView - Spiral's display: where one note goes, trip by trip
//
// A note at 440 Hz. Each dot is where the shifter has put it after one more trip
// round the loop, as big as the feedback leaves it, in the same log scale as the
// spectrum behind it. It says what the numbers do to a single partial - with the
// filters' cuts dimming what falls outside them - not what is playing now.
//
//   drag along            the shift (fine near the middle)
//   drag up and down      the feedback
//   the wheel             the delay
// =====================================================================

SpiralView : FxParamView {
	var dragging = false;

	*wantsSpectrum { ^true }

	prDraw {
		var fb = unit.feedback.min(1), shift = unit.shift, level = 1;
		var drift = (unit.extras ? [])[0] ? 0;
		var hz = shift * (1 + (drift * unit.driftDepth));
		Pen.fillColor = FxPalette.bg;
		Pen.fillRect(Rect(0, 0, bw, bh));
		this.prDrawSpectrum;
		Pen.fillColor = FxPalette.text;
		Pen.fillRect(Rect(FxParamView.freqToX(440, bw) - 1, 0, 2, bh));
		64.do { |trip|
			var f, x, y, size, passed, colour;
			level = level * fb;
			f = Spiral.climb(440, hz, trip + 1);
			if (f > 1 and: { f < 22000 } and: { level > 0.02 }) {
				passed = if (f > unit.damp or: { f < unit.loCut }) { 0.3 } { 1 };
				x = FxParamView.freqToX(f, bw);
				// up the view, a step a trip: it reads as the climb it is
				y = (bh - 12) - ((trip + 1) * (bh - 24) / 16);
				size = 3 + (level * 8);
				colour = FxPalette.accent.copy.alpha_(level * passed);
				Pen.fillColor = colour;
				Pen.fillOval(Rect(x - (size * 0.5), y.max(8) - (size * 0.5), size, size));
			};
		};
		this.prCaption("% Hz a trip, one every % ms, feedback %%".format(shift.round(0.01), (unit.delay * 1000).round(0.1),
			(unit.feedback * 100).round(1), "%"));
	}

	prMouseDown { |x, y, mod, count|
		dragging = true;
		this.prMouseMove(x, y, mod);
	}

	prMouseMove { |x, y, mod|
		var spec = Spiral.param(\shift)[\spec];
		if (dragging) {
			unit.shift = spec.map((x / bw).clip(0, 1));
			unit.feedback = (1 - (y / bh)).clip(0, 1.05);
		};
	}

	prMouseUp { dragging = false }

	prWheel { |x, y, delta| unit.delay = unit.delay * (1.03 ** delta.sign) }

	hint { ^"where a 440 Hz note goes on each trip round the loop - drag along for the shift, up and down for the feedback, the wheel for the delay" }
}
