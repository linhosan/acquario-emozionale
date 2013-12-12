package linho.arduino_if.acquarioemozionale;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageButton;
import android.widget.Toast;

public class Main extends Activity {
	private static final int HDLR_BT_VERIFY_SUPPORT_OK = 0;
	private static final int HDLR_BT_VERIFY_SUPPORT_KO = 1;
	private static final int HDLR_BT_VERIFY_ANTENNA_DISABLED = 2;
	private static final int HDLR_BT_VERIFY_ANTENNA_ENABLED = 3;
	private static final int HDLR_BT_DISCOVERY_FINISHED_EMPTY = 4;
	private static final int HDLR_BT_DISCOVERY_FINISHED_NOTEMPTY = 5;
	private static final int HDLR_BT_CONNECTION_ESTABLISHED = 6;
	private static final int HDLR_BT_CONNECTION_FAILED = 7;
	private static final int HDLR_BT_CONNECTION_ABORTED = 8;
	private static final int HDLR_BT_SOCKET_IOSTREAM_ESTABLISHED = 9;
	private static final int HDLR_BT_SOCKET_IOSTREAM_FAIL = 10;
	private static final int HDLR_BT_MESSAGE_READ_ARRIVED = 11;
	private static final int HDLR_BT_MESSAGE_READ_FAIL = 12;
	private static final int HDLR_BT_MESSAGE_WRITE_OK = 13;
	private static final int HDLR_BT_MESSAGE_WRITE_FAIL = 14;

	private static final int BT_REQUEST_ANTENNA_ENABLE = 1;
	
	private ProgressDialog pDialog;
	private ArrayList<BluetoothDevice> discoveredDeviceArray;
	private ArrayList<BluetoothDevice> pairedDeviceArray;

	//private final UUID MY_UUID = java.util.UUID.randomUUID();	
	private final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb"); 
	private BluetoothDevice selectedDevice;
	private BluetoothAdapter mBluetoothAdapter;

    private ConnectedThread connThread;

	ImageButton btn_Paired;
	ImageButton btn_ConnectionStatus;
	ImageButton btn_Discovery;
	ImageButton btn_OnOff;
    Boolean btn_OnOff_Status = false;
	
	IntentFilter filter = new IntentFilter();

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		/*
		 * Imposto il filtro del BroadcastReceiver (mReceiver)
		 * per la callback dei seguenti eventi della discovery:
		 * 
		 * BluetoothDevice.ACTION_FOUND					(Quando viene trovato un dispositivo vicino)
		 * BluetoothAdapter.ACTION_DISCOVERY_FINISHED	(Terminata la ricerca)
		 */
		filter.addAction(BluetoothDevice.ACTION_FOUND);
		filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
		
		/*
		 * Come prima cosa all'avvio dell'app
		 * verifico che il device supporti il Bluetooth.
		 */
		verificaSupportoBT();

