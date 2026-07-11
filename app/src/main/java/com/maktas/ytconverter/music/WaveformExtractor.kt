package com.maktas.ytconverter.music

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max

/** Decodes a local/content:// audio file into a downsampled amplitude array for the waveform editor. */
object WaveformExtractor {

    /** Returns [bucketCount] normalized (0..1) peak-amplitude buckets spanning the whole track. */
    suspend fun extract(context: Context, uri: Uri, bucketCount: Int = 400): FloatArray =
        withContext(Dispatchers.IO) {
            val extractor = MediaExtractor()
            try {
                extractor.setDataSource(context, uri, null)
                val trackIndex = (0 until extractor.trackCount).firstOrNull { i ->
                    extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
                } ?: return@withContext FloatArray(0)

                val format = extractor.getTrackFormat(trackIndex)
                extractor.selectTrack(trackIndex)
                val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) {
                    format.getLong(MediaFormat.KEY_DURATION)
                } else {
                    1L
                }

                val codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
                codec.configure(format, null, null, 0)
                codec.start()

                val buckets = FloatArray(bucketCount)
                var overallMax = 1f
                var bucketIndex = 0
                var bucketPeak = 0f

                val bufferInfo = MediaCodec.BufferInfo()
                var inputDone = false
                var outputDone = false

                while (!outputDone) {
                    if (!inputDone) {
                        val inIndex = codec.dequeueInputBuffer(10_000)
                        if (inIndex >= 0) {
                            val inputBuffer = codec.getInputBuffer(inIndex)!!
                            val sampleSize = extractor.readSampleData(inputBuffer, 0)
                            if (sampleSize < 0) {
                                codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputDone = true
                            } else {
                                codec.queueInputBuffer(inIndex, 0, sampleSize, extractor.sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }

                    val outIndex = codec.dequeueOutputBuffer(bufferInfo, 10_000)
                    if (outIndex >= 0) {
                        if (bufferInfo.size > 0 && durationUs > 0) {
                            val outputBuffer = codec.getOutputBuffer(outIndex)!!
                            outputBuffer.position(bufferInfo.offset)
                            outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                            val samples = outputBuffer.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()

                            val progress = (bufferInfo.presentationTimeUs.toFloat() / durationUs).coerceIn(0f, 1f)
                            val targetBucket = (progress * (bucketCount - 1)).toInt().coerceIn(0, bucketCount - 1)
                            if (targetBucket != bucketIndex) {
                                buckets[bucketIndex] = bucketPeak
                                bucketIndex = targetBucket
                                bucketPeak = 0f
                            }
                            while (samples.hasRemaining()) {
                                val v = abs(samples.get().toInt()).toFloat()
                                if (v > bucketPeak) bucketPeak = v
                            }
                            if (bucketPeak > overallMax) overallMax = bucketPeak
                        }
                        codec.releaseOutputBuffer(outIndex, false)
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    }
                }
                buckets[bucketIndex] = max(buckets[bucketIndex], bucketPeak)

                codec.stop()
                codec.release()

                for (i in buckets.indices) buckets[i] = (buckets[i] / overallMax).coerceIn(0f, 1f)
                buckets
            } catch (e: Exception) {
                FloatArray(0)
            } finally {
                extractor.release()
            }
        }
}
