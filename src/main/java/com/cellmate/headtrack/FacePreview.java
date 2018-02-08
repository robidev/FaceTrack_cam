package com.cellmate.headtrack;

import java.io.IOException;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.os.Handler;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.vision.CameraSource;
import com.google.android.gms.vision.Detector;
import com.google.android.gms.vision.Tracker;
import com.google.android.gms.vision.face.Face;
import com.google.android.gms.vision.face.FaceDetector;
import com.google.android.gms.vision.face.LargestFaceFocusingProcessor;


public class FacePreview extends Activity {
    private static final String TAG             = "Sample::Activity";
    
    public static float         minFaceSize = 0.5f;
    
    public static final int     VIEW_MODE_RGBA  = 0;
    public static final int     VIEW_MODE_GRAY  = 1;
    public static final int     VIEW_MODE_CANNY = 2;

    public static final float EPSILON = 0.01f;
    
    private MenuItem            mItemFace50;
    private MenuItem            mItemFace40;
    private MenuItem            mItemFace30;
    private MenuItem            mItemFace20;

    float prevRotateLeftRight = 0f;
	float prevRotateUpDown = 0f;
    //private TiltCalc mTiltCalc = null;
    
    private ClearGLSurfaceView mGLSurfaceView;
    private Handler mHandler = new Handler();
    private FaceDetector mFaceDetector;
    private CameraSource mCameraSource;

    private static final int RC_HANDLE_GMS = 9001;
    // permission request codes need to be < 256
    private static final int RC_HANDLE_CAMERA_PERM = 2;

