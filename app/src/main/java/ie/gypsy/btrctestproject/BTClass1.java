package ie.gypsy.btrctestproject;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;

public class BTClass1 {
    private static final String TAG = "BTClass1";

    private Handler handler;

    private BluetoothSocket bluetoothSocket;
    private BluetoothAdapter bluetoothAdapter;
    private ConnectedThread exampleConnectedThread;
    private String deviceAddress;

    BTClass1(Handler handler, String deviceAddress) {
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
