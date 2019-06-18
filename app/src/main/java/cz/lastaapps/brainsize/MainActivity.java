package cz.lastaapps.brainsize;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Vibrator;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewFlipper;

import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.analytics.FirebaseAnalytics.*;
import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.messaging.FirebaseMessaging;

import java.io.IOException;
import java.io.InputStream;
import java.util.Random;

public class MainActivity extends AppCompatActivity {

    //to better understand the app, run it first

    /**contains different pages (next time only pg) (main, test, result)*/
    ViewFlipper flipper;

    /**starts test, on the main pg, switches to text pg*/
    Button start;
    /**on main pg, shows text describing the methods of measuring*/
    TextView howWork;

    /**on test pg, must be pressed for 3 sec at all, then starts computing process*/
    Button scanning;
    /**on result pg, show progress of testing*/
    ProgressBar bar, computingBar;
    /**on test pg, label pointing to scanning button*/
    TextView label;

    /**on result pg, shows result, a subjects brain*/
    ImageView imageView;
    /**Shows about on click*/
    ImageButton skullView;
    /**on result pg, restart the app, shows main pg*/
    Button restart;

    /**used during scanning process*/
    ScanningTask st;
    /**stop tasks if they are running*/
    boolean finished = false;

    //used to cheat in app
    /**the name of image to set if countOfSet > 0*/
    String nameToSet = null;
    /**how many times will be cheated brain shown, if is 0, img will be generated randomly*/
    int countOfSet = 0;

    /**number of test in one app instance*/
    static int numberOfTests = 0;
    /**time when a session started*/
    static long sessionStarted = 0L;

    /**ignores onStop when FingActivity was opened*/
    boolean ignoreActivityOnStop = false;

