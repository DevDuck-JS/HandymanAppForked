package com.example.handyman

import androidx.appcompat.app.AlertDialog
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ImageView
import android.widget.Spinner
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.database.FirebaseDatabase
import java.util.UUID
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage

class HandymanJobListFragment : Fragment() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: HandymanJobListAdapter
    val handymanID = "handyman8"
    val customerId = "customer2"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_handyman_job_list, container, false)

        recyclerView = view.findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(context)

        adapter = HandymanJobListAdapter(
            handymanId = handymanID,
            onViewDetails = { job ->
                // Handle "View Details" button click
                val action = HandymanJobListFragmentDirections.actionHandymanJobListFragmentToHandymanJobListDetailsFragment(
                    customerId = "",
                    jobId = job.jobId,
                    serviceCategory = job.jobCat,
                    problemDesc = job.jobDesc,
                    dateFrom = job.jobDateFrom,
                    dateTo = job.jobDateTo,
                    timeFrom = job.jobTimeFrom,
                    timeTo = job.jobTimeTo,
                    location = job.jobLocation,
                    salaryFrom = job.jobSalaryFrom,
                    salaryTo = job.jobSalaryTo,
                    paymentOption = job.jobPaymentOption,
                    imageUris = null,
                    assignedTo = job.assignedTo,
                    jobStatus = job.jobStatus
                )
                findNavController().navigate(action)
            },
            onDelete = { job ->
                AlertDialog.Builder(requireContext())
                    .setTitle("Withdraw quote?")
                    .setMessage("Remove your quote for this job?")
                    .setPositiveButton("Yes") { _, _ ->

                        val quotesRef = FirebaseDatabase.getInstance()
                            .getReference("Job")
                            .child(job.jobId)
                            .child("quotedHandymen")

                        // Find the push-key(s) whose VALUE == this handyman name
                        quotesRef.orderByValue().equalTo(handymanID)
                            .addListenerForSingleValueEvent(object : ValueEventListener {

                                override fun onDataChange(snapshot: DataSnapshot) {
                                    if (!snapshot.exists()) {
                                        Toast.makeText(
                                            context,
                                            "You haven’t quoted this job.",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        return
                                    }

                                    snapshot.children.forEach { it.ref.removeValue() }

                                    Toast.makeText(
                                        context,
                                        "Your quote was removed.",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }

                                override fun onCancelled(error: DatabaseError) {
                                    Toast.makeText(
                                        context,
                                        "Failed: ${error.message}",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            })
                    }
                    .setNegativeButton("No", null)
                    .show()
            },
            onUpdate = fun(job: Job) {
                val currentStatus = job.jobStatus

                // build *only* the valid next‐step list
                val nextStatuses = when (currentStatus) {
                    "In-progress" -> arrayOf("Done")
                    "Done"        -> arrayOf()            // nothing left to do
                    else          -> arrayOf("In-progress")  // everything before In-progress
                }

                if (nextStatuses.isEmpty()) {
                    Toast.makeText(context, "No further updates available", Toast.LENGTH_SHORT).show()
                    return
                }

                // now show the dialog with only the valid choices
                var chosen = 0
                AlertDialog.Builder(requireContext())
                    .setTitle("Update status")
                    .setSingleChoiceItems(nextStatuses, 0) { _, which ->
                        chosen = which
                    }
                    .setPositiveButton("OK") { _, _ ->
                        val newStatus = nextStatuses[chosen]
                        val jobRef = FirebaseDatabase
                            .getInstance()
                            .getReference("Job")
                            .child(job.jobId)

                        // 1) update the handyman’s status field
                        jobRef.child("jobStatusHandyman")
                            .setValue(newStatus)
                            .addOnSuccessListener {
                                Toast.makeText(
                                    context,
                                    "Status updated to $newStatus",
                                    Toast.LENGTH_SHORT
                                ).show()

                                // 2) only once customer’s status matches do we move lists
                                jobRef.child("jobStatusCustomer")
                                    .addListenerForSingleValueEvent(object : ValueEventListener {
                                        override fun onDataChange(snap: DataSnapshot) {
                                            val customerStatus = snap.getValue(String::class.java)
                                            if (customerStatus == newStatus) {

                                                // 3) unify the main status
                                                jobRef.child("jobStatus")
                                                    .setValue(newStatus)
                                                    .addOnSuccessListener {
                                                        val updatedList = if (newStatus == "In-progress" || newStatus == "Done") {
                                                            adapter.currentList.filter { it.jobId != job.jobId }
                                                        } else {
                                                            // there aren't any other cases, but just in case:
                                                            adapter.currentList
                                                        }
                                                        adapter.submitList(updatedList)
                                                    }

                                                // 4) decide which lists to move between
                                                val (custFrom, hmFrom, toList) = when (newStatus) {
                                                    "In-progress" -> Triple(
                                                        "assignedJobs",   // customer’s old
                                                        "acceptedJobs",   // handyman’s old
                                                        "inProgressJobs"  // new
                                                    )
                                                    "Done" -> Triple(
                                                        "inProgressJobs",
                                                        "inProgressJobs",
                                                        "completedJobs"
                                                    )
                                                    else -> return
                                                }

                                                // 5) move the jobId under both nodes
                                                val custRef = FirebaseDatabase.getInstance()
                                                    .getReference("dummyCustomers")
                                                    .child(customerId)
                                                val hmRef = FirebaseDatabase.getInstance()
                                                    .getReference("dummyHandymen")
                                                    .child(handymanID)

                                                moveJobId(custRef, custFrom, toList, job.jobId)
                                                moveJobId(hmRef, hmFrom, toList, job.jobId)
                                            }
                                        }
                                        override fun onCancelled(e: DatabaseError) {}
                                    })
                            }
                            .addOnFailureListener { e ->
                                Toast.makeText(
                                    context,
                                    "Failed to update status: ${e.message}",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        )
        recyclerView.adapter = adapter

        val spinner = view.findViewById<Spinner>(R.id.spinnerStatus)
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>, v: View?, pos: Int, id: Long
            ) {
                val display = parent.getItemAtPosition(pos) as String
                val key = when (display) {
                    "Quoted"      -> "quotedJobs"
                    "Accepted"  -> "acceptedJobs"
                    "All"    -> "allJobs"
                    "In-progress" -> "inProgressJobs"
                    "Done"        -> "completedJobs"
                    else          -> return
                }
                fetchJobsForCategory(key)
            }
            override fun onNothingSelected(parent: AdapterView<*>) { }
        }

        fetchJobsForCategory("allJobs")

        return view
    }

    private fun moveJobId(
        ref: DatabaseReference,
        fromList: String,
        toList: String,
        jobId: String
    ) {
        ref.child(fromList)
            .orderByValue()
            .equalTo(jobId)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    snapshot.children.forEach { it.ref.removeValue() }
                    ref.child(toList)
                        .push()
                        .setValue(jobId)
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }

    private fun fetchJobsForCategory(category: String) {
        // read the job‐ID list under /dummyHandymen/{handymanId}/{category}
        val listRef = FirebaseDatabase.getInstance()
            .getReference("dummyHandymen")
            .child(handymanID)
            .child(category)

        listRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(listSnap: DataSnapshot) {
                val jobIds = listSnap.children
                    .mapNotNull { it.getValue(String::class.java) }

                fetchJobsByIds(jobIds)
            }
            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(context, "Error loading $category", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun fetchJobsByIds(jobIds: List<String>) {
        FirebaseDatabase.getInstance()
            .getReference("Job")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val jobs = snapshot.children.mapNotNull { jobSnap ->
                        val key = jobSnap.key ?: return@mapNotNull null
                        if (key !in jobIds) return@mapNotNull null

                        val parsed = jobSnap.getValue(Job::class.java) ?: return@mapNotNull null

                        parsed.copy(jobId = key)
                    }

                    adapter.submitList(jobs)
                }
                override fun onCancelled(error: DatabaseError) { /*…*/ }
            })
    }
}