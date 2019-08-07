package uz.anor.clientserver

import android.content.Context
import android.graphics.Color
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.io.*
import java.net.ServerSocket
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.*

class Server : AppCompatActivity(), View.OnClickListener {

    private var serverSocket: ServerSocket? = null
    private var tempClientSocket: Socket? = null
    internal var serverThread: Thread? = null
    private var handler: Handler? = null
    private var mNsdManager: NsdManager? = null
    private var msgList: LinearLayout? = null
    private var greenColor: Int = 0
    private var edMessage: EditText? = null



    internal var mRegistrationListener: NsdManager.RegistrationListener = object : NsdManager.RegistrationListener {

        @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
        override fun onServiceRegistered(NsdServiceInfo: NsdServiceInfo) {
            val mServiceName = NsdServiceInfo.serviceName
            SERVICE_NAME = mServiceName
            Log.d("MRX", "Registered name : $mServiceName")
        }

        override fun onRegistrationFailed(
            serviceInfo: NsdServiceInfo,
            errorCode: Int
        ) {
            // Registration failed! Put debugging code here to determine
            // why.
        }

        override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {
            // Service has been unregistered. This only happens when you
            // call
            // NsdManager.unregisterService() and pass in this listener.
            Log.d(
                "MRX",
                "Service Unregistered : " + serviceInfo.serviceName
            )
        }

        override fun onUnregistrationFailed(
            serviceInfo: NsdServiceInfo,
            errorCode: Int
        ) {
            // Unregistration failed. Put debugging code here to determine
            // why.
        }
    }

    internal val time: String
        get() {
            val sdf = SimpleDateFormat("HH:mm:ss")
            return sdf.format(Date())
        }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.ac_ser)
        title = "Server"
        greenColor = ContextCompat.getColor(this, R.color.green)
        handler = Handler()
        msgList = findViewById(R.id.msgList)
        edMessage = findViewById(R.id.edMessage)

        mNsdManager = getSystemService(Context.NSD_SERVICE) as NsdManager
        //        registerService(SERVER_PORT);
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
    override fun onPause() {
        if (mNsdManager != null) {
            mNsdManager!!.unregisterService(mRegistrationListener)
        }
        super.onPause()
    }


    override fun onResume() {
        super.onResume()
        if (mNsdManager != null) {
            registerService(SERVER_PORT)
        }
    }

    fun registerService(port: Int) {
        var serviceInfo: NsdServiceInfo? = null
        serviceInfo = NsdServiceInfo()
        serviceInfo.serviceName = SERVICE_NAME
        serviceInfo.serviceType = SERVICE_TYPE
        serviceInfo.port = port

        mNsdManager!!.registerService(
            serviceInfo,
            NsdManager.PROTOCOL_DNS_SD,
            mRegistrationListener
        )
    }

    ////////////////////////////////////////////////////////////////////

    fun textView(message: String?, color: Int): TextView {
        var message = message
        if (null == message || message.trim { it <= ' ' }.isEmpty()) {
            message = "<Empty Message>"
        }
        val tv = TextView(this)
        tv.setTextColor(color)
        tv.text = "$message [$time]"
        tv.textSize = 20f
        tv.setPadding(0, 5, 0, 0)
        return tv
    }

    fun showMessage(message: String, color: Int) {
        handler!!.post { msgList!!.addView(textView(message, color)) }
    }

    override fun onClick(view: View) {
        if (view.id == R.id.start_server) {
            msgList!!.removeAllViews()
            showMessage("Server Started.", Color.BLACK)
            this.serverThread = Thread(ServerThread())
            this.serverThread!!.start()
            return
        }
        if (view.id == R.id.send_data) {
            val msg = edMessage!!.text.toString().trim { it <= ' ' }
            showMessage("Server : $msg", Color.BLUE)
            clientsSocket.forEach {
                sendMessage(msg, it)
            }
        }
    }

    private fun sendMessage(message: String, socket: Socket) {
        try {
            if (null != tempClientSocket) {
                Thread(Runnable {
                    var out: PrintWriter? = null
                    try {
                        out = PrintWriter(
                            BufferedWriter(
                                OutputStreamWriter(socket.getOutputStream())
                            ),
                            true
                        )
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }

                    out!!.println(message)
                }).start()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

    }

    private val clientsSocket = arrayListOf<Socket>()

    internal inner class ServerThread : Runnable {

        override fun run() {
            var socket: Socket
            try {
                serverSocket = ServerSocket(SERVER_PORT)
                //                findViewById(R.id.start_server).setVisibility(View.GONE);
            } catch (e: IOException) {
                e.printStackTrace()
                showMessage("Error Starting Server : " + e.message, Color.RED)
            }

            if (null != serverSocket) {
                while (!Thread.currentThread().isInterrupted) {
                    try {
                        socket = serverSocket!!.accept()
                        clientsSocket.add(socket)
                        val commThread = CommunicationThread(socket)
                        Thread(commThread).start()
                    } catch (e: IOException) {
                        e.printStackTrace()
                        showMessage("Error Communicating to Client :" + e.message, Color.RED)
                    }

                }
            }
        }
    }

    internal inner class CommunicationThread(private val clientSocket: Socket) : Runnable {

        private var input: BufferedReader? = null

        init {
            tempClientSocket = clientSocket
            try {
                this.input = BufferedReader(InputStreamReader(this.clientSocket.getInputStream()))
            } catch (e: IOException) {
                e.printStackTrace()
                showMessage("Error Connecting to Client!!", Color.RED)
            }

            showMessage("Connected to Client!!", greenColor)
        }

        override fun run() {

            while (!Thread.currentThread().isInterrupted) {
                try {
                    var read: String? = input!!.readLine()
                    if (null == read || "Disconnect".contentEquals(read)) {
                        Thread.interrupted()
                        read = "Client Disconnected"
                        showMessage("Client : $read", greenColor)
                        break
                    }
                    showMessage("Client : $read", greenColor)
                    clientsSocket.forEach {
                        sendMessage(read,it)
                    }
                } catch (e: IOException) {
                    e.printStackTrace()
                }

            }
        }

    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
    override fun onDestroy() {
//        if (mNsdManager != null) {
//            mNsdManager!!.unregisterService(mRegistrationListener)
//        }
        super.onDestroy()
        if (null != serverThread) {
            clientsSocket.forEach {
                sendMessage("Disconnect", it)
            }
            serverThread!!.interrupt()
            serverThread = null
        }
    }

    companion object {
        val SERVER_PORT = 3003
        var SERVICE_NAME = "Server Device"
        val SERVICE_TYPE = "_http._tcp."
    }
}