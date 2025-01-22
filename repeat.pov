// $Id: repeat.pov,v 1.1 2011/10/23 04:32:10 trevin Exp trevin $
//
// Persistence Of Vision version 3.6 scene description
// Repeat icon
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
#include "finish.inc"

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

union {
    merge {
	sphere { <-0.15, 0, -0.75>, 0.25 }
	cylinder { <-0.501, 0, -0.75>, <-0.15, 0, -0.75>, 0.25 }
	intersection {
	    torus { 0.75, 0.25 }
	    box { <-1.01, -0.26, -1.01>, <0.001, 0.26, 1.01> }
	    translate -0.5 * x
	}
	cone { <-0.7, 0, 0.75>, 0.6, <-0.1, 0, 0.75>, 0
	    scale <1, 0.625, 1>
	}
    }
    merge {
	sphere { <0.15, 0, 0.75>, 0.25 }
	cylinder { <0.15, 0, 0.75>, <0.501, 0, 0.75>, 0.25 }
	intersection {
	    torus { 0.75, 0.25 }
	    box { <0.001, -0.26, -1.01>, <1.01, 0.26, 1.01> }
	    translate 0.5 * x
	}
	cone { <0.1, 0, -0.75>, 0, <0.7, 0, -0.75>, 0.6
	    scale <1, 0.625, 1>
	}
    }
    texture {
	Dull
	pigment { color ForestGreen }
    }
    scale <1, 0.35, 1>
    rotate -90 * x
}
