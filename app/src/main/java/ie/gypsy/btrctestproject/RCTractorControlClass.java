package ie.gypsy.btrctestproject;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;

public class RCTractorControlClass {

    Handler spreadingActivityHandler;
    String esp32DKAddress = "D8:A0:1D:69:E0:2A";
    String wifiLora32Address = "8C:AA:B5:83:A1:3A";
    String esp32PicoSecondAddress = "D8:A0:1D:69:E7:FA";
    double metresToDegreesFactor = 8.95E-6;
    double latitude = 0, longitude = 0;
    int lengthOfBox = 0, widthOfBox = 0;
    int widthOfTractor = 10, lengthOfTractor = 11;
    Integer angleCorrection;
    int time = 0;
    static final int  controllerMessage = 1, tractorMessage = 2;
    RcTractorBluetoothClass rcTractorBluetoothClass;

    public RCTractorControlClass(Handler spreadingActivityHandler){
        this.spreadingActivityHandler = spreadingActivityHandler;
        Handler esp32Handler = new Handler(Looper.getMainLooper()){
            @Override
            public void handleMessage(@NonNull Message msg) {
                Message msg1 = new Message();
                msg1.what = msg.what;
                msg1.obj = msg.obj;
                switch(msg.what){
                    case controllerMessage:
                        if(rcTractorBluetoothClass.getExampleConnectedThread() != null){
                            rcTractorBluetoothClass.getExampleConnectedThread().write((byte[]) msg.obj);
                        }
                        spreadingActivityHandler.sendMessage(msg1);
                        break;
                    case tractorMessage:
                        byte[] tractorUltrasonicPositions = (byte[]) msg.obj;
                        int ultrasonicRightLower = (int) ( tractorUltrasonicPositions[1] & 0xff );
                        int ultrasonicRightUpper = (int) ( tractorUltrasonicPositions[0] & 0xff );
//                        int ultrasonicBackPosition = (int) ( ( tractorUltrasonicPositions[2] << 8 ) | tractorUltrasonicPositions[3] );
                        int ultrasonicRightPosition = (ultrasonicRightUpper * 256) + ultrasonicRightLower;
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

                        if(angleCorrection == null){
                            angleCorrection = 180 - angle;
                            lengthOfBox = ultrasonicFrontPosition + 14;//14 = Distance from edge of back wheels to front of tractor body
                            widthOfBox = ultrasonicRightPosition + 8;//8 = Distance from outer rim of back wheel to opposite edge of tractor body
                            msg1.obj = new TractorPosnMsg(ultrasonicFrontPosition,ultrasonicLeftPosition,ultrasonicBackPosition,ultrasonicRightPosition,new int[]{widthOfBox, lengthOfBox});
//                            msg1.obj = new int[]{widthOfBox,lengthOfBox};
                            spreadingActivityHandler.sendMessage(msg1);
                            break;
                        }else{
                            angle = (angle + angleCorrection) % 360;
                        }


                        //Find Latitude and Longitude
                        ArrayList<USVector> usVectors = new ArrayList<>();
                        usVectors.add(new USVector(ultrasonicFrontPosition,angle,1));
                        usVectors.add(new USVector(ultrasonicRightPosition,(angle + 90)%360,2));
                        usVectors.add(new USVector(ultrasonicBackPosition,(angle + 180)%360,3));
                        usVectors.add(new USVector(ultrasonicLeftPosition,(angle + 270)%360,4));

                        /// Not sure about this code
                        usVectors.removeIf(usVector -> usVector.distance < 3);
                        if(usVectors.size() < 3) {
                            break;
                        }

                        int[] xyCoords = calculateXYCoordsOfTractor(usVectors);

                        msg1.obj = new TractorPosnMsg(ultrasonicFrontPosition,ultrasonicLeftPosition,ultrasonicBackPosition,ultrasonicRightPosition,xyCoords);
                        spreadingActivityHandler.sendMessage(msg1);
                        break;
                }
            }
        };
//        RcControllerBluetoothClass rcControllerBluetoothClass = new RcControllerBluetoothClass(esp32Handler,wifiLora32Address);
        rcTractorBluetoothClass = new RcTractorBluetoothClass(esp32Handler,esp32DKAddress);
        RcControllerBluetoothClass rcControllerBluetoothClass = new RcControllerBluetoothClass(esp32Handler,esp32PicoSecondAddress);
    }


