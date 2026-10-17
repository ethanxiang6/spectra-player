package com.spectra.player.visualizer

/**
 * Holds live frames back by a few hundred milliseconds. The capture sees audio when the phone
 * sends it out; it reaches the ears later (much later over Bluetooth), so undelayed beats look early.
 */
class LiveDelay(private val bands: Int, capacity: Int = 90) {
    private val frames = Array(capacity) { FloatArray(bands) }
    private val times = LongArray(capacity)
    private var head = 0
    private var count = 0

    /** Stores [frame] taken at [nowMs] and writes the frame from [delayMs] ago into [out]. */
    fun push(nowMs: Long, frame: FloatArray, delayMs: Long, out: FloatArray) {
        frame.copyInto(frames[head])
        times[head] = nowMs
        head = (head + 1) % frames.size
        if (count < frames.size) count++

        val target = nowMs - delayMs
        // Newest frame that is at least delayMs old; else the oldest we have.
        var chosen = (head - count + frames.size) % frames.size
        for (k in 1..count) {
            val idx = (head - k + frames.size) % frames.size
            if (times[idx] <= target) {
                chosen = idx
                break
            }
        }
        frames[chosen].copyInto(out)
    }
}
