package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.example.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
  
  private lateinit var binding: ActivityMainBinding

  private val requestPermissionLauncher =
      registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
          if (permissions[Manifest.permission.RECEIVE_SMS] == true) {
              try {
                  com.example.service.SmsMonitoringService.startService(this)
                  com.example.worker.ServiceRestartWorker.enqueue(this)
              } catch (e: java.lang.Exception) {
                  e.printStackTrace()
              }
          }
      }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    binding = ActivityMainBinding.inflate(layoutInflater)
    setContentView(binding.root)

    val navHostFragment = supportFragmentManager
        .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
    val navController = navHostFragment.navController
    binding.bottomNav.setupWithNavController(navController)

    navController.addOnDestinationChangedListener { _, destination, _ ->
        if (destination.id == R.id.voucherListFragment) {
            binding.bottomNav.visibility = android.view.View.GONE
        } else {
            binding.bottomNav.visibility = android.view.View.VISIBLE
        }
    }

    requestRequiredPermissions()

    if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED) {
        try {
            com.example.service.SmsMonitoringService.startService(this)
            com.example.worker.ServiceRestartWorker.enqueue(this)
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }
    }
  }

  private fun requestRequiredPermissions() {
      val requiredPermissions = mutableListOf(
          Manifest.permission.RECEIVE_SMS,
          Manifest.permission.SEND_SMS,
          Manifest.permission.READ_SMS
      )
      
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
          requiredPermissions.add(Manifest.permission.POST_NOTIFICATIONS)
      }

      val permissionsToRequest = requiredPermissions.filter {
          ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
      }

      if (permissionsToRequest.isNotEmpty()) {
          requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())
      }
  }
}
