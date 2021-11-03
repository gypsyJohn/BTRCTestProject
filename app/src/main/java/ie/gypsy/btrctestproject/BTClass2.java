package ie.gypsy.btrctestproject;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class BTClass2 {
    private static final String TAG = "BTClass2";

    private Handler handler;

    private BluetoothSocket bluetoothSocket;
    private BluetoothAdapter bluetoothAdapter;
    private ConnectedThread exampleConnectedThread;
    private String deviceAddress;

    BTClass2(Handler handler, String deviceAddress) {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        this.handler = handler;
        this.deviceAddress = deviceAddress;
        initiateBluetoothConnection();


    }

    ConnectedThread getExampleConnectedThread() {
        return exampleConnectedThread;
    }

    private void initiateBluetoothConnection(){
        BluetoothConnectionAsyncTask exampleBluetoothConnectionThread = new BluetoothConnectionAsyncTask();
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
            exampleConnectedThread = new ConnectedThread(bluetoothSocket);
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
//                byte[] mmBuffer = new byte[2048];
//                // Share the sent message with the UI activity.
//                Message writtenMsg = handler.obtainMessage(
//                        3, -1, -1, mmBuffer);
//                writtenMsg.sendToTarget();
            } catch (IOException e) {
                Log.e(TAG, "Error occurred when sending data", e);

                // Send a failure message back to the activity.
//                Message writeErrorMsg =
//                        handler.obtainMessage(MessageConstants.MESSAGE_TOAST);
//                Bundle bundle = new Bundle();
//                bundle.putString("toast",
//                        "Couldn't send data to the other device");
//                writeErrorMsg.setData(bundle);
//                handler.sendMessage(writeErrorMsg);
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
