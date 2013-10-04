package com.example.spheroapp;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import orbotix.robot.base.CollisionDetectedAsyncData;
import orbotix.robot.base.ConfigureCollisionDetectionCommand;
import orbotix.robot.base.ConfigureLocatorCommand;
import orbotix.robot.base.DeviceAsyncData;
import orbotix.robot.base.DeviceMessenger;
import orbotix.robot.base.DeviceSensorsAsyncData;
import orbotix.robot.base.RGBLEDOutputCommand;
import orbotix.robot.base.Robot;
import orbotix.robot.base.RobotControl;
import orbotix.robot.base.RobotProvider;
import orbotix.robot.base.RollCommand;
import orbotix.robot.base.SetDataStreamingCommand;
import orbotix.robot.sensor.DeviceSensorsData;
import orbotix.robot.sensor.LocatorData;
import orbotix.view.calibration.CalibrationView;
import orbotix.view.calibration.widgets.ControllerActivity;
import orbotix.view.connection.SpheroConnectionView;
import orbotix.view.connection.SpheroConnectionView.OnRobotConnectionEventListener;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;
import android.view.animation.TranslateAnimation;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.nuance.nmdp.speechkit.Prompt;
import com.nuance.nmdp.speechkit.SpeechKit;
import com.nuance.nmdp.speechkit.*;
import com.nuance.nmdp.speechkit.Vocalizer;

public class MainActivity extends ControllerActivity implements AnimationListener{

	// Constants
	private static final float DEFAULT_START_SPEED = 0.6f;

	/**
	 * Robot to from which we are streaming
	 */
	private Robot mRobot = null;

	/**
	 * The Sphero Connection View
	 */
	private SpheroConnectionView mSpheroConnectionView;

	private Handler mHandler = new Handler();

	/**
	 * One-Touch Calibration Button
	 */
	private CalibrationImageButtonView mCalibrationImageButtonView;

	/**
	 * Calibration View widget
	 */
	private CalibrationView mCalibrationView;	
	private static boolean isDriving = false;
	private Path currPath;
	private Route currRoute;

	private static double startX = 0.0, startY = 0.0, currX = 0.0, currY = 0.0;

	private static float speed = 0.0f;

	private static List<Route> routes = null;

	View menu;
	View app;
	boolean menuOut = false;
	AnimParams animParams = new AnimParams();

	public final int STEP_INITIAL = 0;
	public final int STEP_SELECT_SOURCE = 1;
	public final int STEP_SELECT_DEST   = 3;
	public final int STEP_ORIENT_GUIDE = 5;
	public final int STEP_START_GUIDE   = 7;
	public final int STEP_DONE   = 9;
	public int mCurrentStep = STEP_INITIAL;

	public String mSourceRoomName="origin";
	public String mDestRoomName="room1";

	/**
	 * Navigate the Sphero on the given route, driving one path at a time.
	 * @param route the route to navigate
	 */
	private void navigateRoute(Route route) {
		if(route == null)
			return;
		
		currRoute = route;
		currPath = currRoute.getFirstPath();
		isDriving = true;
		speed = DEFAULT_START_SPEED;
		RollCommand.sendCommand(mRobot, (float) currPath.getDegree(), speed);
	}

	public static final String TTS_KEY = "com.nuance.nmdp.sample.tts";

	private Vocalizer _vocalizer;
	private Object _lastTtsContext = null;
	private static SpeechKit _speechKit;

