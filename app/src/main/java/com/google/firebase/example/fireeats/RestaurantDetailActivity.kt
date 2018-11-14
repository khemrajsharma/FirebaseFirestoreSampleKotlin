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
import android.support.design.widget.Snackbar
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
import android.widget.TextView
import butterknife.BindView
import butterknife.ButterKnife
import butterknife.OnClick
import com.bumptech.glide.Glide
import com.google.android.gms.tasks.Task
import com.google.firebase.example.fireeats.adapter.RatingAdapter
import com.google.firebase.example.fireeats.model.Rating
import com.google.firebase.example.fireeats.model.Restaurant
import com.google.firebase.example.fireeats.util.FirestoreClient
import com.google.firebase.example.fireeats.util.RestaurantUtil
import com.google.firebase.firestore.*
import me.zhanghai.android.materialratingbar.MaterialRatingBar

class RestaurantDetailActivity : AppCompatActivity(), EventListener<DocumentSnapshot>, RatingDialogFragment.RatingListener {

    @BindView(R.id.restaurant_image)
    lateinit var mImageView: ImageView

    @BindView(R.id.restaurant_name)
    lateinit var mNameView: TextView

    @BindView(R.id.restaurant_rating)
    lateinit var mRatingIndicator: MaterialRatingBar

    @BindView(R.id.restaurant_num_ratings)
    lateinit var mNumRatingsView: TextView

    @BindView(R.id.restaurant_city)
    lateinit var mCityView: TextView

    @BindView(R.id.restaurant_category)
    lateinit var mCategoryView: TextView

    @BindView(R.id.restaurant_price)
    lateinit var mPriceView: TextView

    @BindView(R.id.view_empty_ratings)
    lateinit var mEmptyView: ViewGroup

    @BindView(R.id.recycler_ratings)
    lateinit var mRatingsRecycler: RecyclerView

    private var mRatingDialog: RatingDialogFragment? = null

    private var mRestaurantRef: DocumentReference? = null
    private var mRestaurantRegistration: ListenerRegistration? = null

    private var mRatingAdapter: RatingAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_restaurant_detail)
        ButterKnife.bind(this)

        // Get restaurant ID from extras
        val restaurantId = intent.extras!!.getString(KEY_RESTAURANT_ID)
                ?: throw IllegalArgumentException("Must pass extra $KEY_RESTAURANT_ID")

        // Get reference to the restaurant
        mRestaurantRef = FirestoreClient.getRestaurants().document(restaurantId)

        // Get ratings
        val ratingsQuery = mRestaurantRef!!
                .collection(Rating.FIELD_RATING)
                .orderBy(Rating.FIELD_TIMESTAMP, Query.Direction.DESCENDING)
                .limit(MainActivity.LIMIT)

        // RecyclerView
        mRatingAdapter = object : RatingAdapter(ratingsQuery) {
            override fun onDataChanged() {
                if (itemCount == 0) {
                    mRatingsRecycler.visibility = View.GONE
                    mEmptyView.visibility = View.VISIBLE
                } else {
                    mRatingsRecycler.visibility = View.VISIBLE
                    mEmptyView.visibility = View.GONE
                }
            }
        }

        mRatingsRecycler.layoutManager = LinearLayoutManager(this)
        mRatingsRecycler.adapter = mRatingAdapter

        mRatingDialog = RatingDialogFragment()
    }

    public override fun onStart() {
        super.onStart()

        mRatingAdapter!!.startListening()
        mRestaurantRegistration = mRestaurantRef!!.addSnapshotListener(this)
    }

    public override fun onStop() {
        super.onStop()

        mRatingAdapter!!.stopListening()

        if (mRestaurantRegistration != null) {
            mRestaurantRegistration!!.remove()
            mRestaurantRegistration = null
        }
    }

    private fun addRating(restaurantRef: DocumentReference,
                          rating: Rating): Task<Void> {
        // Create reference for new rating, for use inside the transaction
        val ratingRef: DocumentReference = FirestoreClient.getRatingCollection(restaurantRef).document()

        // In a transaction, add the new rating and update the aggregate totals
        return FirebaseFirestore.getInstance().runTransaction { transaction ->
            val restaurant = transaction.get(restaurantRef).toObject(Restaurant::class.java)

            // Compute new number of ratings
            val newNumRatings = restaurant!!.numRatings + 1

            // Compute new average rating
            val oldRatingTotal = restaurant.avgRating * restaurant.numRatings
            val newAvgRating = (oldRatingTotal + rating.rating) / newNumRatings

            // Set new restaurant info
            restaurant.numRatings = newNumRatings
            restaurant.avgRating = newAvgRating

            // Commit to Firestore
            transaction.set(restaurantRef, restaurant)
            transaction.set(ratingRef, rating)

            null
        }
    }

    /**
     * Listener for the Restaurant document ([.mRestaurantRef]).
     */
    override fun onEvent(snapshot: DocumentSnapshot?, e: FirebaseFirestoreException?) {
        if (e != null) {
            Log.w(TAG, "restaurant:onEvent", e)
            return
        }

        onRestaurantLoaded(snapshot!!.toObject(Restaurant::class.java)!!)
    }

    private fun onRestaurantLoaded(restaurant: Restaurant) {
        mNameView.text = restaurant.name
        mRatingIndicator.rating = restaurant.avgRating.toFloat()
        mNumRatingsView.text = getString(R.string.fmt_num_ratings, restaurant.numRatings)
        mCityView.text = restaurant.city
        mCategoryView.text = restaurant.category
        mPriceView.text = RestaurantUtil.getPriceString(restaurant)

        // Background image
        Glide.with(mImageView.context)
                .load(restaurant.photo)
                .into(mImageView)
    }

    @OnClick(R.id.restaurant_button_back)
    fun onBackArrowClicked(view: View) {
        onBackPressed()
    }

    @OnClick(R.id.fab_show_rating_dialog)
    fun onAddRatingClicked(view: View) {
        mRatingDialog!!.show(supportFragmentManager, RatingDialogFragment.TAG)
    }

    override fun onRating(rating: Rating) {
        // In a transaction, add the new rating and update the aggregate totals
        addRating(mRestaurantRef!!, rating)
                .addOnSuccessListener(this) {
                    Log.d(TAG, "Rating added")

                    // Hide keyboard and scroll to top
                    hideKeyboard()
                    mRatingsRecycler.smoothScrollToPosition(0)
                }
                .addOnFailureListener(this) { e ->
                    Log.w(TAG, "Add rating failed", e)

                    // Show failure message and hide keyboard
                    hideKeyboard()
                    Snackbar.make(findViewById(android.R.id.content), "Failed to add rating",
                            Snackbar.LENGTH_SHORT).show()
                }
    }

    private fun hideKeyboard() {
        val view = currentFocus
        if (view != null) {
            (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                    .hideSoftInputFromWindow(view.windowToken, 0)
        }
    }

    companion object {

        private val TAG = "RestaurantDetail"

        val KEY_RESTAURANT_ID = "key_restaurant_id"
    }
}
