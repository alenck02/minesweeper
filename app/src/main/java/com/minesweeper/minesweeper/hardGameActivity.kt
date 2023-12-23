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
import androidx.appcompat.app.AppCompatActivity
import android.widget.*
import androidx.appcompat.app.AlertDialog
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
class hardGameActivity : AppCompatActivity() {

    private lateinit var chronometer: Chronometer
    private lateinit var flagButton: ImageButton
    private lateinit var mineLeft: TextView

    private var flagMode: Boolean = false
    private var running: Boolean = false
    private var pauseTime = 0L

    private val rowCount = 30
    private val columnCount = 16
    private val mineCount = 99

    private lateinit var gridLayout: GridLayout
    private lateinit var frameLayout: FrameLayout
    private lateinit var scrollView: ScrollView

    private var minePositions = Array(rowCount) { BooleanArray(columnCount) }

    private val dx = intArrayOf(-1, -1, -1, 0, 0, 1, 1, 1)
    private val dy = intArrayOf(-1, 0, 1, -1, 1, -1, 0, 1)

    // 첫 번째 버튼의 좌표를 저장할 변수 추가
    private var firstButtonRow = -1
    private var firstButtonColumn = -1

    // 변수 추가: 지뢰가 아닌 버튼 클릭 횟수
    private var nonMineButtonClickedCount = 0

    // 변수 추가: 지뢰가 아닌 버튼 총 개수
    private var nonMineButtonCount = 0

    // 게임 보드 내 각 셀을 방문한 여부를 저장하는 배열
    private var visited = Array(rowCount) { BooleanArray(columnCount) }

    // ScaleGestureDetector를 정의합니다.
    private lateinit var scaleGestureDetector: ScaleGestureDetector
    private var scaleFactor = 1.0f
    private val MIN_SCALE_FACTOR = 0.7f
    private val MAX_SCALE_FACTOR = 1.3f

    private var lastX = 0f
    private var lastY = 0f
    private var translateX = 0f
    private var translateY = 0f

    private var lastTouchX = 0f   // 마지막 터치 X 좌표 저장 변수 추가
    private var lastTouchY = 0f   // 마지막 터치 Y 좌표 저장 변수 추가
    private var isDragging = false   // 드래그 중인지 여부를 나타내는 변수 추가

    var isGameOver = false  // 게임 오버 상태 저장 변수
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