	/**
	 * AsyncDataListener that will be assigned to the DeviceMessager, listen for streaming data, and then do the
	 *
	 */
	private DeviceMessenger.AsyncDataListener mDataListener = new DeviceMessenger.AsyncDataListener() {
		@Override
		public void onDataReceived(DeviceAsyncData data) {
			
			if(data instanceof DeviceSensorsAsyncData){
				//get the frames in the response
				List<DeviceSensorsData> data_list = ((DeviceSensorsAsyncData)data).getAsyncData();
				if(data_list != null){

					// Iterate over each frame, however we set data streaming as only one frame
					for(DeviceSensorsData datum : data_list){
						LocatorData locatorData = datum.getLocatorData();
						if( locatorData != null ) {
							currX = locatorData.getPositionX();
							currY = locatorData.getPositionY();

							double currDistance = Math.sqrt(Math.pow(currX - startX, 2) + Math.pow(currY - startY, 2));
							double targetDistance = currPath == null ? 0 : currPath.getDistance();

							if (!isDriving) {
								startX = currX;
								startY = currY;

							}
							else if (isDriving && speed == DEFAULT_START_SPEED
									&& currDistance / targetDistance > 0.6f)
							{
								speed /= 1.5; // slow down the Sphero when we approach target
								RollCommand.sendCommand(mRobot, (float)currPath.getDegree(), speed);
							}
							else if (isDriving && currDistance > targetDistance) {
								// Get the next path, if there is one
								Path nextPath = currRoute.next();
								if (nextPath != null) {
									// There is another path, so send a new roll command
									currPath = nextPath;
									startX = currX;
									startY = currY;
									speed = DEFAULT_START_SPEED;
									String text = "";
									_lastTtsContext = new Object();
									if(currPath.getDegree() == 90)
									{
										text = "Turn Right";
									}
									else if (currPath.getDegree() == 0)
									{
										text = "Go Straight";
									}
									else if (currPath.getDegree() == 270)
									{
										text = "Turn Left";
									}
									_vocalizer.speakString(text, _lastTtsContext);
									RollCommand.sendCommand(mRobot, (float) currPath.getDegree(), speed);
									Log.d("Sphero", "New path at x=" + currX + " y=" + currY);
								}
								else {
									// No more paths :( Stop Sphero.
									isDriving = false;
									RollCommand.sendStop(mRobot);
									Log.d("Sphero", "Stop at x=" + currX + " y=" + currY);
									// Reset the coordinate system and flip the orientation
									// so that Sphero is already configured for next route.
									ConfigureLocatorCommand.sendCommand(mRobot,
											ConfigureLocatorCommand.ROTATE_WITH_CALIBRATE_FLAG_ON, 0, 0, 0);
									new RobotControl(mRobot).rotate(180.0f);
								}
							}
						}
					}
				}
			}
			else if (data instanceof CollisionDetectedAsyncData)
			{
				// Update the UI with the collision
				Toast.makeText(MainActivity.this, R.string.collision_detected, Toast.LENGTH_SHORT).show();
				
				// Change the color of the ball to RED
				MainActivity.this.changeColor(false);
				
				// Change it to BLUE after a sec
	            final Handler handler = new Handler();
	            handler.postDelayed(new Runnable()
	            {
	                public void run() 
	                {
	                	// Reset it to normal color (BLUE)
	                    changeColor(true);
	                }
	            }, 300);
			}
		}
	};

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		menu = findViewById(R.id.menu);
		app = findViewById(R.id.app);
		SavedState lastSaved = (SavedState)getLastNonConfigurationInstance();
		if (lastSaved == null)
		{
			_speechKit = SpeechKit.initialize(getApplication().getApplicationContext(), AppInfo.SpeechKitAppId, AppInfo.SpeechKitServer, AppInfo.SpeechKitPort, AppInfo.SpeechKitSsl, AppInfo.SpeechKitApplicationKey);
			_speechKit.connect();
			Prompt beep = _speechKit.defineAudioPrompt(R.raw.beep);
			_speechKit.setDefaultRecognizerPrompts(beep, Prompt.vibration(100), null, null);
		}
		else
		{
			_speechKit = lastSaved.sk;
		}
		setVolumeControlStream(AudioManager.STREAM_MUSIC);

		// Create Vocalizer listener
		Vocalizer.Listener vocalizerListener = new Vocalizer.Listener()
		{
			@Override
			public void onSpeakingBegin(Vocalizer vocalizer, String text, Object context) {
				//Toast.makeText(MainActivity.this, "Playing text: \"" + text + "\"", Toast.LENGTH_LONG).show();
				// for debugging purpose: printing out the speechkit session id
				android.util.Log.d("Nuance SampleVoiceApp", "Vocalizer.Listener.onSpeakingBegin: session id ["
						+ MainActivity._speechKit.getSessionId() + "]");
			}

			@Override
			public void onSpeakingDone(Vocalizer vocalizer,
					String text, SpeechError error, Object context) 
			{
				// Use the context to detemine if this was the final TTS phrase
				if (context != _lastTtsContext)
				{
					//Toast.makeText(MainActivity.this, "More phrases remaining" + text + "\"", Toast.LENGTH_LONG).show();
				} else
				{
					//Toast.makeText(MainActivity.this, "" + text + "\"", Toast.LENGTH_LONG).show();
				}   
			}
		};
		// If this Activity is being recreated due to a config change (e.g. 
		// screen rotation), check for the saved state.
		SavedState savedState = (SavedState)getLastNonConfigurationInstance();
		if (savedState == null)
		{
			// Create a single Vocalizer here.
			_vocalizer = _speechKit.createVocalizerWithLanguage("en_US", vocalizerListener, new Handler());        
			// Get selected voice from the spinner and set the Vocalizer voice
			_vocalizer.setVoice("Samantha");
		}

