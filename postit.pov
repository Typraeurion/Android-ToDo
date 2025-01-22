// $Id: postit.pov,v 1.2 2014/02/05 03:40:15 trevin Exp trevin $
//
// Persistence Of Vision version 3.6 scene description
// Post-It note icon
// by Trevin Beattie
//
// Render with the following settings:
// +FN +AM3 +A0.3 +UA +W48 +H48
//
// This graphic is distributed under the Creative Commons
// Attribution-ShareAlike license:
// http://creativecommons.org/licenses/by-sa/3.0/
// Permission is granted to modify and distribute this work
// under condition that the original author is attributed
// and under the same or derivative Creative Commons license.

#include "colors.inc"
#include "metals.inc"

global_settings {
    assumed_gamma 2.2
    max_trace_level 24
}

camera {
    location <0, 0.75, -5>
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

bicubic_patch {
    type 1
    flatness 0.01
    u_steps 5
    v_steps 5
    <-1, 1, 0>, <-0.5, 1, 0>, <0.5, 1, 0>, <1, 1, 0>,
    <-1, 0.5, 0>, <-0.5, 0.5, 0>, <0.5, 0.5, 0>, <1, 0.5, 0>,
    <-1, -0.875, 0>, <-0.5, -0.875, 0>, <0.5, -0.875, 0>, <1, -0.875, 0>,
    <-1, -0.9375, -0.5>, <-0.5, -0.953125, -0.4375>, <0.75, -0.984375, -0.3125>, <1, -1, -0.25>
    pigment { color Yellow }
    finish {
	ambient 0.2
	specular 0.15
	roughness 0.9
    }
}

union {

    merge {
	cone {
	    <0, 0, -0.125>, 0.125, <0, 0, -0.5>, 0.1
	}
	cone {
	    <0, 0, -0.12>, 0.15, <0, 0, -0.25>, 0.05
	}
	cone {
	    <0, 0, -0.25>, 0.05, <0, 0, -0.505>, 0.125
	}
	pigment { color Red }
	finish {
	    ambient 0.15
	    specular 0.6
	    roughness 0.1
	}
    }

    cone {
	<0, 0, 0.25>, 0.0078125, <0, 0, -0.25>, 0.03125
	texture { T_Chrome_2D }
    }

    rotate <20, -10, 0>
    translate <0, 0.75, 0>
}