    int[] calculateXYCoordsOfTractor(ArrayList<USVector> fullUSVectorsList){
        //Test this block of code
        if(fullUSVectorsList.size() == 4){
            fullUSVectorsList.remove(3);
        }

        //Make sure us1 ( = fuvl.get(0)) and us2 ( = fuvl.get(1)) are parallel
        if(  (fullUSVectorsList.get(0).angle - fullUSVectorsList.get(1).angle) % 180 != 0 ){
            if( (fullUSVectorsList.get(0).angle - fullUSVectorsList.get(2).angle) %180 == 0 ){
                fullUSVectorsList.add(1,fullUSVectorsList.get(2));
                fullUSVectorsList.remove(3);
            }else{
                fullUSVectorsList.add(fullUSVectorsList.get(0));
                fullUSVectorsList.remove(0);
            }
        }


        //Go through calculations to find point with minimal deviation
        ArrayList<TractorPosn> tractorPosns = new ArrayList<>();
        int u1x = (int) fullUSVectorsList.get(0).xDistance; int u1y = (int) fullUSVectorsList.get(0).yDistance;
        int u2x = (int) fullUSVectorsList.get(1).xDistance; int u2y = (int) fullUSVectorsList.get(1).yDistance;
        int u3x = (int) fullUSVectorsList.get(2).xDistance; int u3y = (int) fullUSVectorsList.get(2).yDistance;

        tractorPosns.add(new TractorPosn( u3x<0? Math.abs(u3x) : widthOfBox - u3x ,          Math.abs( Math.min( u1y, u2y ) ) ,              Math.abs(lengthOfBox - Math.abs(u1y) - Math.abs(u2y))));                                           //U1, U2 pointing at opposite walls in y-direction
        tractorPosns.add(new TractorPosn( Math.abs(Math.min(u1x,u2x)),                       u3y<0? Math.abs(u3y) : lengthOfBox - u3y,       Math.abs(widthOfBox - u1x - u2x)));                                                                //U1, U2 pointing at opposite walls in x-direction
        tractorPosns.add(new TractorPosn( u3x < 0? Math.abs(u3x) : widthOfBox - u3x,         u1y<0? Math.abs(u1y) : lengthOfBox - u1y,       Math.abs(u1x-u3x) ));                                                                              //U1, U3 pointing at same wall in x-direction
        tractorPosns.add(new TractorPosn( u3x < 0? Math.abs(u3x) : widthOfBox - u3x,         u2y<0? Math.abs(u2y) : lengthOfBox - u2y,       Math.abs(u2x-u3x) ));                                                                              //U2, U3 pointing at same wall in x-direction
        tractorPosns.add(new TractorPosn( u1x<0? Math.abs(u1x) : widthOfBox - u1x,           u3y<0? Math.abs(u3y) : lengthOfBox-u3y,         Math.abs(u3y - u1y) ));                                                                            //U1, U3 pointing at same wall in y-direction
        tractorPosns.add(new TractorPosn( u2x<0? Math.abs(u2x) : widthOfBox - u2x,           u3y<0? Math.abs(u3y) : lengthOfBox-u3y,         Math.abs(u3y - u2y) ));                                                                            //U2, U3 pointing at same wall in y-direction
        tractorPosns.add(new TractorPosn( Math.abs(Math.min(u1x,u3x)),                       u1y<0? Math.abs(u1y): lengthOfBox - u1y,        Math.abs( widthOfBox - Math.abs(u3x) - Math.abs(u1x) )));                                          //U1, U3 pointing at opposite walls in x-direction
        tractorPosns.add(new TractorPosn( Math.abs(Math.min(u2x,u3x)),                       u2y<0? Math.abs(u2y): lengthOfBox - u2y,        Math.abs( widthOfBox - Math.abs(u3x) - Math.abs(u2x) )));                                          //U2, U3 pointing at opposite walls in x-direction
        tractorPosns.add(new TractorPosn( u1x<0 ? Math.abs(u1x) : widthOfBox - u1x,          Math.abs(Math.min(u1y, u3y)),                   Math.abs(lengthOfBox - Math.abs(u1y) - Math.abs(u3y))));                                           //U1, U3 pointing at opposite walls in y-direction
        tractorPosns.add(new TractorPosn( u2x<0 ? Math.abs(u2x) : widthOfBox - u2x,          Math.abs(Math.min(u2y, u3y)),                   Math.abs(lengthOfBox - Math.abs(u2y) - Math.abs(u3y))));                                           //U2, U3 pointing at opposite walls in y-direction

        //Sort Possible tractor positions by deviance
        boolean change = true;
        while(change){
            change = false;
            for (int i = 0; i < tractorPosns.size() - 1; i++) {
                if(tractorPosns.get(i).deviation > tractorPosns.get(i+1).deviation){
                    change = true;
                    tractorPosns.add(i,tractorPosns.get(i+1));
                    tractorPosns.remove(i+2);
                }
            }
        }

        // TODO: 02/11/2021 Check distance of new tractor positions from previously recorded position


        //
        return new int[]{tractorPosns.get(0).xPosn, tractorPosns.get(1).yPosn};
        

    }

