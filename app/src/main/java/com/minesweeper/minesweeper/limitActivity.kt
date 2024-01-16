package com.minesweeper.minesweeper

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.LayerDrawable
import android.os.*
import android.util.TypedValue
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.ads.*
import com.google.android.gms.ads.rewarded.RewardItem
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.gson.Gson
import kotlinx.coroutines.launch
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.random.Random

@SuppressLint("NewApi")
class limitActivity : AppCompatActivity() {

    private lateinit var chronometer: Chronometer
    private lateinit var flagButton: ImageButton
    private lateinit var mineLeft: TextView

    private var flagMode: Boolean = false
    private var running: Boolean = false
    private var pauseTime = 0L

    private val rowCount = 30
    private val columnCount = 20
    private val mineCount = 150

    private lateinit var gridLayout: GridLayout
    private lateinit var frameLayout: FrameLayout
    private lateinit var scrollView: ScrollView

    private var minePositions = Array(rowCount) { BooleanArray(columnCount) }

    private val dx = intArrayOf(-1, -1, -1, 0, 0, 1, 1, 1)
    private val dy = intArrayOf(-1, 0, 1, -1, 1, -1, 0, 1)

    private var firstButtonRow = -1
    private var firstButtonColumn = -1

    private var nonMineButtonClickedCount = 0

    private var nonMineButtonCount = 0

    private var visited = Array(rowCount) { BooleanArray(columnCount) }

    private lateinit var scaleGestureDetector: ScaleGestureDetector
    private var scaleFactor = 1.0f
    private val MIN_SCALE_FACTOR = 0.7f
    private val MAX_SCALE_FACTOR = 1.3f

    private var lastX = 0f
    private var lastY = 0f
    private var translateX = 0f
    private var translateY = 0f

    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var isDragging = false

    var isGameOver = false
    private lateinit var myScaleListener: MyScaleListener

    private var downTime: Long = 0
    private var upTime: Long = 0
    private val LONG_CLICK_DURATION_MS = 500L

    private val DRAG_THRESHOLD_SQ = 500
    private var isLongClicked = false

    private var lastScaleTime = System.currentTimeMillis()

    private var gameStatusDao: MineSweeperDao? = null

    private var isGameLoaded = false

    private var isCellClicked = false

    private var hasGameOverDialogBeenShown = false

    private val PREF_NAME = "MyAppPreferences"
    private val KEY_KEEP_GAME_CALLED = "keepGameCalled"

    private var overFlagCount = 0

    private val currentIndex = 4

