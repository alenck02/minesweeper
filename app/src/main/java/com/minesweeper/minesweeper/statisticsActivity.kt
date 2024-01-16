package com.minesweeper.minesweeper

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import java.util.concurrent.TimeUnit

class statisticsActivity : AppCompatActivity() {
    private val difficulties = arrayOf("Beginner", "Intermediate", "Advanced", "Expert", "Extreme")
    private var currentIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_statistics)

        val buttonPrev: Button = findViewById(R.id.button_prev)
        val buttonNext: Button = findViewById(R.id.button_next)
        val textDifficulty: TextView = findViewById(R.id.chart_difficulty)

        gameStatistics()

        buttonPrev.setOnClickListener {
            if (currentIndex > 0) {
                currentIndex--
                textDifficulty.text = difficulties[currentIndex]
                updateStatistics()
                gameStatistics()
            }
        }

        buttonNext.setOnClickListener {
            if (currentIndex < difficulties.size - 1) {
                currentIndex++
                textDifficulty.text = difficulties[currentIndex]
                updateStatistics()
                gameStatistics()
            }
        }
    }

    private fun convertMillisToTimeString(millis: Long): String {
        val minutes = TimeUnit.MILLISECONDS.toMinutes(millis)
        val seconds = TimeUnit.MILLISECONDS.toSeconds(millis - TimeUnit.MINUTES.toMillis(minutes))
        return String.format("%02d:%02d", minutes, seconds)
    }

    private fun gameStatistics() {
        val total_game: TextView = findViewById(R.id.total_game)
        val game_clear: TextView = findViewById(R.id.clear_game)
        val game_percent: TextView = findViewById(R.id.clear_game_percent)
        val best_time: TextView = findViewById(R.id.best_time)
        val avg_time: TextView = findViewById(R.id.avg_time)

        val sharedPref = getSharedPreferences("gameState", Context.MODE_PRIVATE)
        val gameCount = sharedPref.getInt("gameCount$currentIndex", 0)
        val gameClearCount = sharedPref.getInt("gameClearCount$currentIndex", 0)

        val bestTimeKey = sharedPref.getLong("bestTime$currentIndex", 0)
        val totalTimeKey = sharedPref.getLong("totalTime$currentIndex", 0)

        total_game.text = gameCount.toString()
        game_clear.text = gameClearCount.toString()

        game_percent.text = if (gameCount != 0) {
            (gameClearCount * 100 / gameCount).toString() + "%"
        } else {
            "0%"
        }

        best_time.text = if (bestTimeKey != 0L) {
            convertMillisToTimeString(bestTimeKey)
        } else {
            "No Record"
        }

        avg_time.text = if (gameClearCount != 0) {
            convertMillisToTimeString((totalTimeKey.toDouble() / gameClearCount).toLong())
        } else {
            "No Record"
        }
    }

    private fun updateStatistics(): String {
        return when (currentIndex) {
            0 -> "Beginner"
            1 -> "Intermediate"
            2 -> "Advanced"
            3 -> "Expert"
            else -> "Extreme"
        }
    }
}