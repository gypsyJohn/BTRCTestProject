package ie.gypsy.btrctestproject;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

public class MainActivity extends AppCompatActivity {

    BTClass2 tractorBT;
    BTClass1 joystickBT;
    String esp32DKAddress = "D8:A0:1D:69:E0:2A";
    String wifiLora32Address = "8C:AA:B5:83:A1:3A";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN );

        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                // Set the content to appear under the system bars so that the
                // content doesn't resize when the system bars hide and show.
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                // Hide the nav bar and status bar
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION

                | View.SYSTEM_UI_FLAG_FULLSCREEN);

        final TextView joystickX = findViewById(R.id.joystick_horizontal_textview);
        final TextView joystickY = findViewById(R.id.joystick_vertical_textview);
        final TextView fustv = findViewById(R.id.fustv);
        final TextView rustv = findViewById(R.id.rustv);
        final TextView angleTextview = findViewById(R.id.angle_texview);
        final TextView bustv = findViewById(R.id.bustv);
        final TextView lustv = findViewById(R.id.lustv);

        Handler handler = new Handler(Looper.getMainLooper()){
            @Override
            public void handleMessage(@NonNull Message msg) {
                switch (msg.what){
                    case 1:
                        byte[] joystickPositions = (byte[]) msg.obj;
                        joystickX.setText(""+(int) (joystickPositions[0] & 0xff));
                        joystickY.setText(""+(int) (joystickPositions[1] & 0xff));
                        if(tractorBT.getExampleConnectedThread() != null){
                            tractorBT.getExampleConnectedThread().write(joystickPositions);
                        }
                        break;
                    case 2:
                        byte[] tractorUltrasonicPositions = (byte[]) msg.obj;
//                        int ultrasonicSidePosition = (int) ( ( tractorUltrasonicPositions[0] << 8 ) | tractorUltrasonicPositions[1] );
                        int ultrasonicSideLower = (int) ( tractorUltrasonicPositions[1] & 0xff );
                        int ultrasonicSideUpper = (int) ( tractorUltrasonicPositions[0] & 0xff );
//                        int ultrasonicBackPosition = (int) ( ( tractorUltrasonicPositions[2] << 8 ) | tractorUltrasonicPositions[3] );
                        int ultrasonicSidePosition = (ultrasonicSideUpper * 256) + ultrasonicSideLower;
                        int ultrasonicFrontLower = (int) ( tractorUltrasonicPositions[3] & 0xff );
                        int ultrasonicFrontUpper = (int) ( tractorUltrasonicPositions[2] & 0xff );
                        int ultrasonicFrontPosition = (ultrasonicFrontUpper * 256) + ultrasonicFrontLower;

                        int uslLower = (int) (tractorUltrasonicPositions[5] & 0xff);
                        int uslUpper = (int) (tractorUltrasonicPositions[4] & 0xff);
                        int ultrasonicLeftPosition = (256 * uslUpper) + uslLower;

                        int usbLower = (int) (tractorUltrasonicPositions[7] & 0xff);
                        int usbUpper = (int) (tractorUltrasonicPositions[6] & 0xff);
                        int ultrasonicBackPosition = (256 * usbUpper) + usbLower;

                        int angleLower = (int) ( tractorUltrasonicPositions[9] & 0xff );
                        int angleUpper = (int) ( tractorUltrasonicPositions[8] & 0xff );
                        int angle = (angleUpper*256) + angleLower;
//                        tractorX.setText(""+ (int) (ultrasonicSidePosition & 0xffff));
//                        tractorY.setText(""+ (int) (ultrasonicBackPosition & 0xffff));
//                        angleTextview.setText(""+angle);
                        rustv.setText(""+ ultrasonicSidePosition);
                        fustv.setText(""+ ultrasonicFrontPosition);
                        bustv.setText(""+ultrasonicBackPosition);
                        lustv.setText(""+ultrasonicLeftPosition);
                        angleTextview.setText(""+angle);
                        break;
                }
            }
        };


        //BTClass1 communicates with RC Controller to receive joystick direction
        //BTClass2 sends joystick location to RC Tractor and receives back distances from the ultrasonic sensors
//        BTClass1 joystickBT = new BTClass1(handler,"8C:AA:B5:83:A1:3A");//Lora32
        joystickBT = new BTClass1(handler,wifiLora32Address);//esp32 dk
//
        tractorBT = new BTClass2(handler,esp32DKAddress);

//        RCTractorControlClass rcTractorControlClass = new RCTractorControlClass(handler);

//        SendDataThread sendDataThread = new SendDataThread(handler,tractorBT);
//        sendDataThread.start();

    }

    public void nextScreen(View view){
        joystickBT.getExampleConnectedThread().cancel();
        tractorBT.getExampleConnectedThread().cancel();
        joystickBT = null;
        tractorBT = null;
        Intent intent = new Intent(getApplicationContext(),TractorLocatorActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    class SendDataThread extends Thread{
        Handler handler;
        BTClass2 tractorBT;
        public  SendDataThread(Handler handler, BTClass2 btClass2){
            this.handler = handler;
            this.tractorBT = btClass2;
        }

        @Override
        public void run() {
            int i=0;
            while(true){
                if(tractorBT.getExampleConnectedThread() != null){
                    tractorBT.getExampleConnectedThread().write(new byte[]{0,(byte)i});
                    i++;
                }
                SystemClock.sleep(500);
            }
        }
    }
}