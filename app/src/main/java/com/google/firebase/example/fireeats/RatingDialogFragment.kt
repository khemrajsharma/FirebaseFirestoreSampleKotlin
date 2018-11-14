/**
 * Copyright 2017 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.firebase.example.fireeats

import android.content.Context
import android.os.Bundle
import android.support.v4.app.DialogFragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.example.fireeats.model.Rating

import butterknife.BindView
import butterknife.ButterKnife
import butterknife.OnClick
import me.zhanghai.android.materialratingbar.MaterialRatingBar

/**
 * Dialog Fragment containing rating form.
 */
class RatingDialogFragment : DialogFragment() {

    @BindView(R.id.restaurant_form_rating)
    lateinit var mRatingBar: MaterialRatingBar

    @BindView(R.id.restaurant_form_text)
    lateinit var mRatingText: EditText

    private var mRatingListener: RatingListener? = null

    internal interface RatingListener {

        fun onRating(rating: Rating)

    }

    override fun onCreateView(inflater: LayoutInflater,
                              container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        val v = inflater.inflate(R.layout.dialog_rating, container, false)
        ButterKnife.bind(this, v)

        return v
    }

    override fun onAttach(context: Context?) {
        super.onAttach(context)

        if (context is RatingListener) {
            mRatingListener = context
        }
    }

    override fun onResume() {
        super.onResume()
        dialog.window!!.setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT)

    }

    @OnClick(R.id.restaurant_form_button)
    fun onSubmitClicked(view: View) {
        val rating = Rating(
                FirebaseAuth.getInstance().currentUser!!,
                mRatingBar.rating.toDouble(),
                mRatingText.text.toString())

        if (mRatingListener != null) {
            mRatingListener!!.onRating(rating)
        }

        dismiss()
    }

    @OnClick(R.id.restaurant_form_cancel)
    fun onCancelClicked(view: View) {
        dismiss()
    }

    companion object {

        val TAG = "RatingDialog"
    }
}
