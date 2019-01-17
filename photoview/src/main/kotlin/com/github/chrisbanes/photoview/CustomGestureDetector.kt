package com.github.chrisbanes.photoview

import android.content.Context
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.VelocityTracker
import android.view.ViewConfiguration

internal class CustomGestureDetector(context: Context, private val mListener: OnGestureListener) {
    private val INVALID_POINTER_ID = -1
    private var mActivePointerId = INVALID_POINTER_ID
    private var mActivePointerIndex = 0
    private val mDetector: ScaleGestureDetector

    private var mVelocityTracker: VelocityTracker? = null
    private var mLastTouchX = 0f
    private var mLastTouchY = 0f
    private val mTouchSlop: Float
    private val mMinimumVelocity: Float
    var isDragging = false

    fun getIsScaling() = mDetector.isInProgress

    init {
        val configuration = ViewConfiguration.get(context)
        mMinimumVelocity = configuration.scaledMinimumFlingVelocity.toFloat()
        mTouchSlop = configuration.scaledTouchSlop.toFloat()
        val scaleListener = object : ScaleGestureDetector.OnScaleGestureListener {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val scaleFactor = detector.scaleFactor

                if (java.lang.Float.isNaN(scaleFactor) || java.lang.Float.isInfinite(scaleFactor))
                    return false

                if (scaleFactor >= 0) {
                    mListener.onScale(scaleFactor, detector.focusX, detector.focusY)
                }
                return true
            }

            override fun onScaleBegin(detector: ScaleGestureDetector) = true

            override fun onScaleEnd(detector: ScaleGestureDetector) {}
        }
        mDetector = ScaleGestureDetector(context, scaleListener)
    }

    fun setAllowFingerDragZoom(allow: Boolean) {
        mDetector.isQuickScaleEnabled = allow
    }

    private fun getActiveX(ev: MotionEvent): Float {
        return try {
            ev.getX(mActivePointerIndex)
        } catch (e: Exception) {
            ev.x
        }
    }

    private fun getActiveY(ev: MotionEvent): Float {
        return try {
            ev.getY(mActivePointerIndex)
        } catch (e: Exception) {
            ev.y
        }
    }

    fun onTouchEvent(ev: MotionEvent): Boolean {
        return try {
            mDetector.onTouchEvent(ev)
            processTouchEvent(ev)
        } catch (e: IllegalArgumentException) {
            true
        }
    }

    private fun processTouchEvent(ev: MotionEvent): Boolean {
        val action = ev.action
        when (action and MotionEvent.ACTION_MASK) {
            MotionEvent.ACTION_DOWN -> {
                mActivePointerId = ev.getPointerId(0)

                mVelocityTracker = VelocityTracker.obtain()
                mVelocityTracker?.addMovement(ev)

                mLastTouchX = getActiveX(ev)
                mLastTouchY = getActiveY(ev)
                isDragging = false
            }
            MotionEvent.ACTION_MOVE -> {
                val x = getActiveX(ev)
                val y = getActiveY(ev)
                val dx = x - mLastTouchX
                val dy = y - mLastTouchY

                if (!isDragging) {
                    isDragging = Math.sqrt((dx * dx + dy * dy).toDouble()) >= mTouchSlop
                }

                if (isDragging) {
                    mListener.onDrag(dx, dy)
                    mLastTouchX = x
                    mLastTouchY = y
                    mVelocityTracker?.addMovement(ev)
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                mActivePointerId = INVALID_POINTER_ID
                mVelocityTracker?.recycle()
                mVelocityTracker = null
            }
            MotionEvent.ACTION_UP -> {
                mActivePointerId = INVALID_POINTER_ID
                if (isDragging) {
                    if (mVelocityTracker != null) {
                        mLastTouchX = getActiveX(ev)
                        mLastTouchY = getActiveY(ev)

                        mVelocityTracker!!.addMovement(ev)
                        mVelocityTracker!!.computeCurrentVelocity(1000)

                        val vX = mVelocityTracker!!.xVelocity
                        val vY = mVelocityTracker!!.yVelocity

                        if (Math.max(Math.abs(vX), Math.abs(vY)) >= mMinimumVelocity) {
                            mListener.onFling(mLastTouchX, mLastTouchY, -vX, -vY)
                        }
                    }
                }

                mVelocityTracker?.recycle()
                mVelocityTracker = null
            }
            MotionEvent.ACTION_POINTER_UP -> {
                val pointerIndex = ev.action and MotionEvent.ACTION_POINTER_INDEX_MASK shr MotionEvent.ACTION_POINTER_INDEX_SHIFT
                val pointerId = ev.getPointerId(pointerIndex)
                if (pointerId == mActivePointerId) {
                    val newPointerIndex = if (pointerIndex == 0) 1 else 0
                    mActivePointerId = ev.getPointerId(newPointerIndex)
                    mLastTouchX = ev.getX(newPointerIndex)
                    mLastTouchY = ev.getY(newPointerIndex)
                }
            }
        }

        mActivePointerIndex = ev.findPointerIndex(if (mActivePointerId != INVALID_POINTER_ID) {
            mActivePointerId
        } else {
            0
        })
        return true
    }
}