    @SuppressLint("MissingInflatedId", "ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hard_game)

        scrollView = findViewById(R.id.hard_scrollView)
        frameLayout = findViewById(R.id.hard_frameLayout)
        gridLayout = findViewById(R.id.gridlayout_hard_game_board)

        chronometer = findViewById(R.id.hard_chronometer)
        flagButton = findViewById(R.id.hard_btn_flag)
        mineLeft = findViewById(R.id.hard_mine_left)

        // visited 배열 초기화
        visited = Array(rowCount) { BooleanArray(columnCount) }

        // ScaleGestureDetector를 초기화합니다.
        scaleGestureDetector = ScaleGestureDetector(this, MyScaleListener().also { myScaleListener = it })

        scaleFactor = 1.0f

        createGridLayout()

        // FrameLayout의 크기 조정
        val layoutParams = frameLayout.layoutParams
        layoutParams.width = (columnCount * 120 * scaleFactor).toInt()
        layoutParams.height = (rowCount * 120 * scaleFactor).toInt()
        frameLayout.layoutParams = layoutParams

        // 게임 시작 시 모든 버튼 수를 nonMineButtonCount 변수에 저장
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

        // 버튼을 생성할 때 첫 번째 버튼의 좌표를 저장
        for (row in 0 until rowCount) {
            for (column in 0 until columnCount) {
                val cellButton = gridLayout.getChildAt(row * columnCount + column) as Button

                cellButton.setOnLongClickListener { v ->
                    if (!isGameOver && !isDragging && !myScaleListener.isScaling) {
                        if (cellButton.text.isNullOrEmpty() && !cellButton.isSelected) {
                            showVibrator()
                            // 0이나 텍스트가 비어 있지 않은 셀에는 깃발을 설치하지 않음
                            cellButton.isSelected = true
                            val vectorDrawable = ResourcesCompat.getDrawable(resources, R.drawable.ic_flag, null)
                            val shapeDrawable = ResourcesCompat.getDrawable(resources, R.drawable.cell_open, null)
                            val layerDrawable = LayerDrawable(arrayOf(shapeDrawable, vectorDrawable))

                            cellButton.background = layerDrawable
                            updateMineLeftCount(-1) // 지뢰 갯수 갱신
                        } else if (cellButton.text.isNullOrEmpty() && cellButton.isSelected) {
                            showVibrator()
                            // 이미 깃발이 설치된 셀에서 깃발을 다시 누르면 깃발 제거
                            cellButton.isSelected = false
                            cellButton.setBackgroundResource(R.drawable.cell_close)
                            updateMineLeftCount(1) // 지뢰 갯수 갱신
                            visited[row][column] = false
                        }
                    }
                    true  // 롱 클릭 이벤트 소비, 추가적인 클릭 이벤트 발생 방지.
                }

                cellButton.setOnTouchListener { v, event ->
                    if (event.pointerCount > 1) {
                        // ScaleGestureDetector에 터치 이벤트 전달하여 처리합니다.
                        scaleGestureDetector.onTouchEvent(event)
                    }

                    val x = event.x
                    val y = event.y

                    if (!myScaleListener.isScaling) {
                        gridLayout.requestDisallowInterceptTouchEvent(true)
                        scrollView.requestDisallowInterceptTouchEvent(true)
                        when (event.actionMasked) {
                            MotionEvent.ACTION_DOWN -> {
                                lastX = x   // X 좌표 저장
                                lastY = y   // Y 좌표 저장
                                isDragging = false   // 드래그 상태 초기화
                                isLongClicked = false

                                downTime = System.currentTimeMillis()  // 롱 클릭 감지를 위한 다운 타임 기록
                                scaleGestureDetector.isQuickScaleEnabled = false // 핀치 줌 비활성화
                            }
                            MotionEvent.ACTION_MOVE -> {
                                val distanceMovedSq = (x - lastX)*(x - lastX) + (y - lastY)*(y - lastY)

                                if(distanceMovedSq >= DRAG_THRESHOLD_SQ){
                                    isDragging=true
                                }

                                if (isDragging) {
                                    val deltaX= event.x - lastX * scaleFactor   // X 좌표 변위 계산
                                    val deltaY= event.y - lastY * scaleFactor   // Y 좌표 변위 계산

                                    translateX += deltaX * scaleFactor
                                    translateY += deltaY * scaleFactor

                                    // Update the last touch position before applying the translation limit.
                                    lastTouchX = event.x     // 마지막 터치한 위치 업데이트
                                    lastTouchY = event.y     // 마지막 터치한 위치 업데이트

                                    // Limit the translation within the specified range
                                    translateX = translateX.coerceIn(-(columnCount * 120 * scaleFactor) / 2, (columnCount * 120 * scaleFactor) / 2)
                                    translateY = translateY.coerceIn(-(rowCount * 120 * scaleFactor) / 2, (rowCount * 120 * scaleFactor) / 2)

                                    // Apply translation to the gridLayout after limiting it.
                                    gridLayout.translationX = translateX
                                    gridLayout.translationY = translateY

                                    return@setOnTouchListener true   // 이벤트 소비하여 스크롤 동작 방지
                                }
                            }
                            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {

                                upTime = System.currentTimeMillis()  // 롱 클릭 감지를 위한 업 타임 기록

                                if (!isGameOver && !isDragging && upTime - downTime > LONG_CLICK_DURATION_MS && !myScaleListener.isScaling) { }
                                else if (!isGameOver && !isDragging && !myScaleListener.isScaling && (System.currentTimeMillis() - lastScaleTime > 300)) {

                                    lifecycleScope.launch {
                                        if (firstButtonRow == -1 && firstButtonColumn == -1) {

                                            // 첫 번째 버튼을 누른 경우 좌표 저장
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

                                isDragging = false   // 드래그 종료 상태로 변경
                                isLongClicked = false
                                scaleGestureDetector.isQuickScaleEnabled = true // 핀치 줌 활성화
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
                // Handle touch events for dragging and pinch-to-zoom
                scaleGestureDetector.onTouchEvent(event)
            }

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    // Handle dragging
                    lastX = event.x
                    lastY = event.y
                    isDragging = true
                    scaleGestureDetector.isQuickScaleEnabled = false // 핀치 줌 비활성화
                }
                MotionEvent.ACTION_MOVE -> {
                    // Calculate translation based on touch movement
                    val deltaX = event.x - lastX
                    val deltaY = event.y - lastY
                    translateX += deltaX
                    translateY += deltaY

                    // Apply translation to the gridLayout
                    gridLayout.translationX = translateX
                    gridLayout.translationY = translateY

                    lastX = event.x
                    lastY = event.y

                    // Limit the translation within the specified range
                    translateX = translateX.coerceIn(-(columnCount * 120 * scaleFactor) / 2, (columnCount * 120 * scaleFactor) / 2)
                    translateY = translateY.coerceIn(-(rowCount * 120 * scaleFactor) / 2, (rowCount * 120 * scaleFactor) / 2)

                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isDragging = false
                    scaleGestureDetector.isQuickScaleEnabled = true // 핀치 줌 활성화
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

    // 확대/축소를 감지하는 ScaleGestureDetector의 리스너를 구현합니다.
    inner class MyScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        var isScaling = false  // 핀치 줌 중임을 나타내는 플래그 변수

        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            isScaling = true  // 핀치 줌 시작 시 플래그 값을 참으로 설정합니다.
            return super.onScaleBegin(detector)
        }

        override fun onScaleEnd(detector: ScaleGestureDetector) {
            isScaling = false  // 핀치 줌 종료 시 플래그 값을 거짓으로 설정합니다.
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

            // 핀치 줌 동작 중일 때는 UI 업데이트 최소화
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
        // 지도 크기 업데이트
        gridLayout.scaleX = scaleFactor
        gridLayout.scaleY = scaleFactor

        // 버튼 크기 업데이트 (여기서 gridLayout은 부모 레이아웃인 GridLayout을 가리킵니다)
        for (row in 0 until rowCount) {
            for (column in 0 until columnCount) {
                val cellButton = gridLayout.getChildAt(row * columnCount + column) as Button
                cellButton.layoutParams.width = (120 * scaleFactor).toInt()
                cellButton.layoutParams.height = (120 * scaleFactor).toInt()
                cellButton.setTextSize(TypedValue.COMPLEX_UNIT_PX, 30f * scaleFactor)
            }
        }

        // FrameLayout의 크기 조정
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
                    rowSpec = GridLayout.spec(row, 1f)  // 올바른 레이아웃 파라미터 설정
                    columnSpec = GridLayout.spec(column, 1f)  // 올바른 레이아웃 파라미터 설정
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
                    // 모든 지뢰가 아닌 버튼이 클릭되었을 때 게임 종료
                    finishDialog()
                }

                // 주변 셀의 깃발 개수가 실제 지뢰 개수 이상인 경우에만 주변 셀을 엽니다.
                val flagCount = getAdjacentFlagCount(row, column)
                if (flagCount >= adjacentMineCount && visited[row][column]) {
                    openAdjacentCells(row, column)
                }

                if (adjacentMineCount == 0) {
                    openAdjacentCells(row, column)
                }

                // 클릭한 셀을 방문했음을 표시
                visited[row][column] = true
            }
        } else {
            if (cellButton.text.isNullOrEmpty() && !cellButton.isSelected) {
                // 0이나 텍스트가 비어 있지 않은 셀에는 깃발을 설치하지 않음
                cellButton.isSelected = true
                val vectorDrawable = ResourcesCompat.getDrawable(resources, R.drawable.ic_flag, null)
                val shapeDrawable = ResourcesCompat.getDrawable(resources, R.drawable.cell_open, null)
                val layerDrawable = LayerDrawable(arrayOf(shapeDrawable, vectorDrawable))

                cellButton.background = layerDrawable
                updateMineLeftCount(-1) // 지뢰 갯수 갱신
            } else if (cellButton.text.isNullOrEmpty() && cellButton.isSelected) {
                // 이미 깃발이 설치된 셀에서 깃발을 다시 누르면 깃발 제거
                cellButton.isSelected = false
                cellButton.setBackgroundResource(R.drawable.cell_close)
                updateMineLeftCount(1) // 지뢰 갯수 갱신
                visited[row][column] = false
            }
        }
    }

    private fun openAdjacentCells(row: Int, column: Int) {
        // 재귀적으로 주변 셀을 열어주는 함수
        val queue = ArrayDeque<Pair<Int, Int>>()

        queue.add(Pair(row, column))
        visited[row][column] = true

        while (queue.isNotEmpty()) {
            val (currentRow, currentColumn) = queue.removeFirst()
            var adjacentFlagCount = 0 // 주변에 표시된 깃발 개수를 세기 위한 변수
            var adjacentMineCount = 0 // 주변에 있는 지뢰 개수를 세기 위한 변수

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
                                    // 모든 지뢰가 아닌 버튼이 클릭되었을 때 게임 종료
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
                visited[row][column] = false // 방문 배열 초기화
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
                    Toast.makeText(this@hardGameActivity, "Failed to road Ad", Toast.LENGTH_SHORT).show()
                    onAdFailed() // 광고 로드 실패 시, onAdFailed 실행
                }

                override fun onAdLoaded(rewardedAd: RewardedAd) {
                    rewardedAd.fullScreenContentCallback = object: FullScreenContentCallback() {
                        override fun onAdDismissedFullScreenContent() {
                            onAdFailed() // 광고를 완전히 보지 않고 나올 경우, onAdFailed 실행
                        }
                    }

                    // 광고 로드 성공, 광고를 표시하고 시청 완료 시 onAdFinished 실행
                    rewardedAd.show(this@hardGameActivity, object : OnUserEarnedRewardListener {
                        override fun onUserEarnedReward(rewardItem: RewardItem) {
                            // 광고 시청 완료, onAdFinished 실행
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

            // 모든 지뢰 표시
            for (row in 0 until rowCount) {
                for (column in 0 until columnCount) {
                    val cellButton = gridLayout.getChildAt(row * columnCount + column) as Button
                    if (minePositions[row][column]) {
                        // 이미 깃발로 표시된 셀은 처리하지 않고 다음 셀로 넘어감
                        if (!cellButton.isSelected) {
                            val vectorDrawable = ResourcesCompat.getDrawable(resources, R.drawable.ic_mine, null)
                            val shapeDrawable = ResourcesCompat.getDrawable(resources, R.drawable.cell_close, null)
                            val layerDrawable = LayerDrawable(arrayOf(shapeDrawable, vectorDrawable))

                            cellButton.background = layerDrawable
                        }
                    } else if (cellButton.isSelected) {
                        // 깃발이 표시된 셀은 처리하지 않고 다음 셀로 넘어감
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
                                    // 이미 깃발로 표시된 셀은 처리하지 않고 다음 셀로 넘어감
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

        // 게임 승리 시 지뢰 0으로 표시
        mineLeft.text = 0.toString()

        // 모든 지뢰 표시
        for (row in 0 until rowCount) {
            for (column in 0 until columnCount) {
                val cellButton = gridLayout.getChildAt(row * columnCount + column) as Button
                if (minePositions[row][column]) {
                    // 이미 깃발로 표시된 셀은 처리하지 않고 다음 셀로 넘어감
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
        return "Advanced"
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

    private suspend fun saveGameState() {
        val cellFlagged = Array(rowCount) { BooleanArray(columnCount) }
        val cellMineCountNearby = Array(rowCount) { IntArray(columnCount) }

        for (row in 0 until rowCount) {
            for (column in 0 until columnCount) {
                val cellButton = gridLayout.getChildAt(row * columnCount + column) as Button
                cellFlagged[row][column] = cellButton.isSelected
                if (!minePositions[row][column]) {
                    // Only non-mine cells have a valid mine count nearby.
                    cellMineCountNearby[row][column] = getAdjacentMineCount(row, column)
                }
            }
        }

        val minePositionsJson = Gson().toJson(minePositions)
        val cellOpenedJson = Gson().toJson(visited)
        val cellFlaggedJson = Gson().toJson(cellFlagged)
        val cellMineCountNearbyJson = Gson().toJson(cellMineCountNearby)

        val gameStatus = MineSweeperEntity(
            id = 1, // Assuming only one game is saved at a time.
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
            gameStatusDao?.get(1) // Assuming only one game is saved at a time.

        isGameLoaded = true

        if (gameStatus != null){
            chronometer.base=SystemClock.elapsedRealtime() - gameStatus.elapsedTime
            chronometer.start()

            viewMode("start")
            mineLeft.text = gameStatus.mineCount.toString()

            nonMineButtonClickedCount = gameStatus.nonMineButtonClickedCount
            nonMineButtonCount = gameStatus.nonMineButtonCount

            // Restore the arrays from JSON strings.
            minePositions= Gson().fromJson(gameStatus.minePositions, Array<BooleanArray>::class.java)
            visited= Gson().fromJson(gameStatus.cellOpened, Array<BooleanArray>::class.java)

            var flaggedCells= Gson().fromJson(gameStatus.cellFlagged, Array<BooleanArray>::class.java)

            var minesAroundCells= Gson().fromJson(gameStatus.cellMineCountNearby, Array<IntArray>::class.java)

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
            return true // 게임 상태가 성공적으로 로드되었음을 반환합니다.
        }
        return false // 저장된 게임 상태가 없음을 반환합니다.
    }

    private suspend fun deleteAllGameStates() {
        gameStatusDao?.deleteAll()
    }
}