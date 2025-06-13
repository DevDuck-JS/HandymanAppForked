package com.example.handyman

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.gms.tasks.Tasks
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.storage.FirebaseStorage

class CustomerJobDetailsFragment : Fragment() {
    private val handymanList = mutableListOf<String>()
    private lateinit var adapter: QuotedHandymenAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_customer_job_details, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val recyclerView = view.findViewById<RecyclerView>(R.id.recyclerQuotedHandymen)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        val args = CustomerJobDetailsFragmentArgs.fromBundle(requireArguments())

        val customerId = args.customerId
        val jobId = args.jobId
        val serviceName = args.serviceCategory
        val jobDescription = args.problemDesc
        val dateFrom = args.dateFrom
        val dateTo = args.dateTo
        val timeFrom = args.timeFrom
        val timeTo = args.timeTo
        val location = args.location
        val salaryFrom = args.salaryFrom
        val salaryTo = args.salaryTo
        val paymentOption = args.paymentOption
        val assignedTo = args.assignedTo
        val jobStatus = args.jobStatus

        Log.d("DEBUG", "Job ID: $jobId")

        val jobTitle = view.findViewById<TextView>(R.id.tvJobTitle)
        jobTitle.text = serviceName
        val salaryDisplay = view.findViewById<TextView>(R.id.tvPrice)
        val jobRef = FirebaseDatabase.getInstance().getReference("DummyJob").child(jobId)

        jobRef.get().addOnSuccessListener { snapshot ->
            val paymentStatus = snapshot.child("paymentStatus").getValue(String::class.java) ?: ""
            val custpay = snapshot.child("custpay").getValue(String::class.java) ?: ""

            if (paymentStatus == "done" && custpay.isNotBlank()) {
                salaryDisplay.text = "Paid: BDT $custpay"
            } else if (salaryFrom.isNotBlank() && salaryTo.isNotBlank()) {
                salaryDisplay.text = if (paymentOption == "Per Day")
                    "BDT $salaryFrom-$salaryTo/day"
                else
                    "BDT $salaryFrom-$salaryTo"
            } else {
                salaryDisplay.text = "To be negotiated"
            }
        }
        val jobDescDisplay = view.findViewById<TextView>(R.id.tvJobSubtitle)
        jobDescDisplay.text = jobDescription
        val dateDisplay = view.findViewById<TextView>(R.id.tvDate)
        if (dateFrom == dateTo) {
            dateDisplay.text = "$dateFrom"
        } else {
            dateDisplay.text = "$dateFrom — $dateTo"
        }
        val timeDisplay = view.findViewById<TextView>(R.id.tvTime)
        timeDisplay.text = "$timeFrom — $timeTo"
        val locationDisplay = view.findViewById<TextView>(R.id.tvAddress)
        locationDisplay.text = "$location, Melbourne, VIC"

        // Initialize the adapter with the empty list.
        adapter = QuotedHandymenAdapter(handymanList, jobId, assignedTo, "customer2", requireContext())
        recyclerView.adapter = adapter

        val quotedHandymenRef = FirebaseDatabase.getInstance()
            .getReference("DummyJob")
            .child(jobId)
            .child("quotedHandymen")

        // Retrieve quoted handymen data from Firebase.
        quotedHandymenRef.addListenerForSingleValueEvent(object : ValueEventListener {
            @SuppressLint("NotifyDataSetChanged")
            override fun onDataChange(snapshot: DataSnapshot) {
                // Clear the list to avoid duplicates if the listener fires multiple times.
                handymanList.clear()

                // Log the number of children for debugging.
                Log.d("DEBUG", "quotedHandymen count: ${snapshot.childrenCount}")

                for (childSnapshot in snapshot.children) {
                    // Optional: log the child key and value.
                    Log.d("DEBUG", "child key = ${childSnapshot.key}, value = ${childSnapshot.value}")

                    // Get the handyman ID from the value.
                    val handymanId = childSnapshot.getValue(String::class.java)
                    if (handymanId != null) {
                        // Add the handymanId to your list.
                        handymanList.add(handymanId)
                    }
                }

                if (assignedTo.isNotBlank()) {
                    handymanList.remove(assignedTo)
                    handymanList.add(0, assignedTo)
                }

                // Notify the adapter once after all items are added.
                adapter.notifyDataSetChanged()

                // Log the final list for verification.
                Log.d("DEBUG", "Handyman list: $handymanList")
            }

            override fun onCancelled(error: DatabaseError) {
                // Handle potential errors (e.g., log the error or show a message to the user)
            }
        })

        displayJobImages(view, jobId)
    }

    private fun displayJobImages(rootView: View, jobId: String) {
        // Get references to your HorizontalScrollView and its LinearLayout container.
        val photoScroll = rootView.findViewById<HorizontalScrollView>(R.id.photoScroll)
        val photosContainer = rootView.findViewById<LinearLayout>(R.id.attachPhotosContainer)

        // Create a reference to the folder "jobImages/<jobId>" in Firebase Storage.
        val storageRef = FirebaseStorage.getInstance().getReference("jobImages").child(jobId)

        // List all files in that folder.
        storageRef.listAll()
            .addOnSuccessListener { listResult ->
                val downloadUrlTasks = mutableListOf<com.google.android.gms.tasks.Task<Uri>>()

                // Add a download URL task for each item found.
                listResult.items.forEach { itemRef ->
                    downloadUrlTasks.add(itemRef.downloadUrl)
                }

                // Wait for all download URL tasks to succeed.
                Tasks.whenAllSuccess<Uri>(downloadUrlTasks)
                    .addOnSuccessListener { uriList ->
                        // Convert URI list to a list of string URLs.
                        val imageUrls = uriList.map { it.toString() }

                        // Show or hide the scroll view based on whether we have images.
                        photoScroll.visibility = if (imageUrls.isNotEmpty()) View.VISIBLE else View.GONE

                        // Clear any existing views in the container.
                        photosContainer.removeAllViews()

                        // Dynamically create ImageViews and load the images using Glide.
                        for (url in imageUrls) {
                            val imageView = ImageView(requireContext())
                            val params = LinearLayout.LayoutParams(400, 400)
                            params.setMargins(8, 8, 8, 8)
                            imageView.layoutParams = params
                            imageView.scaleType = ImageView.ScaleType.CENTER_CROP

                            Glide.with(this)
                                .load(url)
                                .into(imageView)

                            // Add the ImageView to the LinearLayout container.
                            photosContainer.addView(imageView)
                        }
                    }
                    .addOnFailureListener { exception ->
                        // Handle error while getting download URLs.
                        photoScroll.visibility = View.GONE
                    }
            }
            .addOnFailureListener { exception ->
                // Handle error when listing files.
                photoScroll.visibility = View.GONE
            }
    }
}