    class TractorPosnMsg{
        int front, back, left, right;
        int[] positions;

        public TractorPosnMsg(int front, int left, int back, int right, int[] positions) {
            this.front = front;
            this.back = back;
            this.left = left;
            this.right = right;
            this.positions = positions;
        }
    }


    class TractorPosn{
        int xPosn, yPosn;
        double deviation;

        public TractorPosn(int xPosn, int yPosn, double deviation) {
            this.xPosn = xPosn;
            this.yPosn = yPosn;
            this.deviation = deviation;
        }
    }


    class USVector{
        int distance, angle;
        int horizontalOffset, verticalOffset;
        int widthOfTractor = 6, lengthOfTractor = 12;
        double xDistance, yDistance;

        public USVector(int distance, int angle, int direction) {
            this.distance = distance;
            this.angle = angle;
            calculateDistances();
            //Direction 1 = forward, 2 = right, 3 = back, 4 = left
            if(direction == 1){this.horizontalOffset = (int) ((lengthOfTractor * Math.sin(Math.toRadians(angle)))/2); this.verticalOffset = (int) ( (lengthOfTractor * Math.cos(Math.toRadians(angle))) /2);}
            else if (direction == 2){this.horizontalOffset = (int) ( (widthOfTractor * Math.sin(Math.toRadians(angle)))/2); this.verticalOffset = (int) (  ( -1 * widthOfTractor * Math.cos(Math.toRadians(angle)) )/2);}
            else if (direction == 3){this.horizontalOffset = (int) ( (-1 * lengthOfTractor * Math.sin(Math.toRadians(angle))) / 2); this.verticalOffset = (int) ( ( -1 * lengthOfTractor * Math.cos(Math.toRadians(angle))) /2 );}
            else if (direction == 4){this.horizontalOffset = (int) ((-1 * widthOfTractor * Math.sin(Math.toRadians(angle)) )/2); this.verticalOffset = (int) ((widthOfTractor * Math.cos(Math.toRadians(angle)))/2);}
            else {//Problem
            }

        }

        void calculateDistances(){
            this.xDistance = distance * Math.sin( Math.toRadians(angle));
            this.yDistance = distance * Math.cos( Math.toRadians(angle));
        }
    }

    class RcControllerBluetoothClass{
        private static final String TAG = "RcControllerBluetoothClass";

        private Handler handler;

        private BluetoothSocket bluetoothSocket;
        private BluetoothAdapter bluetoothAdapter;
        private RcControllerBluetoothClass.ConnectedThread exampleConnectedThread;
        private String deviceAddress;

        RcControllerBluetoothClass(Handler handler, String deviceAddress) {
            bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            this.handler = handler;
            this.deviceAddress = deviceAddress;
            initiateBluetoothConnection();


        }

        RcControllerBluetoothClass.ConnectedThread getExampleConnectedThread() {
            return exampleConnectedThread;
        }

        private void initiateBluetoothConnection(){
            RcControllerBluetoothClass.BluetoothConnectionAsyncTask exampleBluetoothConnectionThread = new RcControllerBluetoothClass.BluetoothConnectionAsyncTask();
            exampleBluetoothConnectionThread.execute();
        }

        private class BluetoothConnectionAsyncTask extends AsyncTask<Void,Void,Void> {


            @Override
            protected void onPreExecute() {
            }