    /**used to make analytics about the number of app start, test and cheat menu opens*/
    private FirebaseAnalytics analytics;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //fullscreen
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_main);

        //default values
        numberOfTests = 0;
        sessionStarted = 0;

        //get analytics from App class
        analytics = App.getAnalytics();

        //init Firebase cloud messaging
        FirebaseMessaging.getInstance().setAutoInitEnabled(true);

        System.out.println("Token: " + FirebaseInstanceId.getInstance().getInstanceId());

        //init
        flipper = findViewById(R.id.flipper);
        start  = findViewById(R.id.start);
        scanning = findViewById(R.id.scanning);
        bar = findViewById(R.id.bar);
        computingBar = findViewById(R.id.computing_bar);
        imageView = findViewById(R.id.imageView);
        skullView = findViewById(R.id.skull_view);
        restart = findViewById(R.id.restart);
        label = findViewById(R.id.input_label);
        howWork = findViewById(R.id.how_work);

        //start the test
        start.setOnTouchListener(new View.OnTouchListener() {
            //time, when the button was pressed
            long lastTime = 0l;

            @Override
            public boolean onTouch(View v, MotionEvent e) {

                //called on button press
                if (e.getAction() == MotionEvent.ACTION_DOWN) {
                    System.out.println("start on pressed");

                    //saves time
                    lastTime = System.currentTimeMillis();

                } else if (e.getAction() == MotionEvent.ACTION_UP){
                    System.out.println("start on released");

                    long time = System.currentTimeMillis();
                    //checks, if button was pressed at least for 3 sec
                    if (time <= lastTime + 3000) {
                        //shows test pg
                        finished = false;
                        flipper.setDisplayedChild(1);
                    } else {

                        //sending event to Firebase
                        Bundle data = new Bundle();
                        data.putDouble(Param.VALUE, ((time - lastTime) / 1000));
                        analytics.logEvent("fing_opened", data);

                        ignoreActivityOnStop = true;

                        //starts FingActivity
                        Intent intent = new Intent(MainActivity.this, FingActivity.class);
                        startActivityForResult(intent, FingActivity.REQUEST_CODE);
                    }
                }

                return false;
            }
        });

        //if is pressed, makes the test
        scanning.setOnTouchListener(new View.OnTouchListener() {

            /**used to hide setOnTouchListener method ;)*/
            void a(){}

            @Override
            public boolean onTouch(View view, MotionEvent e) {
                //if is nothing happening, starts new task
                if (st == null) {
                    st = new ScanningTask();
                    st.execute();
                }

                //stops event if button is invisible
                if (scanning.getVisibility() == View.INVISIBLE)
                    return false;


                if (e.getAction() == MotionEvent.ACTION_DOWN) {
                    System.out.println("scan on pressed");

                    //resumes everything
                    label.setText(R.string.scanning);
                    bar.setVisibility(View.VISIBLE);
                    st.setPaused(false);

                } else if (e.getAction() == MotionEvent.ACTION_UP){
                    System.out.println("scan on released");

                    //pauses everything
                    label.setText(R.string.touch_the_button);
                    bar.setVisibility(View.INVISIBLE);
                    st.setPaused(true);
                }

                return false;
            }
        });

        //singed as "try your friends", restarts the app
        restart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startNew();
            }
        });

        //label opens popup, witch describes app functionality
        howWork.setOnClickListener(new View.OnClickListener() {
            /**used to hide setOnTouchListener method ;)*/
            void a(){}

            @Override
            public void onClick(View v) {
                AlertDialog dialog = new AlertDialog.Builder(MainActivity.this)
                        .setCancelable(true)
                        .create();

                View view = View.inflate(MainActivity.this, R.layout.how_work_dialog, null);
                TextView text = (TextView) view.findViewById(R.id.how_work_text);
                //formatting the text because off new lines
                text.setText(getString(R.string.how_work_text).replace("\\n", "\n"));
                dialog.setView(view);
                dialog.setCanceledOnTouchOutside(true);

                final long time = System.currentTimeMillis();
                dialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialog) {
                        Bundle bundle = new Bundle();
                        bundle.putInt(Param.VALUE, (int) Math.round((System.currentTimeMillis() - time) / 1000));
                        App.getAnalytics().logEvent("how_work", bundle);
                    }
                });
                dialog.show();
            }
        });

        //shows about
        skullView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AlertDialog dialog = new AlertDialog.Builder(MainActivity.this)
                        .setCancelable(true)
                        .create();

                View view = View.inflate(MainActivity.this, R.layout.about_dialog, null);

                TextView version = view.findViewById(R.id.version);
                TextView release = view.findViewById(R.id.release);
                TextView author = view.findViewById(R.id.author);

                version.setText(getString(R.string.version) + ": " + BuildConfig.VERSION_NAME);
                release.setText(getString(R.string.release) + ": " + String.format("%03d", BuildConfig.VERSION_CODE));
                author.setText(getString(R.string.author) + " " + (BuildConfig.BUILD_TIME.getYear() + 1900));

                ImageButton share = view.findViewById(R.id.share);
                share.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Toast.makeText(getApplicationContext(), R.string.soon, Toast.LENGTH_LONG).show();
                    }
                });
                ImageButton facebook = view.findViewById(R.id.facebook);
                facebook.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        String url = "https://www.facebook.com/lastaapps/";
                        Uri uri = Uri.parse(url);
                        try {
                            ApplicationInfo applicationInfo = getPackageManager().getApplicationInfo("com.facebook.katana", 0);
                            if (applicationInfo.enabled) {
                                uri = Uri.parse("fb://facewebmodal/f?href=" + url);
                            }
                        } catch (PackageManager.NameNotFoundException ignored) {}

                        startActivity(new Intent(Intent.ACTION_VIEW, uri));
                    }
                });
                ImageButton web = view.findViewById(R.id.web);
                web.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Toast.makeText(getApplicationContext(), R.string.soon, Toast.LENGTH_LONG).show();
                    }
                });

                dialog.setView(view);
                dialog.setCanceledOnTouchOutside(true);
                dialog.show();
            }
        });

        //bar init
        bar.setMax(100);
        bar.setVisibility(View.INVISIBLE);

    }

    /**loads and sets image*/
    public void setImage(String name) {
        try {
            System.gc();
            AssetManager assets = getAssets();
            InputStream in = assets.open("img/" + name);
            final Bitmap bitmap = BitmapFactory.decodeStream(in);
            in.close();
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    imageView.setImageBitmap(bitmap);
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**used to handle data from FingActivity or to restore them from previous instance*/
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (FingActivity.REQUEST_CODE == requestCode) {
            if (resultCode == Activity.RESULT_OK) {
                //if the image was selected, saves the values
                nameToSet = data.getStringExtra("name");
                countOfSet = data.getIntExtra("number", 1);
                if (nameToSet == null)
                    countOfSet = 0;
            }
            System.gc();
        } else
            super.onActivityResult(requestCode, resultCode, data);
    }

    /**if the app is not on main pg, moves to it, or closes the app*/
    @Override
    public void onBackPressed() {
        if (flipper.getDisplayedChild() == 0) {
            super.onBackPressed();
        } else {
            startNew();
        }
    }

    /**starts ne instance of MainActivity*/
    private void startNew() {
        //stops background tasks
        finished = true;

        //resets all values and view to default
        st = null;
        flipper.setDisplayedChild(0);

        bar.setVisibility(View.INVISIBLE);
        computingBar.setVisibility(View.INVISIBLE);
        scanning.setVisibility(View.VISIBLE);

        bar.setProgress(0);
        computingBar.setProgress(0);
        label.setText(R.string.touch_the_button);

    }

    @Override
    protected void onStart() {
        super.onStart();

        ignoreActivityOnStop = false;

        if (sessionStarted == 0) {
            sessionStarted = System.currentTimeMillis();
        }
    }

    /**Sends data about the number of tests and session time to Firebase*/
    @Override
    protected void onStop() {
        super.onStop();

        System.out.println("MainActivity onStop()");

        if (!ignoreActivityOnStop) {
            sendDataOnStop();
        }
    }

    /**Sends data about the number of tests and session time to Firebase*/
    public static void sendDataOnStop() {
        Bundle bundle = new Bundle();
        bundle.putInt("number_of_tests", numberOfTests);
        bundle.putInt("session_time", Math.round((System.currentTimeMillis() - sessionStarted) / 1000));
        App.getAnalytics().logEvent("session_stop", bundle);

        numberOfTests = 0;
        sessionStarted = 0;
    }

    /**background process when the scanning button is pressed/released*/
    class ScanningTask extends AsyncTask<Object, Integer, Object> {

        //when is true, the scanning button is not pressed
        boolean isPaused = true;

        Vibrator vibrator;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            //vibrator init
            vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        }

        @Override
        protected Object doInBackground(Object... objects) {

            System.out.println("starting scanning");

            for (int i = 0; i <= 100; i++) {

                //if an activity is finishing after back button press, stops task
                if (finished == true) {
                    return null;
                }

                //do nothing when button is not pressed
                while (isPaused == true) {

                    //if an activity is finishing after back button press, stops task
                    if (finished == true) {
                        return null;
                    }

                    //one pressed cycle last 1 mls
                    try {
                        Thread.sleep(1);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                //update UI
                publishProgress(i);

                //wait to user to interrupt
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            //returns Obj, so onPostExecute knows it was not interrupted by back button
            return new Object();
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            bar.setProgress(values[0]);
        }

        @Override
        protected void onPostExecute(Object o) {
            super.onPostExecute(o);

            vibrator.cancel();

            if (o == null)
                return;

            //hiding components
            scanning.setVisibility(View.INVISIBLE);
            bar.setVisibility(View.VISIBLE);

            //starts next task, computing brain size
            new CalculateTask().execute();
        }

        //pauses/resumes the scanning process
        public void setPaused(boolean paused) {
            isPaused = paused;

            if (paused == false) {
                AudioManager audio = (AudioManager) MainActivity.this.getSystemService(Context.AUDIO_SERVICE);
                if ((audio.getRingerMode() != AudioManager.RINGER_MODE_SILENT)
                        && vibrator.hasVibrator())
                    vibrator.vibrate(5000);
            } else {
                vibrator.cancel();
            }
        }
    }

    /**background process moving progressbar and loading images*/
    class CalculateTask extends AsyncTask<Object, Integer, Object> {

        /**prepares scene*/
        @Override
        protected void onPreExecute() {
            label.setText(R.string.counting);
            computingBar.setVisibility(View.VISIBLE);
        }

        @Override
        protected Object doInBackground(Object... objects) {
            System.out.println("starting calculating");

            //the name of the result img
            String imgName = null;

            //loads the image to RAM
            if (countOfSet <= 0) {
                //if not cheated loads random image
                try {
                    AssetManager assets = getAssets();
                    String[] array = assets.list("img");
                    imgName = array[new Random().nextInt(array.length)];

                } catch (IOException e) {
                    e.printStackTrace();
                }
            } else {
                //if cheated loads named img
                imgName = nameToSet;
            }

            //shows image
            setImage(imgName);


            //progress bar stops at epicPause, waits here and then continues to 100%
            int epicPause = 95;

            //updates progressbar and checks for interrupt from back button
            if (!waitingCycle(0, epicPause, true)) {
                return null;
            }
            if (!waitingCycle(0, 50, false)) {
                return null;
            }
            if (!waitingCycle(epicPause, 100, true)) {
                return null;
            }
            if (!waitingCycle(0, 20, false)) {
                return null;
            }

            countOfSet--;


            //sending event to Firebase
            Bundle data = new Bundle();
            data.putString(Param.CONTENT, imgName);
            analytics.logEvent("test_done", data);

            //increasing tests number
            numberOfTests++;

            //returns Obj, so onPostExecute knows it was not interrupted by back button
            return new Object();
        }

        /**@param   output  if output is false, just counts down, if true, updates the progressbar
         * @returns if progress was stopped by back button*/
        public boolean waitingCycle(int begin, int end, boolean output) {
            for (int i = begin; i <= end; i++) {

                if (output)
                    publishProgress(i);

                //if an activity is finishing after back button press, stops task
                if (finished == true) {
                    return false;
                }

                try {
                    Thread.sleep(20);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            return true;
        }

        /**updates progressbar*/
        @Override
        protected void onProgressUpdate(Integer... values) {
            computingBar.setProgress(values[0]);
            bar.setProgress(values[0]);
        }

        /**displays result pg*/
        @Override
        protected void onPostExecute(Object o) {
            super.onPostExecute(o);

            if (o == null) {
                return;
            }

            flipper.setDisplayedChild(2);

        }
    }
}