    public FacePreview() {
        Log.i(TAG, "Instantiated new " + this.getClass());
    }

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "onCreate");
        super.onCreate(savedInstanceState);
        
	      final Window win = getWindow(); 
	      win.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        
        mGLSurfaceView = new ClearGLSurfaceView(this); //new GLSurfaceView(this);
        
        setContentView(mGLSurfaceView);
        mGLSurfaceView.requestFocus();
        mGLSurfaceView.setFocusableInTouchMode(true);
        
        // This will make sure we update the renderer with the values received from the tilt sensors
        //mTiltCalc = new TiltCalc(this,mGLSurfaceView.mRenderer);
        
        mHandler.removeCallbacks(mUpdateTimeTask);
        mHandler.postDelayed(mUpdateTimeTask, 100);

        // Check for the camera permission before accessing the camera.  If the
        // permission is not granted yet, request permission.
        int rc = ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA);
        if (rc == PackageManager.PERMISSION_GRANTED) {
            createCameraSource();
        } else {
            requestCameraPermission();
        }
    }


    /**
     * Creates and starts the camera.  Note that this uses a higher resolution in comparison
     * to other detection examples to enable the barcode detector to detect small barcodes
     * at long distances.
     */
    private void createCameraSource() {

        mFaceDetector = new FaceDetector.Builder(this)
                .setProminentFaceOnly(true) // optimize for single, relatively large face
                .setTrackingEnabled(true) // enable face tracking
                .setClassificationType(/* eyes open and smile */ FaceDetector.NO_CLASSIFICATIONS)
                .setMode(FaceDetector.ACCURATE_MODE) // for one face this is OK
                .setLandmarkType(FaceDetector.NO_LANDMARKS)
                .build();
        mFaceDetector.setProcessor(new LargestFaceFocusingProcessor(mFaceDetector, new FaceTracker()));

        if (! mFaceDetector.isOperational()) {
            // Note: The first time that an app using face API is installed on a device, GMS will
            // download a native library to the device in order to do detection.
            Log.w(TAG, "Face detector dependencies are not yet available.");
        }

        mCameraSource = new CameraSource.Builder(this, mFaceDetector)
                .setRequestedPreviewSize(640, 480) // the lower the resolution the better the speed.
                .setFacing(CameraSource.CAMERA_FACING_FRONT) // use front camera
                .setRequestedFps(30f)
                .build();
    }

    /**
     * Handles the requesting of the camera permission.  This includes
     * showing a "Snackbar" message of why the permission is needed then
     * sending the request.
     */
    private void requestCameraPermission() {
        Log.w(TAG, "Camera permission is not granted. Requesting permission");

        final String[] permissions = new String[]{Manifest.permission.CAMERA};

        if (!ActivityCompat.shouldShowRequestPermissionRationale(this,
                Manifest.permission.CAMERA)) {
            ActivityCompat.requestPermissions(this, permissions, RC_HANDLE_CAMERA_PERM);
            return;
        }

        final Activity thisActivity = this;

        View.OnClickListener listener = new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                ActivityCompat.requestPermissions(thisActivity, permissions,
                        RC_HANDLE_CAMERA_PERM);
            }
        };

        Snackbar.make(mGLSurfaceView, R.string.permission_camera_rationale,
                Snackbar.LENGTH_INDEFINITE)
                .setAction(R.string.ok, listener)
                .show();
    }

    /**
     * Callback for the result from requesting permissions. This method
     * is invoked for every call on {@link #requestPermissions(String[], int)}.
     * <p>
     * <strong>Note:</strong> It is possible that the permissions request interaction
     * with the user is interrupted. In this case you will receive empty permissions
     * and results arrays which should be treated as a cancellation.
     * </p>
     *
     * @param requestCode  The request code passed in {@link #requestPermissions(String[], int)}.
     * @param permissions  The requested permissions. Never null.
     * @param grantResults The grant results for the corresponding permissions
     *                     which is either {@link PackageManager#PERMISSION_GRANTED}
     *                     or {@link PackageManager#PERMISSION_DENIED}. Never null.
     * @see #requestPermissions(String[], int)
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode != RC_HANDLE_CAMERA_PERM) {
            Log.d(TAG, "Got unexpected permission result: " + requestCode);
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
            return;
        }

        if (grantResults.length != 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "Camera permission granted - initialize the camera source");
            // we have permission, so create the camerasource
            createCameraSource();
            return;
        }

        Log.e(TAG, "Permission not granted: results len = " + grantResults.length +
                " Result code = " + (grantResults.length > 0 ? grantResults[0] : "(empty)"));

        DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                finish();
            }
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Face Tracker sample")
                .setMessage(R.string.no_camera_permission)
                .setPositiveButton(R.string.ok, listener)
                .show();
    }

    //==============================================================================================
    // Camera Source Preview
    //==============================================================================================

    /**
     * Starts or restarts the camera source, if it exists.  If the camera source doesn't exist yet
     * (e.g., because onResume was called before the camera source was created), this will be called
     * again when the camera source is created.
     */
    private void startCameraSource() {

        // check that the device has play services available.
        int code = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(
                getApplicationContext());
        if (code != ConnectionResult.SUCCESS) {
            Dialog dlg =
                    GoogleApiAvailability.getInstance().getErrorDialog(this, code, RC_HANDLE_GMS);
            dlg.show();
        }

        if (mCameraSource != null) {
            try {
                mCameraSource.start();
            } catch (IOException e) {
                Log.e(TAG, "Unable to start camera source.", e);
                mCameraSource.release();
                mCameraSource = null;
            }
        }
    }


    public class FaceTracker extends Tracker<Face> {

        FaceTracker() {
        }
        @Override
        public void onUpdate(Detector.Detections<Face> detections, Face face) {
            mGLSurfaceView.mRenderer.FaceX =face.getPosition().x; // 300 <> -300
            mGLSurfaceView.mRenderer.FaceY = face.getPosition().y;//400 <> -400
            mGLSurfaceView.mRenderer.FaceZ = face.getWidth();//0 <> 500

        }
    }

    private Runnable mUpdateTimeTask = new Runnable() {
    	   public void run() {

    			float[] currRotation = new float[] {0.0f,0.0f,0.0f,0.0f};
    		   //mTiltCalc.getTilt(currRotation);
               
               // So vals[0] is rotating the phone around like a compass, vals[1] is tilting the phone up and down, and vals[2] is tilting the phone left and right.
               // vals[2] is left/right
               if (prevRotateLeftRight == 0)
               {
               		prevRotateLeftRight = currRotation[2];
               }
               else
               {
	               	float deltaRotateLeftRight = currRotation[2] - prevRotateLeftRight;
	               	Log.d("HeadTrack", "DELTA LEFT-RIGHT " + deltaRotateLeftRight);
	               	
	               	if (Math.abs(deltaRotateLeftRight) > EPSILON)
	               	{
		               	if (deltaRotateLeftRight < 0)
		               	{
		               		mGLSurfaceView.mRenderer.handlePressRight();
		               	}
		               	else if (deltaRotateLeftRight > 0)
		               	{
		               		mGLSurfaceView.mRenderer.handlePressLeft();
		               	}
	               	}
	               	prevRotateLeftRight = currRotation[2];
               }
               
               // vals[1] is up/down
               if (prevRotateUpDown == 0)
               {
               		prevRotateUpDown = currRotation[1];
               }
               else
               {
	               	float deltaRotateUpDown = currRotation[1] - prevRotateUpDown;
	               	Log.d("HeadTrack", "DELTA UP-DOWN " + deltaRotateUpDown);
	               	
	               	if (Math.abs(deltaRotateUpDown) > EPSILON)
	               	{
		               	if (deltaRotateUpDown < 0)
		               	{
		               		mGLSurfaceView.mRenderer.handlePressDown();
		               	}
		               	else if (deltaRotateUpDown > 0)
		               	{
		               		mGLSurfaceView.mRenderer.handlePressUp();
		               	}
	               	}
	               	prevRotateUpDown = currRotation[1];
               }
    	       mHandler.postDelayed(this, 15);
    	   }
    	};
    	
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        Log.i(TAG, "onCreateOptionsMenu");
        mItemFace50 = menu.add("Face size 50%");
        mItemFace40 = menu.add("Face size 40%");
        mItemFace30 = menu.add("Face size 30%");
        mItemFace20 = menu.add("Face size 20%");
        return true;
    }
    /**
     * Restarts the camera.
     */
    @Override
    protected void onResume() {
        super.onResume();

        startCameraSource();
    }

    /**
     * Stops the camera.
     */
    @Override
    protected void onPause() {
        super.onPause();
        if (mCameraSource != null) {
            mCameraSource.stop();
        }
    }

    /**
     * Releases the resources associated with the camera source, the associated detector, and the
     * rest of the processing pipeline.
     */
    @Override
    protected void onDestroy() {
        mHandler.removeCallbacks(mUpdateTimeTask);
        super.onDestroy();
        if (mCameraSource != null) {
            mCameraSource.release();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Log.i(TAG, "Menu Item selected " + item);
        if (item == mItemFace50)
            minFaceSize = 0.5f;
        else if (item == mItemFace40)
            minFaceSize = 0.4f;
        else if (item == mItemFace30)
            minFaceSize = 0.3f;
        else if (item == mItemFace20)
            minFaceSize = 0.2f;
        return true;
    }
    
    class ClearGLSurfaceView extends GLSurfaceView {
    	OpenGLRenderer mRenderer;
    	float prevX = 0f;
    	float prevY = 0f;
    	
    	private ScaleGestureDetector mScaleDetector;
    	private float mScaleFactor = 1.f;
    	private float prevScaleFactor = 1.f;
    	
        public ClearGLSurfaceView(Context context) {
            super(context);
            
            // Create our ScaleGestureDetector
            mScaleDetector = new ScaleGestureDetector(context, new ScaleListener());
            //setEGLContextClientVersion(2);//set shader version 2
            // set our renderer to be the main renderer with
            // the current activity context
            // This is already done inside the SampleCvViewBase constructor
            mRenderer = new OpenGLRenderer(context);
            
            setRenderer(mRenderer);
            //mGLSurfaceView.setMyRenderer(iGLR);
            setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
        }

        public boolean onTouchEvent(final MotionEvent event) {
        	// Let the ScaleGestureDetector inspect all events.
            mScaleDetector.onTouchEvent(event);

            queueEvent(new Runnable(){
                public void run() {
                	if (prevX == 0 && prevY == 0)
                	{
                    	prevX = event.getX();
                    	prevY = event.getY();
                	}
                	else
                	{
                    	float deltaX = event.getX() - prevX;
                    	float deltaY = event.getY() - prevY;
                    	
                    	// Moved right
                    	if (deltaX > 0)
                    	{
                    		mRenderer.handlePressRight();
                    	}
                    	else if (deltaX < 0)
                    	{
                    		mRenderer.handlePressLeft();
                    	}
                    	
                    	// Move down
                    	if (deltaY > 0)
                    	{
                    		mRenderer.handlePressDown();
                    	}
                    	else if (deltaY < 0)
                    	{
                    		mRenderer.handlePressUp();
                    	}
                    	
                    	prevX = event.getX();
                    	prevY = event.getY();
                	}
                }});
                return true;
            }
        
        private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                mScaleFactor *= detector.getScaleFactor();
                
                // Don't let the object get too small or too large.
                mScaleFactor = Math.max(0.1f, Math.min(mScaleFactor, 5.0f));
                
                if (prevScaleFactor < mScaleFactor)
                {
                	// we enlarged
                	mRenderer.handlePressA();
                }
                else if (prevScaleFactor > mScaleFactor)
                {
                	// we made smaller
                	mRenderer.handlePressZ();
                	
                }

                prevScaleFactor = mScaleFactor;

               // invalidate();
                return true;
            }
        }
    }
}
