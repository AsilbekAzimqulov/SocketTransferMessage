package uz.anor.clientserver

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import kotlinx.android.synthetic.main.activity_main.*
import uz.anor.clientserver.client.ClientActivity
import uz.anor.clientserver.server.ServerActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        server.setOnClickListener {
            startActivity(Intent(this, Server::class.java))
        }
        client.setOnClickListener {
            startActivity(Intent(this, Client::class.java))

        }

    }
}
