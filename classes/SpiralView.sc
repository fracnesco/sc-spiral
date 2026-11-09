// =====================================================================
// SpiralView - Spiral's display: where one note goes, trip by trip
//
//   climb · +24.0 Hz a trip, every 120 ms · 83 trips · 9.84 s
//   10k |-------------------------------------- lo pass 8.00 kHz ---|
//       |                                  . . . . . . . . . :      |
//    1k |             . . . . . . . . . . .                  :      |
//   in >|. . . . . .                                  -60 dB :      |
//       |--------------------------------------- hi pass 40.0 Hz ---|
//       0          2.00 s          4.00 s          6.00 s
//   shift          clang       metal   beat  phase  beat   metal    clang
//         down -2k ======================|===V=================== 2k up
//
// The climb: a note at 440 Hz (a4) goes in; each bar is where the shifter has
// put it after one more trip round the loop, as long as a trip takes, as bright
// as the feedback leaves it. Hz go up (log), seconds along, so the slope is the
// shift and the length of the train is the feedback. The lo pass and hi pass
// are the walls a climbing or falling note dies against; with sides, the right
// side's trail is hollow; with ring, every trip goes both ways; drift is the
// band the trail wanders in (accent: modulated). The output's spectrum lies on
// its side at the left edge, and the live drift sways the trail.
//
// The strip under it is the shift on the knob's own scale, 0 in the middle,
// down to the left and up to the right, with the left side's needle (filled)
// and the right side's (hollow).
//
//   drag in the climb      up and down: the shift; along: the train stretches
//                          (the delay - the time axis holds while you drag)
//   drag the end line      the feedback: where the train dies out
//   drag a wall            the lo pass (top) or the hi pass (bottom)
//   drag the strip         the shift, 0 sticks in the middle; the hollow
//                          needle: the sides
//   the wheel              the feedback (on the strip: the shift, on a wall: it)
//   shift                  fine, everywhere
//   double click           back to the default of what is under the pointer
//
// SpiralShiftKnob reads the shift with its sign ("+24.0 Hz", "-1.50 kHz");
// SpiralPanel is the device panel with the display and the knobs lit together.
// =====================================================================

