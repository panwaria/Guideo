package com.example.spheroapp;

import orbotix.robot.base.CollisionDetectedAsyncData;
import orbotix.robot.base.ConfigureCollisionDetectionCommand;
import orbotix.robot.base.DeviceAsyncData;
import orbotix.robot.base.DeviceMessenger;
import orbotix.robot.base.RGBLEDOutputCommand;
import orbotix.robot.base.DeviceMessenger.AsyncDataListener;
import orbotix.robot.base.Robot;
import orbotix.robot.base.RobotProvider;
import orbotix.view.connection.SpheroConnectionView;
import orbotix.view.connection.SpheroConnectionView.OnRobotConnectionEventListener;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.NavUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

public class CollisionActivity extends Activity {
	
    private SpheroConnectionView spheroConnectionView;

	private Robot mRobot;
    
    private Handler mHandler = new Handler();

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		setContentView(R.layout.activity_collision);
		// Show the Up button in the action bar.
		setupActionBar();

        spheroConnectionView = (SpheroConnectionView)findViewById(R.id.sphero_connection_view);
        // Set the connection event listener 
        spheroConnectionView.setOnRobotConnectionEventListener(new OnRobotConnectionEventListener() {
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
				// Skip this next step if you want the user to be able to connect multiple Spheros
				spheroConnectionView.setVisibility(View.GONE);
			
				// Calling Configure Collision Detection Command right after the robot connects, will not work
				// You need to wait a second for the robot to initialize
				mHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
        				// Start streaming collision detection data
        				//// First register a listener to process the data
        				DeviceMessenger.getInstance().addAsyncDataListener(mRobot,
        						mCollisionListener);

        				ConfigureCollisionDetectionCommand.sendCommand(mRobot, ConfigureCollisionDetectionCommand.DEFAULT_DETECTION_METHOD,
        						45, 45, 100, 100, 100);
                    }
                }, 1000);
			}
			
			@Override
			public void onBluetoothNotEnabled() {
				// See UISample Sample on how to show BT settings screen, for now just notify user
				Toast.makeText(CollisionActivity.this, "Bluetooth Not Enabled", Toast.LENGTH_LONG).show();
			}
		});
	}
	
    /**
     * Called when the user comes back to this app
     */
    @Override
    protected void onResume() {
    	super.onResume();
        // Refresh list of Spheros
        spheroConnectionView.showSpheros();
    }
    
    /**
     * Called when the user presses the back or home button
     */
    @Override
    protected void onPause() {
    	super.onPause();
		// Remove async data listener
		DeviceMessenger.getInstance().removeAsyncDataListener(mRobot, mCollisionListener);
    	// Disconnect Robot properly
    	RobotProvider.getDefaultProvider().disconnectControlledRobots();
    }
	
	private final AsyncDataListener mCollisionListener = new AsyncDataListener() 
	{
		public void onDataReceived(DeviceAsyncData asyncData) 
		{
			Log.d(TAG, "onDataReceived Called");
			
			if (asyncData instanceof CollisionDetectedAsyncData)
			{
				// Update the UI with the collision
				Toast.makeText(CollisionActivity.this, R.string.collision_detected, Toast.LENGTH_SHORT).show();
				
				//final CollisionDetectedAsyncData collisionData = (CollisionDetectedAsyncData) asyncData;
				//Acceleration acceleration = collisionData.getImpactAcceleration();
				
				// Change the color of the ball to RED
				CollisionActivity.this.changeColor(false);
				
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
	
	private String TAG = "CollisionActivity";
	
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
    
	/**
	 * Set up the {@link android.app.ActionBar}, if the API is available.
	 */
	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	private void setupActionBar() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
			getActionBar().setDisplayHomeAsUpEnabled(true);
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.collision, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case android.R.id.home:
			// This ID represents the Home or Up button. In the case of this
			// activity, the Up button is shown. Use NavUtils to allow users
			// to navigate up one level in the application structure. For
			// more details, see the Navigation pattern on Android Design:
			//
			// http://developer.android.com/design/patterns/navigation.html#up-vs-back
			//
			NavUtils.navigateUpFromSameTask(this);
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

}
