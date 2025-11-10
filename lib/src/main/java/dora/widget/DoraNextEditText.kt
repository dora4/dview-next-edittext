package dora.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import android.text.InputFilter
import android.text.InputFilter.LengthFilter
import android.text.InputType
import android.text.TextUtils
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputConnectionWrapper
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.widget.AppCompatEditText
import dora.widget.nextedittext.R
import java.util.Timer
import java.util.TimerTask

class DoraNextEditText : AppCompatEditText {

    private var itemWidth = DIGIT_ITEM_WIDTH

    /**
     * 数字密码位数。
     */
    private var digitSize = 6
    private var borderWidth = BORDER_WIDTH
    private var outsideBorderWidth = 1
    private var borderColor = Color.GRAY
    private var cursorWidth = CURSOR_WIDTH
    private var cursorHeight = 30
    private var cursorColor = -0xcccccd
    private var textColor = -0xcccccd
    private var textSize = 40
    private var textWidth = 0f
    private var autoFocus = true
    private var inputMethodManager: InputMethodManager? = null
    private var isCursorShowing = true
    private val cursorFlashTime = 400
    private var paint: Paint? = null
    private var rectF: RectF? = null
    private var rectArr: Array<Rect?>? = null
    private var cursorPosArr: Array<Rect?>? = null
    private var textXPosArr = IntArray(digitSize)
    private var textYPos = 0
    private var digitArr: Array<String?>? = null

    /**
     * 光标当前显示在第几个方格内。
     */
    private var index = 0

    /**
     * 已输入数字的个数。
     */
    private var count = 0
    private val timer: Timer = Timer()
    private val timerTask: TimerTask = object : TimerTask() {
        override fun run() {
            isCursorShowing = !isCursorShowing
            postInvalidate()
        }
    }
    private var onCompleteListener: OnCompleteListener? = null
    private var drawCipherText = false

    interface OnCompleteListener {
        fun completeInput(text: String?)
        fun onInput(count: Int)
    }

    fun setOnCompleteListener(l: OnCompleteListener?) {
        onCompleteListener = l
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        init(context, attrs)
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    ) {
        init(context, attrs)
    }

    private fun init(context: Context, attrs: AttributeSet?) {
        val a = context.obtainStyledAttributes(attrs, R.styleable.DoraNextEditText)
        itemWidth =
            a.getDimensionPixelOffset(R.styleable.DoraNextEditText_dview_net_itemWidth, itemWidth)
        borderWidth = a.getDimensionPixelOffset(
            R.styleable.DoraNextEditText_dview_net_borderWidth,
            borderWidth
        )
        outsideBorderWidth = borderWidth * 2
        borderColor = a.getColor(R.styleable.DoraNextEditText_dview_net_borderColor, borderColor)
        digitSize = a.getInt(R.styleable.DoraNextEditText_dview_net_digitSize, digitSize)
        cursorWidth =
            a.getDimensionPixelSize(R.styleable.DoraNextEditText_dview_net_cursorWidth, cursorWidth)
        cursorColor = a.getColor(R.styleable.DoraNextEditText_dview_net_cursorColor, cursorColor)
        textColor = a.getColor(R.styleable.DoraNextEditText_dview_net_textColor, textColor)
        textSize =
            a.getDimensionPixelOffset(R.styleable.DoraNextEditText_dview_net_textSize, textSize)
        autoFocus = a.getBoolean(R.styleable.DoraNextEditText_dview_net_autoFocus, autoFocus)
        drawCipherText =
            a.getBoolean(R.styleable.DoraNextEditText_dview_net_drawCipherText, drawCipherText)
        a.recycle()
        isCursorVisible = false
        inputType = EditorInfo.TYPE_CLASS_NUMBER or EditorInfo.TYPE_NUMBER_VARIATION_PASSWORD
        setSingleLine()
        setLines(1)
        filters = arrayOf<InputFilter>(LengthFilter(0))
        isLongClickable = false
        setTextIsSelectable(false)
        background = BitmapDrawable()
        rectArr = arrayOfNulls(digitSize - 1)
        cursorPosArr = arrayOfNulls(digitSize)
        textXPosArr = IntArray(digitSize)
        paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint!!.textSize = textSize.toFloat()
        textWidth = paint!!.measureText(CIPHER_TEXT)
        inputMethodManager =
            context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        digitArr = arrayOfNulls(digitSize)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        var width = MeasureSpec.getSize(widthMeasureSpec)
        var height = itemWidth + borderWidth * 2
        val requireWidth = digitSize * itemWidth + borderWidth * (digitSize + 1)
        if (widthMode == MeasureSpec.AT_MOST) {
            if (width < requireWidth) {
                itemWidth = width - borderWidth * (digitSize + 1)
                height = itemWidth + borderWidth * 2
            } else {
                width = requireWidth
            }
        } else if (widthMode == MeasureSpec.EXACTLY) {
            itemWidth = (width - borderWidth * (digitSize + 1)) / digitSize
            height = itemWidth + borderWidth * 2
        } else {
            width = requireWidth
        }
        setMeasuredDimension(width, height)
        if (rectF == null || width.toFloat() != rectF!!.right) {
            cursorHeight = itemWidth * 2 / 4
            rectF = RectF(0f, 0f, width.toFloat(), height.toFloat())
            var left = 0
            for (i in rectArr!!.indices) {
                left = (itemWidth + borderWidth) * (i + 1)
                rectArr!![i] = Rect(left, borderWidth, left, itemWidth + borderWidth)
            }
            for (i in textXPosArr.indices) {
                textXPosArr[i] = (borderWidth + itemWidth) * i + borderWidth + itemWidth / 2
                cursorPosArr!![i] = Rect(
                    ((borderWidth + itemWidth) * i + borderWidth + itemWidth / 2),
                    (itemWidth - cursorHeight) / 2,
                    ((borderWidth + itemWidth) * i + borderWidth + itemWidth / 2),
                    itemWidth / 2 + cursorHeight / 2
                )
            }
            textYPos = itemWidth / 2
        }
    }

