package com.antidaze.pocketvm.ui

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.antidaze.pocketvm.R
import com.antidaze.pocketvm.core.VirtualStorageManager
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SharedStorageActivity : AppCompatActivity() {

    private lateinit var storageManager: VirtualStorageManager
    private lateinit var recyclerFiles: RecyclerView
    private lateinit var emptyView: View
    private lateinit var adapter: SharedFileAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_shared_storage)

        storageManager = VirtualStorageManager.get(this)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar_shared)
        toolbar.setNavigationOnClickListener { finish() }

        val textSharedPath = findViewById<TextView>(R.id.text_shared_path)
        textSharedPath.text = storageManager.sharedFolder.absolutePath

        val btnImport = findViewById<MaterialButton>(R.id.btn_import_file)
        btnImport.setOnClickListener { openFilePicker() }

        val btnRefresh = findViewById<MaterialButton>(R.id.btn_refresh_files)
        btnRefresh.setOnClickListener { refreshFiles() }

        recyclerFiles = findViewById(R.id.recycler_shared_files)
        recyclerFiles.layoutManager = LinearLayoutManager(this)
        emptyView = findViewById(R.id.shared_empty_view)

        adapter = SharedFileAdapter(emptyList()) { file ->
            Toast.makeText(this, "Path in VM: /sdcard/Shared/${file.name}", Toast.LENGTH_SHORT).show()
        }
        recyclerFiles.adapter = adapter

        refreshFiles()
    }

    private fun refreshFiles() {
        val files = storageManager.listSharedFiles()
        adapter.update(files)
        emptyView.visibility = if (files.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "*/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        startActivityForResult(intent, REQUEST_CODE_PICK_FILE)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_PICK_FILE && resultCode == Activity.RESULT_OK) {
            val uri = data?.data ?: return
            copyUriToShared(uri)
        }
    }

    private fun copyUriToShared(uri: Uri) {
        try {
            var fileName = "imported_file_${System.currentTimeMillis()}"
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1 && cursor.moveToFirst()) {
                    fileName = cursor.getString(nameIndex)
                }
            }

            val targetFile = File(storageManager.sharedFolder, fileName)
            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            }
            Toast.makeText(this, "Imported '$fileName' to Shared folder", Toast.LENGTH_SHORT).show()
            refreshFiles()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Failed to import file: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    class SharedFileAdapter(
        private var files: List<File>,
        private val onClick: (File) -> Unit
    ) : RecyclerView.Adapter<SharedFileAdapter.FileViewHolder>() {

        class FileViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val icon: ImageView = view.findViewById(R.id.app_icon)
            val name: TextView = view.findViewById(R.id.app_label)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_desktop_app, parent, false)
            return FileViewHolder(view)
        }

        override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
            val file = files[position]
            val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.US)
            val sizeKb = (file.length() / 1024).coerceAtLeast(1)

            holder.name.text = "${file.name}\n($sizeKb KB)"
            val iconRes = if (file.name.endsWith(".apk", ignoreCase = true)) {
                R.drawable.ic_apps
            } else {
                R.drawable.ic_folder
            }
            holder.icon.setImageResource(iconRes)
            holder.itemView.setOnClickListener { onClick(file) }
        }

        override fun getItemCount(): Int = files.size

        fun update(newFiles: List<File>) {
            files = newFiles
            notifyDataSetChanged()
        }
    }

    companion object {
        private const val REQUEST_CODE_PICK_FILE = 1001
    }
}
