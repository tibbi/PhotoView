/*
 Copyright 2011, 2012 Chris Banes.
 <p>
 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at
 <p>
 http://www.apache.org/licenses/LICENSE-2.0
 <p>
 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
 */
package com.github.chrisbanes.photoview

import android.content.Context
import android.graphics.Matrix
import android.graphics.Matrix.ScaleToFit
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView
import android.widget.ImageView.ScaleType
import android.widget.OverScroller

class PhotoViewAttacher(private val mImageView: ImageView) : View.OnTouchListener, View.OnLayoutChangeListener {
    companion object {
        private const val DEFAULT_ZOOM_DURATION = 200

        private const val HORIZONTAL_EDGE_NONE = -1
        private const val HORIZONTAL_EDGE_LEFT = 0
        private const val HORIZONTAL_EDGE_RIGHT = 1
        private const val HORIZONTAL_EDGE_BOTH = 2
        private const val VERTICAL_EDGE_NONE = -1
        private const val VERTICAL_EDGE_TOP = 0
        private const val VERTICAL_EDGE_BOTTOM = 1
        private const val VERTICAL_EDGE_BOTH = 2

        internal const val DEFAULT_MAX_SCALE = 3.0f
        internal const val DEFAULT_MID_SCALE = 1.75f
        internal const val DEFAULT_MIN_SCALE = 1.0f
    }

    private val mInterpolator = AccelerateDecelerateInterpolator()
    var minimumScale = DEFAULT_MIN_SCALE
    var mediumScale = DEFAULT_MID_SCALE
    var maximumScale = DEFAULT_MAX_SCALE

    private var mBlockParentIntercept = false

    private var mGestureDetector: GestureDetector?
    private val mScaleDragDetector: CustomGestureDetector?

    private val mBaseMatrix = Matrix()
    private val mDrawMatrix = Matrix()
    private val mSuppMatrix = Matrix()
    private val mDisplayRect = RectF()
    private val mMatrixValues = FloatArray(9)

    private var mOnClickListener: View.OnClickListener? = null
    private var mOnGestureListener: OnGestureListener? = null

    private var mCurrentFlingRunnable: FlingRunnable? = null
    private var mHorizontalScrollEdge = HORIZONTAL_EDGE_BOTH
    private var mVerticalScrollEdge = VERTICAL_EDGE_BOTH
    private val mBaseRotation: Float

    @get:Deprecated("")
    val isZoomEnabled = true
    private var mScaleType = ScaleType.FIT_CENTER