		/*
		 * Imposto l'onClick del bottone "Paired"
		 */
		btn_Paired = (ImageButton)findViewById(R.id.btn_Paired);
		btn_Paired.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View arg0) {
				showPariedDeviceArray();
			}
		});

		/*
		 * Imposto l'onClick del bottone "Discovery"
		 */
		btn_Discovery = (ImageButton)findViewById(R.id.btn_Discovery);
		btn_Discovery.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View arg0) {
				deviceDiscovery();
			}
		});

		/*
		 * Imposto l'onClick del bottone "On / Off"
		 */
		btn_OnOff = (ImageButton)findViewById(R.id.btn_OnOff);
		btn_OnOff.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View arg0) {
				connThread.write( "SET_RELAY_QUADRO: " + (btn_OnOff_Status ? "LOW" : "HIGH") );
			}
		});
		btn_OnOff.setClickable(false);
		
		/*
		 * Imposto l'onClick del bottone "Connection Status"
		 */
		btn_ConnectionStatus = (ImageButton)findViewById(R.id.btn_ConnectionStatus);
		btn_ConnectionStatus.setClickable(false);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}
	
	
	
	
	
	
	/*
	 * alla finish():
	 * - dismetto la ProgressDialog (se visibile)
	 * - fermo la discovery dei dispositivi vicini (se in corso)
	 */
	@Override
	protected void onStop() {
		if (pDialog.isShowing())
			pDialog.dismiss();

		if (mBluetoothAdapter.isDiscovering())
			mBluetoothAdapter.cancelDiscovery();
		
		super.onStop();
	}
	
	
	
	
	
	
	
	

	
	
	
	
	
	
	/*
	 * onResume registro il BroadcastReceiver (mReceiver) e
	 * onPause lo unregistro
	 */
	@Override
	protected void onResume() {
		super.onResume();
		registerReceiver(mReceiver, filter);
	}
	@Override
	protected void onPause() {
		unregisterReceiver(mReceiver);
		super.onPause();
	}

	
	
	
	
	
	
	
	
	
	
	
	
	/*
	 * In un Thread separato inizializzo il BluetoothAdapter per verificare il supporto Bluetooth
	 * Prima lancio una ProgressDialog per l'attesa
	 * Il Thread comunica l'esito all'handler (sendToTarget)
	 * che si occupa di gestire il messaggio di trigger.
	 * - Se il supporto è KO esce dall'app
	 * - Se il supporto è OK lancia la verificaAntennaBT();
	 */
	protected void verificaSupportoBT()
	{
		pDialog = ProgressDialog.show(this, "Verifica supporto Bluetooth ", "Attendere prego", true,false);
		new Thread() {
			public void run() 
			{				
				mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
				
				if (mBluetoothAdapter == null)
					handler.obtainMessage(Main.HDLR_BT_VERIFY_SUPPORT_KO).sendToTarget();
				else
					handler.obtainMessage(Main.HDLR_BT_VERIFY_SUPPORT_OK).sendToTarget();
			}
		}.start();
	}
	
	
	
	
	
	
	
	
	
	/*
	 * Se la verifica del supporto Bluetooth va a buon fine
	 * in un Thread separato verifico che l'antenna sia abilitata
	 * Prima lancio una ProgressDialog per l'attesa
	 * Il Thread comunica l'esito all'handler (sendToTarget)
	 * che si occupa di gestire il messaggio di trigger
	 * - Se l'antenna è abilitata si limita alla dismiss() della ProgressDialog
	 * - Se l'antenna è disabilitata lancia la abilitaAntennaBT();
	 */
	protected void verificaAntennaBT()
	{
		pDialog.setTitle("Verifica abilitazione antenna");
		new Thread() {
			public void run() 
			{
				if (!mBluetoothAdapter.isEnabled()) 
					handler.obtainMessage(Main.HDLR_BT_VERIFY_ANTENNA_DISABLED).sendToTarget();
				else
					handler.obtainMessage(Main.HDLR_BT_VERIFY_ANTENNA_ENABLED).sendToTarget();
			}
		}.start();
	}
	/*
	 * Lancia la richiesta di abilitazione dell'antenna Bluetooth
	 * Se la richiesta viene rifiutata dall'utente esce dall'app
	 * altrimenti si limita alla dismiss() della ProgressDialog (HDLR_BT_VERIFY_ANTENNA_ENABLED)
	 */
	protected void abilitaAntennaBT()
	{
		Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
		startActivityForResult(enableBtIntent, BT_REQUEST_ANTENNA_ENABLE);
	}
	
	
	
	
	
	
	
	
	
	
	
	/*
	 * (Re)inizializza la lista dei dispositivi raggiungibili
	 * ed avvia la procedura di sistema di discovery
	 */
	protected void deviceDiscovery()
	{
		pDialog = ProgressDialog.show(this, "Ricerca dispositivi vicini", "Attendere prego", true,false);
		discoveredDeviceArray = new ArrayList<BluetoothDevice>();
		mBluetoothAdapter.startDiscovery();
	}
	/*
	 * Ogni volta che la discovery triggera un messaggio
	 * comunico l'esito all'handler (sendToTarget)
	 * - ACTION_FOUND (Trovato un nuovo dispositivo onair):
	 *   aggiunge il device alla lista (discoveredDeviceArray) e aggiorna la ProgressDialog
	 * - ACTION_DISCOVERY_FINISHED (la procedura di sistema di discovery è terminata)
	 *   Se non sono trovati dispositivi si limita a visualizzare un Toast
	 *   Se sono trovati dispositivi lancia la showDiscoveredDeviceArray
	 */
	private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();

			if (BluetoothDevice.ACTION_FOUND.equals(action)) {
				BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
				discoveredDeviceArray.add(device);
				pDialog.setTitle("Trovati "+discoveredDeviceArray.size()+" dispositivi");
			} 
			else if (action.equals(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)) 
			{
				if (discoveredDeviceArray.isEmpty())
					handler.obtainMessage(Main.HDLR_BT_DISCOVERY_FINISHED_EMPTY).sendToTarget();
				else
					handler.obtainMessage(Main.HDLR_BT_DISCOVERY_FINISHED_NOTEMPTY).sendToTarget();
			}
		}
		
	};
	/*
	 * Visualizza una lista dei dispositivi raggiungibili onair
	 * e onClick lancia la connectToDevice();
	 */
	private void showDiscoveredDeviceArray()
	{
		List<String> list = new ArrayList<String>();
		for (int i=0; i<discoveredDeviceArray.size(); i++)
			list.add(discoveredDeviceArray.get(i).getName());
		final CharSequence[] items = list.toArray(new CharSequence[list.size()]);
		
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle("Dispositivi raggiungibili");
		builder.setItems(items, new DialogInterface.OnClickListener() {
		    public void onClick(DialogInterface dialog, int item) {
		         connectToDevice(item, false);
		    }
		});
		AlertDialog alert = builder.create();
		alert.show();
	}
	
	
	
	
	
	
	

	/*
	 * Visualizza una lista dei dispositivi già accoppiati (paired)
	 * e onClick lancia la connectToDevice();
	 */
	private void showPariedDeviceArray()
	{
		Set<BluetoothDevice> pairedTmp = mBluetoothAdapter.getBondedDevices();
		pairedDeviceArray = new ArrayList<BluetoothDevice>();
		if (pairedTmp.size() > 0)
		{
			for (BluetoothDevice device : pairedTmp)
				pairedDeviceArray.add(device);
		}
		
		List<String> list = new ArrayList<String>();
		for (int i=0; i<pairedDeviceArray.size(); i++)
			list.add(pairedDeviceArray.get(i).getName());
		final CharSequence[] items = list.toArray(new CharSequence[list.size()]);
		
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle("Dispositivi già accoppiati");
		builder.setItems(items, new DialogInterface.OnClickListener() {
		    public void onClick(DialogInterface dialog, int item) {
		         connectToDevice(item, true);
		    }
		});
		AlertDialog alert = builder.create();
		alert.show();
	}
	
	
	
	
	
	
	
	
	
	
	/*
	 * Prima faccio partire una ProgressDialog per l'attesa
	 * poi inizializzo una BluetoothSocket (poi mmSocket) per la connessione...
	 * ... se fallisce comunica l'esito all'handler (sendToTarget) e subito "return";
	 * Altrimenti in un Thread separato tenta la connessione
	 * e comunica l'esito all'handler (sendToTarget)
	 * che si occupa di gestire il messaggio di trigger
	 * - Se non si connette si limita alla dismiss() della ProgressDialog e visualizza un Toast
	 * - Se si connette lancia la manageConnectedSocket();
	 */
	private void connectToDevice(int deviceArrayindex, boolean isPaired)
	{
		if (isPaired)
			selectedDevice = pairedDeviceArray.get(deviceArrayindex); 
		else
			selectedDevice = discoveredDeviceArray.get(deviceArrayindex);
		
		pDialog = ProgressDialog.show(this, "Connessione a " + selectedDevice.getName(), "Attendere prego", true,false);

		connThread = new ConnectedThread();
	}
	private class ConnectedThread extends Thread
	{
		private Boolean mStopThread = false;
		
	    private BluetoothSocket mmSocket;
        private InputStream mmInStream;
        private OutputStream mmOutStream;
      
        public ConnectedThread()
        {        	
			BluetoothSocket tmp = null;
			try {
				//tmp = selectedDevice.createRfcommSocketToServiceRecord(MY_UUID);
				tmp = selectedDevice.createInsecureRfcommSocketToServiceRecord(MY_UUID);
			} catch (IOException e) {
				handler.obtainMessage(Main.HDLR_BT_CONNECTION_FAILED).sendToTarget();
				return;
			}
			mmSocket = tmp;
			handler.obtainMessage(Main.HDLR_BT_CONNECTION_ESTABLISHED).sendToTarget();

        	InputStream tmpIn = null;
		    OutputStream tmpOut = null;
			try {
	            mmSocket.connect();
	        } catch (IOException connectException) {
	        	handler.obtainMessage(Main.HDLR_BT_CONNECTION_FAILED).sendToTarget();
				return;
	        }
			
			try {
	            tmpIn = mmSocket.getInputStream();
	            tmpOut = mmSocket.getOutputStream();
	            handler.obtainMessage(Main.HDLR_BT_SOCKET_IOSTREAM_ESTABLISHED).sendToTarget();
	        } catch (IOException e) {
	        	handler.obtainMessage(Main.HDLR_BT_SOCKET_IOSTREAM_FAIL).sendToTarget();
				return;
	        }

	        mmInStream = tmpIn;
	        mmOutStream = tmpOut;
        }
      
        public void run() 
        {	        
///////////////        	byte[] buffer = new byte[1024];  // buffer store for the stream
///////////////	        int bytes; // bytes returned from read()
        	try {
        		BufferedReader inStreamReader = new BufferedReader(new InputStreamReader(mmInStream));
        		
        		// Keep listening to the InputStream until an exception occurs
    	        while (true) {
    	        	if (mStopThread)
    	        	{
    	        		try {
    	        			inStreamReader.close();
    						mmInStream.close();
    		        		mmOutStream.close();
    		        		mmSocket.close();
    					} catch (IOException e) { }
    	        		return;
    	        	}
    	            try {
    	                // Read from the InputStream
    	            	byte[] buffer = inStreamReader.readLine().getBytes();
    	            	int bytes = buffer.length;
    	                // Send the obtained bytes to the UI activity
    	                handler.obtainMessage(HDLR_BT_MESSAGE_READ_ARRIVED, bytes, -1, buffer).sendToTarget();
    	            } catch (IOException e) {
    	            	handler.obtainMessage(HDLR_BT_MESSAGE_READ_FAIL).sendToTarget();
    	                break;
    	            }
    	        }
        	} catch (Exception eee) {
        		handler.obtainMessage(HDLR_BT_SOCKET_IOSTREAM_FAIL).sendToTarget();
        	}
        }

        public void write(String message) 
        {
        	byte[] msgBuffer = message.getBytes();
        	try {
                mmOutStream.write(msgBuffer);
                handler.obtainMessage(HDLR_BT_MESSAGE_WRITE_OK).sendToTarget();
            } catch (IOException e) {
            	handler.obtainMessage(HDLR_BT_MESSAGE_WRITE_FAIL).sendToTarget();
            }
        }
        public void stopThread()
        {
        	mStopThread = true;
        }
    }
	public void disconnectFromDevice()
	{
		handler.obtainMessage(Main.HDLR_BT_CONNECTION_ABORTED).sendToTarget();
	}
	
	

	
	
	
	
	
	
	
	/*
	 * Con questo Handler gestisco tutti i trigger di callback
	 * soprattutto per la comunicazione con i Thread
	 */
	private Handler handler = new Handler() 
	{
	    @Override
	    public void handleMessage(Message msg) 
	    {
	    	switch (msg.what) {
            case HDLR_BT_VERIFY_SUPPORT_KO:
                Toast.makeText(getApplicationContext(), "Questo dispositivo non supporta il Bluetooth", Toast.LENGTH_LONG).show();
                finish();
                break;
            case HDLR_BT_VERIFY_SUPPORT_OK:
                verificaAntennaBT();
                break;
            case HDLR_BT_VERIFY_ANTENNA_DISABLED:
                abilitaAntennaBT();
                break;
            case HDLR_BT_VERIFY_ANTENNA_ENABLED:
            	pDialog.dismiss();
                break;
            case HDLR_BT_DISCOVERY_FINISHED_EMPTY:
				pDialog.dismiss();
            	Toast.makeText(getApplicationContext(), "Nessun dispositivo raggiungibile", Toast.LENGTH_SHORT).show();
                break;
            case HDLR_BT_DISCOVERY_FINISHED_NOTEMPTY:
				pDialog.dismiss();
            	showDiscoveredDeviceArray();
                break;
            case HDLR_BT_CONNECTION_ESTABLISHED:
            	connThread.start();
                break;
            case HDLR_BT_CONNECTION_FAILED:
            	disconnectFromDevice();
				pDialog.dismiss();
            	Toast.makeText(getApplicationContext(), "Impossibile connettersi a " + selectedDevice.getName(), Toast.LENGTH_SHORT).show();
                break;
            case HDLR_BT_CONNECTION_ABORTED:
            	connThread.stopThread();
            	btn_ConnectionStatus.setImageResource(R.drawable.ic_conn3_ko);
            	btn_OnOff.setImageResource(R.drawable.ic_lamp1_off);
            	btn_OnOff.setClickable(false);
            	break;
            case HDLR_BT_SOCKET_IOSTREAM_ESTABLISHED:            	
            	btn_ConnectionStatus.setImageResource(R.drawable.ic_conn3_ok);
            	connThread.write("GET_RELAY_QUADRO: STATUS");
            	btn_OnOff.setClickable(true);
				pDialog.dismiss();
				// Toast.makeText(getApplicationContext(), "Connesso", Toast.LENGTH_SHORT).show();
            	break;
            case HDLR_BT_SOCKET_IOSTREAM_FAIL:
            	disconnectFromDevice();
				pDialog.dismiss();
				Toast.makeText(getApplicationContext(), "Errore di comunicazione con " + selectedDevice.getName(), Toast.LENGTH_SHORT).show();
            	break;
            case HDLR_BT_MESSAGE_READ_ARRIVED:
            	byte[] readBuf = (byte[]) msg.obj;
                String readMessage = new String(readBuf, 0, msg.arg1);
                Log.e("Messaggio dall'Arduino: ", readMessage);
                Log.e("LLLLLLL", readMessage.substring(readMessage.length()-1));
                if (Integer.decode(readMessage.substring(readMessage.length()-1)) == 0)
                	btn_OnOff_Status = false;
                else if (Integer.decode(readMessage.substring(readMessage.length()-1)) == 1)
                	btn_OnOff_Status = true;
				btn_OnOff.setImageResource((btn_OnOff_Status ? R.drawable.ic_lamp1_on : R.drawable.ic_lamp1_off ));
            	break;
            case HDLR_BT_MESSAGE_READ_FAIL:
            	disconnectFromDevice();
            	Toast.makeText(getApplicationContext(), "Impossibile ricevere messaggi da " + selectedDevice.getName(), Toast.LENGTH_SHORT).show();
            	break;
            case HDLR_BT_MESSAGE_WRITE_OK:
            	break;
            case HDLR_BT_MESSAGE_WRITE_FAIL:
            	disconnectFromDevice();
				Toast.makeText(getApplicationContext(), "Impossibile inviare messaggi a " + selectedDevice.getName(), Toast.LENGTH_SHORT).show();
            	break;
            }
	    }
	};
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	
	protected void onActivityResult(int requestCode, int resultCode, Intent data) 
	{
		if ((requestCode == BT_REQUEST_ANTENNA_ENABLE)) 
		{
			if ((resultCode == RESULT_OK)) 
			{
				boolean isEnabling = mBluetoothAdapter.enable();
				
				if (!isEnabling) {
					Toast.makeText(getApplicationContext(), "Errore imprevisto - forse il Bluetooth è già attivo?", Toast.LENGTH_LONG).show();
					finish();
				} else if (mBluetoothAdapter.getState() == BluetoothAdapter.STATE_TURNING_ON) {
					/*
					 * The system, in the background, is trying to turn the Bluetooth on
					 * while your activity carries on going without waiting for it to finish;
					 * of course, you could listen for it to finish yourself -
					 * eg, using a ProgressDialog that checked mBluetoothAdapter.getState()
					 * every x milliseconds and reported when it became STATE_ON
					 * (or STATE_OFF, if the system failed to start the Bluetooth.)
					 */
					Toast.makeText(getApplicationContext(), "Il sistema sta cercando di abilitare l'antenna in background...", Toast.LENGTH_LONG).show();
					finish();
				} else {
					handler.obtainMessage(Main.HDLR_BT_VERIFY_ANTENNA_ENABLED).sendToTarget();
				}
			} else if ((resultCode == Activity.RESULT_CANCELED)) {
				Toast.makeText(getApplicationContext(), "L'app necessita del Bluetooth", Toast.LENGTH_LONG).show();
				finish();
			}
		}
	}
}



/*
 * try { Thread.sleep(500); } catch (InterruptedException e) { e.printStackTrace(); };
 */