package uz.anor.sockettransfermessagebywifi

import android.content.Context
import android.graphics.Color
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Handler
import android.util.Log
import java.io.*
import java.net.InetAddress
import java.net.Socket
import java.net.UnknownHostException

class Client(context: Context) {

    private val TAG = "MRX"
    private var clientThread: ClientThread? = null
    private var thread: Thread? = null
    private var handler: Handler? = null


    private val SERVICE_NAME = "Client Device"
    private val SERVICE_TYPE = "_http._tcp."

    private var hostAddress: InetAddress? = null
    private var hostPort: Int = 0
    private var mNsdManager: NsdManager? = null
    lateinit var onReceive: (String) -> Unit
    lateinit var onDisconnectServer: (String) -> Unit
    lateinit var onErrorConnection: (String) -> Unit

    init {
        handler = Handler()
        mNsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    }

    fun onPause() {
        if (mNsdManager != null) {
            mNsdManager!!.stopServiceDiscovery(mDiscoveryListener)
        }
    }

    fun onResume() {
        if (mNsdManager != null) {
            mNsdManager!!.discoverServices(
                SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, mDiscoveryListener
            )
        }
    }

    fun onDestroy() {
        if (null != clientThread) {
            clientThread!!.sendMessage("Disconnect")
            clientThread = null
        }
    }

    fun connectServer() {
        clientThread = ClientThread()
        thread = Thread(clientThread)
        thread!!.start()
    }

    fun sendData(clientMessage: String) {
        if (null != clientThread) {
            clientThread!!.sendMessage(clientMessage)
        }
    }

    internal inner class ClientThread : Runnable {

        private var socket: Socket? = null
        private var input: BufferedReader? = null

        override fun run() {

            try {
                val serverAddr = InetAddress.getByName(SERVER_IP)
                socket = Socket(serverAddr, SERVERPORT)

                while (!Thread.currentThread().isInterrupted) {

                    this.input = BufferedReader(InputStreamReader(socket!!.getInputStream()))
                    var message: String? = input!!.readLine()
                    if (null == message || "Disconnect".contentEquals(message)) {
                        Thread.interrupted()
                        message = "Server Disconnected."
                        onDisconnectServer.invoke(message)
//                        showMessage(message, Color.RED)
                        break
                    }
                    onReceive.invoke(message)
//                    showMessage("Server: $message", clientTextColor)
                }

            } catch (e1: UnknownHostException) {
                e1.printStackTrace()
                onErrorConnection.invoke(e1.message.toString())
            } catch (e1: IOException) {
                e1.printStackTrace()
                onErrorConnection.invoke(e1.message.toString())
            }

        }

        fun sendMessage(message: String) {
            Thread(Runnable {
                try {
                    if (null != socket) {
                        val out = PrintWriter(
                            BufferedWriter(
                                OutputStreamWriter(socket!!.getOutputStream())
                            ),
                            true
                        )
                        out.println(message)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }).start()
        }

    }

    internal var mDiscoveryListener: NsdManager.DiscoveryListener = object : NsdManager.DiscoveryListener {

        // Called as soon as service discovery begins.
        override fun onDiscoveryStarted(regType: String) {
            Log.d(TAG, "Service discovery started")
        }

        override fun onServiceFound(service: NsdServiceInfo) {
            // A service was found! Do something with it.
            Log.d(TAG, "Service discovery success : $service")
            Log.d(TAG, "Host = " + service.serviceName)
            Log.d(TAG, "port = " + service.port.toString())

            if (service.serviceType != SERVICE_TYPE) {
                // Service type is the string containing the protocol and
                // transport layer for this service.
                Log.d(TAG, "Unknown Service Type: " + service.serviceType)
            } else if (service.serviceName == SERVICE_NAME) {
                // The name of the service tells the user what they'd be
                // connecting to. It could be "Bob's Chat App".
                Log.d(TAG, "Same machine: $SERVICE_NAME")
            } else {
                Log.d(TAG, "Diff Machine : " + service.serviceName)
                // connect to the service and obtain serviceInfo

                mNsdManager!!.resolveService(service, object : NsdManager.ResolveListener {

                    override fun onResolveFailed(nsdServiceInfo: NsdServiceInfo, errorCode: Int) {
                        Log.e(TAG, "Resolve failed $errorCode")
                        Log.e(TAG, "serivce = $nsdServiceInfo")
                    }

                    override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                        Log.d(TAG, "Resolve Succeeded. $serviceInfo")

                        if (serviceInfo.serviceName == SERVICE_NAME) {
                            Log.d(TAG, "Same IP.")
                            return
                        }

                        // Obtain port and IP
                        hostPort = serviceInfo.port
                        hostAddress = serviceInfo.host
                        Log.d(TAG, "hostPort=$hostPort")
                        Log.d(TAG, "hostAddess=" + hostAddress!!)
                        if (serviceInfo.serviceName.equals(Server.SERVICE_NAME) && serviceInfo.serviceType.equals(Server.SERVICE_TYPE))
                            SERVER_IP = hostAddress!!.hostAddress

                    }
                })
            }
        }

        override fun onServiceLost(service: NsdServiceInfo) {
            // When the network service is no longer available.
            // Internal bookkeeping code goes here.
            Log.e(TAG, "service lost$service")
        }

        override fun onDiscoveryStopped(serviceType: String) {
            Log.i(TAG, "Discovery stopped: $serviceType")
        }

        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
            Log.e(TAG, "Discovery failed: Error code:$errorCode")
            mNsdManager!!.stopServiceDiscovery(this)
        }

        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
            Log.e(TAG, "Discovery failed: Error code:$errorCode")
            mNsdManager!!.stopServiceDiscovery(this)
        }
    }


    companion object {

        val SERVERPORT = 3003

        var SERVER_IP = "192.168.0.189"
    }
}