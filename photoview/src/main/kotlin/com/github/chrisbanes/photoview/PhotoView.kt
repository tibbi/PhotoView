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
import android.graphics.drawable.Drawable
import android.net.Uri
import android.util.AttributeSet
import android.view.View
import android.widget.ImageView
import androidx.appcompat.widget.AppCompatImageView

/**
 * A zoomable ImageView. See [PhotoViewAttacher] for most of the details on how the zooming
 * is accomplished
 */
class PhotoView @JvmOverloads constructor(context: Context, attr: AttributeSet? = null, defStyle: Int = 0) : AppCompatImageView(context, attr, defStyle) {

    /**
     * Get the current [PhotoViewAttacher] for this view. Be wary of holding on to references
     * to this attacher, as it has a reference to this view, which, if a reference is held in the
     * wrong place, can cause memory leaks.
     *
     * @return the attacher.
     */
    var attacher: PhotoViewAttacher? = null
        private set

    // adding a workaround to make sure Tap listener works with zooming disabled
    var isZoomable: Boolean
        get() = attacher!!.isZoomable!!
        set(zoomable) = if (zoomable) {
            attacher!!.minimumScale = attacher!!.DEFAULT_MIN_SCALE
            attacher!!.mediumScale = attacher!!.DEFAULT_MID_SCALE
            attacher!!.maximumScale = attacher!!.DEFAULT_MAX_SCALE
        } else {
            attacher!!.minimumScale = 1f
            attacher!!.mediumScale = 1f
            attacher!!.maximumScale = 1f
        }

    init {
        attacher = PhotoViewAttacher(this)
        super.setScaleType(ImageView.ScaleType.MATRIX)
    }

    override fun getScaleType(): ImageView.ScaleType {
        return attacher!!.scaleType
    }

    override fun getImageMatrix(): Matrix {
        return attacher!!.imageMatrix
    }

    override fun setOnClickListener(l: View.OnClickListener) {
        attacher!!.setOnClickListener(l)
    }

    override fun setScaleType(scaleType: ImageView.ScaleType) {
        if (attacher != null) {
            attacher!!.scaleType = scaleType
        }
    }

    override fun setImageDrawable(drawable: Drawable?) {
        super.setImageDrawable(drawable)
        // setImageBitmap calls through to this method
        if (attacher != null) {
            attacher!!.update()
        }
    }

    override fun setImageResource(resId: Int) {
        super.setImageResource(resId)
        if (attacher != null) {
            attacher!!.update()
        }
    }

    override fun setImageURI(uri: Uri?) {
        super.setImageURI(uri)
        if (attacher != null) {
            attacher!!.update()
        }
    }

    override fun setFrame(l: Int, t: Int, r: Int, b: Int): Boolean {
        val changed = super.setFrame(l, t, r, b)
        if (changed) {
            attacher!!.update()
        }
        return changed
    }

    fun setAllowFingerDragZoom(allow: Boolean) {
        attacher!!.setAllowFingerDragZoom(allow)
    }
}
