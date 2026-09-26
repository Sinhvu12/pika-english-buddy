package com.pika.englishbuddy
import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat

class MainActivity:AppCompatActivity(),RealtimeVoiceClient.Listener{
 private lateinit var voice:RealtimeVoiceClient; private lateinit var status:TextView; private lateinit var bubble:TextView
 private lateinit var stars:TextView; private var score=0
 override fun onCreate(b:Bundle?){super.onCreate(b);score=getSharedPreferences("pika",0).getInt("stars",0);ui();voice=RealtimeVoiceClient(BuildConfig.PIKA_TOKEN_URL,this);mic()}
 private fun ui(){val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER_HORIZONTAL;setPadding(24,24,24,24);setBackgroundColor(Color.rgb(255,244,248))}
  val title=TextView(this).apply{text="Pika English Buddy";textSize=26f;gravity=Gravity.CENTER}
  stars=TextView(this).apply{text="⭐ $score";textSize=16f}
  val wardrobe=Button(this).apply{text="👗";setOnClickListener{wardrobe()}}
  val bar=LinearLayout(this).apply{gravity=Gravity.END;addView(stars);addView(wardrobe)}
  val robot=ImageView(this).apply{setImageResource(R.drawable.pika_robot);scaleType=ImageView.ScaleType.FIT_CENTER}
  bubble=TextView(this).apply{text="Hi! I'm Pika. Let's talk in English!";textSize=18f;gravity=Gravity.CENTER;setPadding(16,16,16,16);setBackgroundColor(Color.WHITE)}
  status=TextView(this).apply{text="Starting Pika…";gravity=Gravity.CENTER;setPadding(8,12,8,12)}
  root.addView(title);root.addView(bar,LinearLayout.LayoutParams(-1,-2));root.addView(robot,LinearLayout.LayoutParams(-1,0,1f));root.addView(bubble,LinearLayout.LayoutParams(-1,-2));root.addView(status);setContentView(root)}
 private fun mic(){if(ActivityCompat.checkSelfPermission(this,Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED)voice.connect() else ActivityCompat.requestPermissions(this,arrayOf(Manifest.permission.RECORD_AUDIO),7)}
 override fun onRequestPermissionsResult(r:Int,p:Array<out String>,g:IntArray){super.onRequestPermissionsResult(r,p,g);if(r==7&&g.firstOrNull()==PackageManager.PERMISSION_GRANTED)voice.connect()else onError("Pika needs the microphone to hear you.")}
 override fun onStatus(t:String)=runOnUiThread{status.text=t}
 override fun onUserTranscript(t:String)=runOnUiThread{if(t.isNotBlank()){score++;getSharedPreferences("pika",0).edit().putInt("stars",score).apply();stars.text="⭐ $score";bubble.text="You: $t"}}
 override fun onPikaTranscript(t:String)=runOnUiThread{bubble.text="Pika: $t"}
 override fun onSpeaking(active:Boolean,level:Float)=runOnUiThread{status.text=if(active)"Pika is speaking…" else "Your turn!"}
 override fun onReady(ready:Boolean)=Unit
 override fun onError(t:String)=runOnUiThread{status.text=t}
 private fun wardrobe(){val items=arrayOf("Pink dress","Sky blue dress • 10 ⭐","Sunny yellow • 20 ⭐","Mint green • 35 ⭐","Purple sparkle • 50 ⭐");android.app.AlertDialog.Builder(this).setTitle("Pika Wardrobe").setItems(items,null).setPositiveButton("Close",null).show()}
 override fun onDestroy(){voice.close();super.onDestroy()}
}