            @Override
            protected Void doInBackground(Void... voids) {
                try {
                    BluetoothDevice bluetoothDevice = bluetoothAdapter.getRemoteDevice(deviceAddress);//John Kelleher Glo Address
                    bluetoothSocket = bluetoothDevice.createInsecureRfcommSocketToServiceRecord(java.util.UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"));//This is serial comm uid
                    bluetoothSocket.connect();
                }catch (IOException e){
                    e.printStackTrace();
                }
                return null;
            }

            @Override
            protected void onPostExecute(Void aVoid) {
                super.onPostExecute(aVoid);
                exampleConnectedThread = new RcControllerBluetoothClass.ConnectedThread(bluetoothSocket);
                exampleConnectedThread.start();


            }
        }

        public class ConnectedThread extends Thread {
            private final BluetoothSocket mmSocket;
            private final InputStream mmInStream;

            ConnectedThread(BluetoothSocket socket) {
                mmSocket = socket;
                InputStream tmpIn = null;



                // Get the input and output streams; using temp objects because
                // member streams are final.
                try {
                    tmpIn = socket.getInputStream();
                } catch (IOException e) {
                    Log.e(TAG, "Error occurred when creating input stream", e);
                }


                mmInStream = tmpIn;
            }

            // TODO: 14/08/2020 Add timeout counter here, if glo hasn't connected
            public void run() {
                // mmBuffer store for the stream
                byte[] mmBuffer = new byte[4];
                int numBytes; // bytes returned from read()

                // Keep listening to the InputStream until an exception occurs.
                while (true) {
                    try {
                        // Read from the InputStream.
                        numBytes = mmInStream.read(mmBuffer);
                        byte[] joystickPositions = new byte[numBytes];
                        for (int i = 0; i < numBytes; i++) {
                            joystickPositions[i] = (byte) mmBuffer[i];
                        }


                        Message msg1 = new Message();
                        msg1.what = 1;
                        msg1.obj = joystickPositions;
                        handler.sendMessage(msg1);

//                            Log.d(TAG, "run: GPRMC obtained");

                        //break;





                    } catch (IOException e) {
                        Log.d(TAG, "Input stream was disconnected", e);
                        break;
                    }
                }
            }

            // Call this method from the main activity to shut down the connection.
            void cancel() {
                try {
                    mmSocket.close();
                } catch (IOException e) {
                    Log.e(TAG, "Could not close the connect socket", e);
                }
            }


        }
    }

    class RcTractorBluetoothClass{
        private static final String TAG = "RcTractorBluetoothClass";

        private Handler handler;

        private BluetoothSocket bluetoothSocket;
        private BluetoothAdapter bluetoothAdapter;
        private RcTractorBluetoothClass.ConnectedThread exampleConnectedThread;
        private String deviceAddress;

        RcTractorBluetoothClass(Handler handler, String deviceAddress) {
            bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            this.handler = handler;
            this.deviceAddress = deviceAddress;
            initiateBluetoothConnection();


        }

        RcTractorBluetoothClass.ConnectedThread getExampleConnectedThread() {
            return exampleConnectedThread;
        }

        private void initiateBluetoothConnection(){
            RcTractorBluetoothClass.BluetoothConnectionAsyncTask exampleBluetoothConnectionThread = new RcTractorBluetoothClass.BluetoothConnectionAsyncTask();
            exampleBluetoothConnectionThread.execute();
        }

        private class BluetoothConnectionAsyncTask extends AsyncTask<Void,Void,Void> {


            @Override
            protected void onPreExecute() {
            }

            @Override
            protected Void doInBackground(Void... voids) {
                try {
                    BluetoothDevice bluetoothDevice = bluetoothAdapter.getRemoteDevice(deviceAddress);//John Kelleher Glo Address
                    bluetoothSocket = bluetoothDevice.createInsecureRfcommSocketToServiceRecord(java.util.UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"));//This is serial comm uid
                    bluetoothSocket.connect();
                }catch (IOException e){
                    e.printStackTrace();
                }
                return null;
            }

            @Override
            protected void onPostExecute(Void aVoid) {
                super.onPostExecute(aVoid);
                exampleConnectedThread = new RcTractorBluetoothClass.ConnectedThread(bluetoothSocket);
                exampleConnectedThread.start();


            }
        }

        public class ConnectedThread extends Thread {
            private final BluetoothSocket mmSocket;
            private final InputStream mmInStream;
            private final OutputStream mmOutStream;

