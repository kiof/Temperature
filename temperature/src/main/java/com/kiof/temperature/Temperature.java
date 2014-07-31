package com.kiof.temperature;

import java.lang.reflect.Array;
import java.util.Random;

import org.json.JSONException;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.AudioManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.text.Html;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnLongClickListener;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewSwitcher;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;

public class Temperature extends FragmentActivity {

	private Context mContext;
	private Resources mResources;
	private SharedPreferences mSharedPreferences;
	private LocationManager mLocationManager;
	private Location lastKnownLocation;
	private ViewSwitcher mViewSwitcher;

	protected static final String MY_PICTURE = "mypicture";
	protected static final String KEEP_MY_PICTURE = "keepmypicture";
	protected static final String RANDOM = "random";
	protected static final String TAG = "Temperature";

	protected static final String NTP_SERVER = "pool.ntp.org";
	protected static final int NTP_NB_TRY = 5;
	protected static final int NTP_SLEEP_TIME = 1000;
	protected static final int TIME_WAIT = 3;
	protected static final int RETURN_SETTING = 1;
	protected static final int NETWORK_TIMEOUT = 10000;

    private String[] names;
	private TypedArray pictures;

	private String[] countries;
	private TypedArray flags;
	private TypedArray sounds;
	private int initVolume, maxVolume;
	private long gpsTime = 0, gpsDelta = 0, ntpTime = 0, ntpDelta = 0, newDelta = 0, sysTime = 0;
	private int currentPosition;
	private int myPicture;

	private TextView cityText;
	private ImageView imgView;
	private TextView condDescr;
	private TextView temp;
    private TextView hum;
	private TextView press;
	private TextView windSpeed;
	private TextView windDeg;

    private ViewPager mPager;
    private PagerAdapter mPagerAdapter;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

        AdView adView = (AdView) this.findViewById(R.id.adView);
        AdRequest adRequest = new AdRequest.Builder()
                .addTestDevice(AdRequest.DEVICE_ID_EMULATOR)
//                .addTestDevice("53356E870D99B80A68F8E2DBBFCD28FB")
                .build();
        adView.loadAd(adRequest);

        mViewSwitcher = (ViewSwitcher) findViewById(R.id.viewSwitcher);
		
		String initRequest = "?lat=&lon=";

		mContext = this.getApplicationContext();
		mResources = mContext.getResources();
		mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(mContext);

		pictures = mResources.obtainTypedArray(R.array.pictures);
		names = mResources.getStringArray(R.array.names);

		cityText = (TextView) findViewById(R.id.cityText);
		condDescr = (TextView) findViewById(R.id.condDescr);
		temp = (TextView) findViewById(R.id.temp);
		hum = (TextView) findViewById(R.id.hum);
		press = (TextView) findViewById(R.id.press);
		windSpeed = (TextView) findViewById(R.id.windSpeed);
		windDeg = (TextView) findViewById(R.id.windDeg);
		imgView = (ImageView) findViewById(R.id.condIcon);

//		cityText.setVisibility(View.GONE);
		cityText.setText("City");
		cityText.setVisibility(View.VISIBLE);
		
		// Acquire a reference to the system Location Manager
		mLocationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);

		// Register the listener with the Location Manager to receive location updates
		mLocationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, locationListener);

		lastKnownLocation = mLocationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);

		JSONWeatherTask weatherTask = new JSONWeatherTask();
		weatherTask.execute(new String[] { initRequest });

		findViewById(R.id.wheather).setOnLongClickListener(
				new OnLongClickListener() {

					@Override
					public boolean onLongClick(View v) {
						mViewSwitcher.showNext();
						return false;
					}
				});

        // Instantiate a ViewPager and a PagerAdapter.
        mPager = (ViewPager) findViewById(R.id.pager);
        mPagerAdapter = new ScreenSlidePagerAdapter(getSupportFragmentManager());
        mPager.setAdapter(mPagerAdapter);
        mPager.setOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override
            public void onPageSelected(int position) {
                // When changing pages, reset the action bar actions since they are dependent
                // on which page is currently active. An alternative approach is to have each
                // fragment expose actions itself (rather than the activity exposing actions),
                // but for simplicity, the activity provides the actions in this sample.
                invalidateOptionsMenu();
                currentPosition = position;
        		Log.d(TAG, "currentPosition : " + currentPosition);
            }
        });
