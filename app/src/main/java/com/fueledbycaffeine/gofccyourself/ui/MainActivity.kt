package com.fueledbycaffeine.gofccyourself.ui

import android.Manifest
import android.app.role.RoleManager
import android.app.role.RoleManager.ROLE_CALL_SCREENING
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.fueledbycaffeine.gofccyourself.R
import com.fueledbycaffeine.gofccyourself.ScreeningPreferences
import com.fueledbycaffeine.gofccyourself.databinding.ActivityMainBinding
import pub.devrel.easypermissions.EasyPermissions
import timber.log.Timber

class MainActivity : AppCompatActivity(), EasyPermissions.PermissionCallbacks {
  companion object {
    private const val REQUEST_ID_BECOME_CALL_SCREENER = 1
    private const val REQUEST_ID_REQUEST_READ_CONTACTS_PERMISSION = 1

    private const val EXTRA_CONTACT_READ_PERMISSION_DENIED = "contact_permission_denied_forever"
  }

  private val roleManager by lazy { getSystemService(RoleManager::class.java) }
  private val prefs by lazy { ScreeningPreferences(this) }

  private val hasCallScreeningRole: Boolean
    get() = roleManager.isRoleHeld(ROLE_CALL_SCREENING)
  private val readContactsPermissionGranted: Boolean
    get() = EasyPermissions.hasPermissions(this, Manifest.permission.READ_CONTACTS)
  private var contactsAccessDeniedForever = false


  private lateinit var binding: ActivityMainBinding
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    binding = ActivityMainBinding.inflate(layoutInflater)
    setContentView(R.layout.activity_main)

    contactsAccessDeniedForever = savedInstanceState
      ?.getBoolean(EXTRA_CONTACT_READ_PERMISSION_DENIED, false) ?: false

    addUiListeners()
    updateUi()
  }

  public override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    if (requestCode == REQUEST_ID_BECOME_CALL_SCREENER) {
      updateUi()

      when (resultCode) {
        RESULT_OK -> Timber.i("Role was granted")
        else -> Timber.e("Role was not granted")
      }
    }
  }

  override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults)

    // Forward results to EasyPermissions
    EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this)
  }

  override fun onSaveInstanceState(outState: Bundle) {
    super.onSaveInstanceState(outState)
    outState.putBoolean(EXTRA_CONTACT_READ_PERMISSION_DENIED, contactsAccessDeniedForever)
  }

  //<editor-fold desc="EasyPermissions.PermissionCallbacks">
  override fun onPermissionsGranted(requestCode: Int, grantedPermissions: List<String>) {
    if (Manifest.permission.READ_CONTACTS in grantedPermissions) {
      requestRole()
    }
  }

  override fun onPermissionsDenied(requestCode: Int, deniedPermissions: List<String>) {
    // only in onPermissionsDenied() is it certain if a permission has been permanently denied
    if (Manifest.permission.READ_CONTACTS in deniedPermissions) {
      contactsAccessDeniedForever = EasyPermissions.permissionPermanentlyDenied(
        this,
        Manifest.permission.READ_CONTACTS
      )
    }
  }
  //</editor-fold>

  private fun requestContactsPermission() {
    if (contactsAccessDeniedForever) {
      try {
        val intent = Intent()
        intent.action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
        intent.data = Uri.fromParts("package", this.packageName, null)
        startActivity(intent)
        Toast.makeText(this,
          R.string.enable_read_contacts_permissions_instructions, Toast.LENGTH_LONG).show()
      } catch (e: ActivityNotFoundException) {
        Toast.makeText(this,
          R.string.enable_read_contacts_permissions_instructions_long, Toast.LENGTH_LONG).show()
      }
    } else {
      requestPermissions(
        arrayOf(Manifest.permission.READ_CONTACTS),
        REQUEST_ID_REQUEST_READ_CONTACTS_PERMISSION
      )
    }
  }

  private fun requestRole() {
    val intent = roleManager.createRequestRoleIntent(ROLE_CALL_SCREENING)
    @Suppress("DEPRECATION")
    startActivityForResult(intent,
      REQUEST_ID_BECOME_CALL_SCREENER
    )
  }

  private fun addUiListeners() {
    binding.activateButton.setOnClickListener { requestContactsPermission() }

    binding.enableSwitch.setOnCheckedChangeListener { _, enabled ->
      prefs.isServiceEnabled = enabled
      updateUi()
    }
    binding.skipNotificationSwitch.setOnCheckedChangeListener { _, skip ->
      prefs.skipCallNotification = skip
      updateUi()
    }
    binding.skipCallLogSwitch.setOnCheckedChangeListener { _, skip ->
      prefs.skipCallLog = skip
      updateUi()
    }
    binding.declineUnknownCallers.setOnCheckedChangeListener { _, skip ->
      prefs.declineUnknownCallers = skip
      updateUi()
    }
    binding.declineAuthenticationFailures.setOnCheckedChangeListener { _, skip ->
      prefs.declineAuthenticationFailures = skip
      updateUi()
    }
    binding.declineUnauthenticatedCallers.setOnCheckedChangeListener { _, skip ->
      prefs.declineUnauthenticatedCallers = skip
      updateUi()
    }
  }

  private fun updateUi() {
    val isInstalled = readContactsPermissionGranted && hasCallScreeningRole
    binding.statusLabel.text = when (isInstalled) {
      true -> getString(R.string.status_activated)
      else -> getString(R.string.status_inactive)
    }
    binding.activateButton.isVisible = isInstalled.not()
    binding.enableSwitch.isVisible = isInstalled
    binding.skipNotificationSwitch.isVisible = isInstalled
    binding.skipCallLogSwitch.isVisible = isInstalled
    binding.declineUnknownCallers.isVisible = isInstalled
    binding.declineAuthenticationFailures.isVisible = isInstalled
    binding.declineUnauthenticatedCallers.isVisible = isInstalled

    binding.enableSwitch.isChecked = prefs.isServiceEnabled

    if (Build.VERSION.SDK_INT > 100) {
      binding.skipNotificationSwitch.isEnabled = prefs.isServiceEnabled
      binding.skipNotificationSwitch.isChecked = prefs.skipCallNotification

      binding.skipCallLogSwitch.isEnabled = prefs.isServiceEnabled
      binding.skipCallLogSwitch.isChecked = prefs.skipCallLog
    } else {
      binding.skipNotificationSwitch.isEnabled = false
      binding.skipNotificationSwitch.isChecked = true
      binding.skipNotificationDescription.visibility = View.VISIBLE

      binding.skipCallLogSwitch.isEnabled = false
      binding.skipCallLogSwitch.isChecked = true
      binding.skipCallLogDescription.visibility = View.VISIBLE
    }


    binding.declineUnknownCallers.isEnabled = prefs.isServiceEnabled
    binding.declineUnknownCallers.isChecked = prefs.declineUnknownCallers

    binding.declineAuthenticationFailures.isEnabled = prefs.isServiceEnabled
    binding.declineAuthenticationFailures.isChecked = prefs.declineAuthenticationFailures

    binding.declineUnauthenticatedCallers.isEnabled = prefs.isServiceEnabled
    binding.declineUnauthenticatedCallers.isChecked = prefs.declineUnauthenticatedCallers
  }
}
