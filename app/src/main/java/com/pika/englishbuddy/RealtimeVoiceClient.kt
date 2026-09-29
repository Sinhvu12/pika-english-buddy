package com.pika.englishbuddy
import android.media.*
import android.util.Base64
import okhttp3.*
import org.json.JSONObject
import org.json.JSONArray
import kotlin.concurrent.thread
import android.os.Handler
import android.os.Looper
import java.util.concurrent.TimeUnit

class RealtimeVoiceClient(private val unusedTokenUrl:String,private val listener:Listener){
 interface Listener{
  fun onStatus(text:String)
  fun onUserTranscript(text:String)
  fun onPikaTranscript(text:String)
  fun onSpeaking(active:Boolean,level:Float)
  fun onReady(ready:Boolean)
  fun onError(text:String)
 }
 private val http=OkHttpClient.Builder().connectTimeout(10,TimeUnit.SECONDS).readTimeout(0,TimeUnit.SECONDS).build()
 private val main=Handler(Looper.getMainLooper())
 private var timeout:Runnable?=null
 @Volatile private var intentionalClose=false
 @Volatile private var connecting=false
 @Volatile private var failed=false
 @Volatile private var socketOpened=false
 private var ws:WebSocket?=null
 private var recorder:AudioRecord?=null
 private var player:AudioTrack?=null
 @Volatile private var recording=false
 @Volatile private var connected=false
 private val inputRate=16000
 private val outputRate=24000
 fun connect(apiKey:String){
  if(apiKey.isBlank()){listener.onError("Enter your Gemini API key first.");return}
  if(connected||connecting){close();listener.onStatus("Voice session ended.");return}
  intentionalClose=false;failed=false;connecting=true;socketOpened=false
  timeout?.let{main.removeCallbacks(it)}
  timeout=Runnable{if(connecting&&!connected)fail(if(socketOpened)"Gemini WebSocket opened but setup was not confirmed within 30s" else "Could not open Gemini WebSocket within 30s; check network, VPN or firewall")}
  main.postDelayed(timeout!!,30000)
  listener.onStatus("Connecting to Gemini Live…")
  val url="wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent?key="+apiKey.trim()
  ws=http.newWebSocket(Request.Builder().url(url).build(),object:WebSocketListener(){
   override fun onOpen(w:WebSocket,r:Response){socketOpened=true;listener.onStatus("Gemini WebSocket open · waiting for session…");configure(w)}
   override fun onMessage(w:WebSocket,text:String){runCatching{handle(JSONObject(text))}.onFailure{fail("Gemini response could not be decoded: "+it.javaClass.simpleName)}}
   override fun onFailure(w:WebSocket,t:Throwable,r:Response?){if(!intentionalClose)fail("Gemini handshake failed: "+(r?.code?.toString()?:t.javaClass.simpleName)+" "+(t.message?:"").take(90))}
   override fun onClosed(w:WebSocket,code:Int,reason:String){if(!intentionalClose)fail("Gemini closed connection: $code $reason")}
   override fun onClosing(w:WebSocket,code:Int,reason:String){if(!intentionalClose)fail("Gemini rejected session: $code $reason")}
  })
 }
 private fun fail(reason:String){
  if(failed||intentionalClose)return
  failed=true
  timeout?.let{main.removeCallbacks(it)};timeout=null
  listener.onError(reason)
  close()
 }
 private fun configure(w:WebSocket){
  val prompt="You are Lumi, a cheerful English speaking companion for children. Speak naturally using short clear English, respond to what the child says, and ask one friendly follow-up question. Gently model correct grammar. Never ask for private information. Avoid unsafe topics."
  val setup=JSONObject().put("model","models/gemini-2.5-flash-native-audio-preview-12-2025")
   .put("generationConfig",JSONObject().put("responseModalities",JSONArray().put("AUDIO"))
    .put("speechConfig",JSONObject().put("voiceConfig",JSONObject().put("prebuiltVoiceConfig",JSONObject().put("voiceName","Aoede")))))
   .put("systemInstruction",JSONObject().put("parts",JSONArray().put(JSONObject().put("text",prompt))))
   .put("inputAudioTranscription",JSONObject())
   .put("outputAudioTranscription",JSONObject())
  if(!w.send(JSONObject().put("setup",setup).toString()))fail("Could not send Gemini session setup")
 }
 private fun startMic(){
  if(recording)return
  try{
   val min=AudioRecord.getMinBufferSize(inputRate,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT)
   recorder=AudioRecord.Builder().setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
    .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(inputRate).setChannelMask(AudioFormat.CHANNEL_IN_MONO).build())
    .setBufferSizeInBytes(maxOf(min,6400)).build()
   recorder?.startRecording()
   recording=true
   listener.onStatus("Listening…")
   thread(name="gemini-mic"){
    val buf=ByteArray(3200)
    while(recording){
     val n=try{recorder?.read(buf,0,buf.size)?:-1}catch(_:Exception){-1}
     if(n>0){
      val audio=JSONObject().put("mimeType","audio/pcm;rate=16000").put("data",Base64.encodeToString(buf,0,n,Base64.NO_WRAP))
      ws?.send(JSONObject().put("realtimeInput",JSONObject().put("audio",audio)).toString())
     }else if(n<0)break
    }
   }
  }catch(e:Exception){fail("Microphone cannot start") }
 }
 private fun handle(j:JSONObject){
  if(j.has("setupComplete")){
   connecting=false;connected=true
   timeout?.let{main.removeCallbacks(it)};timeout=null
   listener.onReady(true)
   startMic()
   val greetingPart=JSONObject().put("text","Say a short cheerful hello to the child in English, then invite them to speak.")
   val greetingTurn=JSONObject().put("role","user").put("parts",JSONArray().put(greetingPart))
   val hello=JSONObject().put("clientContent",JSONObject().put("turns",JSONArray().put(greetingTurn)).put("turnComplete",true))
   ws?.send(hello.toString())
   return
  }
  val error=j.optJSONObject("error")
  if(error!=null){fail(error.optString("message","Gemini API error"));return}
  val content=j.optJSONObject("serverContent")?:return
  content.optJSONObject("inputTranscription")?.optString("text")?.takeIf{it.isNotBlank()}?.let{listener.onUserTranscript(it)}
  content.optJSONObject("outputTranscription")?.optString("text")?.takeIf{it.isNotBlank()}?.let{listener.onPikaTranscript(it)}
  if(content.optBoolean("interrupted")){player?.pause();player?.flush();player?.play();listener.onSpeaking(false,0f)}
  val parts=content.optJSONObject("modelTurn")?.optJSONArray("parts")
  if(parts!=null)for(i in 0 until parts.length()){
   val inline=parts.optJSONObject(i)?.optJSONObject("inlineData")?:continue
   val data=inline.optString("data")
   if(data.isNotBlank())play(Base64.decode(data,Base64.DEFAULT))
  }
  if(content.optBoolean("turnComplete"))listener.onSpeaking(false,0f)
 }
 private fun play(bytes:ByteArray){
  if(player==null){
   val min=AudioTrack.getMinBufferSize(outputRate,AudioFormat.CHANNEL_OUT_MONO,AudioFormat.ENCODING_PCM_16BIT)
   player=AudioTrack.Builder().setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
    .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(outputRate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
    .setBufferSizeInBytes(maxOf(min,48000)).setTransferMode(AudioTrack.MODE_STREAM).build().also{it.play()}
  }
  player?.write(bytes,0,bytes.size)
  listener.onSpeaking(true,.5f)
 }
 fun close(){
  intentionalClose=true;connecting=false
  timeout?.let{main.removeCallbacks(it)};timeout=null
  recording=false;connected=false
  try{recorder?.stop()}catch(_:Exception){}
  recorder?.release();recorder=null
  player?.stop();player?.release();player=null
  ws?.close(1000,"bye");ws=null
 }
}
