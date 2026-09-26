package com.pika.englishbuddy
import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
class MainActivity:AppCompatActivity(),RealtimeVoiceClient.Listener{
 private lateinit var voice:RealtimeVoiceClient;private lateinit var status:TextView;private lateinit var bubble:TextView;private lateinit var stars:TextView;private var score=0
 private fun shape(c:Int,r:Float)=GradientDrawable().apply{setColor(c);cornerRadius=r}
 override fun onCreate(b:Bundle?){super.onCreate(b);score=getSharedPreferences("pika",0).getInt("stars",0);ui();voice=RealtimeVoiceClient(BuildConfig.PIKA_TOKEN_URL,this)}
 private fun ui(){val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(24,28,24,22);setBackgroundColor(Color.rgb(255,246,250))}
  val top=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL};val logo=TextView(this).apply{text="Pika";textSize=34f;setTextColor(Color.rgb(230,39,103));setTypeface(typeface,Typeface.BOLD)}
  stars=TextView(this).apply{text="⭐ $score";textSize=17f;background=shape(Color.WHITE,50f);setPadding(18,10,18,10)}
  val dress=TextView(this).apply{text="👗";textSize=22f;background=shape(Color.WHITE,50f);setPadding(16,9,16,9);setOnClickListener{wardrobe()}}
  top.addView(logo);top.addView(TextView(this).apply{text="  English Buddy";textSize=15f;setTextColor(Color.rgb(128,62,95))});top.addView(Space(this),LinearLayout.LayoutParams(0,1,1f));top.addView(stars);top.addView(Space(this),LinearLayout.LayoutParams(10,1));top.addView(dress)
  val stage=FrameLayout(this).apply{background=shape(Color.rgb(255,238,246),55f)};val pika=ImageView(this).apply{setImageResource(R.drawable.pika_friend);scaleType=ImageView.ScaleType.CENTER_CROP};stage.addView(pika,FrameLayout.LayoutParams(-1,-1))
  bubble=TextView(this).apply{text="Hello! I'm Pika!\nLet's speak English together 💗";textSize=19f;setTextColor(Color.rgb(82,35,67));setTypeface(typeface,Typeface.BOLD);gravity=Gravity.CENTER;background=shape(Color.argb(242,255,255,255),40f);setPadding(20,16,20,16)}
  stage.addView(bubble,FrameLayout.LayoutParams(-1,-2).apply{gravity=Gravity.TOP;setMargins(22,22,22,0)})
  status=TextView(this).apply{text="Tap the microphone and talk to Pika";textSize=15f;setTextColor(Color.rgb(111,72,93));gravity=Gravity.CENTER;setPadding(8,14,8,10)}
  val mic=TextView(this).apply{text="🎙";textSize=42f;gravity=Gravity.CENTER;setTextColor(Color.WHITE);background=shape(Color.rgb(244,51,112),100f);setOnClickListener{startVoice()}}
  root.addView(top);root.addView(Space(this),LinearLayout.LayoutParams(1,14));root.addView(stage,LinearLayout.LayoutParams(-1,0,1f));root.addView(status);root.addView(mic,LinearLayout.LayoutParams(-1,92));setContentView(root)}
 private fun startVoice(){if(ActivityCompat.checkSelfPermission(this,Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED)voice.connect() else ActivityCompat.requestPermissions(this,arrayOf(Manifest.permission.RECORD_AUDIO),7)}
 override fun onRequestPermissionsResult(r:Int,p:Array<out String>,g:IntArray){super.onRequestPermissionsResult(r,p,g);if(r==7&&g.firstOrNull()==PackageManager.PERMISSION_GRANTED)voice.connect()}
 override fun onStatus(t:String)=runOnUiThread{status.text=t}
 override fun onUserTranscript(t:String)=runOnUiThread{if(t.isNotBlank()){score++;getSharedPreferences("pika",0).edit().putInt("stars",score).apply();stars.text="⭐ $score";bubble.text=t}}
 override fun onPikaTranscript(t:String)=runOnUiThread{if(t.isNotBlank())bubble.text=t}
 override fun onSpeaking(a:Boolean,l:Float)=runOnUiThread{status.text=if(a)"Pika is speaking…" else "I'm listening…"}
 override fun onReady(r:Boolean)=Unit
 override fun onError(t:String)=runOnUiThread{status.text=if(BuildConfig.PIKA_TOKEN_URL.contains("example.invalid"))"Voice will be connected in the final API step" else t}
 private fun wardrobe(){val need=intArrayOf(0,10,20,35,50);val a=arrayOf("🌸 Pink Princess  ✓","💙 Sky Blue  • 10 ⭐","💛 Sunny Yellow • 20 ⭐","🌿 Mint Green • 35 ⭐","💜 Purple Sparkle • 50 ⭐");android.app.AlertDialog.Builder(this).setTitle("Pika's Wardrobe").setItems(a){_,i->Toast.makeText(this,if(score>=need[i])"Dress saved for Pika 💗" else "Keep talking with Pika to earn stars!",Toast.LENGTH_SHORT).show()}.setNegativeButton("Close",null).show()}
 override fun onDestroy(){voice.close();super.onDestroy()}
}