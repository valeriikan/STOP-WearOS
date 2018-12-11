package com.aware.app.stop_wearos;

import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Point;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.support.wearable.activity.WearableActivity;
import android.view.Display;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.aware.Accelerometer;
import com.aware.Aware;
import com.aware.Aware_Preferences;

public class MainActivity extends WearableActivity {

    // UI elements
    private FrameLayout containerLayout;
    private TextView timer;
    private BallView ballView;

    // Sensors variables
    private Accelerometer.AWARESensorObserver observerAccelerometer;

    // Timer component
    private static volatile CountDownTimer countDownTimer;

    // ball game variables
    private float ballXpos, ballXmax, ballXaccel, ballXvel = 0.0f;
    private float ballYpos, ballYmax, ballYaccel, ballYvel = 0.0f;
    private float bigCircleXpos, bigCircleYpos;
    private float smallCircleXpos, smallCircleYpos;
    private double ballMaxDistance, scoreRaw;
    private int scoreCounter;
    private Bitmap ball;
    private Bitmap circleBig;
    private Bitmap circleSmall;

    // Ball game settings variables
    private int ballSize = 80;
    private int smallCircleSize = 240;
    private int bigCircleSize = 400;
    private float sensitivity = 3; // 3.0 is default
    private int gameTime = 10000; // in milliseconds

    // sampling flag
    static boolean sampling;

    private static final String SAMPLE_KEY_DOUBLE_VALUES_0 = "double_values_0";
    private static final String SAMPLE_KEY_DOUBLE_VALUES_1 = "double_values_1";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Enables Always-on
        setAmbientEnabled();

        // Initializing views
        timer = findViewById(R.id.timer);
        containerLayout = findViewById(R.id.container);

