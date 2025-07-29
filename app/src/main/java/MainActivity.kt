package com.example.integerprogressionsynth

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.math.BigInteger
import kotlin.math.PI
import kotlin.math.sin

class MainActivity : AppCompatActivity() {
    private lateinit var sequenceInput: EditText
    private lateinit var playButton: Button
    private lateinit var stopButton: Button
    private var audioTrack: AudioTrack? = null
    private var isPlaying = false
    private val sampleRate = 44100
    private val bpm = 60

    // Таблица нот и их индексов (без октав)
    private val noteToIndex = mapOf(
        "A" to 0, "A#" to 1, "Bb" to 1, "B" to 2, "Cb" to 2, "C" to 3, "C#" to 4,
        "Db" to 4, "D" to 5, "D#" to 6, "Eb" to 6, "E" to 7, "Fb" to 7, "E#" to 8,
        "F" to 8, "F#" to 9, "Gb" to 9, "G" to 10, "G#" to 11, "Ab" to 11
    )

    private val startFrequencies = mapOf(
        "A" to 55.0000,     // Ля большой октавы (A2)
        "A#" to 58.2705,    // Ля-диез
        "Bb" to 58.2705,    // Си-бемоль
        "B" to 61.7354,     // Си (B2)
        "Cb" to 61.7354,    // До-бемоль
        "B#" to 65.4064,    // Си-диез
        "C" to 65.4064,     // До (C3)
        "C#" to 69.2957,    // До-диез
        "Db" to 69.2957,    // Ре-бемоль
        "D" to 73.4162,     // Ре (D3)
        "D#" to 77.7818,    // Ре-диез
        "Eb" to 77.7818,    // Ми-бемоль
        "E" to 82.4069,     // Ми (E3)
        "Fb" to 82.4069,    // Фа-бемоль
        "E#" to 87.3071,    // Ми-диез
        "F" to 87.3071,     // Фа (F3)
        "F#" to 92.4986,    // Фа-диез
        "Gb" to 92.4986,    // Соль-бемоль
        "G" to 97.9989,     // Соль (G3)
        "G#" to 103.8262,   // Соль-диез
        "Ab" to 103.8262    // Ля-бемоль
    )

    private val chordStructures = mapOf(
        1 to listOf(4, 6, 8, 10, 12, 15, 16, 18, 20, 24, 30, 36, 45),
        2 to listOf(10, 15, 20, 24, 30, 36, 40, 48, 60, 72, 90, 135),
        3 to listOf(8, 16, 20, 25, 30, 32, 40, 45, 50, 60, 75, 90),
        4 to listOf(10, 15, 20, 24, 30, 40, 45, 48, 60, 75, 90, 135),
        5 to listOf(4, 6, 8, 12, 14, 16, 18, 21, 24, 27, 28, 32, 38, 42, 54),
        6 to listOf(4, 6, 8, 12, 14, 16, 17, 21, 24, 28, 32, 34, 42, 51),
        7 to listOf(2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 14, 16, 18, 22, 27),
        8 to listOf(2, 4, 5, 7, 8, 10, 11, 13, 14, 17, 19, 20, 28)
    )

    // Правила для шагов между аккордами (1-4)
    private val stepRules1To4 = mapOf(
        -1 to Fraction(16, 15), -2 to Fraction(9, 8), -3 to Fraction(6, 5),
        -4 to Fraction(5, 4), -5 to Fraction(4, 3), -6 to Fraction(45, 32),
        -7 to Fraction(3, 2), -8 to Fraction(8, 5), -9 to Fraction(5, 3),
        -10 to Fraction(9, 5), -11 to Fraction(48, 25),
        1 to Fraction(24, 25), 2 to Fraction(8, 9), 3 to Fraction(5, 6),
        4 to Fraction(4, 5), 5 to Fraction(3, 4), 6 to Fraction(32, 45),
        7 to Fraction(2, 3), 8 to Fraction(5, 8), 9 to Fraction(3, 5),
        10 to Fraction(5, 9), 11 to Fraction(8, 15)
    )

    // Правила для шагов между аккордами (5-8)
    private val stepRules5To8 = mapOf(
        -1 to Fraction(16, 15), -2 to Fraction(8, 7), -3 to Fraction(32, 27),
        -4 to Fraction(16, 13), -5 to Fraction(4, 3), -6 to Fraction(16, 11),
        -7 to Fraction(32, 21), -8 to Fraction(8, 5), -9 to Fraction(32, 19),
        -10 to Fraction(16, 9), -11 to Fraction(32, 17),
        1 to Fraction(16, 17), 2 to Fraction(8, 9), 3 to Fraction(16, 19),
        4 to Fraction(4, 5), 5 to Fraction(16, 21), 6 to Fraction(8, 11),
        7 to Fraction(2, 3), 8 to Fraction(8, 13), 9 to Fraction(16, 27),
        10 to Fraction(4, 7), 11 to Fraction(8, 15)
    )

    data class Fraction(val numerator: Int, val denominator: Int) {
        fun toDouble(): Double = numerator.toDouble() / denominator.toDouble()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        sequenceInput = findViewById(R.id.sequenceInput)
        playButton = findViewById(R.id.playButton)
        stopButton = findViewById(R.id.stopButton)

        playButton.setOnClickListener {
            if (!isPlaying) {
                playSequence()
            }
        }

        stopButton.setOnClickListener {
            stopPlayback()
        }
    }

