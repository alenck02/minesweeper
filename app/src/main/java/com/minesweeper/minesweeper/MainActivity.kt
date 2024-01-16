package com.minesweeper.minesweeper

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.ProgressBar
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.google.android.play.core.review.ReviewManagerFactory

class MainActivity : AppCompatActivity() {
    private var currentDifficultyId: Int = 1

    private lateinit var restartBtn: Button
    private lateinit var startBtn: Button
    private lateinit var progressBar: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        startBtn = findViewById(R.id.gameStart)
        restartBtn = findViewById(R.id.gameRestart)
        progressBar = findViewById(R.id.progress_bar)
        val chartBtn: ImageButton = findViewById(R.id.chart)

        val prefs = getPreferences(Context.MODE_PRIVATE)
        currentDifficultyId = prefs.getInt("currentDifficultyId", 1) // 기본 난이도를 쉬움으로 설정

        val visible = getPreferences(Context.MODE_PRIVATE).getBoolean("restartBtnVisibility", false)
        restartBtn.visibility = if (visible) View.VISIBLE else View.INVISIBLE
        restartBtn.text = getButtonText()

        restartBtn.setOnClickListener {
            val gameIntent = Intent()
            // 기존에 저장되어 있는 게임 데이터 가져오기
            gameIntent.setClass(this, when (currentDifficultyId) {
                1 -> gameActivity::class.java
                2 -> medGame::class.java
                3 -> hardGameActivity::class.java
                4 -> veryHardGame::class.java
                else -> limitActivity::class.java
            })
            gameIntent.putExtra("loadGameState", true)

            progressBar.visibility = View.VISIBLE

            startActivityForResult(gameIntent, GAME_ACTIVITY_REQUEST_CODE)
        }

        startBtn.setOnClickListener {
            startDialog()
        }

        chartBtn.setOnClickListener {
            val intent = Intent(this, statisticsActivity::class.java)
            startActivity(intent)
        }
    }

    private fun getButtonText(): String {
        return when (currentDifficultyId) {
            1 -> "Continue - Beginner"
            2 -> "Continue - Intermediate"
            3 -> "Continue - Advanced"
            4 -> "Continue - Expert"
            else -> "Continue - Extreme"
        }
    }

    override fun onPause() {
        super.onPause()

        getPreferences(Context.MODE_PRIVATE).edit {
            putBoolean("restartBtnVisibility", restartBtn.visibility == View.VISIBLE)
            putInt("currentDifficultyId", currentDifficultyId)
        }
    }

    override fun onResume() {
        super.onResume()

        restartBtn = findViewById(R.id.gameRestart)
        startBtn = findViewById(R.id.gameStart)
        progressBar = findViewById(R.id.progress_bar)

        val visible = getPreferences(Context.MODE_PRIVATE).getBoolean("restartBtnVisibility", false)
        val prefs = getPreferences(Context.MODE_PRIVATE)

        progressBar.visibility = View.INVISIBLE

        if (visible) {
            startBtn.backgroundTintList = ColorStateList.valueOf(Color.WHITE)
            startBtn.setTextColor(ContextCompat.getColor(this, R.color.skyblue))

            restartBtn.visibility=View.VISIBLE
        } else {
            // restart 버튼이 안보일 때
            startBtn.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this,R.color.skyblue))
            startBtn.setTextColor(Color.WHITE)

            restartBtn.visibility=View.INVISIBLE
        }

        val sharedPref = getSharedPreferences("gameState", Context.MODE_PRIVATE)
        val gameOver = sharedPref.getBoolean("gameOver", false)
        val cellClicked = prefs.getBoolean("isCellClicked", false)
        val gameClear = sharedPref.getBoolean("gameClear", false)

        if (gameOver || gameClear) {
            restartBtn.visibility = View.INVISIBLE
            startBtn.setTextColor(Color.WHITE)
            startBtn.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.skyblue))
        } else {
            if (cellClicked) {
                restartBtn.visibility = View.VISIBLE
                startBtn.setTextColor(ContextCompat.getColor(this, R.color.skyblue))
                startBtn.backgroundTintList = ColorStateList.valueOf(Color.WHITE)
            }
        }
    }

    override fun onBackPressed() {
        val sharedPreferences = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val hasRequestedReview = sharedPreferences.getBoolean("hasRequestedReview", false)

        if (!hasRequestedReview) {
            val manager = ReviewManagerFactory.create(applicationContext)
            val request = manager.requestReviewFlow()
            request.addOnCompleteListener { request ->
                if (request.isSuccessful) {
                    val reviewInfo = request.result
                    val flow = manager.launchReviewFlow(this, reviewInfo)
                    flow.addOnCompleteListener { _ ->

                        sharedPreferences.edit().putBoolean("hasRequestedReview", true).apply()
                    }
                } else {
                    super.onBackPressed()
                }
            }
        } else {
            super.onBackPressed()
        }
    }

    private fun startGame(gameClass: Class<*>, difficultyId: Int) {
        currentDifficultyId = difficultyId

        val gameIntent = Intent()

        gameIntent.setClass(this, gameClass)
        gameIntent.putExtra("loadGameState", false)

        startActivityForResult(gameIntent, GAME_ACTIVITY_REQUEST_CODE)

        restartBtn.visibility = View.VISIBLE
        restartBtn.text = getButtonText()
        startBtn.setTextColor(ContextCompat.getColor(this, R.color.skyblue))
        startBtn.backgroundTintList = ColorStateList.valueOf(Color.WHITE)
        progressBar.visibility = View.VISIBLE
    }

    private fun startDialog() {
        val builder: AlertDialog.Builder = AlertDialog.Builder(this, R.style.RoundedAlertDialog)
        val dialogView = layoutInflater.inflate(R.layout.activity_game_level, null)
        builder.setView(dialogView)

        val alertDialog: AlertDialog = builder.create()
        alertDialog.show()

        val gameStart1 = dialogView.findViewById<Button>(R.id.level1)
        val gameStart2 = dialogView.findViewById<Button>(R.id.level2)
        val gameStart3 = dialogView.findViewById<Button>(R.id.level3)
        val gameStart4 = dialogView.findViewById<Button>(R.id.level4)
        val gameStart5 = dialogView.findViewById<Button>(R.id.level5)

        gameStart1.setOnClickListener {
            alertDialog.dismiss()
            startGame(gameActivity::class.java, 1)
        }

        gameStart2.setOnClickListener {
            alertDialog.dismiss()
            startGame(medGame::class.java, 2)
        }

        gameStart3.setOnClickListener {
            alertDialog.dismiss()
            startGame(hardGameActivity::class.java, 3)
        }

        gameStart4.setOnClickListener {
            alertDialog.dismiss()
            startGame(veryHardGame::class.java, 4)
        }

        gameStart5.setOnClickListener {
            alertDialog.dismiss()
            startGame(limitActivity::class.java, 5)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == GAME_ACTIVITY_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK) {
                val gameOver = data?.getBooleanExtra("gameOver", false)
                val cellClicked = data?.getBooleanExtra("isCellClicked", false)
                val gameClear = data?.getBooleanExtra("gameClear", false)

                if (gameOver == true || cellClicked == false || gameClear == true) {
                    restartBtn.visibility = View.INVISIBLE
                    startBtn.setTextColor(Color.WHITE)
                    startBtn.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.skyblue))
                    getPreferences(Context.MODE_PRIVATE).edit {
                        putBoolean("restartBtnVisibility", false)
                    }
                }
            }
        }
    }

    companion object {
        const val GAME_ACTIVITY_REQUEST_CODE = 1
    }
}