            ConnectedThread(BluetoothSocket socket) {
                mmSocket = socket;
                InputStream tmpIn = null;
                OutputStream tmpOut = null;

                // Get the input and output streams; using temp objects because
                // member streams are final.
                try {
                    tmpIn = socket.getInputStream();
                } catch (IOException e) {
                    Log.e(TAG, "Error occurred when creating input stream", e);
                }
                try {
                    tmpOut = socket.getOutputStream();
                } catch (IOException e) {
                    Log.e(TAG, "Error occurred when creating output stream", e);
                }


                mmInStream = tmpIn;
                mmOutStream = tmpOut;
            }

            // TODO: 14/08/2020 Add timeout counter here, if glo hasn't connected
            public void run() {
                // mmBuffer store for the stream
                byte[] mmBuffer = new byte[2048];
                int numBytes; // bytes returned from read()

                // Keep listening to the InputStream until an exception occurs.
                while (true) {
                    try {
                        // Read from the InputStream.
                        numBytes = mmInStream.read(mmBuffer);
                        byte[] tractorUltrasonicPositions = new byte[numBytes];
                        for (int i = 0; i < numBytes ; i++) {
                            tractorUltrasonicPositions[i] = (byte) mmBuffer[i];
                        }





                        //  Message msg = handler.obtainMessage(1,newLocation);
                        Message msg1 = new Message();
                        msg1.what = 2;
                        msg1.obj = tractorUltrasonicPositions;
                        handler.sendMessage(msg1);

//                            Log.d(TAG, "run: GPRMC obtained");



                    } catch (IOException e) {
                        Log.d(TAG, "Input stream was disconnected", e);
                        break;
                    }
                }
            }

            // Call this from the main activity to send data to the remote device.
            public void write(byte[] bytes) {
                try {
                    //Send Left and right pwm signals as separate bytes
                    byte leftPWM = 0, rightPWM = 0;

                    int joystickX = bytes[0] & 0xff; if(joystickX > 230){ joystickX = 230; }
                    int joystickY = bytes[1] & 0xff; if(joystickY > 230){ joystickY = 230; }

                    leftPWM = calculateLeftPWM(joystickX, joystickY);
                    rightPWM = calculateRightPWM(joystickX, joystickY);


                    mmOutStream.write(new byte[]{leftPWM, rightPWM});

                } catch (IOException e) {
                    Log.e(TAG, "Error occurred when sending data", e);


                }
            }

            // Call this method from the main activity to shut down the connection.
            void cancel() {
                try {
                    mmSocket.close();
                } catch (IOException e) {
                    Log.e(TAG, "Could not close the connect socket", e);
                }
            }


            byte calculateLeftPWM(int joystickX, int joystickY){
                byte leftPWM = 0;
                if( joystickY < 100 ){
                    if(joystickX < 100){
                        leftPWM = (byte) (90 + joystickX * ( ( 90 - (9 * joystickY)/10f ) / 100f));
                    }else{
                        leftPWM = (byte) (180 - joystickY * (9/10f));
                    }

                }else if( joystickY < 130 ){
                    leftPWM = 90;
                }else if( joystickY <= 230 ){
                    if(joystickX < 100){
                        leftPWM = (byte) (90 + joystickX * ( ( 117 - (9 * joystickY)/10f ) / 100f));
                    }else{
                        leftPWM = (byte) (207 - joystickY * (9/10f));
                    }
                }else{
                    //Problem
                }
                return leftPWM;
            }

            byte calculateRightPWM(int joystickX, int joystickY){
                byte rightPWM = 0;
                if( joystickY < 100 ){
                    if(joystickX < 130){
                        rightPWM = (byte) (joystickY * (9/10f));
                    }else{
                        rightPWM = (byte) (-117 + (23 * 9 * joystickY)/100f + (joystickX / 100f) * (90 - (9 * joystickY)/10f) );
                    }

                }else if( joystickY < 130 ){
                    rightPWM = 90;
                }else if( joystickY <= 230 ){
                    if(joystickX < 130){
                        rightPWM = (byte) (-27 + joystickY * (9/10f));
                    }else{
                        rightPWM = (byte) (-1791/10f + (207 * joystickY)/100f + (joystickX / 100f) * (117 - (9 * joystickY)/10f) );
                    }
                }else{
                    //Problem
                }
                return rightPWM;
            }

        }
    }

}