		// Check for text from the intent (present if we came from DictationView)
		else
		{
			_vocalizer = savedState.Vocalizer;
			_lastTtsContext = savedState.Context;
			//Toast.makeText(this, savedState.Text, Toast.LENGTH_LONG).show();
			// Need to update the listener, since the old listener is pointing at
			// the old TtsView activity instance.
			_vocalizer.setListener(vocalizerListener);
		}

		app.findViewById(R.id.BtnSlide).setOnClickListener(new ClickListener());

		mSpheroConnectionView = (SpheroConnectionView)findViewById(R.id.sphero_connection_view);
		// Set the connection event listener 
		mSpheroConnectionView.setOnRobotConnectionEventListener(new OnRobotConnectionEventListener() {
			// If the user clicked a Sphero and it failed to connect, this event will be fired
			@Override
			public void onRobotConnectionFailed(Robot robot) {}
			// If there are no Spheros paired to this device, this event will be fired
			@Override
			public void onNonePaired() {}
			// The user clicked a Sphero and it successfully paired.
			@Override
			public void onRobotConnected(Robot robot) {
				mRobot = robot;
				setRobot(mRobot);
				// Skip this next step if you want the user to be able to connect multiple Spheros
				mSpheroConnectionView.setVisibility(View.GONE);
				mCalibrationView.setRobot(mRobot);

				// This delay post is to give the connection time to be created
				mHandler.postDelayed(new Runnable() {
					@Override
					public void run() {
						// Start streaming Locator values
						requestDataStreaming();

						// Reset coordinate system of Locator 
						ConfigureLocatorCommand.sendCommand(mRobot, ConfigureLocatorCommand.ROTATE_WITH_CALIBRATE_FLAG_ON, 0, 0, 0);
						
						// Configure the collision detector
						ConfigureCollisionDetectionCommand.sendCommand(mRobot, ConfigureCollisionDetectionCommand.DEFAULT_DETECTION_METHOD,
        						45, 45, 100, 100, 100);

						//Set the AsyncDataListener that will process each response.
						DeviceMessenger.getInstance().addAsyncDataListener(mRobot, mDataListener);
					}
				}, 1000);
			}
			@Override
			public void onBluetoothNotEnabled() {
				// See UISample Sample on how to show BT settings screen, for now just notify user
				Toast.makeText(MainActivity.this, "Bluetooth Not Enabled", Toast.LENGTH_LONG).show();
			}
		});
		mCalibrationView = (CalibrationView)findViewById(R.id.calibration_view);
		// Initialize calibrate button view where the calibration circle shows above button
		// This is the default behavior
		mCalibrationImageButtonView = (CalibrationImageButtonView)findViewById(R.id.calibration_image_button);
		mCalibrationImageButtonView.setCalibrationView(mCalibrationView);
		// You can also change the size and location of the calibration views (or you can set it in XML)
		mCalibrationImageButtonView.setRadius(100);
		mCalibrationImageButtonView.setOrientation(CalibrationView.CalibrationCircleLocation.ABOVE);