    init {
        mImageView.setOnTouchListener(this)
        mImageView.addOnLayoutChangeListener(this)
        mBaseRotation = 0f
        initGestureListener()
        mScaleDragDetector = CustomGestureDetector(mImageView.context, mOnGestureListener!!)
        mGestureDetector = GestureDetector(mImageView.context, object : GestureDetector.SimpleOnGestureListener() {
        })

        mGestureDetector!!.setOnDoubleTapListener(object : GestureDetector.OnDoubleTapListener {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                if (mOnClickListener != null) {
                    mOnClickListener!!.onClick(mImageView)
                }
                return false
            }

            override fun onDoubleTap(ev: MotionEvent): Boolean {
                try {
                    val scale = getScale()
                    val x = ev.x
                    val y = ev.y
                    if (scale < mediumScale) {
                        setScale(mediumScale, x, y, true)
                    } else if (scale >= mediumScale && scale < maximumScale) {
                        setScale(maximumScale, x, y, true)
                    } else {
                        setScale(minimumScale, x, y, true)
                    }
                } catch (e: ArrayIndexOutOfBoundsException) {
                }

                return true
            }

            override fun onDoubleTapEvent(e: MotionEvent): Boolean {
                return false
            }
        })
    }

    private fun initGestureListener() {
        mOnGestureListener = object : OnGestureListener {
            override fun onDrag(dx: Float, dy: Float) {
                if (mScaleDragDetector!!.isScaling) {
                    return
                }

                mSuppMatrix.postTranslate(dx, dy)
                checkAndDisplayMatrix()

                val parent = mImageView.parent
                if (!mScaleDragDetector!!.isScaling && !mBlockParentIntercept) {
                    if (mHorizontalScrollEdge == HORIZONTAL_EDGE_BOTH
                            || mHorizontalScrollEdge == HORIZONTAL_EDGE_LEFT && dx >= 1f
                            || mHorizontalScrollEdge == HORIZONTAL_EDGE_RIGHT && dx <= -1f
                            || mVerticalScrollEdge == VERTICAL_EDGE_TOP && dy >= 1f
                            || mVerticalScrollEdge == VERTICAL_EDGE_BOTTOM && dy <= -1f) {
                        parent?.requestDisallowInterceptTouchEvent(false)
                    }
                } else {
                    parent?.requestDisallowInterceptTouchEvent(true)
                }
            }

            override fun onFling(startX: Float, startY: Float, velocityX: Float, velocityY: Float) {
                mCurrentFlingRunnable = FlingRunnable(mImageView.context)
                mCurrentFlingRunnable!!.fling(getImageViewWidth(mImageView), getImageViewHeight(mImageView), velocityX.toInt(), velocityY.toInt())
                mImageView.post(mCurrentFlingRunnable)
            }

            override fun onScale(scaleFactor: Float, focusX: Float, focusY: Float) {
                if (getScale() < maximumScale || scaleFactor < 1f) {
                    mSuppMatrix.postScale(scaleFactor, scaleFactor, focusX, focusY)
                    checkAndDisplayMatrix()
                }
            }
        }
    }

    fun setAllowFingerDragZoom(allow: Boolean) {
        mScaleDragDetector!!.setAllowFingerDragZoom(allow)
    }

    private fun getDisplayRect(): RectF? {
        checkMatrixBounds()
        return getDisplayRect(getDrawMatrix())
    }

    private fun setRotationBy(degrees: Float) {
        mSuppMatrix.postRotate(degrees % 360)
        checkAndDisplayMatrix()
    }

    private fun getScale(): Float {
        return Math.sqrt((Math.pow(getValue(mSuppMatrix, Matrix.MSCALE_X).toDouble(), 2.0).toFloat() + Math.pow(getValue(mSuppMatrix, Matrix.MSKEW_Y).toDouble(), 2.0).toFloat()).toDouble()).toFloat()
    }

    fun getScaleType(): ScaleType {
        return mScaleType
    }

    override fun onLayoutChange(v: View, left: Int, top: Int, right: Int, bottom: Int, oldLeft: Int, oldTop: Int, oldRight: Int, oldBottom: Int) {
        if (left != oldLeft || top != oldTop || right != oldRight || bottom != oldBottom) {
            updateBaseMatrix(mImageView.drawable)
        }
    }

    override fun onTouch(v: View, ev: MotionEvent): Boolean {
        var handled = false
        if (isZoomEnabled && (v as ImageView).drawable != null) {
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> {
                    val parent = v.getParent()
                    parent?.requestDisallowInterceptTouchEvent(true)
                    cancelFling()
                }
                MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_UP -> if (getScale() < minimumScale) {
                    val rect = getDisplayRect()
                    if (rect != null) {
                        v.post(AnimatedZoomRunnable(getScale(), minimumScale, rect.centerX(), rect.centerY()))
                        handled = true
                    }
                } else if (getScale() > maximumScale) {
                    val rect = getDisplayRect()
                    if (rect != null) {
                        v.post(AnimatedZoomRunnable(getScale(), maximumScale, rect.centerX(), rect.centerY()))
                        handled = true
                    }
                }
            }

            if (mScaleDragDetector != null) {
                val wasScaling = mScaleDragDetector.isScaling
                val wasDragging = mScaleDragDetector.isDragging
                handled = mScaleDragDetector.onTouchEvent(ev)
                val didntScale = !wasScaling && !mScaleDragDetector.isScaling
                val didntDrag = !wasDragging && !mScaleDragDetector.isDragging
                mBlockParentIntercept = didntScale && didntDrag
            }

            if (mGestureDetector != null && mGestureDetector!!.onTouchEvent(ev)) {
                handled = true
            }

        }
        return handled
    }

    fun setOnClickListener(listener: View.OnClickListener) {
        mOnClickListener = listener
    }

    private fun setScale(scale: Float, focalX: Float, focalY: Float, animate: Boolean) {
        if (scale < minimumScale || scale > maximumScale) {
            throw IllegalArgumentException("Scale must be within the range of minScale and maxScale")
        }

        if (animate) {
            mImageView.post(AnimatedZoomRunnable(getScale(), scale, focalX, focalY))
        } else {
            mSuppMatrix.setScale(scale, scale, focalX, focalY)
            checkAndDisplayMatrix()
        }
    }

    fun setScaleType(scaleType: ScaleType) {
        mScaleType = scaleType
        update()
    }

    fun isZoomable(): Boolean {
        return isZoomEnabled
    }

    fun update() {
        if (isZoomEnabled) {
            updateBaseMatrix(mImageView.drawable)
        } else {
            resetMatrix()
        }
    }

    private fun getDrawMatrix(): Matrix {
        mDrawMatrix.set(mBaseMatrix)
        mDrawMatrix.postConcat(mSuppMatrix)
        return mDrawMatrix
    }

    fun getImageMatrix(): Matrix {
        return mDrawMatrix
    }

    private fun getValue(matrix: Matrix, whichValue: Int): Float {
        matrix.getValues(mMatrixValues)
        return mMatrixValues[whichValue]
    }

    private fun resetMatrix() {
        mSuppMatrix.reset()
        setRotationBy(mBaseRotation)
        setImageViewMatrix(getDrawMatrix())
        checkMatrixBounds()
    }

    private fun setImageViewMatrix(matrix: Matrix) {
        mImageView.imageMatrix = matrix
    }

    private fun checkAndDisplayMatrix() {
        if (checkMatrixBounds()) {
            setImageViewMatrix(getDrawMatrix())
        }
    }

    private fun getDisplayRect(matrix: Matrix): RectF? {
        val drawable = mImageView.drawable
        if (drawable != null) {
            mDisplayRect.set(0f, 0f, drawable.intrinsicWidth.toFloat(), drawable.intrinsicHeight.toFloat())
            matrix.mapRect(mDisplayRect)
            return mDisplayRect
        }
        return null
    }

    private fun updateBaseMatrix(drawable: Drawable?) {
        if (drawable == null) {
            return
        }

        val viewWidth = getImageViewWidth(mImageView).toFloat()
        val viewHeight = getImageViewHeight(mImageView).toFloat()
        val drawableWidth = drawable.intrinsicWidth
        val drawableHeight = drawable.intrinsicHeight
        mBaseMatrix.reset()
        var mTempSrc = RectF(0f, 0f, drawableWidth.toFloat(), drawableHeight.toFloat())
        val mTempDst = RectF(0f, 0f, viewWidth, viewHeight)
        if (mBaseRotation.toInt() % 180 != 0) {
            mTempSrc = RectF(0f, 0f, drawableHeight.toFloat(), drawableWidth.toFloat())
        }
        mBaseMatrix.setRectToRect(mTempSrc, mTempDst, ScaleToFit.CENTER)
        resetMatrix()
    }

    private fun checkMatrixBounds(): Boolean {
        val rect = getDisplayRect(getDrawMatrix()) ?: return false

        val height = rect.height()
        val width = rect.width()
        var deltaX = 0f
        var deltaY = 0f
        val viewHeight = getImageViewHeight(mImageView)
        if (height <= viewHeight) {
            deltaY = (viewHeight - height) / 2 - rect.top
            mVerticalScrollEdge = VERTICAL_EDGE_BOTH
        } else if (rect.top > 0) {
            mVerticalScrollEdge = VERTICAL_EDGE_TOP
            deltaY = -rect.top
        } else if (rect.bottom < viewHeight) {
            mVerticalScrollEdge = VERTICAL_EDGE_BOTTOM
            deltaY = viewHeight - rect.bottom
        } else {
            mVerticalScrollEdge = VERTICAL_EDGE_NONE
        }

        val viewWidth = getImageViewWidth(mImageView)
        if (width <= viewWidth) {
            deltaX = (viewWidth - width) / 2 - rect.left
            mHorizontalScrollEdge = HORIZONTAL_EDGE_BOTH
        } else if (rect.left > 0) {
            mHorizontalScrollEdge = HORIZONTAL_EDGE_LEFT
            deltaX = -rect.left
        } else if (rect.right < viewWidth) {
            deltaX = viewWidth - rect.right
            mHorizontalScrollEdge = HORIZONTAL_EDGE_RIGHT
        } else {
            mHorizontalScrollEdge = HORIZONTAL_EDGE_NONE
        }

        mSuppMatrix.postTranslate(deltaX, deltaY)
        return true
    }

    private fun getImageViewWidth(imageView: ImageView) = imageView.width - imageView.paddingLeft - imageView.paddingRight

    private fun getImageViewHeight(imageView: ImageView) = imageView.height - imageView.paddingTop - imageView.paddingBottom

    private fun cancelFling() {
        if (mCurrentFlingRunnable != null) {
            mCurrentFlingRunnable!!.cancelFling()
            mCurrentFlingRunnable = null
        }
    }

    private inner class AnimatedZoomRunnable internal constructor(private val mZoomStart: Float, private val mZoomEnd: Float, private val mFocalX: Float, private val mFocalY: Float) : Runnable {
        private val mStartTime = System.currentTimeMillis()

        override fun run() {
            val t = interpolate()
            val scale = mZoomStart + t * (mZoomEnd - mZoomStart)
            val deltaScale = scale / getScale()
            mOnGestureListener!!.onScale(deltaScale, mFocalX, mFocalY)
            if (t < 1f) {
                mImageView.postOnAnimation(this)
            }
        }

        private fun interpolate(): Float {
            var t = 1f * (System.currentTimeMillis() - mStartTime) / DEFAULT_ZOOM_DURATION
            t = Math.min(1f, t)
            t = mInterpolator.getInterpolation(t)
            return t
        }
    }

    private inner class FlingRunnable internal constructor(context: Context) : Runnable {
        private val mScroller = OverScroller(context)
        private var mCurrentX = 0
        private var mCurrentY = 0

        internal fun cancelFling() {
            mScroller.forceFinished(true)
        }

        internal fun fling(viewWidth: Int, viewHeight: Int, velocityX: Int, velocityY: Int) {
            val rect = getDisplayRect() ?: return
            val startX = Math.round(-rect.left)
            val minX: Int
            val maxX: Int
            val minY: Int
            val maxY: Int
            if (viewWidth < rect.width()) {
                minX = 0
                maxX = Math.round(rect.width() - viewWidth)
            } else {
                maxX = startX
                minX = maxX
            }
            val startY = Math.round(-rect.top)
            if (viewHeight < rect.height()) {
                minY = 0
                maxY = Math.round(rect.height() - viewHeight)
            } else {
                maxY = startY
                minY = maxY
            }
            mCurrentX = startX
            mCurrentY = startY
            if (startX != maxX || startY != maxY) {
                mScroller.fling(startX, startY, velocityX, velocityY, minX, maxX, minY, maxY, 0, 0)
            }
        }

        override fun run() {
            if (mScroller.isFinished) {
                return
            }

            if (mScroller.computeScrollOffset()) {
                val newX = mScroller.currX
                val newY = mScroller.currY
                mSuppMatrix.postTranslate((mCurrentX - newX).toFloat(), (mCurrentY - newY).toFloat())
                checkAndDisplayMatrix()
                mCurrentX = newX
                mCurrentY = newY
                mImageView.postOnAnimation(this)
            }
        }
    }
}