SpiralView : FxParamView {
	classvar <>note = 440, <>maxTrips = 256;
	var <>panel, <hot, hover;
	var drag, dragX, dragY, dragAxis, dragVals;
	var px0 = 44, px1 = 100, py0 = 22, py1 = 100, sy0 = 120, sy1 = 160, scx = 70, shalf = 20;
	var axisNow = 1;

	*wantsSpectrum { ^true }
	*minHeight { ^260 }

	// the knobs a part of the display plays, and back
	*partKeys {
		^(trail: #[\shift, \delay], strip: #[\shift], sides: #[\sides], end: #[\feedback],
			ceiling: #[\damp], floor: #[\loCut], drift: #[\driftDepth])
	}
	*hotKeys { ^#[\shift, \sides, \delay, \feedback, \damp, \loCut, \driftDepth] }

	// ------------------------------------------------------------ the model
	// in numbers, class side, so a check or a help file can ask it

	// what the loop's two filters leave of a partial at f Hz (2nd order each)
	*wall { |f, damp = 20000, loCut = 20|
		var ff = f.max(1e-3);
		^(1 + ((ff / damp) ** 4)).sqrt.reciprocal * (1 + ((loCut / ff) ** 4)).sqrt.reciprocal
	}

	// a trip's gain at low level: the feedback, and the drive's tanh(x g) / sqrt(g)
	*loopGain { |feedback, drive = 0| ^feedback * drive.dbamp.sqrt }

	// where a note goes: [[trip, Hz, amplitude], ...]. Trip 0 is the first pass,
	// at once; each one after it a delay later. Below 0 Hz it folds back up.
	*journey { |freq, hz, feedback, drive = 0, damp = 20000, loCut = 20, trips|
		var g = this.loopGain(feedback, drive), f = freq, a = 1, res = List.new, stop = false;
		(trips ? maxTrips).do { |k|
			if (stop.not) {
				f = (f + hz).abs;
				a = (a * this.wall(f, damp, loCut) * if (k == 0) { 1 } { g }).min(1);
				res.add([k, f, a]);
				if (a < 0.001 or: { feedback <= 0 }) { stop = true };
			};
		};
		^res.asArray
	}

	// true when the note is still there after maxTrips: it goes on
	*goesOn { |journey, feedback| ^(feedback > 0) and: { journey.last[2] >= 0.001 } }

	// when the train has died out (-60 dB), in seconds
	*endTime { |hz, feedback, delay, drive = 0, damp = 20000, loCut = 20, ring = 0|
		^case
		{ feedback <= 0 } { delay }
		{ ring > 0 } { this.ringTrips(note, hz, ring, feedback, drive, damp, loCut).size * delay }
		{ true } { this.journey(note, hz, feedback, drive, damp, loCut).size * delay }
	}

	// the feedback (0..1) that makes the train die out at t seconds
	*feedbackFor { |t, hz, delay, drive = 0, damp = 20000, loCut = 20, ring = 0|
		var lo = 0, hi = 1, mid;
		if (t <= delay) { ^0 };
		if (this.endTime(hz, 1, delay, drive, damp, loCut, ring) < t) { ^1 };
		16.do {
			mid = (lo + hi) * 0.5;
			if (this.endTime(hz, mid, delay, drive, damp, loCut, ring) < t) { lo = mid } { hi = mid };
		};
		^hi.round(0.001)
	}

	// with ring: every trip goes up and down at once, so a note spreads into
	// more and more partials. [trip -> [[Hz, amp], ...]], until they are all
	// together under -60 dB
	*ringTrips { |freq, hz, ring, feedback, drive = 0, damp = 20000, loCut = 20, trips = 48|
		var g = this.loopGain(feedback, drive);
		var upW = ((1 - ring).squared + (ring * 0.5).squared).sqrt, dnW = ring * 0.5;
		var comps = [[freq, 1]], res = List.new;
		trips.do { |k|
			var next = Dictionary.new;
			comps.do { |c|
				var a = c[1] * if (k == 0) { 1 } { g };
				[[(c[0] + hz).abs, a * upW], [(c[0] - hz).abs, a * dnW]].do { |pr|
					var aa = pr[1] * this.wall(pr[0], damp, loCut);
					var key = (pr[0].max(1).log2 * 96).round.asInteger;
					if (aa > 0.0003) {
						next[key] = [pr[0], ((next[key] !? { |e| e[1].squared } ? 0) + aa.squared).sqrt]
					};
				};
			};
			comps = next.values.collect { |e| [e[0], e[1].min(1)] };
			// the trip is gone when all its partials together are under -60 dB
			if (comps.isEmpty or: { comps.sum { |c| c[1].squared }.sqrt < 0.001 }) { ^res.asArray };
			res.add(comps);
			if (feedback <= 0) { ^res.asArray };
		};
		^res.asArray
	}

	// a shift with its sign: "+24.0 Hz", "-1.50 kHz", "0 Hz"
	*hzText { |v, signed = true|
		var a = v.abs, s = if (v < 0 and: { a >= 0.005 }) { "-" } { if (signed and: { a >= 0.005 }) { "+" } { "" } };
		if (a < 0.005) { ^"0 Hz" };
		^s ++ if (a.round(if (a < 100) { 0.01 } { 1 }) >= 1000) { FxPalette.sig3(a / 1000) ++ " kHz" } {
			FxPalette.fixed(a, case { a < 10 } { 2 } { a < 100 } { 1 } { true } { 0 }) ++ " Hz" }
	}

	// a word for what a shift does to a held sound, for the strip and the hints
	*zoneName { |hz|
		var a = hz.abs;
		^case { a < 2 } { "phase" } { a < 20 } { "beat" } { a < 200 } { "metal" } { true } { "clang" }
	}

	// ------------------------------------------------------------- reading

	prVals {
		var v = (), live = 0;
		#[\shift, \sides, \ring, \driftDepth, \feedback, \delay, \damp, \loCut, \drive].do { |k| v[k] = unit.get(k) ? 0 };
		// the drift as the server has it now, while replies come
		if (unit.isAlive) { live = (unit.extras ? [])[0] ? 0 };
		v[\drift] = live;
		v[\hzL] = v[\shift] * (1 + (live * v[\driftDepth]));
		v[\hzR] = v[\hzL] * (1 - (2 * v[\sides]));
		^v
	}

	prLayout {
		px0 = 44; px1 = bw - 8;
		py0 = 24;
		sy1 = bh - 4; sy0 = sy1 - 56;
		py1 = sy0 - 24;
		scx = (px0 + px1) * 0.5;
		shalf = ((px1 - px0) * 0.5 - 28).max(10);
	}

	prY { |f| ^py1 - (f.clip(20, 20000).explin(20, 20000, 0, py1 - py0)) }
	prFreqAt { |y| ^(py1 - y).linexp(0, py1 - py0, 20, 20000) }
	prX { |t| ^px0 + (t / axisNow * (px1 - px0)) }
	prTimeAt { |x| ^(x - px0) / (px1 - px0) * axisNow }
	prStripX { |hz|
		var spec = Spiral.param(\shift)[\spec];
		^scx + ((spec.unmap(hz.clip(spec.minval, spec.maxval)) * 2 - 1) * shalf)
	}
	prHzAt { |x|
		var spec = Spiral.param(\shift)[\spec];
		^spec.map(((x - scx) / shalf * 0.5 + 0.5).clip(0, 1))
	}

	// the axis a train of trips is drawn on: all of it, and at least four trips
	// the train as the display draws it: the left side's trips (tl), the right's
	// (tr, with sides), ring's (rt, with ring), how many, when they die out
	prTrain { |v|
		var c = this.class, t = ();
		t[\tl] = c.journey(note, v[\hzL], v[\feedback], v[\drive], v[\damp], v[\loCut]);
		if (v[\sides] != 0) { t[\tr] = c.journey(note, v[\hzR], v[\feedback], v[\drive], v[\damp], v[\loCut]) };
		if (v[\ring] > 0) {
			t[\rt] = c.ringTrips(note, v[\hzL], v[\ring], v[\feedback], v[\drive], v[\damp], v[\loCut]);
			t[\count] = t[\rt].size;
			t[\goesOn] = (v[\feedback] > 0) and: { t[\count] >= 48 };
		} {
			t[\count] = t[\tl].size;
			t[\goesOn] = c.goesOn(t[\tl], v[\feedback]);
		};
		t[\endT] = if (v[\feedback] <= 0) { v[\delay] } { t[\count] * v[\delay] };
		t[\axis] = max(t[\endT] * 1.08, v[\delay] * 4).max(0.002);
		^t
	}

	prLit { |part|
		^(hover == part) or: { hot.notNil and: { (this.class.partKeys[part] ? #[]).includes(hot) } }
	}

	hot_ { |key| hot = key; this.refresh }

	// --------------------------------------------------------------- drawing

	prDraw {
		var v = this.prVals, ident = this.class.identColour, identR;
		var train, comb;
		this.prLayout;
		identR = Color(ident.red * 0.5 + 0.5, ident.green * 0.5 + 0.5, ident.blue * 0.5 + 0.5);
		train = this.prTrain(v);
		axisNow = dragAxis ? train[\axis];
		comb = (v[\delay] <= 0.025) and: { v[\shift].abs <= 5 } and: { v[\feedback] > 0 };
		this.prDrawCaption(v, train, comb, ident);
		this.prDrawGrid;
		this.prDrawWalls(v);
		this.prDrawSpectrumSideways;
		if (comb) { this.prDrawComb(v, ident) };
		this.prDrawDrift(v);
		if (v[\ring] > 0) {
			this.prDrawRing(train[\rt], v, ident);
		} {
			train[\tr] !? { |tr| this.prDrawTrail(tr, v, identR, true) };
			this.prDrawTrail(train[\tl], v, ident, false);
		};
		this.prDrawNoteIn(v);
		if (v[\feedback] > 0) { this.prDrawEnd(train[\goesOn], train[\endT]) };
		this.prDrawStrip(v, ident, identR);
	}

	*identColour { ^Spiral.identityColour ? FxPalette.accent }

	prDrawCaption { |v, train, comb, ident|
		var title, cap, font = FxPalette.smallFont, w;
		title = case
		{ v[\feedback] <= 0 } { "one trip" }
		{ v[\ring] >= 0.5 } { "ring" }
		{ comb } { "comb" }
		{ v[\shift] < 0 } { "fall" }
		{ true } { "climb" };
		cap = case
		{ v[\feedback] <= 0 } { "every partial % - no feedback, so no trips round".format(this.class.hzText(v[\shift])) }
		{ comb } {
			"teeth every %, sliding % % a second".format(this.class.hzText(1 / v[\delay], false),
				if (v[\shift] >= 0) { "up" } { "down" }, this.class.hzText(v[\shift].abs / v[\delay], false))
		}
		{ train[\goesOn] } {
			"% a trip, every % - it goes on: the loop holds it up".format(this.class.hzText(v[\shift]),
				FxPalette.format(v[\delay], "s", \ms))
		}
		{ true } {
			"% a trip, every % · % trips · %".format(this.class.hzText(v[\shift]), FxPalette.format(v[\delay], "s", \ms),
				train[\count], FxPalette.format(train[\endT], "s", \ms))
		};
		if (v[\driftDepth] > 0 and: { v[\shift] != 0 }) {
			cap = cap ++ " · drift ±%".format(FxPalette.format(v[\driftDepth], "", \pct));
		};
		Pen.stringAtPoint(title, 8 @ 4, font, ident);
		w = title.bounds(font).width;
		Pen.stringAtPoint("·  " ++ cap, (8 + w + 6) @ 4, font, FxPalette.text);
	}

	prDrawGrid {
		var font = FxPalette.smallFont, step, n;
		Pen.fillColor = FxPalette.fade(FxPalette.bg, 0.6);
		Pen.fillRect(Rect(px0, py0, px1 - px0, py1 - py0));
		[50, 100, 200, 500, 1000, 2000, 5000, 10000].do { |f|
			var y = this.prY(f);
			Pen.fillColor = FxPalette.fade(FxPalette.waveDim, 0.35);
			Pen.fillRect(Rect(px0, y, px1 - px0, 1));
			Pen.stringRightJustIn(if (f >= 1000) { (f div: 1000).asString ++ "k" } { f.asString },
				Rect(0, y - 6, px0 - 6, 12), font, FxPalette.dim);
		};
		step = [0.001, 0.002, 0.005, 0.01, 0.02, 0.05, 0.1, 0.2, 0.5, 1, 2, 5, 10, 20, 50].detect { |s| (axisNow / s) <= 7 } ? 100;
		n = (axisNow / step).floor.asInteger;
		n.do { |i|
			var t = (i + 1) * step, x = this.prX(t);
			Pen.fillColor = FxPalette.fade(FxPalette.waveDim, 0.25);
			Pen.fillRect(Rect(x, py0, 1, py1 - py0));
			Pen.stringCenteredIn(FxPalette.format(t, "s", \ms), Rect(x - 30, py1 + 2, 60, 12).left_((x - 30).min(px1 - 60)),
				font, FxPalette.dim);
		};
		Pen.stringLeftJustIn("0", Rect(px0 + 2, py1 + 2, 20, 12), font, FxPalette.dim);
	}

	// the lo pass at the top and the hi pass at the bottom, shaded beyond
	prDrawWalls { |v|
		[[\ceiling, v[\damp], -1, "lo pass"], [\floor, v[\loCut], 1, "hi pass"]].do { |w|
			var y = this.prY(w[1]), y2 = this.prY(w[1] * (4 ** w[2].neg)), r, lit = this.prLit(w[0]);
			r = if (w[2] < 0) { Rect(px0, py0, px1 - px0, y - py0) } { Rect(px0, y, px1 - px0, py1 - y) };
			if (r.height > 0.5) {
				Pen.addRect(r);
				Pen.fillAxialGradient(px0 @ y, px0 @ y2, FxPalette.fade(FxPalette.waveDim, 0.05),
					FxPalette.fade(FxPalette.waveDim, 0.55));
			};
			Pen.fillColor = FxPalette.fade(if (lit) { FxPalette.accent } { FxPalette.handle }, if (lit) { 1 } { 0.55 });
			Pen.fillRect(Rect(px0, y - (lit.binaryValue * 0.5), px1 - px0, 1 + lit.binaryValue));
			// at the left, where the end line never is
			Pen.stringLeftJustIn("% %".format(w[3], FxPalette.format(w[1], "Hz")),
				Rect(px0 + 6, if (w[2] < 0) { (y - 13).max(py0) } { (y + 1).min(py1 - 12) }, px1 - px0 - 6, 12),
				FxPalette.smallFont, if (lit) { FxPalette.text } { FxPalette.dim });
		};
	}

	// what comes out, on its side at the start of the time line
	prDrawSpectrumSideways {
		var levels, freqs, lo = ParaEQSpectrum.floorDb, hi = ParaEQSpectrum.topDb, reach = (px1 - px0) * 0.16;
		if (spectrum.isNil or: { spectrum.visible.not }) { ^this };
		levels = spectrum.levels;
		freqs = spectrum.freqs;
		if (levels.isNil or: { freqs.isNil }) { ^this };
		Pen.moveTo(px0 @ py1);
		levels.do { |db, i|
			Pen.lineTo((px0 + db.linlin(lo, hi, 0, reach, \minmax)) @ this.prY(freqs[i]));
		};
		Pen.lineTo(px0 @ py0);
		Pen.fillColor = FxPalette.fade(FxPalette.wave, 0.22);
		Pen.fill;
	}

	// a short loop with a small shift is a comb whose teeth slide: the barber pole
	prDrawComb { |v, ident|
		var spacing = 1 / v[\delay], pxOct = (py1 - py0) / 1000.log2, fStop = pxOct / (3 * 2.log * v[\delay]);
		var g = this.class.loopGain(v[\feedback], v[\drive]).clip(0, 1);
		(fStop / spacing).floor.asInteger.min(60).do { |k|
			var f = (k + 1.3) * spacing, y = this.prY(f);
			var a = 0.35 * g * (1 - (f / fStop)).clip(0, 1).sqrt;
			if (f < 20000) {
				Pen.fillColor = FxPalette.fade(ident, a);
				Pen.fillRect(Rect(px0, y - 1, px1 - px0, 2));
			};
		};
	}

	// the band the climb wanders in: modulated, so accent
	prDrawDrift { |v|
		var lo, hi, n, lit = this.prLit(\drift);
		if (v[\driftDepth] <= 0 or: { v[\shift] == 0 }) { ^this };
		lo = this.class.journey(note, v[\shift] * (1 - v[\driftDepth]), v[\feedback], v[\drive], v[\damp], v[\loCut]);
		hi = this.class.journey(note, v[\shift] * (1 + v[\driftDepth]), v[\feedback], v[\drive], v[\damp], v[\loCut]);
		n = min(lo.size, hi.size);
		Pen.moveTo(this.prX(0) @ this.prY(hi[0][1]));
		n.do { |i| Pen.lineTo(this.prX((i + 1) * v[\delay]).min(px1) @ this.prY(hi[i][1])) };
		n.reverseDo { |i| Pen.lineTo(this.prX((i + 1) * v[\delay]).min(px1) @ this.prY(lo[i][1])) };
		Pen.lineTo(this.prX(0) @ this.prY(lo[0][1]));
		Pen.fillColor = FxPalette.fade(FxPalette.accent, if (lit) { 0.22 } { 0.12 });
		Pen.fill;
	}

	prAlpha { |a| ^((a.max(1e-6).ampdb + 60) / 60).clip(0, 1) * 0.85 + 0.15 }

	// each trip a bar at its pitch, as long as the trip
	prDrawTrail { |list, v, colour, hollow|
		var prevY, lit = this.prLit(\trail);
		list.do { |e|
			var t = e[0] * v[\delay], x0 = this.prX(t);
			var x1 = if (v[\feedback] <= 0) { px1 } { this.prX(t + v[\delay]).min(px1) };
			var y = this.prY(e[1]), a = this.prAlpha(e[2]);
			if (x0 < px1 and: { e[2] >= 0.001 }) {
				if ((x1 - x0) >= 5) {
					Pen.addRoundedRect(Rect(x0 + 1, y - 2, x1 - x0 - 2, 4), 2, 2);
					if (hollow) {
						Pen.strokeColor = FxPalette.fade(colour, a); Pen.width = 1; Pen.stroke;
					} {
						Pen.fillColor = FxPalette.fade(colour, a); Pen.fill;
					};
					prevY !? {
						Pen.fillColor = FxPalette.fade(colour, a * 0.45);
						Pen.fillRect(Rect(x0, min(prevY, y), 1, (prevY - y).abs.max(1)));
					};
				} {
					Pen.strokeColor = FxPalette.fade(colour, a);
					Pen.width = if (hollow) { 1 } { 2 };
					Pen.line(x0 @ (prevY ? y), x1 @ y);
					Pen.stroke;
				};
			};
			prevY = y;
		};
		// the first trip lit while its knobs or the pointer are on it
		if (lit and: { hollow.not } and: { list.notEmpty }) {
			var e = list[0], x0 = this.prX(0), x1 = if (v[\feedback] <= 0) { px1 } { this.prX(v[\delay]).min(px1) };
			Pen.strokeColor = FxPalette.accent;
			Pen.width = 1.5;
			Pen.addRoundedRect(Rect(x0, this.prY(e[1]) - 3.5, (x1 - x0).max(3), 7), 3, 3);
			Pen.stroke;
		};
	}

	prDrawRing { |trips, v, ident|
		trips.do { |comps, k|
			var x0 = this.prX(k * v[\delay]);
			var x1 = if (v[\feedback] <= 0) { px1 } { this.prX((k + 1) * v[\delay]).min(px1) };
			if (x0 < px1) {
				comps.do { |c|
					if (c[1] >= 0.002) {
					Pen.fillColor = FxPalette.fade(ident, this.prAlpha(c[1]) * 0.9);
					Pen.addRoundedRect(Rect(x0 + 1, this.prY(c[0]) - 1.5, (x1 - x0 - 2).max(2), 3), 1.5, 1.5);
					Pen.fill;
					};
				};
			};
		};
	}

	// the note going in, at the start
	prDrawNoteIn { |v|
		var y = this.prY(note), str = "in % (%)".format(FxPalette.format(note, "Hz"), FxPalette.noteName(note));
		var r = str.bounds(FxPalette.smallFont).moveTo(px0 + 4, y + if (v[\hzL] >= 0) { 4 } { -15 });
		Pen.fillColor = FxPalette.text;
		Pen.fillRect(Rect(px0 - 6, y - 1.5, 6, 3));
		// on a dark ground: a trail may run under it
		Pen.fillColor = FxPalette.fade(FxPalette.waveBg, 0.8);
		Pen.fillRect(r.insetBy(-2, 0));
		Pen.stringAtPoint(str, r.origin, FxPalette.smallFont, FxPalette.dim);
	}

	// where the train has died out, or that it never does
	prDrawEnd { |goesOn, endT|
		var x = if (goesOn) { px1 - 1 } { this.prX(endT).min(px1 - 1) }, lit = this.prLit(\end);
		Pen.strokeColor = if (lit) { FxPalette.accent } { FxPalette.fade(FxPalette.handle, 0.45) };
		Pen.width = if (lit) { 1.5 } { 1 };
		if (lit.not) { Pen.lineDash = FloatArray[3, 3] };
		Pen.line(x @ py0, x @ py1);
		Pen.stroke;
		Pen.lineDash = FloatArray[1, 0];
		Pen.stringRightJustIn(if (goesOn) { "goes on" } { "-60 dB" }, Rect(x - 64, py0 + 2, 60, 12),
			FxPalette.smallFont, if (lit) { FxPalette.text } { FxPalette.dim });
	}

	// the shift on the knob's own scale, 0 in the middle
	prDrawStrip { |v, ident, identR|
		var font = FxPalette.smallFont, top = sy0, ty = top + 30, xs, xl, xr, lit = this.prLit(\strip);
		var rLit = this.prLit(\sides);
		Pen.fillColor = FxPalette.card;
		Pen.fillRect(Rect(8, top - 6, bw - 16, 1));
		Pen.stringAtPoint("shift", 8 @ (top - 1), font, ident);
		Pen.stringRightJustIn(
			if (v[\sides] != 0) { "left %   right %".format(this.class.hzText(v[\hzL]), this.class.hzText(v[\hzR])) }
			{ this.class.hzText(v[\hzL]) }
			++ if (v[\ring] > 0) { "   and % (ring %)".format(this.class.hzText(v[\hzL].neg), FxPalette.format(v[\ring], "", \pct)) } { "" },
			Rect(px0, top - 1, px1 - px0, 12), font, FxPalette.text);
		// the zones: what a shift that size does to a held sound
		[[2, 20, "beat"], [20, 200, "metal"], [200, 2000, "clang"]].do { |z|
			[1, -1].do { |sgn|
				var xa = this.prStripX(z[0] * sgn), xb = this.prStripX(z[1] * sgn);
				Pen.stringCenteredIn(z[2], Rect(min(xa, xb), top + 12, (xb - xa).abs, 12), font, FxPalette.fade(FxPalette.dim, 0.8));
				Pen.fillColor = FxPalette.fade(FxPalette.waveDim, 0.6);
				Pen.fillRect(Rect(xb, top + 24, 1, 6));
				Pen.fillRect(Rect(xa, top + 24, 1, 6));
			};
		};
		Pen.stringCenteredIn("phase", Rect(this.prStripX(-2), top + 12, this.prStripX(2) - this.prStripX(-2), 12).insetBy(-20, 0),
			font, FxPalette.fade(FxPalette.dim, 0.8));
		// the track, filled from 0 like a bipolar knob
		Pen.fillColor = FxPalette.control;
		Pen.addRoundedRect(Rect(scx - shalf, ty, shalf * 2, 4), 2, 2);
		Pen.fill;
		xs = this.prStripX(v[\shift]);
		Pen.fillColor = ident;
		Pen.fillRect(Rect(min(scx, xs), ty, (xs - scx).abs, 4));
		if (v[\driftDepth] > 0) {
			var a = this.prStripX(v[\shift] * (1 - v[\driftDepth])), b = this.prStripX(v[\shift] * (1 + v[\driftDepth]));
			Pen.fillColor = FxPalette.fade(FxPalette.accent, 0.4);
			Pen.addRoundedRect(Rect(min(a, b), ty - 2, (b - a).abs.max(2), 8), 3, 3);
			Pen.fill;
		};
		[-2000, -1000, -100, -10, -1, 0, 1, 10, 100, 1000, 2000].do { |hz|
			var x = this.prStripX(hz);
			Pen.fillColor = FxPalette.fade(FxPalette.dim, if (hz == 0) { 1 } { 0.6 });
			Pen.fillRect(Rect(x, ty + 5, 1, if (hz == 0) { 6 } { 3 }));
			Pen.stringCenteredIn(case { hz.abs >= 1000 } { (hz div: 1000).asString ++ "k" } { true } { hz.asString },
				Rect(x - 16, ty + 11, 32, 12), font, FxPalette.dim);
		};
		Pen.stringLeftJustIn("down", Rect(8, ty + 11, 34, 12), font, FxPalette.text);
		Pen.stringRightJustIn("up", Rect(px1 - 26, ty + 11, 26, 12), font, FxPalette.text);
		// ring's other direction, a ghost
		if (v[\ring] > 0) {
			var x = this.prStripX(v[\hzL].neg);
			Pen.strokeColor = FxPalette.fade(ident, v[\ring] * 0.8);
			Pen.width = 1.5;
			Pen.lineDash = FloatArray[2, 2];
			Pen.line(x @ (ty - 8), x @ (ty + 10));
			Pen.stroke;
			Pen.lineDash = FloatArray[1, 0];
		};
		// the right side's needle, hollow
		if (v[\sides] != 0) {
			xr = this.prStripX(v[\hzR]);
			Pen.strokeColor = if (rLit) { FxPalette.accent } { identR };
			Pen.width = 1.5;
			Pen.line(xr @ (ty - 6), xr @ (ty + 10));
			Pen.stroke;
			Pen.moveTo((xr - 4) @ (ty - 11)); Pen.lineTo((xr + 4) @ (ty - 11)); Pen.lineTo(xr @ (ty - 5)); Pen.lineTo((xr - 4) @ (ty - 11));
			Pen.stroke;
			Pen.stringCenteredIn("R", Rect(xr - 6, ty - 23, 12, 11), font, identR);
		};
		// the left (or only) side's needle, filled
		xl = this.prStripX(v[\hzL]);
		Pen.strokeColor = if (lit) { FxPalette.accent } { ident };
		Pen.width = 2;
		Pen.line(xl @ (ty - 6), xl @ (ty + 10));
		Pen.stroke;
		Pen.fillColor = if (lit) { FxPalette.accent } { ident };
		Pen.moveTo((xl - 5) @ (ty - 12)); Pen.lineTo((xl + 5) @ (ty - 12)); Pen.lineTo(xl @ (ty - 5)); Pen.lineTo((xl - 5) @ (ty - 12));
		Pen.fill;
		if (v[\sides] != 0) { Pen.stringCenteredIn("L", Rect(xl - 6, ty - 24, 12, 11), font, ident) };
	}

	// --------------------------------------------------------------- playing

	// what is under the pointer: \trail \end \ceiling \floor \strip \sides, or nil
	prPart { |x, y|
		var v, xr, xl, endX, tl;
		this.prLayout;
		v = this.prVals;
		if (y >= (sy0 - 4)) {
			if (x < 4 or: { x > (bw - 4) }) { ^nil };
			if (v[\sides] != 0) {
				xr = this.prStripX(v[\hzR]); xl = this.prStripX(v[\hzL]);
				if ((x - xr).abs <= 6 and: { (x - xr).abs < (x - xl).abs }) { ^\sides };
			};
			^\strip
		};
		if (x < px0 or: { x > px1 } or: { y < py0 } or: { y > py1 }) { ^nil };
		if ((y - this.prY(v[\damp])).abs <= 5) { ^\ceiling };
		if ((y - this.prY(v[\loCut])).abs <= 5) { ^\floor };
		if (v[\feedback] > 0) {
			tl = this.prTrain(v);
			axisNow = dragAxis ? tl[\axis];
			endX = if (tl[\goesOn]) { px1 - 1 } { this.prX(tl[\endT]).min(px1 - 1) };
			if ((x - endX).abs <= 6) { ^\end };
		};
		^\trail
	}

	prDefault { |key| ^Spiral.param(key)[\default] }

	prSet { |key, value|
		var spec = Spiral.param(key)[\spec];
		unit.set(key, spec !? { spec.constrain(value) } ? value);
	}

	prMouseDown { |x, y, mod, count|
		var part = this.prPart(x, y), v = this.prVals;
		drag = nil;
		if (part.isNil) { ^this };
		if (count == 2) {
			switch (part,
				\trail, { this.prSet(\shift, this.prDefault(\shift)); this.prSet(\delay, this.prDefault(\delay)) },
				\strip, { this.prSet(\shift, this.prDefault(\shift)) },
				\sides, { this.prSet(\sides, this.prDefault(\sides)) },
				\end, { this.prSet(\feedback, this.prDefault(\feedback)) },
				\ceiling, { this.prSet(\damp, this.prDefault(\damp)) },
				\floor, { this.prSet(\loCut, this.prDefault(\loCut)) }
			);
			^this
		};
		drag = part;
		dragX = x; dragY = y;
		dragAxis = axisNow;
		dragVals = v;
		if (part == \strip and: { (mod & 131072) == 0 }) { this.prMouseMove(x, y, mod) };
	}

	prMouseMove { |x, y, mod|
		var fine = (mod ? 0) & 131072 != 0, v = dragVals, spec, u, t0, ratio, hzR;
		if (drag.isNil) {
			^this
		};
		spec = Spiral.param(\shift)[\spec];
		switch (drag,
			\trail, {
				// up and down: the shift, on the knob's scale; along: the train stretches round the point held
				u = spec.unmap(v[\shift]) - ((y - dragY) / 400 * if (fine) { 0.1 } { 1 });
				this.prSet(\shift, spec.map(u.clip(0, 1)));
				t0 = this.prTimeAt(dragX).max(v[\delay] * 0.5);
				ratio = this.prTimeAt(x).max(1e-4) / t0;
				if (fine) { ratio = ratio ** 0.1 };
				this.prSet(\delay, v[\delay] * ratio);
			},
			\end, {
				if (fine) {
					this.prSet(\feedback, v[\feedback] + ((x - dragX) / 2000));
				} {
					this.prSet(\feedback, this.class.feedbackFor(this.prTimeAt(x), v[\hzL], v[\delay], v[\drive], v[\damp], v[\loCut], v[\ring]));
				}
			},
			\ceiling, { this.prSetWall(\damp, x, y, fine, v) },
			\floor, { this.prSetWall(\loCut, x, y, fine, v) },
			\strip, {
				if (fine) {
					u = spec.unmap(v[\shift]) + ((x - dragX) / (shalf * 2) * 0.1);
					this.prSet(\shift, spec.map(u.clip(0, 1)));
				} {
					// 0 sticks in the middle
					this.prSet(\shift, if ((x - scx).abs <= 3) { 0 } { this.prHzAt(x) });
				}
			},
			\sides, {
				hzR = if (fine) { this.prHzAt(this.prStripX(v[\hzR]) + ((x - dragX) * 0.1)) } { this.prHzAt(x) };
				if (v[\shift].abs > 0.01) { this.prSet(\sides, (1 - (hzR / v[\shift])) * 0.5) };
			}
		);
	}

	prSetWall { |key, x, y, fine, v|
		var f = if (fine) { v[key] * ((this.prFreqAt(y) / this.prFreqAt(dragY)) ** 0.1) } { this.prFreqAt(y) };
		this.prSet(key, f);
	}

	prMouseUp {
		drag = nil;
		dragAxis = nil;
		this.refresh;
	}

	prWheel { |x, y, delta|
		var part = this.prPart(x, y), s = delta.sign, spec;
		if (part.isNil or: { s == 0 }) { ^this };
		switch (part,
			\strip, { spec = Spiral.param(\shift)[\spec]; this.prSet(\shift, spec.map((spec.unmap(unit.get(\shift)) + (s * 0.005)).clip(0, 1))) },
			\sides, { this.prSet(\sides, unit.get(\sides) + (s * 0.01)) },
			\ceiling, { this.prSet(\damp, unit.get(\damp) * (2 ** (s / 12))) },
			\floor, { this.prSet(\loCut, unit.get(\loCut) * (2 ** (s / 12))) },
			{ this.prSet(\feedback, (unit.get(\feedback) + (s * 0.01)).round(0.001)) }
		);
	}

	// ----------------------------------------------------------------- words

	// the knobs a part plays light up while the pointer is over it
	prHover { |part|
		var keys, ws = panel !? { panel.widgets };
		if (part == hover) { ^this };
		hover = part;
		keys = this.class.partKeys[part] ? #[];
		ws !? { this.class.hotKeys.do { |k| ws[k] !? { |w| w.tryPerform(\highlight_, keys.includes(k)) } } };
		this.refresh;
	}

	hint { |x, y|
		var part, v;
		if (x.isNil or: { unit.isNil }) {
			this.prHover(nil);
			^"where a % note goes, trip by trip round the loop: Hz up, seconds along - drag it for the shift and the delay,"
			" its end for the feedback, the walls for the filters; the strip under it is the shift".format(FxPalette.format(note, "Hz"))
		};
		part = this.prPart(x, y);
		this.prHover(part);
		v = this.prVals;
		^switch (part,
			\trail, { "the climb: % a trip, every % - drag up and down for the shift, along to stretch the trips (the delay);"
				" shift: fine; double click: back to % and %".format(this.class.hzText(v[\shift]), FxPalette.format(v[\delay], "s", \ms),
					this.class.hzText(this.prDefault(\shift)), FxPalette.format(this.prDefault(\delay), "s", \ms)) },
			\end, { "the end of the train: feedback % - drag it along for more or fewer trips, the wheel: % a notch;"
				" double click: back to %".format(FxPalette.format(v[\feedback], "", \pct), "1 %", FxPalette.format(this.prDefault(\feedback), "", \pct)) },
			\ceiling, { "the lo pass in the loop, %: a climbing note dies out here - drag it up and down, double click: back to %".format(
				FxPalette.format(v[\damp], "Hz"), FxPalette.format(this.prDefault(\damp), "Hz")) },
			\floor, { "the hi pass in the loop, %: a falling note dies out here - drag it up and down, double click: back to %".format(
				FxPalette.format(v[\loCut], "Hz"), FxPalette.format(this.prDefault(\loCut), "Hz")) },
			\strip, { "the shift, % (%): drag along, 0 sticks in the middle; down to the left, up to the right; shift: fine;"
				" double click: back to %".format(this.class.hzText(v[\shift]), this.class.zoneName(v[\shift]),
					this.class.hzText(this.prDefault(\shift))) },
			\sides, { "the right side, % (sides %): drag it along; double click: both sides together".format(
				this.class.hzText(v[\hzR]), FxPalette.format(v[\sides], "", \pct, nil, true)) },
			{ nil }
		)
	}
}


// =====================================================================
// SpiralShiftKnob - the shift's knob: its value with a sign, in Hz or kHz
// ("+24.0 Hz", "-1.50 kHz"), since down is as much a shift as up
// =====================================================================

SpiralShiftKnob : FxKnob {
	text { ^SpiralView.hzText(this.mapped) }
}


// =====================================================================
// SpiralPanel - the device panel (sc fx kit) as every effect has it, plus:
// the shift's knob says its sign, the display is given the panel, so
// hovering a part of it lights the knobs that set it, and hovering a knob
// lights its part of the display.
//
//   Spiral.panelClass       // SpiralPanel: unit.gui and unit.panel use it
// =====================================================================

SpiralPanel : FxDevicePanel {

	init { |argUnit, parent|
		super.init(argUnit, parent);
		display !? { |d| d.tryPerform(\panel_, this) };
	}

	prCell { |key, colour|
		var e, k;
		if (key != \shift) { ^super.prCell(key, colour) };
		e = cls.param(key);
		entries[key] = e;
		k = SpiralShiftKnob(e[\label], e[\spec], e[\size] ? \big, true, colour ?? { this.identity });
		k.default = e[\default];
		k.box = e[\box];
		k.dec = e[\dec];
		k.hint = e[\hint] ? "";
		k.action = { |kn| unit !? (_.set(key, kn.mapped)) };
		widgets[key] = k;
		^k.view
	}

	hints_ { |bar|
		super.hints_(bar);
		if (bar.isNil or: { display.isNil } or: { display.respondsTo(\hot_).not }) { ^this };
		widgets.keysValuesDo { |key, w|
			if (SpiralView.hotKeys.includes(key)) { this.prAttachHot(bar, key, w) };
		};
	}

	// a widget that also lights its part of the display while the pointer is on it
	prAttachHot { |bar, key, w|
		var e = cls.param(key);
		bar.attach(w.view, {
			display !? { |d| d.hot = key };
			if (w.isKindOf(FxKnob)) { "%  %   -   %".format(w.label, w.text, e[\hint]) } { e[\hint] }
		});
		w.view.mouseLeaveAction = { display !? { |d| d.hot = nil } };
	}
}
