package com.ceron.stalker.activities

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.bumptech.glide.Glide
import com.ceron.stalker.R
import com.ceron.stalker.databinding.ActivityProfileBinding
import com.ceron.stalker.models.UserProfile
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.Firebase
import com.google.firebase.storage.storage
import java.io.File
import java.util.Date

class ProfileActivity : AuthorizedActivity() {

    private lateinit var binding: ActivityProfileBinding

    private val storage = Firebase.storage
    private val refProfileImg =
        storage.reference.child("users/${currentUser?.uid}/profile.jpg")

    private val PERM_CAMERA_CODE = 101
    private val REQUEST_IMAGE_CAPTURE = 10
    private val PERM_GALERY_GROUP_CODE = 202
    private val REQUEST_PICK = 3
    private lateinit var outputPath: Uri

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        binding.profilePhotoBtn.setOnClickListener {
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
        binding.profileGalleryBtn.setOnClickListener {
            startGallery()
        }

        binding.profileButton.setOnClickListener {
            updateProfile()
        }
    }


    private fun getUserData() {
        super.refData.get().addOnSuccessListener { data ->
            val tmpUser = data.getValue(UserProfile::class.java)
            binding.profileName.editText?.setText(tmpUser?.name)
            binding.profilePhone.editText?.setText(tmpUser?.phone)
            if (!this::outputPath.isInitialized)
                Glide.with(this)
                    .load(refProfileImg)
                    .centerCrop()
                    .placeholder(R.drawable.baseline_face_24)
                    .into(binding.profileImage)
            user = tmpUser!!
        }
    }

    override fun onResume() {
        super.onResume()
        getUserData()
    }

    override fun onStart() {
        super.onStart()
        getUserData()
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

    private fun startGallery() {
        val intentPick = Intent(Intent.ACTION_PICK)
        intentPick.type = "image/*"
        startActivityForResult(intentPick, REQUEST_PICK)
    }

    private fun takePhoto() {
        val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        val imageFileName = "${Date()}.jpg"
        val imageFile =
            File(getExternalFilesDir(Environment.DIRECTORY_PICTURES).toString() + "/" + imageFileName)
        outputPath = FileProvider.getUriForFile(
            this,
            "com.example.android.fileprovider",
            imageFile
        )
        takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, outputPath)
        try {
            startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE)
        } catch (e: ActivityNotFoundException) {
            e.localizedMessage?.let { alerts.indefiniteSnackbar(binding.root, it) }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK) {
            Glide.with(this)
                .clear(binding.profileImage)
            when (requestCode) {
                REQUEST_IMAGE_CAPTURE -> {
                    alerts.shortSimpleSnackbar(binding.root, "Foto tomada correctamente")
                }

                REQUEST_PICK -> {
                    outputPath = data?.data ?: outputPath
                    alerts.shortSimpleSnackbar(binding.root, "Imagen seleccionada correctamente")
                }
            }
            Glide.with(this)
                .load(outputPath)
                .centerCrop()
                .placeholder(R.drawable.baseline_face_24)
                .into(binding.profileImage)
        }
    }

    fun validate(): Boolean {
        if (binding.profileName.editText?.text.toString().isEmpty()) {
            binding.profileName.error = "El nombre es requerido"
            return false
        }
        if (binding.profilePhone.editText?.text.toString().isEmpty()) {
            binding.profilePhone.error = "El telefono es requerido"
            return false
        }
        return true
    }

    fun updateProfile() {
        if (validate()) {
            disableFields()
            user.name = binding.profileName.editText?.text.toString()
            user.phone = binding.profilePhone.editText?.text.toString()
            refData.setValue(user).addOnCompleteListener {
                if (outputPath != null) {
                    refProfileImg.putFile(outputPath!!).addOnCompleteListener {
                        enableFields()
                        alerts.shortSimpleSnackbar(binding.root, "Perfil actualizado correctamente")
                    }.addOnFailureListener {
                        enableFields()
                        it.localizedMessage?.let {
                            alerts.showErrorDialog(
                                "Error al actualizar la foto de perfil",
                                it
                            )
                        }
                    }
                } else {
                    if (binding.profilePass.editText?.text.toString().isNotEmpty()) {
                        MaterialAlertDialogBuilder(this)
                            .setTitle("Cambio de Contraseña")
                            .setMessage("Esta seguro de cambiar la contraseña?")
                            .setPositiveButton("Si") { dialog, _ ->
                                currentUser?.updatePassword(binding.profilePass.editText?.text.toString())
                                    ?.addOnFailureListener { err ->
                                        enableFields()
                                        err.localizedMessage?.let {
                                            alerts.showErrorDialog(
                                                "Error al actualizar la contraseña",
                                                it
                                            )
                                        }
                                    }?.addOnSuccessListener { task ->
                                        enableFields()
                                        alerts.shortSimpleSnackbar(
                                            binding.root,
                                            "Perfil y contraseña actualizados correctamente"
                                        )
                                        finish()
                                    }
                            }.setNegativeButton("No") { dialog, _ ->
                                enableFields()
                                dialog.dismiss()
                            }
                            .show()
                    } else {
                        enableFields()
                        alerts.shortSimpleSnackbar(binding.root, "Perfil actualizado correctamente")
                        finish()
                    }
                }
            }.addOnFailureListener {
                enableFields()
                it.localizedMessage?.let {
                    alerts.showErrorDialog(
                        "Error al actualizar los datos",
                        it
                    )
                }
            }
        }
    }

    private fun disableFields() {
        binding.profileName.isEnabled = false
        binding.profilePass.isEnabled = false
        binding.profilePhone.isEnabled = false
        binding.profilePhotoBtn.isEnabled = false
        binding.profileGalleryBtn.isEnabled = false
        binding.profileButton.isEnabled = false
    }

    private fun enableFields() {
        binding.profileName.isEnabled = true
        binding.profilePass.isEnabled = true
        binding.profilePhone.isEnabled = true
        binding.profilePhotoBtn.isEnabled = true
        binding.profileGalleryBtn.isEnabled = true
    }
}