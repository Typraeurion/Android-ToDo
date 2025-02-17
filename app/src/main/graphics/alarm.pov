// $Id: alarm.pov,v 1.1 2014/02/08 04:51:00 trevin Exp trevin $
//
// Persistence Of Vision version 3.6 scene description
// Alarm icon
// by Trevin Beattie
//
// Render with the following settings:
// +FN +AM3 +A0.3 +UA +W288 +H288
// Pov-Ray's anti-aliasing will not capture the details
// of the clock face at lower resolutions, so in this case
// it's better to render at a high resolution and then scale.
//
// This graphic is distributed under the Creative Commons
// Attribution-ShareAlike license:
// http://creativecommons.org/licenses/by-sa/3.0/
// Permission is granted to modify and distribute this work
// under condition that the original author is attributed
// and under the same or derivative Creative Commons license.

#include "colors.inc"
#include "finish.inc"
#include "metals.inc"

global_settings {
    assumed_gamma 2.2
    max_trace_level 24
}

camera {
    location <0, 0.75, -6.25>
    right x
    up y
    sky y
    direction 2 * z
    look_at <0, 0, 0>
}

// The sky should be totally transparent, so the icon has no background.
sky_sphere {
    pigment { rgbt 1 }
}

light_source {
    <-100, 100, -500>
    color White
}

// Casing
merge {
    difference {
	cylinder { -0.5 * z, 0.5 * z, 1 }
	cylinder { -0.75 * z, 0.75 * z, 0.87890625 }
    }
    torus { 0.9375, 0.0625
	rotate 90 * x
	translate -0.5 * z
    }
    torus { 0.9375, 0.0625
	rotate 90 * x
	translate 0.5 * z
    }
    texture { T_Brass_2B }
}

// Bells
difference {
    sphere { 0, 0.375 scale <1, 0.5, 1> }
    sphere { 0, 0.375 scale <1, 0.5, 1> translate -0.015625 * y }
    texture { T_Brass_2B }
    translate 1.125 * y
    rotate 30 * z
}
union {
    cylinder { y, 1.34375 * y, 0.03125 }
    sphere { 1.34375 * y, 0.03125 }
    texture { T_Brass_4D }
    rotate 30 * z
}

difference {
    sphere { 0, 0.375 scale <1, 0.5, 1> }
    sphere { 0, 0.375 scale <1, 0.5, 1> translate -0.015625 * y }
    texture { T_Brass_2B }
    translate 1.125 * y
    rotate -30 * z
}
union {
    cylinder { y, 1.34375 * y, 0.03125 }
    sphere { 1.34375 * y, 0.03125 }
    texture { T_Brass_4D }
    rotate -30 * z
}

// Base
difference {
    cone { -1.125 * y, 0.75, -0.875 * y, 0.5 }
    cylinder { -1 * z, z, 1 }
    texture { T_Brass_2B }
}

// Face plate
cylinder {
    <0, 0, -0.4375>, <0, 0, -0.40625>, 0.8828125
    texture {
	Dull
	pigment { color White }
    }
}

// Center marker for testing
//torus {
//    0.1875, 0.015625
//    rotate 90 * x
//    translate -0.5 * z
//    texture { pigment { color Red } }
//}

// Roman numerals
text {
    ttf "timrom.ttf" "I"
    0.00390625, 0
    pigment { color Black }
    scale <0.1875, 0.25, 1>
    translate <-0.015625, -0.09375, -0.44140625>
    translate <0.359375, 0.62245575897, 0>
}

text {
    ttf "timrom.ttf" "II"
    0.00390625, 0
    pigment { color Black }
    scale <0.1875, 0.25, 1>
    translate <-0.0468750, -0.09375, -0.44140625>
    translate <0.62245575897, 0.359375, 0>
}

text {
    ttf "timrom.ttf" "III"
    0.00390625, 0
    pigment { color Black }
    scale <0.1875, 0.25, 1>
    translate <-0.0859375, -0.09375, -0.44140625>
    translate <0.71875, 0, 0>
}

text {
    ttf "timrom.ttf" "IV"
    0.00390625, 0
    pigment { color Black }
    scale <0.1875, 0.25, 1>
    translate <-0.078125, -0.09375, -0.44140625>
    translate <0.62245575897, -0.359375, 0>
}

