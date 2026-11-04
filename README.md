# Spiral

**A frequency shifter in a feedback loop - the sound that keeps climbing and never gets anywhere - running anywhere a UGen does, with a window.** A SuperCollider extension.

```supercollider
s.boot;
Ndef(\tone, { Spiral.ar(SinOsc.ar([220, 221]) * Decay2.kr(Impulse.kr(0.5), 0.01, 0.5) * 0.3, \spiral) }).play;

p = Spiral(Ndef(\tone), \spiral);
p.gui;
p.shift = 40; p.feedback = 0.8; p.delay = 0.08;
p.spread = 1;                     // the sides go opposite ways
p.preset = 'barber pole';
```

Every effect of this family lives in the same three places. Write `Spiral.ar` into a graph of your own and drive it from the object, as above; give it a node of its own on a bus, `Spiral.onBus(0, 2).enabled_(true)`; or put it in a chain, `FxChain.master.add(Spiral())`. If SuperDirtGUI is installed, it is an insert on an orbit too: `~sdg.addInsert(0, \spiral)`.

It is built on [FxKit](../sc%20fx%20kit), which is built on [ParaEQ](../sc%20EQ), and the class library will not compile without them; the installer links both when the folders sit side by side. It uses SuperCollider's own UGens, not sc3-plugins.

Written for SuperCollider 3.13 on macOS. Nothing in it is platform specific apart from the symlink in `install.scd`, which Linux shares and Windows does not.

## Install

### As a quark

```supercollider
Quarks.install("https://github.com/fracnesco/sc-spiral.git");
thisProcess.recompile;
```

The manifest names `sc-fx-kit` as a dependency, which names `sc-eq`, so `Quarks` fetches those first. The quark goes by the name of the repository, `sc-spiral`; the class inside it is `Spiral`.

### From a clone

Clone `sc-eq` into a folder called `sc EQ`, `sc-fx-kit` into `sc fx kit` and this into `sc spiral`, side by side. Open `install.scd` and evaluate its first block, then recompile the class library. It links what is missing and tells you what it did.

On Windows there is no `ln -s`, so use the quark, or copy the three folders into the extensions folder by hand: `Platform.userExtensionDir.openOS` opens it.

Install it one way or the other, **not both**: two copies on the class path is a duplicate class error at compile time.

### Check that it took

```supercollider
Spiral.presetNames;
Spiral.onBus(0, 2).enabled_(true).gui;
```

Select `Spiral` and press cmd/ctrl + d for the help file, and see [examples/spiral-demo.scd](examples/spiral-demo.scd) for a walkthrough you can evaluate a block at a time.

## What it does

A pitch shifter multiplies every partial; a frequency shifter adds the same number of Hz to each, so harmonics stop being harmonics. In a loop, each trip moves the sound further: `shift` is how far, `delay` how long a trip takes, and `feedback` how many trips it survives. `damp` and `loCut` are the walls climbing and falling notes die out against.

`spread` sends the sides apart, and at 100% in opposite directions. `ring` fades over to ring modulation at the same frequency, which goes both ways at once. `driftDepth` makes the shift wander.

The knob for `shift` is fine near 0 and coarse at the ends (`FxCubeWarp`), since 0.5 Hz and 500 Hz are both things you want.

A loop of less than a block is not possible without `LocalIn`, which the family does not use: the shortest `delay` is about 1.4 ms at 48 kHz and 64 samples.

## The window

`p.gui` opens the top row (on, presets, save, copy, paste), the display, and a row of knobs per section. Hover over anything and the bar at the bottom says what it does.

In the display, drag along for the shift (fine near the middle), up and down for the feedback; the wheel for the delay. It draws where a 440 Hz note goes, trip by trip: what the numbers do to one partial, not what is playing now.

## Presets

Built in: default, barber pole, falling, opposite ways, detuned air, phasing, bell ring, alien radio, roar.

Yours go in `Platform.userAppSupportDir/spiral-presets.scd`: `p.saveAsPreset("mine")`, or type a name in the window and press save. `p.copySettings` and `other.pasteSettings` carry settings from one to another.

## Parameters

Every parameter is a property: `p.shift`, `p.shift = ...`. These also work by name, `p.set(\shift, ...)`, `p.get(\shift)`. Knobs clip to their range. This table is generated from the class.

| parameter | section | range | default | what it does |
|---|---|---|---|---|
| `shift` | shift | -2000 .. 2000 Hz | 30 | how far each trip moves every partial, in Hz |
| `spread` | shift | -1 .. 1 | 0 | the sides apart: at 100 they go opposite ways |
| `ring` | shift | 0 .. 1 | 0 | over to ring modulation: both directions at once |
| `driftDepth` | shift | 0 .. 1 | 0 | how far the shift wanders, as a part of itself |
| `driftShape` | shift | sine triangle sawUp sawDown square sampleHold noise | noise | how it wanders |
| `driftRate` | shift | 0.01 .. 12 Hz | 0.3 | how quickly |
| `feedback` | loop | 0 .. 1.05 | 0.6 | how many trips it survives - past 100, it builds to a roar |
| `delay` | loop | 0.0015 .. 2 s | 0.08 | how long a trip takes |
| `damp` | loop | 200 .. 20000 Hz | 12000 | a low pass in the loop: climbing notes die out at the top |
| `loCut` | loop | 20 .. 4000 Hz | 40 | a high pass in the loop: falling notes die out at the bottom |
| `drive` | loop | 0 .. 24 dB | 0 | how hard the loop saturates |
| `width` | loop | 0 .. 2 | 1 | how far apart the sides are |
| `mix` | loop | 0 .. 1 | 0.5 | how much of the effect goes out, against the dry signal |
| `output` | loop | -36 .. 24 dB | 0 | the level of the whole thing, in dB |

## Files

| file | what it is |
|---|---|
| [classes/Spiral.sc](classes/Spiral.sc) | the parameters, the presets, the DSP |
| [classes/SpiralView.sc](classes/SpiralView.sc) | the window's display |
| [HelpSource/Classes/Spiral.schelp](HelpSource/Classes/Spiral.schelp) | the help file |
| [HelpSource/Classes/SpiralView.schelp](HelpSource/Classes/SpiralView.schelp) | the help file for the display |
| [examples/spiral-demo.scd](examples/spiral-demo.scd) | a walkthrough, one block at a time |
| [install.scd](install.scd) | the symlink, a check, an uninstall |
| [sc-spiral.quark](sc-spiral.quark) | the quark manifest |

## License

MIT - see [LICENSE](LICENSE).
