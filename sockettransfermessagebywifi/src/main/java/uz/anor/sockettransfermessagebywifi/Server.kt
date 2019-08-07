package uz.anor.sockettransfermessagebywifi

import android.content.Context
import android.graphics.Color
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.os.Handler
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat.getSystemService
import java.io.*
import java.net.ServerSocket
import java.net.Socket

class Server(context: Context) {
    private var serverSocket: ServerSocket? = null
    private var tempClientSocket: Socket? = null
    private var serverThread: Thread? = null
    private var handler: Handler? = null
    private var mNsdManager: NsdManager? = null
    private val clientsSocket = arrayListOf<Socket>()
     lateinit var onReceive: (String) -> Unit
    lateinit var onConnectedDevice: (ArrayList<Socket>) -> Unit
    lateinit var onDisconnectDevice: (String) -> Unit

    init {
        handler = Handler()
        mNsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    }

    fun onPause() {
        if (mNsdManager != null) {
            mNsdManager!!.unregisterService(mRegistrationListener)
        }
    }

    fun onResume() {
        if (mNsdManager != null) {
            registerService(SERVER_PORT)
        }
    }

    fun onDestroy() {
        if (null != serverThread) {
            clientsSocket.forEach {
                sendMessage("Disconnect", it)
            }
            serverThread!!.interrupt()
            serverThread = null
        }
    }

    fun startServer() {
        this.serverThread = Thread(ServerThread())
        this.serverThread!!.start()
    }

    fun sendData(data: String) {
        clientsSocket.forEach {
            sendMessage(data, it)
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

    private fun registerService(port: Int) {
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

    internal inner class ServerThread : Runnable {

        override fun run() {
            var socket: Socket
            try {
                serverSocket = ServerSocket(SERVER_PORT)
                //                findViewById(R.id.start_server).setVisibility(View.GONE);
            } catch (e: IOException) {
                e.printStackTrace()
//                showMessage("Error Starting Server : " + e.message, Color.RED)
            }

            if (null != serverSocket) {
                while (!Thread.currentThread().isInterrupted) {
                    try {
                        socket = serverSocket!!.accept()
                        clientsSocket.add(socket)
                        val commThread = CommunicationThread(socket)
                        Thread(commThread).start()
                        onConnectedDevice.invoke(clientsSocket)
                    } catch (e: IOException) {
                        e.printStackTrace()
//                        showMessage("Error Communicating to Client :" + e.message, Color.RED)
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
//                showMessage("Error Connecting to Client!!", Color.RED)
            }

//            showMessage("Connected to Client!!", greenColor)
        }

        override fun run() {
//Receive
            while (!Thread.currentThread().isInterrupted) {
                try {
                    var read: String? = input!!.readLine()
                    if (null == read || "Disconnect".contentEquals(read)) {
                        Thread.interrupted()
                        read = "Client Disconnected"
                        onDisconnectDevice.invoke(read)
//                        showMessage("Client : $read", greenColor)
                        break
                    }
//                    showMessage("Client : $read", greenColor)
                    clientsSocket.forEach {
                        sendMessage(read, it)
                    }
                    onReceive.invoke(read)
                } catch (e: IOException) {
                    e.printStackTrace()
                }

            }
        }

    }

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


    companion object {
        val SERVER_PORT = 3003
        var SERVICE_NAME = "Server Device"
        val SERVICE_TYPE = "_http._tcp."
    }
}