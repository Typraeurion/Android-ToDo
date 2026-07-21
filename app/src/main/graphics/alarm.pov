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

// Default to light-mode colors
#ifndef (DarkMode)
    #declare DarkMode = 0;
#end

// The clock colors depend on whether we're rendering the clock for
// light mode (black text on a white face) or dark mode (light gray
// text on a dark gray face).
#if (DarkMode = 0)
    // Light mode
    #declare Global_Light_Color = color White;
    #declare Clock_Housing_Texture = texture { T_Brass_2B };
    #declare Bell_Pin_Texture = texture { T_Brass_4D };
    #declare Clock_Face_Texture = texture {
	Dull
	pigment { color White }
    };
    #declare Clock_Text_Texture = texture {
	pigment { color Black }
    };
    #declare Clock_Hands_Texture = texture {
	pigment { color Black }
    };
#else
    // Dark mode
    #declare Global_Light_Color = color rgb < 0.7, 0.7, 0.7 >;
    #declare Clock_Housing_Texture = texture {
	T_Brass_1C
	finish { specular 0.3 roughness 0.1 }
    };
    #declare Bell_Pin_Texture = texture {
	T_Brass_5E
	finish { specular 0.3 roughness 0.1 }
    };
    #declare Clock_Face_Texture = texture {
	Dull
	pigment { color rgb < 0.07, 0.07, 0.07 > }
    };
    #declare Clock_Text_Texture = texture {
	Dull
	pigment { color rgb < 0.45, 0.45, 0.45 > }
    };
    // This should be a soft green, such as glow-in-the-dark
    #declare Clock_Hands_Texture = texture {
	finish { ambient 0.1 phong 0.5 phong_size 1 }
	pigment { color rgb < 0.27, 0.54, 0.27 > }
    };
#end

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
    color Global_Light_Color
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
    texture { Clock_Housing_Texture }
}

// Bells
difference {
    sphere { 0, 0.375 scale <1, 0.5, 1> }
    sphere { 0, 0.375 scale <1, 0.5, 1> translate -0.015625 * y }
    texture { Clock_Housing_Texture }
    translate 1.125 * y
    rotate 30 * z
}
union {
    cylinder { y, 1.34375 * y, 0.03125 }
    sphere { 1.34375 * y, 0.03125 }
    texture { Bell_Pin_Texture }
    rotate 30 * z
}

difference {
    sphere { 0, 0.375 scale <1, 0.5, 1> }
    sphere { 0, 0.375 scale <1, 0.5, 1> translate -0.015625 * y }
    texture { Clock_Housing_Texture }
    translate 1.125 * y
    rotate -30 * z
}
union {
    cylinder { y, 1.34375 * y, 0.03125 }
    sphere { 1.34375 * y, 0.03125 }
    texture { Bell_Pin_Texture }
    rotate -30 * z
}

// Base
difference {
    cone { -1.125 * y, 0.75, -0.875 * y, 0.5 }
    cylinder { -1 * z, z, 1 }
    texture { Clock_Housing_Texture }
}

// Face plate
cylinder {
    <0, 0, -0.4375>, <0, 0, -0.40625>, 0.8828125
    texture { Clock_Face_Texture }
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
    texture { Clock_Text_Texture }
    scale <0.1875, 0.25, 1>
    translate <-0.015625, -0.09375, -0.44140625>
    translate <0.359375, 0.62245575897, 0>
}

text {
    ttf "timrom.ttf" "II"
    0.00390625, 0
    texture { Clock_Text_Texture }
    scale <0.1875, 0.25, 1>
    translate <-0.0468750, -0.09375, -0.44140625>
    translate <0.62245575897, 0.359375, 0>
}

text {
    ttf "timrom.ttf" "III"
    0.00390625, 0
    texture { Clock_Text_Texture }
    scale <0.1875, 0.25, 1>
    translate <-0.0859375, -0.09375, -0.44140625>
    translate <0.71875, 0, 0>
}

text {
    ttf "timrom.ttf" "IV"
    0.00390625, 0
    texture { Clock_Text_Texture }
    scale <0.1875, 0.25, 1>
    translate <-0.078125, -0.09375, -0.44140625>
    translate <0.62245575897, -0.359375, 0>
}

text {
    ttf "timrom.ttf" "V"
    0.00390625, 0
    texture { Clock_Text_Texture }
    scale <0.1875, 0.25, 1>
    translate <-0.0625, -0.09375, -0.44140625>
    translate <0.359375, -0.62245575897, 0>
}

text {
    ttf "timrom.ttf" "VI"
    0.00390625, 0
    texture { Clock_Text_Texture }
    scale <0.1875, 0.25, 1>
    translate <-0.078125, -0.09375, -0.44140625>
    translate <0, -0.71875, 0>
}

text {
    ttf "timrom.ttf" "VII"
    0.00390625, 0
    texture { Clock_Text_Texture }
    scale <0.1875, 0.25, 1>
    translate <-0.125, -0.09375, -0.44140625>
    translate <-0.359375, -0.62245575897, 0>
}

text {
    ttf "timrom.ttf" "VIII"
    0.00390625, 0
    texture { Clock_Text_Texture }
    scale <0.1875, 0.25, 1>
    translate <-0.15625, -0.09375, -0.44140625>
    translate <-0.62245575897, -0.359375, 0>
}

text {
    ttf "timrom.ttf" "IX"
    0.00390625, 0
    texture { Clock_Text_Texture }
    scale <0.1875, 0.25, 1>
    translate <-0.078125, -0.09375, -0.44140625>
    translate <-0.71875, 0, 0>
}

text {
    ttf "timrom.ttf" "X"
    0.00390625, 0
    texture { Clock_Text_Texture }
    scale <0.1875, 0.25, 1>
    translate <-0.0625, -0.09375, -0.44140625>
    translate <-0.62245575897, 0.359375, 0>
}

text {
    ttf "timrom.ttf" "XI"
    0.00390625, 0
    texture { Clock_Text_Texture }
    scale <0.1875, 0.25, 1>
    translate <-0.09375, -0.09375, -0.44140625>
    translate <-0.359375, 0.62245575897, 0>
}

text {
    ttf "timrom.ttf" "XII"
    0.00390625, 0
    texture { Clock_Text_Texture }
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
    texture { Clock_Hands_Texture }
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
    texture { Clock_Hands_Texture }
    rotate <-90, 0, 150>
    translate -0.453125 * z
}
