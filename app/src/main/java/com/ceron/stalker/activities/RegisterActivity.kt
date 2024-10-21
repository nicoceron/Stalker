package com.ceron.stalker.activities

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.ceron.stalker.R
import com.ceron.stalker.databinding.ActivityRegisterBinding
import com.ceron.stalker.models.UserProfile
import com.ceron.stalker.utils.Alerts
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.database.database
import com.google.firebase.storage.storage
import java.io.File
import java.util.Date

class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    val TAG = RegisterActivity::class.java.simpleName

    private val auth = Firebase.auth
    private val database = Firebase.database
    private val storage = Firebase.storage

    private var alerts = Alerts(this)

    private val PERM_CAMERA_CODE = 101
    private val REQUEST_IMAGE_CAPTURE = 10
    private val PERM_GALERY_GROUP_CODE = 202
    private val REQUEST_PICK = 3
    private var userPhotoPath: Uri? = null

    private var isLoading = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        binding.signupButton.setOnClickListener {
            signUp()
        }
        binding.regPhotoBtn.setOnClickListener() {
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED -> {
                    takePhoto()
                }

                shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) -> {
                    alerts.indefiniteSnackbar(
                        binding.root,
                        "El permiso de Camara es necesario para usar esta actividad 😭"
                    )
                }

                else -> {
                    requestPermissions(arrayOf(Manifest.permission.CAMERA), PERM_CAMERA_CODE)
                }
            }
        }
        binding.regGalleryBtn.setOnClickListener() {
            startGallery()
        }
    }

    private fun validateFields(): Boolean {
        // Validate email
        if (binding.signupEmail.editText?.text.toString().isEmpty() ||
            !android.util.Patterns.EMAIL_ADDRESS.matcher(binding.signupEmail.editText?.text.toString())
                .matches()
        ) {
            binding.signupEmail.error = getString(R.string.mail_error_label)
            return false
        } else binding.signupEmail.isErrorEnabled = false
        // Validate password
        if (binding.signupPass.editText?.text.toString().isEmpty()) {
            binding.signupPass.error = getString(R.string.error_pass_label)
            return false
        } else binding.signupPass.isErrorEnabled = false
        // Validate name
        if (binding.signupName.editText?.text.toString().isEmpty()) {
            binding.signupName.error = getString(R.string.error_name_label)
            return false
        } else binding.signupName.isErrorEnabled = false
        // Validate phone
        if (binding.signupPhone.editText?.text.toString().isEmpty()) {
            binding.signupPhone.error = getString(R.string.error_phone_label)
            return false
        } else binding.signupPhone.isErrorEnabled = false
        if (userPhotoPath == null) {
            alerts.showErrorDialog(
                "Error al registrar usuario",
                "Debe seleccionar una foto de perfil"
            )
            return false
        }
        return true
    }

    private fun signUp() {
        if (validateFields()) {
            disableFields()
            auth.createUserWithEmailAndPassword(
                binding.signupEmail.editText?.text.toString(),
                binding.signupPass.editText?.text.toString()
            ).addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    // Sign in success, update UI with the signed-in user's information
                    val refStorage =
                        storage.reference.child("users/${Firebase.auth.currentUser?.uid}/profile.jpg")
                    refStorage.putFile(userPhotoPath!!)
                        .addOnCompleteListener { _ ->
                            val refDatabase =
                                database.getReference("users/${Firebase.auth.currentUser?.uid}")
                            val user = UserProfile(
                                binding.signupName.editText?.text.toString(),
                                binding.signupPhone.editText?.text.toString()
                            )
                            refDatabase.setValue(user)
                                .addOnCompleteListener {
                                    startActivity(Intent(this, MainActivity::class.java))
                                }
                                .addOnFailureListener { err ->
                                    enableFields()
                                    err.localizedMessage?.let {
                                        alerts.showErrorDialog("Error al guardar el usuario", it)
                                    }
                                }
                        }.addOnFailureListener {
                            enableFields()
                            it.localizedMessage?.let {
                                alerts.showErrorDialog("Error al guardar la foto de usuario", it)
                            }
                        }
                } else {
                    enableFields()
                    task.exception?.localizedMessage?.let {
                        alerts.showErrorDialog("Error al crear el usuario", it)
                    }
                }
            }
        }
    }

    private fun takePhoto() {
        val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        val imageFileName = "${Date()}.jpg"
        val imageFile =
            File(getExternalFilesDir(Environment.DIRECTORY_PICTURES).toString() + "/" + imageFileName)
        userPhotoPath = FileProvider.getUriForFile(
            this,
            "com.example.android.fileprovider",
            imageFile
        )
        takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, userPhotoPath)
        try {
            startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE)
        } catch (e: ActivityNotFoundException) {
            e.localizedMessage?.let { alerts.showErrorDialog("Error al tomar la foto", it) }
        }
    }

    private fun startGallery() {
        val intentPick = Intent(Intent.ACTION_PICK)
        intentPick.type = "image/*"
        startActivityForResult(intentPick, REQUEST_PICK)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            PERM_CAMERA_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    takePhoto()
                } else {
                    alerts.shortSimpleSnackbar(
                        binding.root,
                        "Me acaban de negar los permisos de Camara 😭"
                    )
                }
            }

            PERM_GALERY_GROUP_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    startGallery()
                } else {
                    alerts.shortSimpleSnackbar(
                        binding.root,
                        "Me acaban de negar los permisos de Galeria 😭"
                    )
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQUEST_IMAGE_CAPTURE -> {
                if (resultCode == RESULT_OK) {
                    alerts.shortSimpleSnackbar(binding.root, "Foto tomada correctamente")
                    Log.d(TAG, "onActivityResult: ${userPhotoPath.toString()}")
                    binding.materialCardView.removeAllViews()
                    val imageView = ImageView(this)
                    imageView.layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.MATCH_PARENT
                    )
                    imageView.setImageURI(userPhotoPath)
                    imageView.scaleType = ImageView.ScaleType.CENTER_CROP
                    imageView.adjustViewBounds = true
                    binding.materialCardView.addView(imageView)
                } else {
                    alerts.shortSimpleSnackbar(binding.root, "No se pudo tomar la foto")
                }
            }

            REQUEST_PICK -> {
                if (resultCode == RESULT_OK) {
                    alerts.shortSimpleSnackbar(
                        binding.root,
                        "Se selecciono un archivo de la galeria"
                    )
                    if (data != null) {
                        userPhotoPath = data.data
                        binding.materialCardView.removeAllViews()
                        val imageView = ImageView(this)
                        imageView.layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.MATCH_PARENT
                        )
                        imageView.setImageURI(userPhotoPath)
                        imageView.scaleType = ImageView.ScaleType.CENTER_CROP
                        imageView.adjustViewBounds = true
                        binding.materialCardView.addView(imageView)
                    }
                }
            }
        }
    }

    private fun disableFields() {
        binding.registerLoader.visibility = LinearLayout.VISIBLE
        binding.signupName.isEnabled = false
        binding.signupPass.isEnabled = false
        binding.signupEmail.isEnabled = false
        binding.signupButton.isEnabled = false
        binding.signupPhone.isEnabled = false
        binding.regGalleryBtn.isEnabled = false
        binding.regPhotoBtn.isEnabled = false
    }

    private fun enableFields() {
        binding.registerLoader.visibility = LinearLayout.GONE
        binding.signupName.isEnabled = true
        binding.signupPass.isEnabled = true
        binding.signupEmail.isEnabled = true
        binding.signupButton.isEnabled = true
        binding.signupPhone.isEnabled = true
        binding.regGalleryBtn.isEnabled = true
        binding.regPhotoBtn.isEnabled = true
    }
}