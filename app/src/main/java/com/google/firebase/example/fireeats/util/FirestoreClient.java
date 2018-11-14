package com.google.firebase.example.fireeats.util;

import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;

/**
 * Created by KHEMRAJ on 11/14/2018.
 */
public class FirestoreClient {
    public static final String COLL_RESTAURANT = "restaurants";
    public static final String COLL_RATING = "ratings";

    public static CollectionReference getRestaurants() {
        return FirebaseFirestore.getInstance().collection(FirestoreClient.COLL_RESTAURANT);
    }

    public static CollectionReference getRatingCollection(DocumentReference reference) {
        return reference.collection(FirestoreClient.COLL_RATING);
    }
}
