package com.pika.englishbuddy
import android.media.*
import android.util.Base64
import okhttp3.*
import okio.ByteString
import org.json.JSONObject
import org.json.JSONArray
import kotlin.concurrent.thread
import android.os.Handler
import android.os.Looper
import java.util.concurrent.TimeUnit
import java.util.concurrent.LinkedBlockingQueue

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
 private val audioQueue=LinkedBlockingQueue<ByteArray>()
 @Volatile private var audioTurnEnded=false
 @Volatile private var audioGeneration=0
 @Volatile private var suppressMicUntil=0L
 @Volatile private var assistantSpeaking=false
 @Volatile private var playingAudio=false
 private var audioWorker:Thread?=null
 @Volatile private var sessionEpoch=0
 @Volatile private var playbackErrorReported=false
 @Volatile private var playbackResetRequested=false
 @Volatile private var recording=false
 @Volatile private var connected=false
 private val inputRate=16000
 private val outputRate=24000
 fun connect(apiKey:String){
  if(apiKey.isBlank()){listener.onError("Enter your Gemini API key first.");return}
  if(connected||connecting){close();listener.onStatus("Voice session ended.");return}
  intentionalClose=false;failed=false;connecting=true;socketOpened=false;sessionEpoch++
  val epoch=sessionEpoch
  audioQueue.clear();audioTurnEnded=false;assistantSpeaking=false;suppressMicUntil=0L;playbackResetRequested=false
  timeout?.let{main.removeCallbacks(it)}
  timeout=Runnable{if(connecting&&!connected)fail(if(socketOpened)"Gemini WebSocket opened but setup was not confirmed within 30s" else "Could not open Gemini WebSocket within 30s; check network, VPN or firewall")}
  main.postDelayed(timeout!!,30000)
  listener.onStatus("Connecting to Gemini Live…")
  val url="wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent?key="+apiKey.trim()
  ws=http.newWebSocket(Request.Builder().url(url).build(),object:WebSocketListener(){
   override fun onOpen(w:WebSocket,r:Response){if(epoch!=sessionEpoch)return;socketOpened=true;listener.onStatus("Gemini WebSocket open · waiting for session…");configure(w)}
   override fun onMessage(w:WebSocket,text:String){if(epoch==sessionEpoch)receive(text)}
   override fun onMessage(w:WebSocket,bytes:ByteString){if(epoch==sessionEpoch)receive(bytes.utf8())}
   override fun onFailure(w:WebSocket,t:Throwable,r:Response?){if(epoch==sessionEpoch&&!intentionalClose)fail("Gemini handshake failed: "+(r?.code?.toString()?:t.javaClass.simpleName)+" "+(t.message?:"").take(90))}
   override fun onClosed(w:WebSocket,code:Int,reason:String){if(!intentionalClose)fail("Gemini closed connection: $code $reason")}
   override fun onClosing(w:WebSocket,code:Int,reason:String){if(!intentionalClose)fail("Gemini rejected session: $code $reason")}
  })
 }
 private fun receive(payload:String){
  runCatching{handle(JSONObject(payload))}.onFailure{fail("Gemini message parse error: "+it.javaClass.simpleName)}
 }
 private fun fail(reason:String){
  if(failed||intentionalClose)return
  failed=true
  timeout?.let{main.removeCallbacks(it)};timeout=null
  listener.onError(reason)
  close()
 }
 private fun configure(w:WebSocket){
  val prompt="You are Lumi, a kind, playful bilingual Vietnamese-English speaking friend for a young Vietnamese child learning English. The child may speak English, Vietnamese, mixed language, softly, hesitantly or with imperfect pronunciation. Understand the meaning rather than demanding exact words. Respond naturally to what the child actually says. Use short, clear, warm spoken English at a relaxed pace, usually 3 to 8 words per sentence, with small natural pauses. Do not drill the same question repeatedly or make the child repeat a phrase unless they want to. If the child says 'con không hiểu', 'không biết', 'what?', 'hả?', 'I don't know', asks for Vietnamese, or sounds confused, gently explain the last question in simple Vietnamese (one brief sentence), then give one very easy English example and let them answer in either language. If the child hesitates, encourage them with a simple example or offer two easy choices, without pressure. Acknowledge their answer before changing the topic. Ask at most one question per turn; sometimes just comment or celebrate instead of asking. When the child speaks Vietnamese, reply briefly in Vietnamese and introduce one easy English word naturally. Never demand personal details such as full name, location, school or contact information. Never tell the child to move closer to the phone. Avoid unsafe topics and long monologues. Your role is a supportive friend, not a quiz machine."
  val setup=JSONObject().put("model","models/gemini-2.5-flash-native-audio-preview-12-2025")
   .put("generationConfig",JSONObject().put("responseModalities",JSONArray().put("AUDIO"))
    .put("speechConfig",JSONObject().put("voiceConfig",JSONObject().put("prebuiltVoiceConfig",JSONObject().put("voiceName","Leda")))))
   .put("systemInstruction",JSONObject().put("parts",JSONArray().put(JSONObject().put("text",prompt))))
   .put("inputAudioTranscription",JSONObject())
   .put("outputAudioTranscription",JSONObject())
  if(!w.send(JSONObject().put("setup",setup).toString()))fail("Could not send Gemini session setup")
 }
 private fun startMic(){
  if(recording)return
  if(!connected)return
  try{
   val min=AudioRecord.getMinBufferSize(inputRate,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT)
   recorder=AudioRecord.Builder().setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
    .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(inputRate).setChannelMask(AudioFormat.CHANNEL_IN_MONO).build())
    .setBufferSizeInBytes(maxOf(min,6400)).build()
   if(recorder?.state!=AudioRecord.STATE_INITIALIZED)throw IllegalStateException("Microphone unavailable")
   recorder?.startRecording()
   if(recorder?.recordingState!=AudioRecord.RECORDSTATE_RECORDING)throw IllegalStateException("Microphone did not start")
   recording=true
   listener.onStatus("Listening…")
   thread(name="gemini-mic"){
    val buf=ByteArray(3200)
    while(recording){
     val n=try{recorder?.read(buf,0,buf.size)?:-1}catch(_:Exception){-1}
     if(n>0 && connected && !assistantSpeaking && android.os.SystemClock.elapsedRealtime()>=suppressMicUntil){
      val audio=JSONObject().put("mimeType","audio/pcm;rate=16000").put("data",Base64.encodeToString(buf,0,n,Base64.NO_WRAP))
      ws?.send(JSONObject().put("realtimeInput",JSONObject().put("audio",audio)).toString())
     }else if(n<0)break
    }
   }
  }catch(e:Exception){fail("Microphone cannot start") }
 }
 private fun handle(j:JSONObject){
  if(j.has("setupComplete")||j.has("setup_complete")){
   connecting=false;connected=true
   timeout?.let{main.removeCallbacks(it)};timeout=null
   listener.onReady(true)
   startMic()
   val greetingPart=JSONObject().put("text","Say only: Hi, friend! [pause] Want to play with me? Speak slowly, warmly and naturally.")
   val greetingTurn=JSONObject().put("role","user").put("parts",JSONArray().put(greetingPart))
   val hello=JSONObject().put("clientContent",JSONObject().put("turns",JSONArray().put(greetingTurn)).put("turnComplete",true))
   ws?.send(hello.toString())
   return
  }
  val error=j.optJSONObject("error")
  if(error!=null){fail(error.optString("message","Gemini API error"));return}
  val content=j.optJSONObject("serverContent")?:j.optJSONObject("server_content")?:return
  content.optJSONObject("inputTranscription")?.optString("text")?.takeIf{it.isNotBlank()}?.let{listener.onUserTranscript(it)}
  content.optJSONObject("outputTranscription")?.optString("text")?.takeIf{it.isNotBlank()}?.let{listener.onPikaTranscript(it)}
  if(content.optBoolean("interrupted")){audioGeneration++;audioQueue.clear();audioTurnEnded=false;playbackResetRequested=true;assistantSpeaking=false;suppressMicUntil=android.os.SystemClock.elapsedRealtime()+350;listener.onSpeaking(false,0f)}
  val parts=content.optJSONObject("modelTurn")?.optJSONArray("parts")
  if(parts!=null)for(i in 0 until parts.length()){
   val inline=parts.optJSONObject(i)?.optJSONObject("inlineData")?:continue
   val data=inline.optString("data")
   if(data.isNotBlank())play(Base64.decode(data,Base64.DEFAULT))
  }
  if(content.optBoolean("turnComplete"))audioTurnEnded=true
 }
 private fun play(bytes:ByteArray){
  if(!connected)return
  if(!playingAudio){
   synchronized(audioQueue){
    if(!playingAudio){
     playingAudio=true
     audioWorker=thread(name="gemini-audio",isDaemon=true){
      try{
       val min=AudioTrack.getMinBufferSize(outputRate,AudioFormat.CHANNEL_OUT_MONO,AudioFormat.ENCODING_PCM_16BIT)
       val track=AudioTrack.Builder().setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
        .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(outputRate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
        .setBufferSizeInBytes(maxOf(min,24000)).setTransferMode(AudioTrack.MODE_STREAM).build()
       player=track
       track.play()
       var framesWritten=0L
       while(playingAudio){
        if(playbackResetRequested){
         playbackResetRequested=false
         track.pause()
         track.flush()
         track.stop()
         track.play()
         framesWritten=0L
        }
        val chunk=audioQueue.poll(35,TimeUnit.MILLISECONDS)
        if(chunk!=null){
         val generation=audioGeneration
         var offset=0
         while(offset<chunk.size && playingAudio && generation==audioGeneration){
          val n=track.write(chunk,offset,chunk.size-offset)
          if(n<=0)break
          offset+=n
          framesWritten+=n/2L
         }
        }else if(audioTurnEnded && audioQueue.isEmpty()){
         // A write only fills AudioTrack's buffer: wait for the speaker to
         // actually finish playing the buffered frames before opening the mic.
         val played=track.playbackHeadPosition.toLong() and 0xffffffffL
         if(played>=framesWritten){
          audioTurnEnded=false
          assistantSpeaking=false
          suppressMicUntil=android.os.SystemClock.elapsedRealtime()+180
          listener.onSpeaking(false,0f)
         }
        }
       }
      }catch(_:InterruptedException){}catch(e:Exception){
       if(!intentionalClose && !playbackErrorReported){playbackErrorReported=true;main.post{fail("Audio playback failed: "+(e.message?:"device audio error").take(90))}}
      }finally{
       try{player?.stop()}catch(_:Exception){}
       try{player?.release()}catch(_:Exception){}
       player=null
      }
     }
    }
   }
  }
  audioTurnEnded=false
  assistantSpeaking=true
  audioQueue.offer(bytes)
  listener.onSpeaking(true,.5f)
 }
 fun close(){
  intentionalClose=true;connecting=false;sessionEpoch++
  timeout?.let{main.removeCallbacks(it)};timeout=null
  recording=false;connected=false
  playingAudio=false;audioGeneration++;audioQueue.clear();playbackResetRequested=true;audioWorker?.interrupt();audioWorker=null
  assistantSpeaking=false
  listener.onSpeaking(false,0f)
  try{recorder?.stop()}catch(_:Exception){}
  recorder?.release();recorder=null
  // The audio worker owns AudioTrack shutdown.
  ws?.close(1000,"bye");ws=null
 }
}