    private fun parseInput(inputStr: String): Pair<String, Int> {
        val notePart = StringBuilder()
        var chordNum = 1 // По умолчанию первый аккорд

        for (char in inputStr) {
            if (char.isDigit()) {
                chordNum = char.toString().toInt()
                break
            }
            notePart.append(char)
        }

        val note = notePart.toString()
        if (!noteToIndex.containsKey(note)) {
            throw IllegalArgumentException("Недопустимое обозначение ноты: $note")
        }

        return Pair(note, chordNum)
    }

    private fun getStepRatio(oldChord: Int, newChord: Int, diff: Int): Fraction {
        return if (oldChord <= 4 && newChord <= 4) {
            stepRules1To4.getOrDefault(diff, Fraction(1, 1))
        } else {
            stepRules5To8.getOrDefault(diff, Fraction(1, 1))
        }
    }

    private fun generateChord(lowerHarmonicFreq: Double, chordNum: Int, duration: Double): ShortArray {
        val structure = chordStructures[chordNum] ?: listOf(1)
        val numSamples = (duration * sampleRate).toInt()
        val chordWave = ShortArray(numSamples)

        for (i in 0 until numSamples) {
            var sample = 0.0
            val t = i.toDouble() / sampleRate

            for (ratio in structure) {
                val freq = lowerHarmonicFreq * (ratio.toDouble() / structure[0])
                sample += sin(2.0 * PI * freq * t)
            }

            sample /= structure.size // Нормализация
            chordWave[i] = (sample * Short.MAX_VALUE).toInt().toShort()
        }

        return chordWave
    }

    private fun playSequence() {
        val inputText = sequenceInput.text.toString().trim()
        if (inputText.isEmpty()) {
            showToast("Введите последовательность аккордов")
            return
        }

        try {
            val items = inputText.split("\\s+".toRegex())
            val sequence = mutableListOf<Pair<String, Int>>()

            for (item in items) {
                val (note, chordNum) = parseInput(item)
                if (chordNum < 1 || chordNum > 8) {
                    throw IllegalArgumentException("Номер аккорда должен быть от 1 до 8, получено: $chordNum")
                }
                sequence.add(Pair(note, chordNum))
            }

            isPlaying = true
            playButton.isEnabled = false
            stopButton.isEnabled = true

            Thread {
                val beatDuration = 60.0 / bpm
                val chordDuration = beatDuration

                // Инициализация первого аккорда
                val (firstNote, firstChord) = sequence[0]
                val firstLowerHarmonic = startFrequencies[firstNote] ?: 440.0
                var audio = generateChord(firstLowerHarmonic, firstChord, chordDuration)

                var prevNoteIndex = noteToIndex[firstNote] ?: 0
                var prevLowerHarmonic = firstLowerHarmonic
                var prevChord = firstChord

                for (i in 1 until sequence.size) {
                    if (!isPlaying) break

                    val (note, chordNum) = sequence[i]
                    val currNoteIndex = noteToIndex[note] ?: 0
                    val diff = prevNoteIndex - currNoteIndex

                    val stepRatio = when {
                        (prevChord % 2 == 1) && (chordNum % 2 == 0) && (prevChord <= 4) && (chordNum <= 4) -> {
                            when (diff) {
                                -1 -> Fraction(25, 24)  // Полутон вверх (C1 → C#2)
                                11 -> Fraction(25, 48)   // Септима вверх (C1 → B2)
                                else -> getStepRatio(prevChord, chordNum, diff)
                            }
                        }
                        (prevChord % 2 == 0) && (chordNum % 2 == 1) && (prevChord <= 4) && (chordNum <= 4) -> {
                            when (diff) {
                                1 -> Fraction(24, 25)    // Полутон вниз (C#2 → C1)
                                -11 -> Fraction(48, 25)  // Септима вниз (B2 → C1)
                                else -> getStepRatio(prevChord, chordNum, diff)
                            }
                        }
                        else -> getStepRatio(prevChord, chordNum, diff)
                    }

                    val currLowerHarmonic = prevLowerHarmonic * stepRatio.toDouble()
                    val chordAudio = generateChord(currLowerHarmonic, chordNum, chordDuration)

                    // Объединяем аудио
                    val newAudio = ShortArray(audio.size + chordAudio.size)
                    System.arraycopy(audio, 0, newAudio, 0, audio.size)
                    System.arraycopy(chordAudio, 0, newAudio, audio.size, chordAudio.size)
                    audio = newAudio

                    prevNoteIndex = currNoteIndex
                    prevLowerHarmonic = currLowerHarmonic
                    prevChord = chordNum
                }

                if (isPlaying) {
                    playAudio(audio)
                }

                runOnUiThread {
                    playButton.isEnabled = true
                    stopButton.isEnabled = false
                    isPlaying = false
                }
            }.start()

        } catch (e: Exception) {
            showToast("Ошибка: ${e.message}")
            isPlaying = false
            playButton.isEnabled = true
            stopButton.isEnabled = false
        }
    }

    private fun playAudio(audio: ShortArray) {
        stopPlayback()

        audioTrack = AudioTrack(
            AudioManager.STREAM_MUSIC,
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            audio.size * 2,
            AudioTrack.MODE_STATIC
        )

        audioTrack?.apply {
            write(audio, 0, audio.size)
            setLoopPoints(0, audio.size / 2, -1)
            play()
        }
    }

    private fun stopPlayback() {
        audioTrack?.apply {
            stop()
            release()
        }
        audioTrack = null
        isPlaying = false

        runOnUiThread {
            playButton.isEnabled = true
            stopButton.isEnabled = false
        }
    }

    private fun showToast(message: String) {
        runOnUiThread {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopPlayback()
    }
}