package com.example.weatherapp

import android.Manifest
import android.annotation.SuppressLint
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import com.example.weatherapp.databinding.ActivityMainBinding
import com.example.weatherapp.models.WeatherResponse
import com.example.weatherapp.network.WeatherService
import com.google.android.gms.location.*
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import kotlinx.android.synthetic.main.activity_main.*
import retrofit2.*
import retrofit2.converter.gson.GsonConverterFactory
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit


class MainActivity : AppCompatActivity() {


    private lateinit var mFusedLocationClient: FusedLocationProviderClient
    private var mProgressDialog: Dialog? = null
    private val binding by lazy { ActivityMainBinding.inflate(layoutInflater) }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        mFusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        if (!isLocationEnabled()) {
            Toast.makeText(
                this,
                "Please turn on location provider",
                Toast.LENGTH_SHORT
            ).show()

            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            startActivity(intent)
        } else {
            Dexter.withContext(this).withPermissions(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
                .withListener(object : MultiplePermissionsListener {
                    override fun onPermissionsChecked(report: MultiplePermissionsReport?) {
                        if (report!!.areAllPermissionsGranted()) {
                            getCurrentLocation()
                        }
                        if (report.isAnyPermissionPermanentlyDenied) {
                            Toast.makeText(
                                this@MainActivity,
                                "You have denied location permission",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }

                    override fun onPermissionRationaleShouldBeShown(
                        permissins: MutableList<PermissionRequest>?,
                        token: PermissionToken?
                    ) {
                        showRationalDialogForPermissions()
                    }
                }).onSameThread().check()

        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_refresh -> {
                getCurrentLocation()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    @SuppressLint("MissingPermission")
    private fun getCurrentLocation() {
        val mLocationRequest = LocationRequest.create().apply {
            interval = TimeUnit.SECONDS.toMillis(60)
            fastestInterval = TimeUnit.SECONDS.toMillis(30)
            maxWaitTime = TimeUnit.MINUTES.toMillis(2)
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }

        mFusedLocationClient.requestLocationUpdates(
            mLocationRequest,
            mLocationCallback,
            Looper.myLooper()
        )
    }

    private val mLocationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            super.onLocationResult(locationResult)
            val mLastLocation: Location = locationResult.lastLocation!!
            val latitude = mLastLocation.latitude
            Log.i("Current Latitude", "$latitude")
            val longitude = mLastLocation.longitude
            Log.i("Current Longitude", "$longitude")
            getLocationWeatherDetails(latitude, longitude)

        }
    }

    private fun getLocationWeatherDetails(latitude: Double, longitude: Double) {
        if (Constants.isInternetAvailable(this@MainActivity)) {
            val retrofit: Retrofit = Retrofit.Builder().baseUrl(Constants.BASE_URL)
                .addConverterFactory(GsonConverterFactory.create()).build()
            val service: WeatherService =
                retrofit.create(WeatherService::class.java)
            val listCall: Call<WeatherResponse> =
                service.getWeather(latitude, longitude, Constants.METRIC_UNIT, Constants.APP_ID)

            showCustomProgressDialog()

            listCall.enqueue(object : Callback<WeatherResponse> {
                @RequiresApi(Build.VERSION_CODES.N)
                override fun onResponse(
                    call: Call<WeatherResponse>,
                    response: Response<WeatherResponse>
                ) {
                    if (response.isSuccessful) {
                        hideProgressDialog()
                        val weatherList: WeatherResponse? = response.body()
                        setupUI(weatherList)
                        Log.i("Response Result", "$weatherList")
                    } else {
                        val rc = response.code()
                        when (rc) {
                            400 -> {
                                Log.e("ERROR 400", "Bad Connection")
                            }
                            404 -> {
                                Log.e("ERROR 404", "Not Found")
                            }
                            else -> {
                                Log.e("Error", "Generic Error")
                            }
                        }
                    }
                }

                override fun onFailure(call: Call<WeatherResponse>, t: Throwable) {
                    Log.e("Error", t.message.toString())
                    hideProgressDialog()
                }

            })
        } else {
            Toast.makeText(this@MainActivity, "No internet connection", Toast.LENGTH_LONG).show()
        }
    }


    private fun showRationalDialogForPermissions() {
        AlertDialog.Builder(this)
            .setMessage(
                "It looks like you have turned off permissions required for this feature. It can be enabled under Application Settings"
            )
            .setPositiveButton(
                "GO TO SETTINGS"
            ) { _, _ ->
                try {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    val uri = Uri.fromParts("package", packageName, null)
                    intent.data = uri
                    startActivity(intent)
                } catch (e: ActivityNotFoundException) {
                    e.printStackTrace()
                }
            }.setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }.show()
    }

    // This provides access to the system location services
    private fun isLocationEnabled(): Boolean {
        val locationManager: LocationManager =
            getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    private fun showCustomProgressDialog() {
        mProgressDialog = Dialog(this)
        mProgressDialog!!.setContentView(R.layout.dialog_custom_progress)
        mProgressDialog!!.show()
    }

    private fun hideProgressDialog() {
        if (mProgressDialog != null) {
            mProgressDialog!!.dismiss()
        }
    }


    @RequiresApi(Build.VERSION_CODES.N)
    private fun setupUI(weatherList: WeatherResponse?) {
        for (i in weatherList!!.weather.indices) {
            Log.i("Weather Name", weatherList.weather[i].toString())

            binding.apply {
                tvMain.text = weatherList.weather[i].main
                tvMainDescription.text = weatherList.weather[i].description
                tvTemp.text =
                    "${weatherList.main.temp}${getUnit(application.resources.configuration.locales.toString())}"
                tvHumidity.text = "${weatherList.main.humidity} per cent"
                tvMin.text = "${weatherList.main.temp_min} min"
                tvMax.text = "${weatherList.main.temp_max} max"
                tvSpeed.text = weatherList.wind.speed.toString()
                tvName.text = weatherList.name
                tvCountry.text = weatherList.sys.country
                tvSunriseTime.text = unixTime(weatherList.sys.sunrise)
                tvSunsetTime.text = unixTime(weatherList.sys.sunset)

                when (weatherList.weather[i].icon) {
                    "01d" -> ivMain.setImageResource(R.drawable.sunny)
                    "02d" -> ivMain.setImageResource(R.drawable.cloud)
                    "03d" -> ivMain.setImageResource(R.drawable.cloud)
                    "04d" -> ivMain.setImageResource(R.drawable.cloud)
                    "04n" -> ivMain.setImageResource(R.drawable.cloud)
                    "10d" -> ivMain.setImageResource(R.drawable.rain)
                    "11d" -> ivMain.setImageResource(R.drawable.storm)
                    "13d" -> ivMain.setImageResource(R.drawable.snowflake)
                    "01n" -> ivMain.setImageResource(R.drawable.cloud)
                    "02n" -> ivMain.setImageResource(R.drawable.cloud)
                    "03n" -> ivMain.setImageResource(R.drawable.cloud)
                    "10n" -> ivMain.setImageResource(R.drawable.cloud)
                    "11n" -> ivMain.setImageResource(R.drawable.rain)
                    "13n" -> ivMain.setImageResource(R.drawable.snowflake)
                    "50d" -> ivMain.setImageResource(R.drawable.mist)
                }
            }
        }
    }

    private fun getUnit(value: String): String {
        Log.i("Unit", value)
        var value = "°C"
        if ("US" == value || "LR" == value || "MM" == value) {
            value = "°F"
        }
        return value
    }

    private fun unixTime(timex: Long): String? {
        val date = Date(timex * 1000L)

        @SuppressLint("SimpleDateFormat")
        val sdf = SimpleDateFormat("HH:mm")
        sdf.timeZone = TimeZone.getDefault()
        return sdf.format(date)
    }
}