//        mPager.setPageTransformer(true, new ZoomOutPageTransformer());
        mPager.setPageTransformer(true, new DepthPageTransformer());


		if (mSharedPreferences.getBoolean(KEEP_MY_PICTURE, false)) {
			myPicture = mSharedPreferences.getInt(MY_PICTURE, -1);
		} else if (mSharedPreferences.getBoolean(RANDOM, false)) {
			myPicture = new Random().nextInt(pictures.length());
		} else {
			myPicture = 0;
		}

		if (myPicture >= 0 || myPicture <= pictures.length()) {
			mPager.setCurrentItem(myPicture);
			((ImageView) findViewById(R.id.background)).setImageDrawable(pictures.getDrawable(myPicture));
		}
	}

	public void chooseBackgound(View view) {
		Log.d(TAG, "currentPosition : " + currentPosition);
		myPicture = currentPosition;
		((ImageView) findViewById(R.id.background)).setImageDrawable(pictures.getDrawable(myPicture));

		if (mSharedPreferences.getBoolean(KEEP_MY_PICTURE, false)) {
			// setSetting();
			SharedPreferences.Editor editor = mSharedPreferences.edit();
			editor.putInt(MY_PICTURE, myPicture);
			editor.apply();
		}
		
		mViewSwitcher.showPrevious();

	}

    private class ScreenSlidePagerAdapter extends FragmentStatePagerAdapter {
        public ScreenSlidePagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int position) {
            return ScreenSlidePageFragment.create(position);
        }

        @Override
        public int getCount() {
            return names.length;
        }
    }
	
	@Override
	public void onStart() {
		super.onStart();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.menu, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle item selection
		switch (item.getItemId()) {
		case R.id.setting:
			startActivityForResult(new Intent(Temperature.this, Setting.class),
					RETURN_SETTING);
			return true;
		case R.id.share:
			Intent sharingIntent = new Intent(Intent.ACTION_SEND);
			sharingIntent.setType("text/plain");
			sharingIntent.putExtra(Intent.EXTRA_TITLE,
					getString(R.string.share_title));
			sharingIntent.putExtra(Intent.EXTRA_SUBJECT,
					getString(R.string.share_title));
			sharingIntent.putExtra(Intent.EXTRA_TEMPLATE,
					Html.fromHtml(getString(R.string.share_link)));
			sharingIntent.putExtra(Intent.EXTRA_TEXT,
					Html.fromHtml(getString(R.string.share_link)));
			startActivity(Intent.createChooser(sharingIntent,
					getString(R.string.share_with)));
			return true;
		case R.id.about:
			new HtmlAlertDialog(this, R.raw.about,
					getString(R.string.about_title),
					android.R.drawable.ic_menu_info_details).show();
			return true;
		case R.id.other:
			Intent otherIntent = new Intent(Intent.ACTION_VIEW);
			otherIntent.setData(Uri.parse(getString(R.string.other_link)));
			startActivity(otherIntent);
			return true;
		case R.id.quit:
			// Create out AlterDialog
			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setTitle(R.string.quit_title);
			builder.setIcon(android.R.drawable.ic_menu_close_clear_cancel);
			builder.setMessage(R.string.quit_message);
			builder.setCancelable(true);
			builder.setPositiveButton(R.string.yes,
					new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int which) {
							finish();
						}
					});
			builder.setNegativeButton(R.string.no,
					new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int which) {
							Toast.makeText(mContext, R.string.goingon,
									Toast.LENGTH_SHORT).show();
						}
					});
			builder.show();
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}

	@Override
	protected void onStop() {
		super.onStop();
		// Remove the listener you previously added
		mLocationManager.removeUpdates(locationListener);
	}

	protected void onDestroy() {
		super.onDestroy();
		if (!mSharedPreferences.getBoolean(KEEP_MY_PICTURE, false)) {
			myPicture = -1;
			// setSetting();
			SharedPreferences.Editor editor = mSharedPreferences.edit();
			editor.putInt(MY_PICTURE, myPicture);
			editor.apply();
		}
	}

	// Define a listener that responds to location updates
	LocationListener locationListener = new LocationListener() {
		    public void onLocationChanged(Location location) {
		      // Called when a new location is found by the network location provider.
		      makeUseOfNewLocation(location);
		    }

		    private void makeUseOfNewLocation(Location location) {
				Toast.makeText(mContext, R.string.message, Toast.LENGTH_SHORT).show();
				String request = "?lat=" + location.getLatitude() + "&lon=" + location.getLongitude();

				JSONWeatherTask weatherTask = new JSONWeatherTask();
				weatherTask.execute(new String[] { request });
			}

			public void onStatusChanged(String provider, int status, Bundle extras) {}

		    public void onProviderEnabled(String provider) {}

		    public void onProviderDisabled(String provider) {}
		  };

	private class JSONWeatherTask extends AsyncTask<String, Void, Weather> {

			@Override
			protected Weather doInBackground(String... params) {
				Weather weather = new Weather();
				String data = ((new WeatherHttpClient()).getWeatherData(params[0]));

				try {
					weather = JSONWeatherParser.getWeather(data);

					// Let's retrieve the icon
					weather.iconData = ((new WeatherHttpClient()).getImage(weather.currentCondition.getIcon()));

				} catch (JSONException e) {
					e.printStackTrace();
				}
				return weather;

			}

			@Override
			protected void onPostExecute(Weather weather) {
				super.onPostExecute(weather);

				if (weather.iconData != null && weather.iconData.length > 0) {
					Bitmap img = BitmapFactory.decodeByteArray(weather.iconData, 0, weather.iconData.length);
					imgView.setImageBitmap(img);
				}

				cityText.setText(weather.location.getCity() + "," + weather.location.getCountry());
				cityText.setVisibility(View.VISIBLE);

				condDescr.setText(weather.currentCondition.getCondition() + "(" + weather.currentCondition.getDescr() + ")");
				temp.setText("" + Math.round((weather.temperature.getTemp() - 275.15)) + "°C");
				hum.setText("" + weather.currentCondition.getHumidity() + "%");
				press.setText("" + weather.currentCondition.getPressure() + " hPa");
				windSpeed.setText("" + weather.wind.getSpeed() + " mps");
				windDeg.setText("" + weather.wind.getDeg() + "°");

			}

		}

}