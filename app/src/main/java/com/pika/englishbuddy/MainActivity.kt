package com.pika.englishbuddy
import android.Manifest
import android.animation.ObjectAnimator
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.Window
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity:AppCompatActivity(),RealtimeVoiceClient.Listener{
 private lateinit var voice:RealtimeVoiceClient
 private lateinit var status:TextView
 private lateinit var bubble:TextView
 private lateinit var stars:TextView
 private lateinit var mic:TextView
 private lateinit var pika:ImageView
 private var score=0
 private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
 private fun shape(c:Int,r:Int,stroke:Int=0,sc:Int=Color.TRANSPARENT)=GradientDrawable().apply{setColor(c);cornerRadius=dp(r).toFloat();if(stroke>0)setStroke(dp(stroke),sc)}
 override fun onCreate(b:Bundle?){super.onCreate(b);WindowCompat.setDecorFitsSystemWindows(window,false);window.statusBarColor=Color.TRANSPARENT;window.navigationBarColor=Color.rgb(255,244,248);score=getSharedPreferences("lumi",0).getInt("stars",0);ui();voice=RealtimeVoiceClient(BuildConfig.PIKA_TOKEN_URL,this)}
 private fun ui(){
  val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(18),0,dp(18),dp(12));setBackgroundColor(Color.rgb(255,246,250))}
  ViewCompat.setOnApplyWindowInsetsListener(root){v,i->val s=i.getInsets(WindowInsetsCompat.Type.systemBars());v.setPadding(dp(18),s.top+dp(8),dp(18),s.bottom+dp(10));i}
  val top=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL}
  val logo=TextView(this).apply{text="Lumi";textSize=32f;setTextColor(Color.rgb(245,35,111));setTypeface(typeface,Typeface.BOLD)}
  val sub=TextView(this).apply{text="  English Buddy";textSize=14f;setTextColor(Color.rgb(132,73,103))}
  stars=TextView(this).apply{text="⭐ $score";textSize=16f;background=shape(Color.WHITE,24,1,Color.rgb(255,220,233));setPadding(dp(14),dp(8),dp(14),dp(8))}
  val dress=TextView(this).apply{text="👗";textSize=21f;gravity=Gravity.CENTER;background=shape(Color.WHITE,24,1,Color.rgb(255,220,233));setPadding(dp(11),dp(7),dp(11),dp(7));setOnClickListener{wardrobe()}}
  top.addView(logo);top.addView(sub);top.addView(Space(this),LinearLayout.LayoutParams(0,1,1f));top.addView(stars);top.addView(Space(this),LinearLayout.LayoutParams(dp(8),1));top.addView(dress)
  val stage=FrameLayout(this).apply{background=shape(Color.rgb(255,234,243),30);clipToOutline=true}
  pika=ImageView(this).apply{setImageResource(R.drawable.lumi_chibi);scaleType=ImageView.ScaleType.FIT_CENTER;contentDescription="Lumi"}
  stage.addView(pika,FrameLayout.LayoutParams(-1,-1))
  bubble=TextView(this).apply{text="Hello! I'm Lumi!\nLet's speak English together 💗";textSize=18f;setTextColor(Color.rgb(99,29,71));setTypeface(typeface,Typeface.BOLD);gravity=Gravity.CENTER;background=shape(Color.argb(246,255,255,255),22);elevation=dp(5).toFloat();setPadding(dp(18),dp(13),dp(18),dp(13))}
  stage.addView(bubble,FrameLayout.LayoutParams(-1,-2).apply{gravity=Gravity.TOP;setMargins(dp(20),dp(18),dp(20),0)})
  status=TextView(this).apply{text="Tap the microphone and talk to Lumi";textSize=14f;setTextColor(Color.rgb(119,76,98));gravity=Gravity.CENTER}
  mic=TextView(this).apply{text="🎙";textSize=38f;gravity=Gravity.CENTER;setTextColor(Color.WHITE);background=shape(Color.rgb(250,35,109),38);elevation=dp(8).toFloat();setOnClickListener{startVoice()}}
  root.addView(top,LinearLayout.LayoutParams(-1,dp(58)))
  root.addView(stage,LinearLayout.LayoutParams(-1,0,1f).apply{setMargins(0,dp(5),0,dp(10))})
  root.addView(status,LinearLayout.LayoutParams(-1,dp(32)))
  val micRow=FrameLayout(this);micRow.addView(mic,FrameLayout.LayoutParams(dp(76),dp(76),Gravity.CENTER))
  root.addView(micRow,LinearLayout.LayoutParams(-1,dp(84)));setContentView(root)
  ObjectAnimator.ofFloat(pika,View.TRANSLATION_Y,0f,-dp(5).toFloat(),0f).apply{duration=3000;repeatCount=ObjectAnimator.INFINITE;start()}
 }
 private fun startVoice(){mic.animate().scaleX(.9f).scaleY(.9f).setDuration(100).withEndAction{mic.animate().scaleX(1f).scaleY(1f).duration=140}.start();if(ActivityCompat.checkSelfPermission(this,Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED)voice.connect() else ActivityCompat.requestPermissions(this,arrayOf(Manifest.permission.RECORD_AUDIO),7)}
 override fun onRequestPermissionsResult(r:Int,p:Array<out String>,g:IntArray){super.onRequestPermissionsResult(r,p,g);if(r==7&&g.firstOrNull()==PackageManager.PERMISSION_GRANTED)voice.connect()}
 override fun onStatus(t:String)=runOnUiThread{status.text=t}
 override fun onUserTranscript(t:String)=runOnUiThread{if(t.isNotBlank()){score++;getSharedPreferences("lumi",0).edit().putInt("stars",score).apply();stars.text="⭐ $score";bubble.text=t}}
 override fun onPikaTranscript(t:String)=runOnUiThread{if(t.isNotBlank()){bubble.alpha=0f;bubble.text=t;bubble.animate().alpha(1f).setDuration(220).start()}}
 override fun onSpeaking(a:Boolean,l:Float)=runOnUiThread{status.text=if(a)"Lumi is speaking…" else "I'm listening…"}
 override fun onReady(r:Boolean)=Unit
 override fun onError(t:String)=runOnUiThread{status.text=if(BuildConfig.PIKA_TOKEN_URL.contains("example.invalid"))"Voice will be connected in the final API step" else t}
 private fun wardrobe(){val need=intArrayOf(0,10,20,35,50);val a=arrayOf("🌸 Pink Princess  ✓","💙 Sky Blue  • 10 ⭐","💛 Sunny Yellow • 20 ⭐","🌿 Mint Green • 35 ⭐","💜 Purple Sparkle • 50 ⭐");android.app.AlertDialog.Builder(this).setTitle("Lumi's Wardrobe").setItems(a){_,i->Toast.makeText(this,if(score>=need[i])"Dress saved for Lumi 💗" else "Keep talking with Lumi to earn stars!",Toast.LENGTH_SHORT).show()}.setNegativeButton("Close",null).show()}
 override fun onDestroy(){voice.close();super.onDestroy()}
}