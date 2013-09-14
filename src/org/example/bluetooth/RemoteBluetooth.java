package org.example.bluetooth;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeech.OnInitListener;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.view.Window;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public class RemoteBluetooth extends Activity implements OnClickListener, OnInitListener 
{	
	//Layout view
	private TextView mTitle;
	public String [] Pos;
    public TextView Xpos;
    public TextView Ypos;
    public TextView Zpos;
    public int Indice;
    public String [] EEPROM;
    
    //Limites Zona Trabajo
    private static final int X_SUP = 120;
    private static final int X_INF = 40;
    private static final int Y_SUP = 120;
    private static final int Y_INF = 40;
    private static final int Z_SUP = 150;
    private static final int Z_INF = 50;
    
    //Handler para el incremento automatico
    private Handler ActualizarHandler = new Handler();
    public static int REP_DELAY = 80;   
    private boolean continuarIncremento;
    private boolean continuarDecremento;
	
	//Codigos peticion Activity DeviceList
    private static final int REQUEST_CONNECT_DEVICE = 1;
    private static final int REQUEST_ENABLE_BT = 2;
    
    //Tipos de mensaje que devuelve el BluetoothCommandService
    public static final int MESSAGE_STATE_CHANGE = 1;
    public static final int MESSAGE_READ = 2;
    public static final int MESSAGE_WRITE = 3;
    public static final int MESSAGE_DEVICE_NAME = 4;
    public static final int MESSAGE_TOAST = 5;
    
    //Strings que recibe de BluetoothCommandService
    public static final String DEVICE_NAME = "device_name";
    public static final String TOAST = "toast";
	
	//Nombre del dispositivos conectado
    private String mConnectedDeviceName = null;
    //Referencia al Adaptador Bluetooth
    private BluetoothAdapter mBluetoothAdapter = null;
    //Referencia a la clase BluetoothCommandService
    private BluetoothCommandService mCommandService = null;
    
    /*VARIABLES RECONOCIMIENTO DE VOZ*/
    //Variable asignada como id a la actividad Voice Recognition
    private static final int VR_REQUEST = 10;
    //Variable que guarda el comando dicho por Voz
    private String palabraEscogida;
    
    /*VARIABLES PARA TEXT TO SPEECH (TTS)*/
    //Variable asignada como id a la actividad TTS
    private static final int TTS_REQUEST = 11;
    //Referenciamos un TTS
    private TextToSpeech repeatTTS;
    
    //Comandos
    ArrayList<String> comandos;
    private String ESPIRAL = "espiral";
    private String REPETIBILIDAD = "repetibilidad";
    private String PUNTOS = "puntos";
	
    /**Se llama la primera vez que se ejecuta la actividad */
    @Override
    public void onCreate(Bundle savedInstanceState) 
    {
        super.onCreate(savedInstanceState);
        
        //Colocamos un titulo personalizado
        requestWindowFeature(Window.FEATURE_CUSTOM_TITLE);
        setContentView(R.layout.main);
        getWindow().setFeatureInt(Window.FEATURE_CUSTOM_TITLE, R.layout.custom_title);
        
        //Ponemos el titulo según el estado
        mTitle = (TextView) findViewById(R.id.title_left_text);
        mTitle.setText(R.string.app_name);
        mTitle = (TextView) findViewById(R.id.title_right_text);
        
        //Referenciamos el Boton de Reconocimiento Voz
        Button vozBtn = (Button) findViewById(R.id.vozBtn);
        
        //Referenciamos el Boton de Ayuda Comandos
        Button cmdBtn = (Button) findViewById(R.id.acercaDeCmd);
        cmdBtn.setOnClickListener(this);
        
        //Referenciamos los TextVIew indicadores de la posicion del robot
        Xpos = (TextView) findViewById(R.id.X_ind);
        Ypos = (TextView) findViewById(R.id.Y_ind);
        Zpos = (TextView) findViewById(R.id.Z_ind);
        
        //Inicializamos los comandos por voz
        comandos = new ArrayList<String>();
        comandos.add("guardar " + ESPIRAL);
        comandos.add("reproducir " + ESPIRAL);
        comandos.add("guardar " + REPETIBILIDAD);
        comandos.add("reproducir " + REPETIBILIDAD);
        comandos.add("guardar " + PUNTOS);
        comandos.add("reproducir " + PUNTOS);
        
        //Obtenemos el adaptador bluetooth
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        //Si retorna null, no existe bluetooth en el dispositivo
        if (mBluetoothAdapter == null) 
        {
            Toast.makeText(this, "Bluetooth no disponible", Toast.LENGTH_LONG).show();
            finish();	//Salimos de la aplicacion
            return;
        }
        
        //Averiguamos si soporta reconocimiento por voz
        PackageManager packManager = getPackageManager();
        //Realizamos para ello una petición al entorno
        List<ResolveInfo> intActivities = packManager.queryIntentActivities(new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH), 0);
        
        if (intActivities.size() != 0)		//Soporta reconocimiento de voz, activamos el botón
        {
        	vozBtn.setOnClickListener(this);
        	//Creamos un Intent para el TTS
        	Intent compruebaTTSIntent = new Intent();
        	//Configurado para que compruebe si hay datos
        	compruebaTTSIntent.setAction(TextToSpeech.Engine.ACTION_CHECK_TTS_DATA);
        	//iniciamos la actividad, los resultados se encontraran en onActivityResult
        	startActivityForResult(compruebaTTSIntent, TTS_REQUEST);
        }

        else	//No soporta reconocimiento por voz, desactivamos boton y avisamos al usuario
        {          
        	vozBtn.setEnabled(false);
            Toast.makeText(this, R.string.not_supported, Toast.LENGTH_LONG).show();
        }
        
        /** Referenciamos los botones y declaramos un onTouchListener
         * que escucha si el boton se pulsa o se suelta
         */
        Button zMasBoton = (Button) findViewById(R.id.zmas);
        zMasBoton.setOnTouchListener(new OnTouchListener() 
        {
            @Override
            public boolean onTouch(View v, MotionEvent event) 
            {
            	//Boton Pulsado
            	if(event.getAction() == MotionEvent.ACTION_DOWN)
                {
            		continuarIncremento = true;
            		Indice = 2;
            		ActualizarHandler.post( new Actualizar() );
                }
            	//Boton Soltado
                if(event.getAction() == MotionEvent.ACTION_UP)
                {
                	continuarIncremento = false;
                }
                return false;
            }
        });

        
        Button zMenosBoton = (Button) findViewById(R.id.zmenos);
        zMenosBoton.setOnTouchListener(new OnTouchListener() 
        {
            @Override
            public boolean onTouch(View v, MotionEvent event) 
            {
            	if(event.getAction() == MotionEvent.ACTION_DOWN)
                {
            		continuarDecremento = true;
            		Indice = 2;
            		ActualizarHandler.post( new Actualizar() );
                }
                if(event.getAction() == MotionEvent.ACTION_UP)
                {
                	continuarDecremento = false;
                }
                return false;
            }
        });

        
        Button xMenosBoton = (Button) findViewById(R.id.xmenos);
        xMenosBoton.setOnTouchListener(new OnTouchListener() 
        {
        	@Override
            public boolean onTouch(View v, MotionEvent event) 
            {
            	if(event.getAction() == MotionEvent.ACTION_DOWN)
                {
            		continuarDecremento = true;
            		Indice = 0;
            		ActualizarHandler.post( new Actualizar() );
                }
                if(event.getAction() == MotionEvent.ACTION_UP)
                {
                	continuarDecremento = false;
                }
                return false;
            }
        });

        
        Button xMasBoton = (Button) findViewById(R.id.xmas);
        xMasBoton.setOnTouchListener(new OnTouchListener() 
        {
            @Override
            public boolean onTouch(View v, MotionEvent event) 
            {
            	if(event.getAction() == MotionEvent.ACTION_DOWN)
                {
            		continuarIncremento = true;
            		Indice = 0;
            		ActualizarHandler.post( new Actualizar() );
                }
                if(event.getAction() == MotionEvent.ACTION_UP)
                {
                	continuarIncremento = false;
                }
                return false;
            }
        });
        
        Button yMenosBoton = (Button) findViewById(R.id.ymenos);
        yMenosBoton.setOnTouchListener(new OnTouchListener() 
        {
        	@Override
            public boolean onTouch(View v, MotionEvent event) 
            {
            	if(event.getAction() == MotionEvent.ACTION_DOWN)
                {
            		continuarDecremento = true;
            		Indice = 1;
            		ActualizarHandler.post( new Actualizar() );
                }
                if(event.getAction() == MotionEvent.ACTION_UP)
                {
                	continuarDecremento = false;
                }
                return false;
            }
        });

        Button yMasBoton = (Button) findViewById(R.id.ymas);
        yMasBoton.setOnTouchListener(new OnTouchListener() 
        {
            @Override
            public boolean onTouch(View v, MotionEvent event) 
            {
            	if(event.getAction() == MotionEvent.ACTION_DOWN)
                {
            		continuarIncremento = true;
            		Indice = 1;
            		ActualizarHandler.post( new Actualizar() );
                }
                if(event.getAction() == MotionEvent.ACTION_UP)
                {
                	continuarIncremento = false;
                }
                return false;
            }
        });
    }

	@Override
	protected void onStart() 
	{
		super.onStart();
		//Si el Bluetooth esta apagado, solicitamos encenderlo
		if (!mBluetoothAdapter.isEnabled()) 
		{
			Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
			startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
		}
		
		//Si ya está encendido lo inicializamos
		else 
		{
			if (mCommandService==null)
				setupCommand();
		}
	}
	
	/** Se ejecuta si la actividad se minimizó o pasó a un segundo plano porque se 
	 * llamó a otra actividad (ejemplo DeviceListActivity)*/
	@Override
	protected void onResume() 
	{
		super.onResume();		
		//Comprobamos si cuando vuelve a ejecutarse la actividad en primer plano
		//el bluetooth esta activado
		if (mCommandService != null) 
		{
			//Comprobamos si seguimos conectados
			if (mCommandService.getState() == BluetoothCommandService.STATE_NONE) 
			{
				mCommandService.start();
			}
		}
	}

	private void setupCommand() 
	{
		//Crea una referencia a la clase BluetoothCommandService 
		//que gestiona las conexiones
        mCommandService = new BluetoothCommandService(this, mHandler);
	}

	@Override
	protected void onDestroy() //Se ejecuta cuando se destruye la aplicacion
	{
		super.onDestroy();
		if (mCommandService != null) //Si el gestor de conexiones esta activo lo paramos
			mCommandService.stop();
	}
	
    /**Se ejecuta cada vez que se hace click en un botón*/
	@Override
	public void onClick(View v) 
	{
	    if (v.getId() == R.id.vozBtn) 
	        //Escuchamos
	        EscucharVoz();
	    if (v.getId() == R.id.acercaDeCmd)
	    {
	        Intent i = new Intent(this, AcercaDe.class);
	        startActivity(i);
	    }
	}
	
	//Handler (Manejador) que se comunica con el gestor 
	//de conexiones BluetoothCommandService
    private final Handler mHandler = new Handler() 
    {
        @Override
        public void handleMessage(Message msg) 
        {
            switch (msg.what) 
            {
            	case MESSAGE_STATE_CHANGE:
            		switch (msg.arg1) 
            		{
                		case BluetoothCommandService.STATE_CONNECTED:
                			mTitle.setText(R.string.title_connected_to);
                			mTitle.append(mConnectedDeviceName);
                			break;
                		case BluetoothCommandService.STATE_CONNECTING:
                			mTitle.setText(R.string.title_connecting);
                			break;
                		case BluetoothCommandService.STATE_NONE:
                			mTitle.setText(R.string.title_not_connected);
                			break;
            		}
            		break;
            	case MESSAGE_DEVICE_NAME:
            		//Guardamos el nombre del dispositivo al que nos conectamos
            		mConnectedDeviceName = msg.getData().getString(DEVICE_NAME);
            		Toast.makeText(getApplicationContext(), "Conectado con "
                               + mConnectedDeviceName, Toast.LENGTH_SHORT).show();
            		break;
            	case MESSAGE_TOAST:
            		Toast.makeText(getApplicationContext(), msg.getData().getString(TOAST),
                               Toast.LENGTH_SHORT).show();
            		break;
                
            	case MESSAGE_READ: //Recibimos un mensaje
            		byte[] readBuf = (byte[]) msg.obj;
            		//Leemos el mensaje y construimos un string con la parte valida del mismo
            		String readMessage = new String(readBuf, 0, msg.arg1);
            		
            		//Finalizado el envio o reproduccion de trayectoria
            		if(readMessage.equals("FINALIZADO"))
            		{
            			repeatTTS.speak("Finalizado", TextToSpeech.QUEUE_FLUSH, null);
                		Toast.makeText(getApplicationContext(), "FINALIZADO", Toast.LENGTH_SHORT).show();
            		}
            			
            		//Primer mensaje al conectarse envia posiciones y que hay en la EEPROM
            		else
            		{
            			//Miramos que trayectorias estan guardadas en memoria
            			EEPROM = readMessage.split("/")[1].split(":");
            		
            			//Separamos las posiciones de cada servo
            			Pos = readMessage.split("/")[0].split(":");

            			Xpos.setText(Pos[0]);
            			Ypos.setText(Pos[1]);
            			Zpos.setText(Pos[2]);
            		}
            		break;
            }
        }
    };
	
    /**Función que recibe el resultado de la Actividad DeviceListActivity*/
	public void onActivityResult(int requestCode, int resultCode, Intent data) 
	{
        switch (requestCode) 
        {
        	case REQUEST_CONNECT_DEVICE:
        		//Cuando la Actividad devuelve un dispositivo al que conectarse
        		if (resultCode == Activity.RESULT_OK) 
        		{
        			//Obtenemos la direccion MAC
        			String address = data.getExtras()
                                     .getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
        			//Instanciamos un BluetoothDevice
        			BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        			//Intentamos la conexión
        			mCommandService.connect(device);
        		}
        		break;
        	case REQUEST_ENABLE_BT:
        		//Resultado de la petición de activar Bluetooth
        		if (resultCode == Activity.RESULT_OK) 
        			//Bluetooth activado y se inicia sesion
        			setupCommand();
        		
        		else 
        		{
        			//Si el usuario no acepta activar bluetooth
        			//O ha ocurrido un error
        			Toast.makeText(this, R.string.bt_not_enabled_leaving, Toast.LENGTH_SHORT).show();
        			finish();//Sale de la aplicación
        		}
        	case VR_REQUEST:
        		//Resultado de la peticion de reconocimiento de voz
        		if (resultCode == RESULT_OK)
        	    {
        	        //Guarda la lista de palabras sugeridas en un array
        	        ArrayList<String> palabrasSugeridas = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
        	        palabraEscogida = null;
        	        for (String palabra:palabrasSugeridas) 
        	        {
        	        	for (String comando:comandos) 
            	        {
        	        		//Si las palabras sugeridas coinciden con los comandos
        	        		if(palabra.equals(comando))
        	        		{
        	        			palabraEscogida = palabra;
        	        			break;
        	        		}
        	        		//Si coincide con alguna trayectoria personalizada
        	        		else if ((palabra.matches("guardar [a-z]")) || (palabra.matches("reproducir [a-z]")))
        	        		{	
        	        			palabraEscogida = palabra;
        	        			break;
        	        		}
            	        }		
        	        }
        	        
        	        if (palabraEscogida == null)
        	        {
        	        	//Advierte de un comando incorrecto
        	        	Toast.makeText(this, "Comando incorrecto", Toast.LENGTH_SHORT).show();
        	        	repeatTTS.speak("Comando Incorrecto", TextToSpeech.QUEUE_FLUSH, null);
        	        }
        	        else
        	        {	
        	        	String comandoEnviar = new String();
        	        	comandoEnviar = null;
        	        	if (palabraEscogida.regionMatches(0, "guardar", 0, "guardar".length()))
        	        	{
        	        		if(palabraEscogida.regionMatches("guardar ".length(), ESPIRAL, 0, ESPIRAL.length()))
        	        		{
        	        			comandoEnviar = "Trayect:"+"g"+"00";
        	        			EEPROM[1]="1";
        	        		}
        	        		else if(palabraEscogida.regionMatches("guardar ".length(), REPETIBILIDAD, 0, REPETIBILIDAD.length()))
        	        		{
        	        			comandoEnviar = "Trayect:"+"g"+"01";
        	        			EEPROM[0]="1";
        	        		}
        	        		else if(palabraEscogida.regionMatches("guardar ".length(), PUNTOS, 0, PUNTOS.length()))
        	        		{
        	        			comandoEnviar = "Trayect:"+"g"+"02";
        	        			EEPROM[2]="1";
        	        		}
        	        		else
        	        		{
        	        			comandoEnviar = "Trayect:"+"g"+palabraEscogida.substring("guardar ".length())+"3";
        	        			EEPROM[3]="1";
        	        		}
        	        		
        	        		repeatTTS.speak("Guardando: "+palabraEscogida.substring("guardar ".length()), TextToSpeech.QUEUE_FLUSH, null);
        	        		sendMessage(comandoEnviar);
        	        	}
        	        	else
        	        	{
        	        		if(palabraEscogida.regionMatches("reproducir ".length(), ESPIRAL, 0, ESPIRAL.length()))
        	        		{
        	        			if (EEPROM[1].equals("1"))
        	        				comandoEnviar = "Trayect:"+"r"+"00";
        	        			else
        	        				repeatTTS.speak("Guardar antes de reproducir", TextToSpeech.QUEUE_FLUSH, null);
        	        				
        	        		}
        	        		else if(palabraEscogida.regionMatches("reproducir ".length(), REPETIBILIDAD, 0, REPETIBILIDAD.length()))
        	        		{
        	        			if (EEPROM[0].equals("1"))
        	        				comandoEnviar = "Trayect:"+"r"+"01";
        	        			else
        	        				repeatTTS.speak("Guardar antes de reproducir", TextToSpeech.QUEUE_FLUSH, null);
        	        		}
        	        		else if(palabraEscogida.regionMatches("reproducir ".length(), PUNTOS, 0, PUNTOS.length()))
        	        		{
        	        			if (EEPROM[2].equals("1"))
        	        				comandoEnviar = "Trayect:"+"r"+"02";
        	        			else
        	        				repeatTTS.speak("Guardar antes de reproducir", TextToSpeech.QUEUE_FLUSH, null);
        	        		}
        	        		else
        	        		{
        	        			if (EEPROM[3].equals("1"))
        	        				comandoEnviar = "Trayect:"+"r"+palabraEscogida.substring("reproducir ".length())+"3";
        	        			else
        	        				repeatTTS.speak("Guardar antes de reproducir", TextToSpeech.QUEUE_FLUSH, null);
        	        		}
        	        		
        	        		if(comandoEnviar != null)
        	        		{
        	        			repeatTTS.speak("Reproduciendo: "+palabraEscogida.substring("reproducir ".length()), TextToSpeech.QUEUE_FLUSH, null);
        	        			sendMessage(comandoEnviar);
        	        		}
        	        	}
        	        }
        	    }
        		break;
        		
        		
        	case TTS_REQUEST:
        		//Tenemos datos de TTS
        	    if (resultCode == TextToSpeech.Engine.CHECK_VOICE_DATA_PASS)
        	        repeatTTS = new TextToSpeech(this, this);
        	    
        	    //Datos no instalados, ayudamos al usuario a instalarlos
        	    else
        	    {
        	        //Intent para iniciar el google play
        	        Intent installTTSIntent = new Intent();
        	        installTTSIntent.setAction(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA);
        	        startActivity(installTTSIntent);
        	    }
        	    break;
        	    //super.onActivityResult(requestCode, resultCode, data);   
        }
    }

	/** Menú que se activa pulsando botón del movil */
	@Override
	public boolean onCreateOptionsMenu(Menu menu) 
	{
		MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.option_menu, menu);
        return true;
	}
	
	/** Funcion que se activa cuando se selecciona algo en el Menú anterior*/
	@Override
    public boolean onOptionsItemSelected(MenuItem item) 
	{
        switch (item.getItemId()) 
        {
        	case R.id.scan:
        		//Lanza la actividad DeviceListActivity para conectarse a un dispositivo
        		Intent listIntent = new Intent(this, DeviceListActivity.class);
        		startActivityForResult(listIntent, REQUEST_CONNECT_DEVICE);
        		return true;
        }
        return false;
    }
	
    private void sendMessage(String message) 
    {
        //Miramos si estamos conectados antes de comenzar
        if (mCommandService.getState() != BluetoothCommandService.STATE_CONNECTED) 
        {
            Toast.makeText(this, R.string.title_not_connected, Toast.LENGTH_SHORT).show();
            return;
        }

        //Comprobamos si hay algo que mandar
        if (message.length() > 0) 
        {
            //Obtenemos los bytes del mensaje y se los enviamos a  BluetoothCommand
            byte[] send = message.getBytes();
            mCommandService.write(send);
        }
    }
    
	/**Se encarga de escuchar lo que dice el usuario*/
	private void EscucharVoz() 
	{
		//Creamos un Intent para el reconocimiento de voz
		Intent escucharIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
		//Indicamos el package de la aplicacion
		escucharIntent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, getClass().getPackage().getName());
		//Texto extra personalizado
		escucharIntent.putExtra(RecognizerIntent.EXTRA_PROMPT, "¿Orden?");
		//Idioma de voz free-form (Frases dictadas)
		escucharIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
		//Numero total de resultados devueltos (texto aproximado)
		escucharIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 10);
		//Inicia la actividad y espera resultado
		startActivityForResult(escucharIntent, VR_REQUEST);
	}

    /** Se crea un Thread que se encarga de actualizar la posicion si se mantiene
     * pulsado el boton correspondiente así le quita carga al programa principal*/
      class Actualizar implements Runnable 
      {
        public void run() 
        {
          if(continuarIncremento)
          {
        	  incrementar();
        	  ActualizarHandler.postDelayed( new Actualizar(), REP_DELAY);
          }
          
          if(continuarDecremento)
          {
        	  decrementar();
        	  ActualizarHandler.postDelayed( new Actualizar(), REP_DELAY);
          }
        }
     }
      
      public void incrementar()
      {
    	  switch(Indice)
    	  {
    	  	case 0:
    	  		if ((Integer.parseInt(Pos[0])+1) <= X_SUP)
    	    		  Pos[0] = String.format("%03d", Integer.parseInt(Pos[0])+1);
    	  		break;
    	  		
    	  	case 1:
    	  		if ((Integer.parseInt(Pos[1])+1) <= Y_SUP)
    	    		  Pos[1] = String.format("%03d", Integer.parseInt(Pos[1])+1);
    	  		break;
    	  		
    	  	case 2:
    	  		if ((Integer.parseInt(Pos[2])+1) <= Z_SUP)
    	    		  Pos[2] = String.format("%03d", Integer.parseInt(Pos[2])+1);
    	  		break;    	  		
    	  }
    	  Pos[0] = String.format("%03d", Integer.parseInt(Pos[0]));
    	  Pos[1] = String.format("%03d", Integer.parseInt(Pos[1]));
    	  Pos[2] = String.format("%03d", Integer.parseInt(Pos[2]));
    	  sendMessage("" + Pos[0] + ":" + Pos[1] + ":" + Pos[2]);
          Xpos.setText(Pos[0]);
          Ypos.setText(Pos[1]);
          Zpos.setText(Pos[2]);
      }
      
      public void decrementar()
      {
    	  switch(Indice)
    	  {
    	  	case 0:
    	  		if ((Integer.parseInt(Pos[0])-1) >= X_INF)
    	    		  Pos[0] = String.format("%03d", Integer.parseInt(Pos[0])-1);
    	  		break;
    	  		
    	  	case 1:
    	  		if ((Integer.parseInt(Pos[1])-1) >= Y_INF)
    	    		  Pos[1] = String.format("%03d", Integer.parseInt(Pos[1])-1);
    	  		break;
    	  		
    	  	case 2:
    	  		if ((Integer.parseInt(Pos[2])-1) >= Z_INF)
    	    		  Pos[2] = String.format("%03d", Integer.parseInt(Pos[2])-1);
    	  		break;    	  		
    	  }
    	  Pos[0] = String.format("%03d", Integer.parseInt(Pos[0]));
    	  Pos[1] = String.format("%03d", Integer.parseInt(Pos[1]));
    	  Pos[2] = String.format("%03d", Integer.parseInt(Pos[2]));
    	  sendMessage("" + Pos[0] + ":" + Pos[1] + ":" + Pos[2]);
          Xpos.setText(Pos[0]);
          Ypos.setText(Pos[1]);
          Zpos.setText(Pos[2]);
      }
      
      /**onInit se ejecuta cuando se inicia el TTS*/
      public void onInit(int initStatus) 
      {
          //Si se inició con éxito, configuramos el idioma
          if (initStatus == TextToSpeech.SUCCESS)
          {
        	  Locale loc = new Locale ("spa", "ESP"); //español españa
              repeatTTS.setLanguage(loc);
          }
      }
}