    override fun onDraw(canvas: Canvas) {
        drawRect(canvas)
        drawCipherText(canvas)
        drawCursor(canvas)
    }

    private fun drawRect(canvas: Canvas) {
        paint!!.color = borderColor
        paint!!.strokeWidth = outsideBorderWidth.toFloat()
        paint!!.style = Paint.Style.STROKE
        canvas.drawRect(rectF!!, paint!!)
        paint!!.strokeWidth = borderWidth.toFloat()
        for (i in rectArr!!.indices) {
            paint!!.color = borderColor
            canvas.drawLine(
                rectArr!![i]!!.left.toFloat(),
                rectArr!![i]!!.top.toFloat(),
                rectArr!![i]!!.right.toFloat(),
                rectArr!![i]!!.bottom.toFloat(),
                paint!!
            )
        }
    }

    private fun drawCipherText(canvas: Canvas) {
        paint!!.color = textColor
        paint!!.textSize = textSize.toFloat()
        paint!!.textAlign = Paint.Align.CENTER
        paint!!.style = Paint.Style.FILL
        val fontMetrics = paint!!.fontMetrics
        val baselineY = textYPos + (fontMetrics.bottom - fontMetrics.top) / 2 - fontMetrics.bottom
        for (i in digitArr!!.indices) {
            if (!TextUtils.isEmpty(digitArr!![i])) {
                if (drawCipherText) {
                    canvas.drawText(CIPHER_TEXT, textXPosArr[i].toFloat(), baselineY, paint!!)
                } else {
                    canvas.drawText(digitArr!![i]!!, textXPosArr[i].toFloat(), baselineY, paint!!)
                }
            }
        }
    }

