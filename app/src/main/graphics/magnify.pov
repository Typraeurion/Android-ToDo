//
// Persistence Of Vision version 3.6 scene description
// Magnifying glass
// by Trevin Beattie
//
// Render with the following settings:
// +FN +AM3 +A0.3 +UA +W288 +H288
//
// This graphic is distributed under the Creative Commons
// Attribution-ShareAlike license:
// http://creativecommons.org/licenses/by-sa/3.0/
// Permission is granted to modify and distribute this work
// under condition that the original author is attributed
// and under the same or derivative Creative Commons license.

#include "colors.inc"
#include "finish.inc"
#include "glass.inc"
#include "metals.inc"

global_settings {
    assumed_gamma 2.2
    max_trace_level 24
}

camera {
    location < 0, 0.75, -7.5 >
    right x
    up y
    sky y
    direction 2 * z
    look_at < 0.5, -0.5, 0 >

    // Rotate the camera around for testing
    rotate <0, 0, 0>
}

// The sky should be totally transparent, so the icon has no background.
sky_sphere {
    pigment { rgbt 1 }
}

light_source {
    <-100, 100, -500>
    color White
}

// Glass housing
difference {
    cylinder { -0.1 * z, 0.1 * z, 1 }
    cylinder { -0.125 * z, 0.125 * z, 0.96875 }
    texture { T_Chrome_4B }	// 75% gray, fairly soft and dull
}

// Handle
cylinder {
    < 0.75, -0.75, 0 >, < 2.12, -2.12, 0 >, 0.1
    pigment { Gray25 }
    finish {
	ambient 0.2
	diffuse 0.6
	phong 0.75
	phong_size 25
    }
}

// Joint
cylinder {
    < 0.8, -0.8, 0 >, < 0.7, -0.7, 0 >, 0.05
    texture { T_Chrome_4B }
}

// Glass
#declare Cap_Radius = 4.7423828125; // (cyl_radius^2+protrusion^2)/(2*protrusion)
#declare Protrusion = 0.1;
#declare Sphere_Offset = Cap_Radius - Protrusion;
intersection {
    cylinder { -0.125 * z, 0.125 * z, 0.984375 }
    sphere { -Sphere_Offset * z, Cap_Radius }
    sphere { Sphere_Offset * z, Cap_Radius }
    material {
	texture {
	    pigment {
		// A subtle, slightly aged bluish-gray tint with high transparency
		color rgbf < 0.95, 0.98, 1.0, 0.98 >
	    }
	    finish {
		specular 0.7	// Sharp highlights
		roughness 0.001	// Very smooth surface
		ambient 0
		diffuse 0
		reflection {
		    0.1, 1.0	// Variable reflection
		    fresnel on
		}
		conserve_energy
	    }
	}
	interior {
	    ior 1.5	// Standard refraction for glass
	    fade_distance 1.0
	    fade_power 1001
	    fade_color < 0.95, 0.98, 1.0 >
	}
    }
}