		updateUI();
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		if (_vocalizer != null)
		{
			_vocalizer.cancel();
			_vocalizer = null;
		}
		if (_speechKit != null)
		{
			_speechKit.release();
			_speechKit = null;
		}
	}

	@Override
	public boolean dispatchTouchEvent(MotionEvent event)
	{
		mCalibrationView.interpretMotionEvent(event);
		return super.dispatchTouchEvent(event);
	}

	private static Route getDesiredRoute(String source, String destination)
	{
		setUpRoutes();
		for(Route r : routes)
		{
			if(r.getStartLoc().equals(source) && r.getEndLoc().equals(destination))
			{
				return r;
			}
		}
		return null;
	}

	/**
	 * Set up the hard-coded routes
	 */
	private static void setUpRoutes()
	{
		routes = new ArrayList<Route>();
		Route oToRoom1 = new Route("origin", "room1");
		Path r1P1 = new Path(0, 100);
		Path r1P2 = new Path(270, 50);
		oToRoom1.addPath(r1P1);
		oToRoom1.addPath(r1P2);
		routes.add(oToRoom1);

		Route oToRoom2 = new Route("origin", "room2");
		Path r2P1 = new Path(0, 200);
		Path r2P2 = new Path(270, 50);
		oToRoom2.addPath(r2P1);
		oToRoom2.addPath(r2P2);
		routes.add(oToRoom2);

		Route oToRoom3 = new Route("origin", "room3");
		Path r3P1 = new Path(0, 150);
		Path r3P2 = new Path(90, 50);
		oToRoom3.addPath(r3P1);
		oToRoom3.addPath(r3P2);
		routes.add(oToRoom3);

		Route r1ToRoom2 = new Route("room1", "room2");
		Path r1R2P1 = new Path(0, 50);
		Path r1R2P2 = new Path(270, 100);
		Path r1R2P3 = new Path(180, 50);
		r1ToRoom2.addPath(r1R2P1);
		r1ToRoom2.addPath(r1R2P2);
		r1ToRoom2.addPath(r1R2P3);
		routes.add(r1ToRoom2);

		Route r2ToRoom1 = new Route("room2", "room1");
		Path r2R1P1 = new Path(0, 50);
		Path r2R1P2 = new Path(90, 100);
		Path r2R1P3 = new Path(180, 50);
		r2ToRoom1.addPath(r2R1P1);
		r2ToRoom1.addPath(r2R1P2);
		r2ToRoom1.addPath(r2R1P3);
		routes.add(r2ToRoom1);

		Route r1ToRoom3 = new Route("room1", "room3");
		Path r1R3P1 = new Path(0, 50);
		Path r1R3P2 = new Path(270, 50);
		Path r1R3P3 = new Path(0, 50);
		r1ToRoom3.addPath(r1R3P1);
		r1ToRoom3.addPath(r1R3P2);
		r1ToRoom3.addPath(r1R3P3);
		routes.add(r1ToRoom3);

		Route r3ToRoom1 = new Route("room3", "room1");
		Path r3R1P1 = new Path(0, 50);
		Path r3R1P2 = new Path(270, 50);
		Path r3R1P3 = new Path(0, 50);
		r3ToRoom1.addPath(r3R1P1);
		r3ToRoom1.addPath(r3R1P2);
		r3ToRoom1.addPath(r3R1P3);
		routes.add(r3ToRoom1);

		Route r2ToRoom3 = new Route("room2", "room3");
		Path r2R3P1 = new Path(0, 50);
		Path r2R3P2 = new Path(90, 50);
		Path r2R3P3 = new Path(0, 50);
		r1ToRoom3.addPath(r2R3P1);
		r1ToRoom3.addPath(r2R3P2);
		r1ToRoom3.addPath(r2R3P3);
		routes.add(r2ToRoom3);

		Route r3ToRoom2 = new Route("room3", "room2");
		Path r3R2P1 = new Path(0, 50);
		Path r3R2P2 = new Path(90, 50);
		Path r3R2P3 = new Path(0, 50);
		r3ToRoom2.addPath(r3R2P1);
		r3ToRoom2.addPath(r3R2P2);
		r3ToRoom2.addPath(r3R2P3);
		routes.add(r3ToRoom2);

	}

	class ClickListener implements OnClickListener 
	{
		@Override
		public void onClick(View v) 
		{
			System.out.println("onClick " + new Date());
			slideLeftPaneInOrOut();
		}
	}

	void slideLeftPaneInOrOut()
	{
		MainActivity me = MainActivity.this;
		Context context = me;
		Animation anim;

		int w = app.getMeasuredWidth();
		int h = app.getMeasuredHeight();
		int left = (int) (app.getMeasuredWidth() * 0.3);

		if (!menuOut) {
			// anim = AnimationUtils.loadAnimation(context, R.anim.push_right_out_80);
			anim = new TranslateAnimation(0, left, 0, 0);
			menu.setVisibility(View.VISIBLE);
			animParams.init(left, 0, left + w, h);

			//ImageButton slideButton = (ImageButton) findViewById(R.id.BtnSlide);
			//slideButton.setImageResource(R.drawable.arrow_left_32x32);
		} else {
			// anim = AnimationUtils.loadAnimation(context, R.anim.push_left_in_80);
			anim = new TranslateAnimation(0, -left, 0, 0);
			animParams.init(0, 0, w, h);

			//ImageButton slideButton = (ImageButton) findViewById(R.id.BtnSlide);
			//slideButton.setImageResource(R.drawable.arrow_right_32x32);
		}

		anim.setDuration(500);
		anim.setAnimationListener(me);
		//Tell the animation to stay as it ended (we are going to set the app.layout first than remove this property)
		anim.setFillAfter(true);


		// Only use fillEnabled and fillAfter if we don't call layout ourselves.
		// We need to do the layout ourselves and not use fillEnabled and fillAfter because when the anim is finished
		// although the View appears to have moved, it is actually just a drawing effect and the View hasn't moved.
		// Therefore clicking on the screen where the button appears does not work, but clicking where the View *was* does
		// work.
		// anim.setFillEnabled(true);
		// anim.setFillAfter(true);

		app.startAnimation(anim);
	}

	public void onStepButtonClick(View v) 
	{
		switch(v.getId())
		{
		case R.id.btn_01_select_source:

			slideLeftPaneInOrOut();
			mCurrentStep = STEP_SELECT_SOURCE;
			break;

		case R.id.btn_02_select_dest:

			slideLeftPaneInOrOut();
			mCurrentStep = STEP_SELECT_DEST;
			break;

		case R.id.btn_03_orient_guide:

			mCurrentStep = STEP_ORIENT_GUIDE;
			// Show the caliberation view.
			findViewById(R.id.calibration_image_button).setVisibility(View.VISIBLE);
			
			// Make tick mark visible
			findViewById(R.id.tick_03).setVisibility(View.VISIBLE);
			
			menuOut = false;
			break;

		case R.id.btn_04_start_guide:

			slideLeftPaneInOrOut();
			mCurrentStep = STEP_START_GUIDE;
			navigateRoute(getDesiredRoute(mSourceRoomName, mDestRoomName));
			
			findViewById(R.id.calibration_image_button).setVisibility(View.INVISIBLE);
			
			// Make tick mark visible
			findViewById(R.id.tick_04).setVisibility(View.VISIBLE);
			break;
			
		case R.id.btn_05_restart_tour:
			
			resetUI();
			break;
			
		default:
		}
		
		updateUI();
	}

	public void onRoomButtonClick(View v)
	{
		String selectedRoom = "";
		String status_str_part1 = "";

		switch(v.getId())
		{
		case R.id.destOne_btn:

			selectedRoom="room1";
			status_str_part1 = "'Room 1'";

			break;

		case R.id.destTwo_btn:

			selectedRoom="room2";
			status_str_part1 = "'Room 2'";

			break;

		case R.id.destThree_btn:

			selectedRoom="room3";
			status_str_part1 = "'Room 3'";

			break;

		case R.id.destOrigin_btn:

			selectedRoom="origin";
			status_str_part1 = "'Room Origin'";

			break;
		}

		String status_str_part2 = " selected as ";

		if(mCurrentStep == STEP_SELECT_SOURCE)
		{
			mSourceRoomName = selectedRoom;
			slideLeftPaneInOrOut();
			mCurrentStep = STEP_SELECT_DEST;

			String statusStr = status_str_part1 + status_str_part2 + "Source.";
			TextView statusTextview = (TextView) findViewById(R.id.app_status);
			statusTextview.setText(statusStr);

			// Change the color of the button to green
			Button selectedButton = (Button) findViewById(v.getId());
			selectedButton.setBackgroundResource(R.drawable.btn_green);

			// Make tick mark visible
			findViewById(R.id.tick_01).setVisibility(View.VISIBLE);
			findViewById(R.id.btn_01_select_source).setEnabled(false);
			
			findViewById(R.id.btn_01_select_source).setEnabled(false);
			findViewById(R.id.btn_02_select_dest).setEnabled(true);
			findViewById(R.id.btn_03_orient_guide).setEnabled(false);
			findViewById(R.id.btn_04_start_guide).setEnabled(false);
		}
		else if(mCurrentStep == STEP_SELECT_DEST)
		{
			mDestRoomName = selectedRoom;
			slideLeftPaneInOrOut();

			mCurrentStep = STEP_ORIENT_GUIDE;

			String statusStr = status_str_part1 + status_str_part2 + "Destination.";
			TextView statusTextview = (TextView) findViewById(R.id.app_status);
			statusTextview.setText(statusStr);

			// Change the color of the button to blue	
			Button selectedButton = (Button) findViewById(v.getId());
			selectedButton.setBackgroundResource(R.drawable.btn_blue);

			// Make tick mark visible
			findViewById(R.id.tick_02).setVisibility(View.VISIBLE);
			findViewById(R.id.btn_02_select_dest).setEnabled(false);
			
			findViewById(R.id.btn_01_select_source).setEnabled(false);
			findViewById(R.id.btn_02_select_dest).setEnabled(false);
			findViewById(R.id.btn_03_orient_guide).setEnabled(true);
			findViewById(R.id.btn_04_start_guide).setEnabled(false);
		}
	}

	void updateUI()
	{
		TextView statusTextview = (TextView) findViewById(R.id.app_status);
		switch(mCurrentStep)
		{
		case STEP_INITIAL:

			statusTextview.setText("Press on 'Select Source' to Start!");

			findViewById(R.id.btn_01_select_source).setEnabled(true);
			findViewById(R.id.btn_02_select_dest).setEnabled(false);
			findViewById(R.id.btn_03_orient_guide).setEnabled(false);
			findViewById(R.id.btn_04_start_guide).setEnabled(false);
			break;

		case STEP_SELECT_SOURCE:

			statusTextview.setText("Please Select Source.");

//			findViewById(R.id.btn_01_select_source).setEnabled(false);
//			findViewById(R.id.btn_02_select_dest).setEnabled(true);
//			findViewById(R.id.btn_03_orient_guide).setEnabled(false);
//			findViewById(R.id.btn_04_start_guide).setEnabled(false);
			break;

		case STEP_SELECT_DEST:

			statusTextview.setText("Please Select Destination.");

//			findViewById(R.id.btn_01_select_source).setEnabled(false);
//			findViewById(R.id.btn_02_select_dest).setEnabled(false);
//			findViewById(R.id.btn_03_orient_guide).setEnabled(true);
//			findViewById(R.id.btn_04_start_guide).setEnabled(false);
			break;

		case STEP_ORIENT_GUIDE:

			statusTextview.setText("Please Orient Guideo.");

			findViewById(R.id.btn_01_select_source).setEnabled(false);
			findViewById(R.id.btn_02_select_dest).setEnabled(false);
			findViewById(R.id.btn_03_orient_guide).setEnabled(false);
			findViewById(R.id.btn_04_start_guide).setEnabled(true);
			break;

		case STEP_START_GUIDE:

			statusTextview.setText("Follow Guideo!");
			
			findViewById(R.id.btn_01_select_source).setEnabled(false);
			findViewById(R.id.btn_02_select_dest).setEnabled(false);
			findViewById(R.id.btn_03_orient_guide).setEnabled(false);
			findViewById(R.id.btn_04_start_guide).setEnabled(false);
			break;

		case STEP_DONE:
		}
	}
	
	void resetUI()
	{
		mCurrentStep = STEP_INITIAL;
		// Make tick mark visible
		findViewById(R.id.tick_01).setVisibility(View.GONE);
		findViewById(R.id.tick_02).setVisibility(View.GONE);
		findViewById(R.id.tick_03).setVisibility(View.GONE);
		findViewById(R.id.tick_04).setVisibility(View.GONE);
		
		mSourceRoomName = "";
		mDestRoomName = "";
		
		((Button) findViewById(R.id.destOne_btn)).setBackgroundResource(R.drawable.btn_grey);
		((Button) findViewById(R.id.destTwo_btn)).setBackgroundResource(R.drawable.btn_grey);
		((Button) findViewById(R.id.destThree_btn)).setBackgroundResource(R.drawable.btn_grey);
		((Button) findViewById(R.id.destOrigin_btn)).setBackgroundResource(R.drawable.btn_grey);
					
		updateUI();
	}

	/**
	 * Called when the user presses the back or home button
	 */
	@Override
	protected void onPause()
	{
		super.onPause();
		//Set the AsyncDataListener that will process each response.
		DeviceMessenger.getInstance().removeAsyncDataListener(mRobot, mDataListener); 
		// Disconnect Robot properly
		RobotProvider.getDefaultProvider().disconnectControlledRobots();
	}

	/**
	 * Called when the user comes back to this app
	 */
	@Override
	protected void onResume() {
		super.onResume();
		// Refresh list of Spheros
		mSpheroConnectionView.showSpheros();
	}

	private void requestDataStreaming(){

		if(mRobot == null) return;

		// Set up a bitmask containing the sensor information we want to stream, in this case locator
		// with which only works with Firmware 1.20 or greater.
		final long mask = SetDataStreamingCommand.DATA_STREAMING_MASK_LOCATOR_ALL;

		//Specify a divisor. The frequency of responses that will be sent is 400hz divided by this divisor.
		final int divisor = 50;

		//Specify the number of frames that will be in each response. You can use a higher number to "save up" responses
		//and send them at once with a lower frequency, but more packets per response.
		final int packet_frames = 1;

		// Count is the number of async data packets Sphero will send you before
		// it stops.  Use a count of 0 for infinite data streaming.
		final int response_count = 0;

		// Send this command to Sphero to start streaming.  
		// If your Sphero is on Firmware less than 1.20, Locator values will display as 0's
		SetDataStreamingCommand.sendCommand(mRobot, divisor, packet_frames, mask, response_count);
	}

	/*
	@Override
	public boolean onCreateOptionsMenu(Menu menu) 
	{
		boolean result = super.onCreateOptionsMenu(menu);

		menu.add(0, 1, 1, "Local Map");
		menu.add(0, 2, 2, "Google Map");

		return result;
	}

	public boolean onOptionsItemSelected(MenuItem item)
	{
		int id = item.getItemId();
		switch( id ) 
		{
		case 1:	// Local Map

			break;

		case 2: // Google Map

			break;


		}

		return true;
	}*/

	public void onCollisionDetectorButtonClicked(View v) {
		// Launch the collision detection activity
		Intent intent = new Intent(this, CollisionActivity.class);
		this.startActivity(intent);
	}

	public void onControlSpheroButtonClicked(View v) {
		// Launch the collision detection activity
		Intent intent = new Intent(this, AllenMain.class);
		this.startActivity(intent);
	}

	void layoutApp(boolean menuOut) {
		System.out.println("layout [" + animParams.left + "," + animParams.top + "," + animParams.right + ","
				+ animParams.bottom + "]");
		app.layout(animParams.left, animParams.top, animParams.right, animParams.bottom);
		//Now that we've set the app.layout property we can clear the animation, flicker avoided :)
		app.clearAnimation();

	}

	private class SavedState
	{
		int TextColor;
		String Text;
		Vocalizer Vocalizer;
		Object Context;
		SpeechKit sk;
	}
	@Override
	public Object onRetainNonConfigurationInstance()
	{
		// Save the SpeechKit instance, because we know the Activity will be
		// immediately recreated.
		_speechKit = null; // Prevent onDestroy() from releasing SpeechKit
		SavedState savedState = new SavedState();
		savedState.Vocalizer = _vocalizer;
		savedState.Context = _lastTtsContext;
		savedState.sk = _speechKit;
		_vocalizer = null; // Prevent onDestroy() from canceling
		return savedState;
	}

	@Override
	public void onAnimationEnd(Animation animation) {
		System.out.println("onAnimationEnd");
		//        ViewUtils.printView("menu", menu);
		//        ViewUtils.printView("app", app);
		menuOut = !menuOut;
		if (!menuOut) {
			menu.setVisibility(View.INVISIBLE);
		}
		layoutApp(menuOut);
	}

	@Override
	public void onAnimationRepeat(Animation animation) {
		System.out.println("onAnimationRepeat");
	}

	@Override
	public void onAnimationStart(Animation animation) {
		System.out.println("onAnimationStart");
	}

	static class AnimParams {
		int left, right, top, bottom;

		void init(int left, int top, int right, int bottom) {
			this.left = left;
			this.top = top;
			this.right = right;
			this.bottom = bottom;
		}
	}
	
	/**
     * Causes the robot color to change on collision.
     * @param normal
     */
    private void changeColor(final boolean normal){
        
        if(mRobot != null)
        {
            // If normal, send command to show BLUE light, or else, send command to show RED light
            if(normal)
                RGBLEDOutputCommand.sendCommand(mRobot, 0, 0, 255);
            else
                RGBLEDOutputCommand.sendCommand(mRobot, 255, 0, 0);
        }
    }

}
