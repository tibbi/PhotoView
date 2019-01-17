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
import android.graphics.drawable.Drawable
import android.net.Uri
import android.util.AttributeSet
import android.view.View
import android.widget.ImageView
import androidx.appcompat.widget.AppCompatImageView

class PhotoView @JvmOverloads constructor(context: Context, attr: AttributeSet? = null, defStyle: Int = 0) : AppCompatImageView(context, attr, defStyle) {
    private var attacher: PhotoViewAttacher? = null

    // adding a workaround to make sure Tap listener works with zooming disabled
    var isZoomable: Boolean
        get() = attacher!!.isZoomable()
        set(zoomable) = if (zoomable) {
            attacher!!.minimumScale = PhotoViewAttacher.DEFAULT_MIN_SCALE
            attacher!!.mediumScale = PhotoViewAttacher.DEFAULT_MID_SCALE
            attacher!!.maximumScale = PhotoViewAttacher.DEFAULT_MAX_SCALE
        } else {
            attacher!!.minimumScale = 1f
            attacher!!.mediumScale = 1f
            attacher!!.maximumScale = 1f
        }

    init {
        attacher = PhotoViewAttacher(this)
        super.setScaleType(ImageView.ScaleType.MATRIX)
    }

    override fun getScaleType() = attacher!!.getScaleType()

    override fun getImageMatrix() = attacher!!.getImageMatrix()

    override fun setOnClickListener(l: View.OnClickListener) {
        attacher!!.setOnClickListener(l)
    }

    override fun setScaleType(scaleType: ImageView.ScaleType) {
        attacher?.setScaleType(scaleType)
    }

    override fun setImageDrawable(drawable: Drawable?) {
        super.setImageDrawable(drawable)
        attacher?.update()
    }

    override fun setImageResource(resId: Int) {
        super.setImageResource(resId)
        attacher?.update()
    }

    override fun setImageURI(uri: Uri?) {
        super.setImageURI(uri)
        attacher?.update()
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