    @SuppressLint("MissingInflatedId", "ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_limit)

        scrollView = findViewById(R.id.limit_scrollView)
        frameLayout = findViewById(R.id.limit_frameLayout)
        gridLayout = findViewById(R.id.gridlayout_limit_game_board)

        chronometer = findViewById(R.id.limit_chronometer)
        flagButton = findViewById(R.id.limit_btn_flag)
        mineLeft = findViewById(R.id.limit_mine_left)

        visited = Array(rowCount) { BooleanArray(columnCount) }

        scaleGestureDetector = ScaleGestureDetector(this, MyScaleListener().also { myScaleListener = it })

        scaleFactor = 1.0f

        createGridLayout()

        val layoutParams = frameLayout.layoutParams
        layoutParams.width = (columnCount * 120 * scaleFactor).toInt()
        layoutParams.height = (rowCount * 120 * scaleFactor).toInt()
        frameLayout.layoutParams = layoutParams

        nonMineButtonClickedCount = 0
        nonMineButtonCount = rowCount * columnCount - mineCount

        val database = AppDatabase.getInstance(applicationContext)
        gameStatusDao = database?.mineSweeperDao()

        if (intent.getBooleanExtra("loadGameState", false)) {
            lifecycleScope.launch {
                loadGameState()
            }
        } else {
            lifecycleScope.launch {
                deleteAllGameStates()
                setKeepGameCalled(false)
            }
        }

        for (row in 0 until rowCount) {
            for (column in 0 until columnCount) {
                val cellButton = gridLayout.getChildAt(row * columnCount + column) as Button

                cellButton.setOnLongClickListener { v ->
                    if (!isGameOver && !isDragging && !myScaleListener.isScaling) {
                        if (cellButton.text.isNullOrEmpty() && !cellButton.isSelected) {
                            showVibrator()
                            cellButton.isSelected = true
                            val vectorDrawable = ResourcesCompat.getDrawable(resources, R.drawable.ic_flag, null)
                            val shapeDrawable = ResourcesCompat.getDrawable(resources, R.drawable.cell_open, null)
                            val layerDrawable = LayerDrawable(arrayOf(shapeDrawable, vectorDrawable))

                            cellButton.background = layerDrawable
                            updateMineLeftCount(-1)
                        } else if (cellButton.text.isNullOrEmpty() && cellButton.isSelected) {
                            showVibrator()
                            cellButton.isSelected = false
                            cellButton.setBackgroundResource(R.drawable.cell_close)
                            updateMineLeftCount(1)
                            visited[row][column] = false
                        }
                    }
                    true
                }

                cellButton.setOnTouchListener { v, event ->
                    if (event.pointerCount > 1) {
                        scaleGestureDetector.onTouchEvent(event)
                    }

                    val x = event.x
                    val y = event.y

                    if (!myScaleListener.isScaling) {
                        gridLayout.requestDisallowInterceptTouchEvent(true)
                        scrollView.requestDisallowInterceptTouchEvent(true)
                        when (event.actionMasked) {
                            MotionEvent.ACTION_DOWN -> {
                                lastX = x
                                lastY = y
                                isDragging = false
                                isLongClicked = false

                                downTime = System.currentTimeMillis()
                                scaleGestureDetector.isQuickScaleEnabled = false
                            }
                            MotionEvent.ACTION_MOVE -> {
                                val distanceMovedSq = (x - lastX)*(x - lastX) + (y - lastY)*(y - lastY)

                                if(distanceMovedSq >= DRAG_THRESHOLD_SQ){
                                    isDragging=true
                                }

                                if (isDragging) {
                                    val deltaX= event.x - lastX * scaleFactor
                                    val deltaY= event.y - lastY * scaleFactor

                                    translateX += deltaX * scaleFactor
                                    translateY += deltaY * scaleFactor

                                    lastTouchX = event.x
                                    lastTouchY = event.y

                                    translateX = translateX.coerceIn(-(columnCount * 120 * scaleFactor) / 2, (columnCount * 120 * scaleFactor) / 2)
                                    translateY = translateY.coerceIn(-(rowCount * 120 * scaleFactor) / 2, (rowCount * 120 * scaleFactor) / 2)

                                    gridLayout.translationX = translateX
                                    gridLayout.translationY = translateY

                                    return@setOnTouchListener true
                                }
                            }
                            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {

                                upTime = System.currentTimeMillis()

                                if (!isGameOver && !isDragging && upTime - downTime > LONG_CLICK_DURATION_MS && !myScaleListener.isScaling) { }
                                else if (!isGameOver && !isDragging && !myScaleListener.isScaling && (System.currentTimeMillis() - lastScaleTime > 300)) {

                                    lifecycleScope.launch {
                                        if (firstButtonRow == -1 && firstButtonColumn == -1) {

                                            firstButtonRow = row
                                            firstButtonColumn = column

                                            if (!isGameLoaded) {
                                                initializeBoard(firstButtonRow, firstButtonColumn)
                                                nonMineButtonClickedCount = 0
                                                nonMineButtonCount = rowCount * columnCount - mineCount

                                                isGameLoaded = true
                                            }

                                            if (!running) {
                                                chronometer.base =
                                                    SystemClock.elapsedRealtime() - pauseTime
                                                chronometer.start()
                                                viewMode("start")
                                            }
                                        }

                                        handleCellClick(row, column)

                                        isCellClicked = true
                                    }
                                }

                                isDragging = false
                                isLongClicked = false
                                scaleGestureDetector.isQuickScaleEnabled = true
                            }
                        }
                    }
                    return@setOnTouchListener super.onTouchEvent(event)
                }
            }
        }

        flagButton.setOnClickListener {
            flagMode = !flagMode
            if (flagMode) {
                flagButton.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.gray))
                flagButton.background = ContextCompat.getDrawable(this, R.drawable.image_line)
                flagButton.setImageResource(R.drawable.ic_flag_mode)
            } else {
                flagButton.backgroundTintList = null
                flagButton.background = ContextCompat.getDrawable(this, R.drawable.image_line)
                flagButton.setImageResource(R.drawable.ic_flag)
            }
        }

        scrollView.setOnTouchListener { _, event ->
            if (event.pointerCount > 1) {
                scaleGestureDetector.onTouchEvent(event)
            }

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    lastX = event.x
                    lastY = event.y
                    isDragging = true
                    scaleGestureDetector.isQuickScaleEnabled = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = event.x - lastX
                    val deltaY = event.y - lastY
                    translateX += deltaX
                    translateY += deltaY

                    gridLayout.translationX = translateX
                    gridLayout.translationY = translateY

                    lastX = event.x
                    lastY = event.y

                    translateX = translateX.coerceIn(-(columnCount * 120 * scaleFactor) / 2, (columnCount * 120 * scaleFactor) / 2)
                    translateY = translateY.coerceIn(-(rowCount * 120 * scaleFactor) / 2, (rowCount * 120 * scaleFactor) / 2)

                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isDragging = false
                    scaleGestureDetector.isQuickScaleEnabled = true
                }
            }
            true
        }
    }

    override fun onBackPressed() {
        if (!isGameLoaded) {
            val intent = Intent().apply {
                putExtra("isCellClicked", isCellClicked)
            }
            setResult(Activity.RESULT_OK, intent)
        }
        super.onBackPressed()
    }

    override fun onPause() {
        super.onPause()

        val sharedPref = getSharedPreferences("gameState", Context.MODE_PRIVATE)

        if (isGameOver) {
            with(sharedPref.edit()) {
                putBoolean("gameOver", true)
                apply()
            }

            endGame()
            setKeepGameCalled(false)
            isGameOver = false
        } else {
            lifecycleScope.launch {
                saveGameState()
            }

            if (isCellClicked) {
                with(sharedPref.edit()) {
                    putBoolean("gameOver", false)
                    apply()
                }
            }
        }
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        scaleGestureDetector.onTouchEvent(event)
        return super.dispatchTouchEvent(event)
    }

    inner class MyScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        var isScaling = false

        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            isScaling = true
            return super.onScaleBegin(detector)
        }

        override fun onScaleEnd(detector: ScaleGestureDetector) {
            isScaling = false
            lastScaleTime = System.currentTimeMillis()
            super.onScaleEnd(detector)
        }

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val factor = detector.scaleFactor

            if (scaleFactor * factor > MAX_SCALE_FACTOR) {
                scaleFactor = MAX_SCALE_FACTOR
            } else if (scaleFactor * factor < MIN_SCALE_FACTOR) {
                scaleFactor = MIN_SCALE_FACTOR
            } else {
                scaleFactor *= factor
            }

            if (isScaling) {
                gridLayout.scaleX = scaleFactor
                gridLayout.scaleY = scaleFactor
            } else {
                updateLayout()
            }

            return true
        }
    }

    private fun updateLayout() {
        gridLayout.scaleX = scaleFactor
        gridLayout.scaleY = scaleFactor

        for (row in 0 until rowCount) {
            for (column in 0 until columnCount) {
                val cellButton = gridLayout.getChildAt(row * columnCount + column) as Button
                cellButton.layoutParams.width = (120 * scaleFactor).toInt()
                cellButton.layoutParams.height = (120 * scaleFactor).toInt()
                cellButton.setTextSize(TypedValue.COMPLEX_UNIT_PX, 30f * scaleFactor)
            }
        }

        val layoutParams = frameLayout.layoutParams
        layoutParams.width = (columnCount * 120 * scaleFactor).toInt()
        layoutParams.height = (rowCount * 120 * scaleFactor).toInt()
        frameLayout.layoutParams = layoutParams
    }

    private fun createGridLayout() {
        for (row in 0 until rowCount) {
            for (column in 0 until columnCount) {
                val cellButton = Button(this)
                val layoutParams = GridLayout.LayoutParams().apply {
                    width = (120 * scaleFactor).toInt()
                    height = (120 * scaleFactor).toInt()
                    rowSpec = GridLayout.spec(row, 1f)
                    columnSpec = GridLayout.spec(column, 1f)
                }
                cellButton.layoutParams = layoutParams
                cellButton.setTextSize(TypedValue.COMPLEX_UNIT_PX, 30f * scaleFactor)
                cellButton.setTypeface(Typeface.MONOSPACE, Typeface.BOLD)
                cellButton.setBackgroundResource(R.drawable.cell_close)
                gridLayout.addView(cellButton)
            }
        }
    }

    private fun handleCellClick(row: Int, column: Int) {
        val isMine = minePositions[row][column]
        val cellButton = gridLayout.getChildAt(row * columnCount + column) as Button

        if (!flagMode) {
            if (isMine) {
                if (cellButton.isSelected) {
                    cellButton.isSelected = true
                    val vectorDrawable = ResourcesCompat.getDrawable(resources, R.drawable.ic_flag, null)
                    val shapeDrawable = ResourcesCompat.getDrawable(resources, R.drawable.cell_open, null)
                    val layerDrawable = LayerDrawable(arrayOf(shapeDrawable, vectorDrawable))

                    cellButton.background = layerDrawable
                } else {
                    cellButton.isSelected = true
                    overFlagCount++

                    if (!hasGameOverDialogBeenShown) {
                        gameOverDialog()
                        hasGameOverDialogBeenShown = true
                    }

                    val vectorDrawable = ResourcesCompat.getDrawable(resources, R.drawable.ic_select_mine, null)
                    val shapeDrawable = ResourcesCompat.getDrawable(resources, R.drawable.cell_open, null)
                    val layerDrawable = LayerDrawable(arrayOf(shapeDrawable, vectorDrawable))

                    cellButton.background = layerDrawable
                }
            } else if (!cellButton.isSelected) {
                val adjacentMineCount = getAdjacentMineCount(row, column)
                cellButton.text = if (adjacentMineCount > 0) adjacentMineCount.toString() else " "
                cellButton.setTextColor(ContextCompat.getColor(this, R.color.skyblue))
                cellButton.setBackgroundResource(R.drawable.cell_open)

                if (!visited[row][column]) {
                    nonMineButtonClickedCount++
                }

                if (nonMineButtonClickedCount == nonMineButtonCount) {
                    finishDialog()
                }

                val flagCount = getAdjacentFlagCount(row, column)
                if (flagCount >= adjacentMineCount && visited[row][column]) {
                    openAdjacentCells(row, column)
                }

                if (adjacentMineCount == 0) {
                    openAdjacentCells(row, column)
                }

                visited[row][column] = true
            }
        } else {
            if (cellButton.text.isNullOrEmpty() && !cellButton.isSelected) {
                cellButton.isSelected = true
                val vectorDrawable = ResourcesCompat.getDrawable(resources, R.drawable.ic_flag, null)
                val shapeDrawable = ResourcesCompat.getDrawable(resources, R.drawable.cell_open, null)
                val layerDrawable = LayerDrawable(arrayOf(shapeDrawable, vectorDrawable))

                cellButton.background = layerDrawable
                updateMineLeftCount(-1)
            } else if (cellButton.text.isNullOrEmpty() && cellButton.isSelected) {
                cellButton.isSelected = false
                cellButton.setBackgroundResource(R.drawable.cell_close)
                updateMineLeftCount(1)
                visited[row][column] = false
            }
        }
    }

    private fun openAdjacentCells(row: Int, column: Int) {
        val queue = ArrayDeque<Pair<Int, Int>>()

        queue.add(Pair(row, column))
        visited[row][column] = true

        while (queue.isNotEmpty()) {
            val (currentRow, currentColumn) = queue.removeFirst()
            var adjacentFlagCount = 0
            var adjacentMineCount = 0

            for (i in 0 until 8) {
                val newRow = currentRow + dx[i]
                val newColumn = currentColumn + dy[i]
                if (newRow in 0 until rowCount && newColumn in 0 until columnCount) {
                    val newCellButton = gridLayout.getChildAt(newRow * columnCount + newColumn) as Button

                    if (!visited[newRow][newColumn]) {
                        if (newCellButton.isSelected) {
                            adjacentFlagCount++
                            if (minePositions[newRow][newColumn]) {
                                adjacentMineCount++
                            }
                        } else {
                            if (minePositions[newRow][newColumn]) {
                                newCellButton.isSelected = true
                                overFlagCount++

                                if (!hasGameOverDialogBeenShown) {
                                    gameOverDialog()
                                    hasGameOverDialogBeenShown = true
                                }

                                val vectorDrawable = ResourcesCompat.getDrawable(resources, R.drawable.ic_select_mine, null)
                                val shapeDrawable = ResourcesCompat.getDrawable(resources, R.drawable.cell_open, null)
                                val layerDrawable = LayerDrawable(arrayOf(shapeDrawable, vectorDrawable))

                                newCellButton.background = layerDrawable
                            } else {
                                val count = getAdjacentMineCount(newRow, newColumn)
                                if (count == 0) {
                                    queue.add(Pair(newRow, newColumn))
                                }
                                nonMineButtonClickedCount++
                                if (nonMineButtonClickedCount == nonMineButtonCount) {
                                    finishDialog()
                                }
                                newCellButton.text = if (count > 0) count.toString() else " "
                                newCellButton.setBackgroundResource(R.drawable.cell_open)
                                newCellButton.setTextColor(ContextCompat.getColor(this, R.color.skyblue))
                            }
                        }
                        visited[newRow][newColumn] = true
                    }
                }
            }
        }
    }

    private fun getAdjacentFlagCount(row: Int, column: Int): Int {
        var count = 0
        for (i in 0 until 8) {
            val newRow = row + dx[i]
            val newColumn = column + dy[i]
            if (newRow in 0 until rowCount && newColumn in 0 until columnCount) {
                val newCellButton = gridLayout.getChildAt(newRow * columnCount + newColumn) as Button
                if (newCellButton.isSelected) {
                    count++
                }
            }
        }
        return count
    }

    private fun initializeBoard(firstButtonRow: Int, firstButtonColumn: Int) {
        for (row in 0 until rowCount) {
            for (column in 0 until columnCount) {
                minePositions[row][column] = false
                visited[row][column] = false
            }
        }

        var minesToPlace = mineCount
        while (minesToPlace > 0) {
            val randomRow = Random.nextInt(rowCount)
            val randomColumn = Random.nextInt(columnCount)
            if (!minePositions[randomRow][randomColumn] &&
                (abs(randomRow - firstButtonRow) > 1 || abs(randomColumn - firstButtonColumn) > 1)
            ) {
                minePositions[randomRow][randomColumn] = true
                minesToPlace--
            }
        }
    }

    private fun getAdjacentMineCount(row: Int, column: Int): Int {
        var count = 0
        for (i in 0 until 8) {
            val newRow = row + dx[i]
            val newColumn = column + dy[i]
            if (newRow in 0 until rowCount && newColumn in 0 until columnCount && minePositions[newRow][newColumn]) {
                count++
            }
        }
        return count
    }

    private fun isKeepGameCalled(): Boolean {
        val sharedPref = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return sharedPref.getBoolean(KEY_KEEP_GAME_CALLED, false)
    }

    private fun setKeepGameCalled(value: Boolean) {
        val sharedPref = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        with (sharedPref.edit()) {
            putBoolean(KEY_KEEP_GAME_CALLED, value)
            apply()
        }
    }

    private fun loadAndWatchAd(onAdFinished: () -> Unit, onAdFailed: () -> Unit) {
        val adRequest = AdRequest.Builder().build()
        val adUnitId = getString(R.string.rewarded_ad_unit_id)
        if (!isKeepGameCalled()) {
            RewardedAd.load(this, adUnitId, adRequest, object : RewardedAdLoadCallback() {
                override fun onAdFailedToLoad(adError: LoadAdError) {
                    Toast.makeText(this@limitActivity, "Failed to road Ad", Toast.LENGTH_SHORT).show()
                    onAdFailed()
                }

                override fun onAdLoaded(rewardedAd: RewardedAd) {
                    rewardedAd.fullScreenContentCallback = object: FullScreenContentCallback() {
                        override fun onAdDismissedFullScreenContent() {
                            onAdFailed()
                        }
                    }

                    rewardedAd.show(this@limitActivity, object : OnUserEarnedRewardListener {
                        override fun onUserEarnedReward(rewardItem: RewardItem) {
                            onAdFinished()
                        }
                    })
                }
            })
        }
    }

    @SuppressLint("MissingInflatedId")
    private fun gameOverDialog() {
        if (running) {
            chronometer.stop()
            pauseTime = SystemClock.elapsedRealtime() - chronometer.base
            viewMode("stop")
        }

        isGameOver = true

        val builder: AlertDialog.Builder = AlertDialog.Builder(this, R.style.RoundedAlertDialog)
        val dialogView = layoutInflater.inflate(R.layout.activity_game_over, null)
        builder.setView(dialogView)

        val alertDialog: AlertDialog = builder.create()
        alertDialog.setCancelable(false)
        alertDialog.show()

        val tvElapsedTime = dialogView.findViewById<TextView>(R.id.game_over_time)
        tvElapsedTime.text = formatElapsedTime(SystemClock.elapsedRealtime() - chronometer.base)

        val tvDifficulty = dialogView.findViewById<TextView>(R.id.game_over_level)
        tvDifficulty.text = getDifficultyName()

        val keep_game_btn = dialogView.findViewById<Button>(R.id.game_over_keep_game)

        if (isKeepGameCalled()) {
            keep_game_btn.text = "already watched"
            keep_game_btn.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.gray))
            keep_game_btn.setOnClickListener(null)
        }

        val game_over_btn = dialogView.findViewById<Button>(R.id.game_over_re_game)
        val GameOverBtn = View.OnClickListener {
            alertDialog.dismiss()

            isGameOver = true

            val intent = Intent()
            intent.putExtra("gameOver", true)
            setResult(Activity.RESULT_OK, intent)

            lifecycleScope.launch {
                deleteAllGameStates()
                setKeepGameCalled(false)
            }

            for (row in 0 until rowCount) {
                for (column in 0 until columnCount) {
                    val cellButton = gridLayout.getChildAt(row * columnCount + column) as Button
                    if (minePositions[row][column]) {
                        if (!cellButton.isSelected) {
                            val vectorDrawable = ResourcesCompat.getDrawable(resources, R.drawable.ic_mine, null)
                            val shapeDrawable = ResourcesCompat.getDrawable(resources, R.drawable.cell_close, null)
                            val layerDrawable = LayerDrawable(arrayOf(shapeDrawable, vectorDrawable))

                            cellButton.background = layerDrawable
                        }
                    } else if (cellButton.isSelected) {
                        val vectorDrawable = ResourcesCompat.getDrawable(resources, R.drawable.ic_no_mine, null)
                        val shapeDrawable = ResourcesCompat.getDrawable(resources, R.drawable.cell_open, null)
                        val layerDrawable = LayerDrawable(arrayOf(shapeDrawable, vectorDrawable))

                        cellButton.background = layerDrawable
                    }
                }
            }

            flagButton.setOnClickListener(null)
        }

        game_over_btn.setOnClickListener(GameOverBtn)

        keep_game_btn.setOnClickListener {
            if (!isKeepGameCalled()) {
                keep_game_btn.text = "Wait a Sec"
                keep_game_btn.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.gray))
                game_over_btn.setOnClickListener(null)

                isGameOver = false

                loadAndWatchAd(
                    onAdFinished = {
                        alertDialog.dismiss()

                        hasGameOverDialogBeenShown = false

                        if (!running) {
                            chronometer.base =
                                SystemClock.elapsedRealtime() - pauseTime
                            chronometer.start()
                            viewMode("start")
                        }

                        for (row in 0 until rowCount) {
                            for (column in 0 until columnCount) {
                                val cellButton = gridLayout.getChildAt(row * columnCount + column) as Button
                                if (minePositions[row][column]) {
                                    if (cellButton.isSelected) {
                                        val vectorDrawable = ResourcesCompat.getDrawable(resources, R.drawable.ic_flag, null)
                                        val shapeDrawable = ResourcesCompat.getDrawable(resources, R.drawable.cell_open, null)
                                        val layerDrawable = LayerDrawable(arrayOf(shapeDrawable, vectorDrawable))

                                        cellButton.background = layerDrawable
                                    }
                                }
                            }
                        }

                        updateMineLeftCount(-overFlagCount)
                        overFlagCount = 0
                        setKeepGameCalled(true)
                    },
                    onAdFailed = {
                        game_over_btn.setOnClickListener(GameOverBtn)
                        keep_game_btn.text = "Watch Ads"
                        keep_game_btn.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.skyblue))
                    }
                )
            }
        }
    }

    @SuppressLint("MissingInflatedId")
    private fun finishDialog() {
        isGameOver = true

        clearGame()

        val intent = Intent()
        intent.putExtra("gameOver", true)
        setResult(Activity.RESULT_OK, intent)

        lifecycleScope.launch {
            deleteAllGameStates()
            setKeepGameCalled(false)
        }

        if (running) {
            chronometer.stop()
            pauseTime = SystemClock.elapsedRealtime() - chronometer.base
            viewMode("stop")
        }

        mineLeft.text = 0.toString()

        for (row in 0 until rowCount) {
            for (column in 0 until columnCount) {
                val cellButton = gridLayout.getChildAt(row * columnCount + column) as Button
                if (minePositions[row][column]) {
                    val vectorDrawable = ResourcesCompat.getDrawable(resources, R.drawable.ic_flag, null)
                    val shapeDrawable = ResourcesCompat.getDrawable(resources, R.drawable.cell_open, null)
                    val layerDrawable = LayerDrawable(arrayOf(shapeDrawable, vectorDrawable))

                    cellButton.background = layerDrawable
                }
            }
        }

        val builder: AlertDialog.Builder = AlertDialog.Builder(this, R.style.RoundedAlertDialog)
        val dialogView = layoutInflater.inflate(R.layout.activity_game_finish, null)
        builder.setView(dialogView)

        val alertDialog: AlertDialog = builder.create()
        alertDialog.setCancelable(false)
        alertDialog.show()

        val tvElapsedTime = dialogView.findViewById<TextView>(R.id.finish_game_time)
        tvElapsedTime.text = formatElapsedTime(SystemClock.elapsedRealtime() - chronometer.base)

        val tvDifficulty = dialogView.findViewById<TextView>(R.id.finish_game_level)
        tvDifficulty.text = getDifficultyName()

        val finish_btn = dialogView.findViewById<Button>(R.id.go_main)
        finish_btn.setOnClickListener {
            alertDialog.dismiss()

            flagButton.setOnClickListener(null)
        }
    }

    @SuppressLint("MissingPermission")
    private fun showVibrator() {
        val vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            vibrator.vibrate(100)
        }
    }

    private fun getDifficultyName(): String {
        return "Extreme"
    }

    private fun formatElapsedTime(elapsedTime: Long): String {
        val minutes = TimeUnit.MILLISECONDS.toMinutes(elapsedTime)
        val seconds = TimeUnit.MILLISECONDS.toSeconds(elapsedTime) - TimeUnit.MINUTES.toSeconds(minutes)
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }

    private fun viewMode(mode: String) {
        running = mode == "start"
    }

    private fun updateMineLeftCount(change: Int) {
        val currentCount = mineLeft.text.toString().toInt()
        val newCount = currentCount + change
        mineLeft.text = newCount.toString()
    }

    private fun endGame() {
        val sharedPref = getSharedPreferences("gameState", Context.MODE_PRIVATE)
        val gameCount = sharedPref.getInt("gameCount$currentIndex", 0) + 1
        sharedPref.edit().putInt("gameCount$currentIndex", gameCount).apply()
    }

    private fun clearGame() {
        val sharedPref = getSharedPreferences("gameState", Context.MODE_PRIVATE)
        val gameClearCount = sharedPref.getInt("gameClearCount$currentIndex", 0) + 1
        sharedPref.edit().putInt("gameClearCount$currentIndex", gameClearCount).apply()

        val elapsedTime = SystemClock.elapsedRealtime() - chronometer.base
        val bestTime = sharedPref.getLong("bestTime$currentIndex", Long.MAX_VALUE)

        if (elapsedTime < bestTime) {
            sharedPref.edit().putLong("bestTime$currentIndex", elapsedTime).apply()
        }
        val totalTime = sharedPref.getLong("totalTime$currentIndex", 0)
        sharedPref.edit().putLong("totalTime$currentIndex", totalTime + elapsedTime).apply()
    }

    private suspend fun saveGameState() {
        val cellFlagged = Array(rowCount) { BooleanArray(columnCount) }
        val cellMineCountNearby = Array(rowCount) { IntArray(columnCount) }

        for (row in 0 until rowCount) {
            for (column in 0 until columnCount) {
                val cellButton = gridLayout.getChildAt(row * columnCount + column) as Button
                cellFlagged[row][column] = cellButton.isSelected
                if (!minePositions[row][column]) {
                    cellMineCountNearby[row][column] = getAdjacentMineCount(row, column)
                }
            }
        }

        val minePositionsJson = Gson().toJson(minePositions)
        val cellOpenedJson = Gson().toJson(visited)
        val cellFlaggedJson = Gson().toJson(cellFlagged)
        val cellMineCountNearbyJson = Gson().toJson(cellMineCountNearby)

        val gameStatus = MineSweeperEntity(
            id = 1,
            difficulty = 5,
            elapsedTime = SystemClock.elapsedRealtime() - chronometer.base,
            mineCount = mineLeft.text.toString().toInt(),
            nonMineButtonClickedCount = nonMineButtonClickedCount,
            nonMineButtonCount = nonMineButtonCount,
            minePositions = minePositionsJson,
            cellOpened = cellOpenedJson,
            cellFlagged = cellFlaggedJson,
            cellMineCountNearby=cellMineCountNearbyJson
        )

        val existingGameStatus = gameStatusDao?.get(1)
        if (existingGameStatus != null) {
            gameStatusDao?.update(gameStatus)
        } else {
            gameStatusDao?.insert(gameStatus)
        }
    }

    private suspend fun loadGameState(): Boolean {
        val gameStatus: MineSweeperEntity? =
            gameStatusDao?.get(1)

        isGameLoaded = true

        if (gameStatus != null){
            chronometer.base=SystemClock.elapsedRealtime() - gameStatus.elapsedTime
            chronometer.start()

            viewMode("start")
            mineLeft.text = gameStatus.mineCount.toString()

            nonMineButtonClickedCount = gameStatus.nonMineButtonClickedCount
            nonMineButtonCount = gameStatus.nonMineButtonCount

            minePositions=Gson().fromJson(gameStatus.minePositions, Array<BooleanArray>::class.java)
            visited=Gson().fromJson(gameStatus.cellOpened, Array<BooleanArray>::class.java)

            var flaggedCells=Gson().fromJson(gameStatus.cellFlagged, Array<BooleanArray>::class.java)

            var minesAroundCells=Gson().fromJson(gameStatus.cellMineCountNearby, Array<IntArray>::class.java)

            for (row in 0 until rowCount){
                for(column in 0 until columnCount){
                    var button:Button=(gridLayout.getChildAt(row*columnCount + column)) as Button

                    if(flaggedCells[row][column]){
                        button.isSelected=true
                        val vectorDrawable = ResourcesCompat.getDrawable(resources, R.drawable.ic_flag, null)
                        val shapeDrawable = ResourcesCompat.getDrawable(resources, R.drawable.cell_open, null)
                        val layerDrawable = LayerDrawable(arrayOf(shapeDrawable, vectorDrawable))

                        button.background = layerDrawable
                    } else if(visited[row][column]){
                        button.text=if(minesAroundCells[row][column]>0) minesAroundCells[row][column].toString() else " "
                        button.setBackgroundResource(R.drawable.cell_open)
                        button.setTextColor(ContextCompat.getColor(this, R.color.skyblue))
                    }
                }
            }
            return true
        }
        return false
    }

    private suspend fun deleteAllGameStates() {
        gameStatusDao?.deleteAll()
    }
}