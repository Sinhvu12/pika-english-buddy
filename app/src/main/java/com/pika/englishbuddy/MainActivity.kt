package com.pika.englishbuddy
import android.Manifest
import android.animation.ObjectAnimator
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.Bitmap
import android.util.Base64
import android.graphics.Color
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.SpeechRecognizer
import android.speech.RecognizerIntent
import android.speech.RecognitionListener
import android.content.Intent
import java.util.Locale
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
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
 private lateinit var answerRow:LinearLayout
 private lateinit var pika:ImageView
 private var selectedDress=0
 private lateinit var mouth:View
 private lateinit var eyeL:View
 private lateinit var eyeR:View
 private var score=0
 private var offline=false
 private var questionIndex=0
 private var lastStarAwardMs=0L
 private var quizGeneration=0
 private var tts:TextToSpeech?=null
 private var ttsReady=false
 private var recognizer:SpeechRecognizer?=null
 private var listeningOffline=false
 private var geminiFailureMessage=""
 private var expectedAnswer=""
 private val pictures=mapOf("Yellow" to "☀️","Blue" to "🔵","Pink" to "🌸","Cat" to "🐱","Dog" to "🐶","Duck" to "🦆","Green" to "🌿","Red" to "🔴","Purple" to "🟣","Five" to "🖐️","Two" to "✌️","Ten" to "🙌")
 private val quiz=listOf(
  "What color is the sun?" to arrayOf("Yellow","Blue","Pink"),
  "Which animal says meow?" to arrayOf("Cat","Dog","Duck"),
  "What color is grass?" to arrayOf("Green","Red","Purple"),
  "How many fingers on one hand?" to arrayOf("Five","Two","Ten")
 )
 private var sessionKey:String=""
 private val secretAlias="lumi_gemini_parent_key"
 private fun secretKey():SecretKey{
  val store=KeyStore.getInstance("AndroidKeyStore").apply{load(null)}
  val old=store.getKey(secretAlias,null) as? SecretKey
  if(old!=null)return old
  val generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore")
  generator.init(KeyGenParameterSpec.Builder(secretAlias,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
  return generator.generateKey()
 }
 private fun saveParentKey(key:String){
  val cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,secretKey())
  val data=cipher.doFinal(key.toByteArray(Charsets.UTF_8))
  getSharedPreferences("lumi_parent",MODE_PRIVATE).edit().putString("key_iv",Base64.encodeToString(cipher.iv,Base64.NO_WRAP)).putString("key_data",Base64.encodeToString(data,Base64.NO_WRAP)).apply()
 }
 private fun readParentKey():String=runCatching{
  val prefs=getSharedPreferences("lumi_parent",MODE_PRIVATE)
  val iv=Base64.decode(prefs.getString("key_iv",""),Base64.DEFAULT)
  val data=Base64.decode(prefs.getString("key_data",""),Base64.DEFAULT)
  val cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,secretKey(),GCMParameterSpec(128,iv))
  String(cipher.doFinal(data),Charsets.UTF_8)
 }.getOrDefault("")
 private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
 private fun shape(c:Int,r:Int,stroke:Int=0,sc:Int=Color.TRANSPARENT)=GradientDrawable().apply{setColor(c);cornerRadius=dp(r).toFloat();if(stroke>0)setStroke(dp(stroke),sc)}
 override fun onCreate(b:Bundle?){super.onCreate(b);WindowCompat.setDecorFitsSystemWindows(window,false);window.statusBarColor=Color.TRANSPARENT;window.navigationBarColor=Color.rgb(255,244,248);score=getSharedPreferences("lumi",0).getInt("stars",0);selectedDress=getSharedPreferences("lumi",0).getInt("dress",0).coerceIn(0,4);ui();voice=RealtimeVoiceClient(BuildConfig.PIKA_TOKEN_URL,this);tts=TextToSpeech(this){code->ttsReady=code==TextToSpeech.SUCCESS;if(ttsReady)tts?.language=Locale.US}}
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
  pika=ImageView(this).apply{
   scaleType=ImageView.ScaleType.FIT_CENTER
   contentDescription="Lumi"
  }
  applyDress()
  stage.addView(pika,FrameLayout.LayoutParams(-1,-1))
  eyeL=View(this).apply{background=shape(Color.rgb(255,218,220),20);alpha=0f};eyeR=View(this).apply{background=shape(Color.rgb(255,218,220),20);alpha=0f};mouth=View(this).apply{background=shape(Color.rgb(190,45,75),20);alpha=0f}
  stage.addView(eyeL,FrameLayout.LayoutParams(dp(34),dp(11)));stage.addView(eyeR,FrameLayout.LayoutParams(dp(34),dp(11)));stage.addView(mouth,FrameLayout.LayoutParams(dp(22),dp(12)))
  stage.post{fun pos(v:View,x:Float,y:Float){v.x=stage.width*x-v.layoutParams.width/2f;v.y=stage.height*y-v.layoutParams.height/2f};pos(eyeL,.39f,.31f);pos(eyeR,.61f,.31f);pos(mouth,.50f,.39f);blinkLoop()}
  bubble=TextView(this).apply{text="";textSize=18f;setTextColor(Color.rgb(99,29,71));setTypeface(typeface,Typeface.BOLD);gravity=Gravity.CENTER;background=shape(Color.argb(246,255,255,255),22);elevation=dp(5).toFloat();setPadding(dp(18),dp(13),dp(18),dp(13))}
  stage.addView(bubble,FrameLayout.LayoutParams(-1,-2).apply{gravity=Gravity.TOP;setMargins(dp(20),dp(18),dp(20),0)});bubble.visibility=View.GONE
  status=TextView(this).apply{text="";textSize=14f;setTextColor(Color.rgb(119,76,98));gravity=Gravity.CENTER}
  mic=TextView(this).apply{text="🎙";textSize=38f;gravity=Gravity.CENTER;setTextColor(Color.WHITE);background=shape(Color.rgb(250,35,109),38);elevation=dp(8).toFloat();setOnClickListener{startVoice()}}
  root.addView(top,LinearLayout.LayoutParams(-1,dp(76)))
  root.addView(stage,LinearLayout.LayoutParams(-1,0,1f).apply{setMargins(0,dp(5),0,dp(10))})
  root.addView(status,LinearLayout.LayoutParams(-1,dp(32)))
  answerRow=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;visibility=View.GONE}
  root.addView(answerRow,LinearLayout.LayoutParams(-1,-2))
  val micRow=FrameLayout(this);micRow.addView(mic,FrameLayout.LayoutParams(dp(76),dp(76),Gravity.CENTER))
  root.addView(micRow,LinearLayout.LayoutParams(-1,dp(84)));setContentView(root)
  ObjectAnimator.ofFloat(pika,View.TRANSLATION_Y,0f,-dp(5).toFloat(),0f).apply{duration=3000;repeatCount=ObjectAnimator.INFINITE;start()}
 }
 private fun offlineListen(){
  if(!offline||listeningOffline||isFinishing||android.os.Build.VERSION.SDK_INT<31||!SpeechRecognizer.isOnDeviceRecognitionAvailable(this))return
  if(recognizer==null){
   recognizer=SpeechRecognizer.createOnDeviceSpeechRecognizer(this)
   recognizer?.setRecognitionListener(object:RecognitionListener{
    override fun onReadyForSpeech(params:Bundle?){status.text="🎤 Speak!"}
    override fun onBeginningOfSpeech(){}
    override fun onRmsChanged(rmsdB:Float){}
    override fun onBufferReceived(buffer:ByteArray?){}
    override fun onEndOfSpeech(){}
    override fun onError(error:Int){if(!offline||!listeningOffline)return;listeningOffline=false;status.text="🌸 Tap a picture or say the answer"}
    override fun onResults(results:Bundle?){
     if(!offline||!listeningOffline)return
     listeningOffline=false
     val words=results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
     if(words.contains(expectedAnswer,true))offlineAnswer(true)
     else status.text="🌸 Try saying it again or tap a picture"
    }
    override fun onPartialResults(partialResults:Bundle?){}
    override fun onEvent(eventType:Int,params:Bundle?){}
   })
  }
  listeningOffline=true
  val intent=Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE,"en-US").putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM).putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,false)
  runCatching{recognizer?.startListening(intent)}.onFailure{listeningOffline=false;status.text="🌸 Tap a picture to answer"}
 }
 private fun offlineAnswer(good:Boolean){
  listeningOffline=false
  recognizer?.cancel()
  answerRow.visibility=View.GONE
  val reply=if(good)"Great job! ⭐" else "Good try! 🌸"
  if(good){score++;getSharedPreferences("lumi",0).edit().putInt("stars",score).apply();stars.text="⭐ $score"}
  bubble.text=reply
  if(ttsReady)tts?.speak(reply,TextToSpeech.QUEUE_FLUSH,null,"answer")
  questionIndex++
  val nextQuestion=questionIndex
  mic.postDelayed({if(!isFinishing&&offline&&questionIndex==nextQuestion)fallback()},1900)
 }
 private fun fallback(){
  offline=true
  voice.close()
  val q=quiz[questionIndex%quiz.size]
  expectedAnswer=q.second[0]
  bubble.visibility=View.VISIBLE
  bubble.text=q.first
  status.text="🌸 Let's play!"
  answerRow.removeAllViews()
  answerRow.visibility=View.VISIBLE
  val options=q.second.withIndex().shuffled()
  val thisQuiz=++quizGeneration
  for(item in options){
   val button=TextView(this).apply{
    text=pictures[item.value]?:item.value
    contentDescription=item.value
    textSize=46f
    includeFontPadding=true
    gravity=Gravity.CENTER
    setTextColor(Color.rgb(111,39,85))
    setTypeface(typeface,Typeface.BOLD)
    background=shape(Color.WHITE,24,2,Color.rgb(255,169,204))
    setPadding(dp(6),dp(2),dp(6),dp(2))
    setOnClickListener{
     if(offline && thisQuiz==quizGeneration){quizGeneration++;offlineAnswer(item.index==0)}
    }
   }
   answerRow.addView(button,LinearLayout.LayoutParams(-1,dp(90)).apply{setMargins(0,dp(3),0,dp(3))})
  }
  if(ttsReady)tts?.speak(q.first,TextToSpeech.QUEUE_FLUSH,null,"question")
  mic.postDelayed({if(offline&&!isFinishing&&quizGeneration==thisQuiz)offlineListen()},1800)
 }
 private fun connectGemini(){
  if(offline){offline=false;quizGeneration++;answerRow.visibility=View.GONE;recognizer?.cancel();listeningOffline=false;geminiFailureMessage="";status.text="Connecting to Gemini Live…"}
  if(sessionKey.isBlank())sessionKey=readParentKey()
  if(sessionKey.isNotBlank()){voice.connect(sessionKey);return}
  val keyInput=EditText(this).apply{hint="Gemini API key";inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD;setSingleLine(true);setPadding(dp(20),dp(12),dp(20),dp(12))}
  android.app.AlertDialog.Builder(this).setTitle("Gemini Live · Free tier").setMessage("Paste your Gemini API key from Google AI Studio. Parents only: enter the Gemini API key once. It will be encrypted using Android Keystore and reused automatically on this device.").setView(keyInput).setPositiveButton("Connect"){_,_->val k=keyInput.text.toString().trim();if(k.isNotBlank()){runCatching{saveParentKey(k)}.onFailure{status.text="Unable to securely save key.";return@setPositiveButton};sessionKey=k;voice.connect(k)}else status.text="Enter a Gemini API key to connect."}.setNegativeButton("Cancel",null).show()
 }
 private fun startVoice(){mic.animate().scaleX(.9f).scaleY(.9f).setDuration(100).withEndAction{mic.animate().scaleX(1f).scaleY(1f).duration=140}.start();if(ActivityCompat.checkSelfPermission(this,Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED)connectGemini() else ActivityCompat.requestPermissions(this,arrayOf(Manifest.permission.RECORD_AUDIO),7)}
 override fun onRequestPermissionsResult(r:Int,p:Array<out String>,g:IntArray){super.onRequestPermissionsResult(r,p,g);if(r==7&&g.firstOrNull()==PackageManager.PERMISSION_GRANTED)connectGemini()}
 override fun onStatus(t:String)=runOnUiThread{if(!offline&&!isFinishing)status.text=t}
 override fun onUserTranscript(t:String)=runOnUiThread{if(!offline&&!isFinishing&&t.isNotBlank()&&android.os.SystemClock.elapsedRealtime()-lastStarAwardMs>2500){lastStarAwardMs=android.os.SystemClock.elapsedRealtime();score++;getSharedPreferences("lumi",0).edit().putInt("stars",score).apply();stars.text="⭐ $score"}}
 override fun onPikaTranscript(t:String)=Unit
 override fun onSpeaking(a:Boolean,l:Float)=runOnUiThread{if(offline||isFinishing)return@runOnUiThread;status.text=if(a)"Lumi is speaking…" else "I'm listening…";mouth.animate().cancel();if(a){mouth.alpha=.88f;mouth.animate().scaleY(1.8f).setDuration(120).withEndAction{mouth.animate().scaleY(.65f).setDuration(120).start()}.start()}else{mouth.alpha=0f;mouth.scaleY=1f}}
 override fun onReady(r:Boolean)=runOnUiThread{if(r){offline=false;answerRow.visibility=View.GONE;status.text="🟢 Connected · Lumi is listening";bubble.visibility=View.GONE}}
 override fun onError(t:String)=runOnUiThread{geminiFailureMessage=t.take(150);if(!offline)fallback();status.text="⚠ Gemini: $geminiFailureMessage";Toast.makeText(this,"Gemini: $geminiFailureMessage",Toast.LENGTH_LONG).show()}
 private fun blinkLoop(){eyeL.postDelayed(object:Runnable{override fun run(){eyeL.alpha=.92f;eyeR.alpha=.92f;eyeL.postDelayed({eyeL.alpha=0f;eyeR.alpha=0f},130);eyeL.postDelayed(this,3200)}},1800)}
 private fun applyDress(){
  val outfit=when(selectedDress){
   1->R.drawable.lumi_dress_sky
   2->R.drawable.lumi_dress_sunny
   3->R.drawable.lumi_dress_mint
   4->R.drawable.lumi_dress_purple
   else->R.drawable.lumi_dress_pink
  }
  pika.setImageResource(outfit)
 }
 private fun wardrobe(){
  val need=intArrayOf(0,10,20,35,50)
  val names=arrayOf("🌸 Pink Princess","💙 Sky Blue","💛 Sunny Yellow","🌿 Mint Green","💜 Purple Sparkle")
  val options=names.mapIndexed{i,n->n+if(i==selectedDress)"  ✓ Đang mặc" else if(score>=need[i])"  ✓ Đã mở khóa" else "  🔒 ${need[i]} ⭐"}.toTypedArray()
  android.app.AlertDialog.Builder(this).setTitle("Lumi's Wardrobe").setItems(options){_,i->
   if(score>=need[i]){
    selectedDress=i
    getSharedPreferences("lumi",0).edit().putInt("dress",i).apply()
    applyDress()
    Toast.makeText(this,"Lumi đã thay váy! 💗",Toast.LENGTH_SHORT).show()
   }else Toast.makeText(this,"Cần ${need[i]} sao để mở váy này ⭐",Toast.LENGTH_SHORT).show()
  }.setNegativeButton("Close",null).show()
 }
 override fun onDestroy(){offline=false;recognizer?.destroy();tts?.shutdown();voice.close();super.onDestroy()}
}
