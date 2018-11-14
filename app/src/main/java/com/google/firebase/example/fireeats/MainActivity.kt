/**
 * Copyright 2017 Google Inc. All Rights Reserved.
 *
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.firebase.example.fireeats

import android.app.Activity
import android.arch.lifecycle.ViewModelProviders
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.support.design.widget.Snackbar
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.Toolbar
import android.text.Html
import android.text.Spanned
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import butterknife.BindView
import butterknife.ButterKnife
import butterknife.OnClick
import butterknife.internal.Utils.listOf
import com.firebase.ui.auth.AuthUI
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.example.fireeats.adapter.RestaurantAdapter
import com.google.firebase.example.fireeats.model.Restaurant
import com.google.firebase.example.fireeats.util.FirestoreClient
import com.google.firebase.example.fireeats.util.RestaurantUtil
import com.google.firebase.example.fireeats.viewmodel.MainActivityViewModel
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query

class MainActivity : AppCompatActivity(), FilterDialogFragment.FilterListener, RestaurantAdapter.OnRestaurantSelectedListener {

    @BindView(R.id.toolbar)
    lateinit var mToolbar: Toolbar

    @BindView(R.id.text_current_search)
    lateinit var mCurrentSearchView: TextView

    @BindView(R.id.text_current_sort_by)
    lateinit var mCurrentSortByView: TextView

    @BindView(R.id.recycler_restaurants)
    lateinit var mRestaurantsRecycler: RecyclerView

    @BindView(R.id.view_empty)
    lateinit var mEmptyView: ViewGroup

    //    lateinit var mFirestore: FirebaseFirestore
    var mQuery: Query? = null

    lateinit var mFilterDialog: FilterDialogFragment
    var mAdapter: RestaurantAdapter? = null

    lateinit var mViewModel: MainActivityViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        ButterKnife.bind(this)
        setSupportActionBar(mToolbar)

        // View model
        mViewModel = ViewModelProviders.of(this).get(MainActivityViewModel::class.java)

        // Enable Firestore logging
        FirebaseFirestore.setLoggingEnabled(BuildConfig.DEBUG)

        // Initialize Firestore and the main RecyclerView
        initFirestore()
        initRecyclerView()

        // Filter Dialog
        mFilterDialog = FilterDialogFragment()
    }

    private fun initFirestore() {
        // Get the 50 highest rated restaurants
        mQuery = FirestoreClient.getRestaurants()
                .orderBy(Restaurant.FIELD_AVG_RATING, Query.Direction.DESCENDING)
                .limit(LIMIT)
    }

    private fun initRecyclerView() {
        if (mQuery == null) {
            Log.w(TAG, "No query, not initializing RecyclerView")
        }

        mAdapter = object : RestaurantAdapter(mQuery, this) {

            override fun onDataChanged() {
                // Show/hide content if the query returns empty.
                if (itemCount == 0) {
                    mRestaurantsRecycler.visibility = View.GONE
                    mEmptyView.visibility = View.VISIBLE
                } else {
                    mRestaurantsRecycler.visibility = View.VISIBLE
                    mEmptyView.visibility = View.GONE
                }
            }

            override fun onError(e: FirebaseFirestoreException) {
                // Show a snackbar on errors
                Snackbar.make(findViewById(android.R.id.content),
                        "Error: check logs for info.", Snackbar.LENGTH_LONG).show()
            }
        }

        mRestaurantsRecycler.layoutManager = LinearLayoutManager(this)
        mRestaurantsRecycler.adapter = mAdapter
    }

    public override fun onStart() {
        super.onStart()

        // Start sign in if necessary
        if (shouldStartSignIn()) {
            startSignIn()
            return
        }

        // Apply filters
        onFilter(mViewModel.filters)

        // Start listening for Firestore updates
        if (mAdapter != null) {
            mAdapter!!.startListening()
        }
    }

    public override fun onStop() {
        super.onStop()
        if (mAdapter != null) {
            mAdapter!!.stopListening()
        }
    }

    private fun onAddItemsClicked() {
        // Get a reference to the restaurants collection
        for (i in 0..9) {
            // Get a random Restaurant POJO
            val restaurant = RestaurantUtil.getRandom(this)
            // Add a new document to the restaurants collection
            FirestoreClient.getRestaurants().add(restaurant)
        }
    }

    override fun onFilter(filters: Filters) {
        // Construct query basic query
        val reference = FirestoreClient.getRestaurants()

        // Limit items
        var query: Query = reference.limit(LIMIT)

        // Category (equality filter)
        if (filters.hasCategory()) {
            query = query.whereEqualTo(Restaurant.FIELD_CATEGORY, filters.category)
        }

        // City (equality filter)
        if (filters.hasCity()) {
            query = query.whereEqualTo(Restaurant.FIELD_CITY, filters.city)
        }

        // Price (equality filter)
        if (filters.hasPrice()) {
            query = query.whereEqualTo(Restaurant.FIELD_PRICE, filters.price)
        }

        // Sort by (orderBy with direction)
        if (filters.hasSortBy()) {
            query = query.orderBy(filters.sortBy, filters.sortDirection)
        }

        // Update the query
        mQuery = query
        mAdapter?.query = query

        // Set header
        mCurrentSearchView.text = fromHtml(filters.getSearchDescription(this))
        mCurrentSortByView.text = filters.getOrderDescription(this)

        // Save filters
        mViewModel.filters = filters
    }

    private fun fromHtml(html: String): Spanned {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY)
        } else Html.fromHtml(html)
    }


    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_add_items -> onAddItemsClicked()
            R.id.menu_sign_out -> {
                AuthUI.getInstance().signOut(this)
                startSignIn()
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == RC_SIGN_IN) {
            mViewModel.isSigningIn = false
            if (resultCode != Activity.RESULT_OK && shouldStartSignIn()) {
                startSignIn()
            }
        }
    }

    @OnClick(R.id.filter_bar)
    fun onFilterClicked() {
        // Show the dialog containing filter options
        mFilterDialog.show(supportFragmentManager, FilterDialogFragment.TAG)
    }

    @OnClick(R.id.button_clear_filter)
    fun onClearFilterClicked() {
        mFilterDialog.resetFilters()

        onFilter(Filters.getDefault())
    }

    override fun onRestaurantSelected(restaurant: DocumentSnapshot) {
        // Go to the details page for the selected restaurant
        val intent = Intent(this, RestaurantDetailActivity::class.java)
        intent.putExtra(RestaurantDetailActivity.KEY_RESTAURANT_ID, restaurant.id)
        startActivity(intent)
    }

    private fun shouldStartSignIn(): Boolean {
        return !mViewModel.isSigningIn && FirebaseAuth.getInstance().currentUser == null
    }

    private fun startSignIn() {
        // Sign in with FirebaseUI
        val intent = AuthUI.getInstance().createSignInIntentBuilder()
                .setAvailableProviders(listOf<AuthUI.IdpConfig>(AuthUI.IdpConfig.EmailBuilder().build()))
                .setIsSmartLockEnabled(false)
                .build()

        startActivityForResult(intent, RC_SIGN_IN)
        mViewModel.isSigningIn = true
    }

    companion object {

        private val TAG = "MainActivity"

        private val RC_SIGN_IN = 9001

        public val LIMIT = 50.toLong()
    }
}
