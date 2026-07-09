package com.maktas.ytconverter.music

// Intentionally empty. Pure-random shuffle used to be a custom Media3 ShuffleOrder,
// but a non-permutation order violates ExoPlayer's invariants and crashes. It's now
// implemented in PlaybackViewModel via random seeks instead.
