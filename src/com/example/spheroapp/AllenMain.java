package com.example.spheroapp;

import java.util.List;

import orbotix.robot.base.ConfigureLocatorCommand;
import orbotix.robot.base.DeviceAsyncData;
import orbotix.robot.base.DeviceMessenger;
import orbotix.robot.base.DeviceSensorsAsyncData;
import orbotix.robot.base.Robot;
import orbotix.robot.base.RobotProvider;
import orbotix.robot.base.RollCommand;
import orbotix.robot.base.SetDataStreamingCommand;
import orbotix.robot.sensor.DeviceSensorsData;
import orbotix.robot.sensor.LocatorData;

import orbotix.view.calibration.CalibrationView;
import orbotix.view.calibration.widgets.ControllerActivity;
import orbotix.view.connection.SpheroConnectionView;
import orbotix.view.connection.SpheroConnectionView.OnRobotConnectionEventListener;
import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Menu;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;


public class AllenMain extends ControllerActivity {

	/**
	 * Robot to from which we are streaming
	 */
	private Robot mRobot = null;

	/**
	 * The Sphero Connection View
	 */
	private SpheroConnectionView mSpheroConnectionView;
    /**
     * One-Touch Calibration Button
     */
    private CalibrationImageButtonView mCalibrationImageButtonView;
    
    /**
     * Calibration View widget
     */
    private CalibrationView mCalibrationView;
    

	private Handler mHandler = new Handler();
	
	private static boolean isRolling = false;
	
	private static double lastStoppedX = 0.0, lastStoppedY = 0.0,
			currX = 0.0, currY = 0.0;

	private static double targetDistance = 0;
	private static float newDirection = 0.0f;
	private static float speed = 0.0f;
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
							double currSpeed = Math.sqrt(Math.pow(locatorData.getVelocityX(), 2) + Math.pow(locatorData.getVelocityY(), 2));
							((TextView)findViewById(R.id.txt_locator_x)).setText(currX + " cm");
							((TextView)findViewById(R.id.txt_locator_y)).setText(currY + " cm");
							((TextView)findViewById(R.id.txt_locator_vx)).setText(lastStoppedX + " cm");
							((TextView)findViewById(R.id.txt_locator_vy)).setText(lastStoppedY + " cm");
							((TextView)findViewById(R.id.txt_sphero_speed)).setText(currSpeed + " cm/s");
							double currDistance = Math.sqrt(Math.pow(currX - lastStoppedX, 2) + Math.pow(currY - lastStoppedY, 2));
							
							((TextView)findViewById(R.id.txt_distance)).setText(currDistance + " cm");
							
							if (!isRolling && currDistance == 0) {
								lastStoppedX = currX;
								lastStoppedY = currY;
							}
							else if (isRolling && currDistance / targetDistance > 0.6f)
							{
								speed /= 1.5;
								if (speed < 0.05 && targetDistance - currDistance >= 10)
								{
									speed *= 3;
								}
								((TextView)findViewById(R.id.txt_locator_vy)).setText(speed + " cm");
						        RollCommand.sendCommand(mRobot, newDirection, speed);
							}
							else if (isRolling && currDistance > targetDistance) {
								Toast.makeText(AllenMain.this, "*****STOP*****", Toast.LENGTH_SHORT).show();
								Log.d("SpheroStop", "Stop command at x=" + currX + " y=" + currY);
								double v_x = locatorData.getVelocityX();
								double v_y = locatorData.getVelocityY();
								double v_comb = Math.sqrt(Math.pow(v_x, 2) + Math.pow(v_y, 2));
								Log.d("SpheroStop", "Velocity at time of stop: " + v_comb + " cm/s");
								isRolling = false;
								lastStoppedX = currX;
								lastStoppedY = currY;
								RollCommand.sendStop(mRobot);
								//break; // stop looping through data once stopped
							}
						}
					}
				}
			}
		}
	};
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_allen_main);
		findViewById(R.id.back_layout).requestFocus();
		
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

						//Set the AsyncDataListener that will process each response.
						DeviceMessenger.getInstance().addAsyncDataListener(mRobot, mDataListener);
					}
				}, 1000);
			}
			@Override
			public void onBluetoothNotEnabled() {
				// See UISample Sample on how to show BT settings screen, for now just notify user
				Toast.makeText(AllenMain.this, "Bluetooth Not Enabled", Toast.LENGTH_LONG).show();
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
	}

	@Override
    public boolean dispatchTouchEvent(MotionEvent event) {
    	mCalibrationView.interpretMotionEvent(event);
    	return super.dispatchTouchEvent(event);
    }
	 /**
     * When the user clicks the configure button, it calls this function
     * @param v
     */
    public void configurePressed(View v) {

        if( mRobot == null ) return;
        newDirection = 0; 
        targetDistance = 0;

        // Try parsing the integer values from the edit text boxes, if not, use zeros
        try {
            newDirection = Integer.parseInt(((EditText)findViewById(R.id.edit_direction)).getText().toString());
        } catch (NumberFormatException e) {}

        try {
            targetDistance = Integer.parseInt(((EditText)findViewById(R.id.edit_distance)).getText().toString());
        } catch (NumberFormatException e) {}
        SeekBar speedBar = ((SeekBar) findViewById(R.id.speed));
        speed = (float)speedBar.getProgress() / speedBar.getMax();
        isRolling = true;
        lastStoppedX = currX;
        lastStoppedY = currY;
		((TextView)findViewById(R.id.txt_locator_vy)).setText(speed + " cm");
        RollCommand.sendCommand(mRobot, newDirection, speed);
    }
    
	/**
     * Called when the user presses the back or home button
     */
    @Override
    protected void onPause() {
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
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

}