    private fun drawCursor(canvas: Canvas) {
        paint!!.color = cursorColor
        paint!!.strokeWidth = cursorWidth.toFloat()
        paint!!.style = Paint.Style.FILL
        if (!isCursorShowing && hasFocus()) {
            val hasDigit = digitArr!![index] != null
            if (count != digitSize) {
                canvas.drawLine(
                    (if (hasDigit) cursorPosArr?.get(index)!!.left else cursorPosArr?.get(index)!!
                        .centerX()).toFloat(),
                    cursorPosArr!![index]!!.top.toFloat(),
                    (
                            if (hasDigit) cursorPosArr!![index]!!.right else cursorPosArr!![index]!!.centerX()).toFloat(),
                    cursorPosArr!![index]!!.bottom.toFloat(), paint!!
                )
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            index = (event.x / (itemWidth + borderWidth)).toInt()
            invalidate()
            if (inputMethodManager != null && !inputMethodManager!!.isActive(this)) {
                inputMethodManager!!.showSoftInput(this, InputMethodManager.SHOW_FORCED)
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isFocusableInTouchMode = true
        if (autoFocus) {
            requestFocus()
            postDelayed({
                if (inputMethodManager != null) {
                    inputMethodManager!!.showSoftInput(
                        this@DoraNextEditText,
                        InputMethodManager.SHOW_FORCED
                    )
                }
            }, 100)
        }
        timer.scheduleAtFixedRate(timerTask, 100, cursorFlashTime.toLong())
    }

    override fun onDetachedFromWindow() {
        if (inputMethodManager != null && hasFocus() && inputMethodManager!!.isActive(this)) {
            inputMethodManager!!.hideSoftInputFromWindow(
                windowToken,
                InputMethodManager.HIDE_IMPLICIT_ONLY
            )
        }
        super.onDetachedFromWindow()
        isFocusableInTouchMode = false
        timer.cancel()
        onCompleteListener = null
    }

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
        outAttrs.inputType = InputType.TYPE_CLASS_NUMBER
        return DigitInputConnection(super.onCreateInputConnection(outAttrs), false)
    }

    private inner class DigitInputConnection
    /**
     * Initializes a wrapper.
     *
     *
     * **Caveat:** Although the system can accept `(InputConnection) null` in some
     * places, you cannot emulate such a behavior by non-null [InputConnectionWrapper] that
     * has `null` in `target`.
     *
     * @param target  the [InputConnection] to be proxied.
     * @param mutable set `true` to protect this object from being reconfigured to target
     * another [InputConnection].  Note that this is ignored while the target is `null`.
     */
        (target: InputConnection?, mutable: Boolean) : InputConnectionWrapper(target, mutable) {
        private fun dealKeyEvent(keyCode: Int, event: KeyEvent): Boolean {
            if (event.action == KeyEvent.ACTION_DOWN) {
                if (keyCode == KeyEvent.KEYCODE_DEL) {
                    delete()
                    return true
                } else if (keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_9) {
                    if (digitArr!![index] == null) {
                        digitArr!![index] = (keyCode - 7).toString()
                        count++
                        notifyInputChanged()
                    }
                    if (index != digitSize - 1) {
                        index++
                    }
                    invalidate()
                }
            }
            return false
        }

        override fun commitText(text: CharSequence, newCursorPosition: Int): Boolean {
            if (text.length == 1) {
                val c = text[0]
                if (c.code in 48..57) {
                    if (digitArr!![index] == null) {
                        digitArr!![index] = c.toString()
                        count++
                        notifyInputChanged()
                    }
                    if (index != digitSize - 1) {
                        index++
                    }
                    invalidate()
                    return true
                }
            }
            return true
        }

        override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
            delete()
            return true
        }

        private fun delete() {
            if (digitArr!![index] == null) {
                if (index != 0) {
                    index--
                }
            }
            count = if (count == 0) 0 else --count
            digitArr!![index] = null
            invalidate()
            notifyInputChanged()
        }

        override fun sendKeyEvent(event: KeyEvent): Boolean {
            return if (dealKeyEvent(event.keyCode, event)) {
                true
            } else super.sendKeyEvent(event)
        }
    }

    private fun notifyInputChanged() {
        if (count == digitSize) {
            onCompleteListener?.completeInput(getDigitText())
        } else {
            onCompleteListener?.onInput(count)
        }
    }

    fun getDigitText(): String? {
        if (digitArr == null) {
            return null
        }
        val sb = StringBuilder()
        for (s in digitArr!!) {
            if (s == null) {
                return null
            } else {
                sb.append(s)
            }
        }
        return sb.toString()
    }

    fun reset() {
        if (digitArr == null || count == 0 && index == 0) {
            return
        }
        postDelayed({
            val size = digitArr!!.size
            for (i in 0 until size) {
                digitArr!![i] = null
            }
            index = 0
            count = 0
            invalidate()
        }, 10)
    }

    companion object {
        /**
         * 默认方格宽度。
         */
        private const val DIGIT_ITEM_WIDTH = 40

        /**
         * 默认边框宽度。
         */
        private const val BORDER_WIDTH = 1

        /**
         * 默认光标宽度。
         */
        private const val CURSOR_WIDTH = 2

        /**
         * 显示的*。
         */
        private const val CIPHER_TEXT = "·"
    }
}