        timer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startGame();
            }
        });

        // Initializing sensor observers
        observerAccelerometer = new Accelerometer.AWARESensorObserver() {
            @Override
            public void onAccelerometerChanged(ContentValues data) {
                ballXaccel = data.getAsFloat(SAMPLE_KEY_DOUBLE_VALUES_0);
                ballYaccel = -data.getAsFloat(SAMPLE_KEY_DOUBLE_VALUES_1);
                updateBall();
            }
        };
    }

    @Override
    protected void onResume() {
        super.onResume();

        // detection of the display size
        Point size = new Point();
        Display display = this.getWindowManager().getDefaultDisplay();
        display.getSize(size);

        bigCircleSize = size.x;
        ballSize = bigCircleSize/5;
        smallCircleSize = ballSize*3;


        // setting up the maximum allowed X and Y values
        ballXmax = (float) size.x - ballSize;
        ballYmax = (float) size.y - ballSize; // toolbar = 235, bottom nav bar = 175

        // put ball to the center
        ballXpos = ballXmax /2;
        ballYpos = ballYmax /2;

        // count maximum possible distance ball can cover from center
        ballMaxDistance = Math.sqrt(ballXpos*ballXpos + ballYpos*ballYpos);

        // put circles to the center
        smallCircleXpos = (size.x - smallCircleSize)/2;
        smallCircleYpos = (size.y - smallCircleSize)/2;
        bigCircleXpos = (size.x - bigCircleSize)/2;
        bigCircleYpos = (size.y - bigCircleSize)/2;

        // sampling to false to prevent unnecessary data recording
        sampling = false;

        // Initializing timer
        countDownTimer = new CountDownTimer(gameTime + 5000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                // updating UI according to timeframes and handling sensors
                if ((millisUntilFinished >= gameTime + 1000) && (millisUntilFinished <= gameTime + 4000)) {
                    String counter = String.valueOf((millisUntilFinished/1000 - gameTime/1000)) + "...";
                    timer.setText(counter);
                }

                if ((millisUntilFinished >= gameTime) && (millisUntilFinished < gameTime + 1000)) {
                    timer.setText(R.string.game_start);
                    sampling = true;
                }

                if ((millisUntilFinished >= 0) && (millisUntilFinished < gameTime)) {
                    String counter = String.valueOf((millisUntilFinished/1000 - gameTime/1000)*(-1));
                    timer.setText(counter);
                }
            }

            @Override
            public void onFinish() {
                sampling = false;
                if (getApplicationContext() != null) {
                    stopGame();
                }
            }
        };
    }

    // Inflate BallView and start sensors
    private void startGame() {

        timer.setText(R.string.game_get_ready);
        timer.setEnabled(false);

        // adding custom BallView to the fragment
        ballView = new BallView(getApplicationContext());
        containerLayout.addView(ballView);

        // starting sensors
        Aware.startAccelerometer(getApplicationContext());
        Accelerometer.setSensorObserver(observerAccelerometer);

        // making sample values empty (for second and following games)
        scoreRaw = 0;
        scoreCounter = 0;

        // starting timer
        countDownTimer.start();
    }

    // Stop data sampling
    private void stopGame() {

        // calculating game score
        double finalScore = 100 - ((scoreRaw/scoreCounter)/ ballMaxDistance)*100;
        String gameDone = getString(R.string.game_done_1)
                + String.format("%.1f", finalScore) + getString(R.string.game_done_2);

        // updating UI
        containerLayout.removeAllViews();
        ballView = null;
        timer.setText(gameDone);
        timer.setEnabled(true);

        // set ball coordinates to center for playinig again
        ballXpos = ballXmax /2;
        ballYpos = ballYmax /2;

        // Stopping sensors
        Accelerometer.setSensorObserver(null);
        Aware.stopAccelerometer(getApplicationContext());
        Aware.setSetting(getApplicationContext(), Aware_Preferences.STATUS_ACCELEROMETER, false);
    }

    // updating ball's X and Y positioning
    private void updateBall() {

        ballXvel = (ballXaccel * sensitivity);
        ballYvel = (ballYaccel * sensitivity);

        float xS = (ballXvel / 2) * sensitivity;
        float yS = (ballYvel / 2) * sensitivity;

        ballXpos -= xS;
        ballYpos -= yS;

        float changeX = ballXpos - ballXmax / 2;
        float changeY = ballYpos - ballYmax / 2;
        double distance = Math.sqrt(changeX * changeX + changeY * changeY);

        if (sampling) {
            scoreRaw += distance;
            scoreCounter += 1;
        }

        //off screen movements
        if (ballXpos > ballXmax) {
            ballXpos = ballXmax;
        } else if (ballXpos < 0) {
            ballXpos = 0;
        }

        if (ballYpos > ballYmax) {
            ballYpos = ballYmax;
        } else if (ballYpos < 0) {
            ballYpos = 0;
        }
    }


    // custom view for BallGame
    private class BallView extends View {

        public BallView(Context context) {
            super(context);
            // ball bitmap initializing
            Bitmap ballSrc = BitmapFactory.decodeResource(getResources(), R.drawable.ball);
            ball = Bitmap.createScaledBitmap(ballSrc, ballSize, ballSize, true);

            // circles bitmap initializing
            Bitmap smallSrc = BitmapFactory.decodeResource(getResources(), R.drawable.circle_small);
            circleSmall = Bitmap.createScaledBitmap(smallSrc, smallCircleSize, smallCircleSize, true);
            Bitmap bigSrc = BitmapFactory.decodeResource(getResources(), R.drawable.circle_big);
            circleBig = Bitmap.createScaledBitmap(bigSrc, bigCircleSize, bigCircleSize, true);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            // drawing circles
            canvas.drawBitmap(circleSmall, smallCircleXpos, smallCircleYpos, null);
            canvas.drawBitmap(circleBig, bigCircleXpos, bigCircleYpos, null);

            // drawing (and redrawing) the ball
            canvas.drawBitmap(ball, ballXpos, ballYpos, null);
            invalidate();
        }
    }
}
