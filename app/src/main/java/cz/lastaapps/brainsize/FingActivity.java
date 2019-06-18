package cz.lastaapps.brainsize;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.AppCompatImageButton;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;

import com.google.firebase.analytics.FirebaseAnalytics;

import java.io.IOException;
import java.io.InputStream;

//this activity is used to cheat in program, to setup a few next brains
public class FingActivity extends AppCompatActivity {

    /**request used in MainActivity*/
    public static final int REQUEST_CODE = 4321;

    /**shows the images of available brains*/
    LinearLayout list;

    /**used to reset previous set values*/
    Button reset;

    /**the number of times when selected brain will be shown*/
    EditText input;

    /**used in the counting process of images sizes*/
    int screenWidth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //fullscreen
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.fing_activity);

        //default value
        setResult(Activity.RESULT_CANCELED);

        //getting screen width
        Point point = new Point();
        getWindowManager().getDefaultDisplay().getSize(point);
        screenWidth = point.x - 10;

        /*//enabling back arrow in left top corner
        android.support.v7.app.ActionBar actionBar = getSupportActionBar();
        actionBar.setHomeButtonEnabled(true);
        actionBar.setDisplayHomeAsUpEnabled(true);
        */

        //init
        list = (LinearLayout) findViewById(R.id.list);
        reset = (Button) findViewById(R.id.reset);
        input = (EditText) findViewById(R.id.input);

        //reset action, returns RESULT_OK and null name to main
        reset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onImageClicked(null);
            }
        });

        //filing up list with images saved in assets/img
        try {
            System.gc();
            for (String name : getAssets().list("img")) {
                list.addView(new BrainView(this, name));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    /**Useless now...*/
    /**Sends data about the number of tests and session time to Firebase*/
    @Override
    protected void onStop() {
        super.onStop();

        //MainActivity.sendDataOnStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        list.removeAllViews();
        System.gc();
    }

    /**handles back arrow action*/
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**called when brain is selected*/
    public void onImageClicked(String name) {
        //data to send back to MainActivity
        Intent intent = new Intent();
        int number = Integer.parseInt(input.getText().toString());
        intent.putExtra("name", name);
        intent.putExtra("number", number);
        setResult(Activity.RESULT_OK, intent);

        //sends data to Firebase
        Bundle bundle = new Bundle();
        bundle.putString("img_name", name);
        bundle.putInt("number", number);
        App.getAnalytics().logEvent("fing_selected", bundle);

        finish();
    }

    /**used to show brain image, used in list*/
    public class BrainView extends AppCompatImageButton {

        //the name of asset img
        String name;

        public BrainView(final Context context, String fileName) {
            super(context);

            name = fileName;

            setMinimumWidth(screenWidth);
            setMaxWidth(screenWidth);

            //if the image was selected, call onImageClicked() to close activity
            setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    onImageClicked(name);
                }
            });

            //image loading background thread
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        //loading image into RAM
                        AssetManager assets = context.getAssets();
                        Bitmap map = BitmapFactory.decodeStream(assets.open("img/" + name));

                        //preparing transforming
                        int width = map.getWidth();
                        int height = map.getHeight();

                        Matrix matrix = new Matrix();
                        matrix.postScale(screenWidth /width, screenWidth /height);

                        try {
                            //transforming img
                            map = Bitmap.createBitmap(map, 0, 0, width, height, matrix, true);
                            matrix = null;
                        } catch (Exception e) {
                            System.out.println("Error " + e.getMessage() + " when transforming img " + name);
                            e.printStackTrace();
                        }

                        final Bitmap toShow = map;

                        FingActivity.this.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                //setting img to show
                                setImageBitmap(toShow);
                            }
                        });

                        System.gc();

                    } catch (Exception e) {
                        System.out.println("Error " + e.getMessage() + " when loading img " + name);
                        e.printStackTrace();
                    }
                }
            }).start();
        }

        //if the name is same, the obj are equals
        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof BrainView))
                return false;

            BrainView br = (BrainView) obj;

            if (name.equals(br.name))
                return true;

            return false;
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