text {
    ttf "timrom.ttf" "V"
    0.00390625, 0
    pigment { color Black }
    scale <0.1875, 0.25, 1>
    translate <-0.0625, -0.09375, -0.44140625>
    translate <0.359375, -0.62245575897, 0>
}

text {
    ttf "timrom.ttf" "VI"
    0.00390625, 0
    pigment { color Black }
    scale <0.1875, 0.25, 1>
    translate <-0.078125, -0.09375, -0.44140625>
    translate <0, -0.71875, 0>
}

text {
    ttf "timrom.ttf" "VII"
    0.00390625, 0
    pigment { color Black }
    scale <0.1875, 0.25, 1>
    translate <-0.125, -0.09375, -0.44140625>
    translate <-0.359375, -0.62245575897, 0>
}

text {
    ttf "timrom.ttf" "VIII"
    0.00390625, 0
    pigment { color Black }
    scale <0.1875, 0.25, 1>
    translate <-0.15625, -0.09375, -0.44140625>
    translate <-0.62245575897, -0.359375, 0>
}

text {
    ttf "timrom.ttf" "IX"
    0.00390625, 0
    pigment { color Black }
    scale <0.1875, 0.25, 1>
    translate <-0.078125, -0.09375, -0.44140625>
    translate <-0.71875, 0, 0>
}

text {
    ttf "timrom.ttf" "X"
    0.00390625, 0
    pigment { color Black }
    scale <0.1875, 0.25, 1>
    translate <-0.0625, -0.09375, -0.44140625>
    translate <-0.62245575897, 0.359375, 0>
}

text {
    ttf "timrom.ttf" "XI"
    0.00390625, 0
    pigment { color Black }
    scale <0.1875, 0.25, 1>
    translate <-0.09375, -0.09375, -0.44140625>
    translate <-0.359375, 0.62245575897, 0>
}

text {
    ttf "timrom.ttf" "XII"
    0.00390625, 0
    pigment { color Black }
    scale <0.1875, 0.25, 1>
    translate <-0.125, -0.09375, -0.44140625>
    translate <0, 0.71875, 0>
}

// Minute hand
prism {
    bezier_spline 0, 0.00390625, 24,
    <0, -0.03125>, <-0.015625, -0.03125>, <-0.03125, -0.015625>, <-0.03125, 0>,
    <-0.03125, 0>, <-0.03125, 0.03125>, <-0.015625, 0.7109375>, <-0.015625, 0.71875>,
    <-0.015625, 0.71875>, <-0.015625, 0.7265625>, <-0.0078125, 0.734375>, <0, 0.734375>,
    <0, 0.734375>, <0.0078125, 0.734375>, <0.015625, 0.7265625>, <0.015625, 0.71875>,
    <0.015625, 0.71875>, <0.015625, 0.7109375>, <0.03125, 0.03125>, <0.03125, 0>,
    <0.03125, 0>, <0.03125, -0.015625>, <0.015625, -0.03125>, <0, -0.03125>
    pigment { color Black }
    rotate -90 * x
    translate -0.46875 * z
}

// Hour hand
prism {
    bezier_spline 0, 0.00390625, 24,
    <0, -0.046875>, <-0.0234375, -0.046875>, <-0.046875, -0.0234375>, <-0.046875, 0>,
    <-0.046875, 0>, <-0.046875, 0.046875>, <-0.0234375, 0.46875>, <-0.0234375, 0.5>,
    <-0.0234375, 0.5>, <-0.0234375, 0.53125>, <-0.015625, 0.5625>, <0, 0.5625>,
    <0, 0.5625>, <0.015625, 0.5625>, <0.0234375, 0.53125>, <0.0234375, 0.5>,
    <0.0234375, 0.5>, <0.0234375, 0.46875>, <0.046875, 0.046875>, <0.046875, 0>,
    <0.046875, 0>, <0.046875, -0.0234375>, <0.0234375, -0.046875>, <0, -0.046875>
    pigment { color Black }
    rotate <-90, 0, 150>
    translate -0.453125 * z
}
