package com.pika.englishbuddy
import android.media.*
import android.util.Base64
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import kotlin.concurrent.thread
import kotlin.math.abs

class RealtimeVoiceClient(private val tokenUrl:String,private val listener:Listener){
 interface Listener{fun onStatus(text:String);fun onUserTranscript(text:String);fun onPikaTranscript(text:String);fun onSpeaking(active:Boolean,level:Float);fun onReady(ready:Boolean);fun onError(text:String)}
 private val http=OkHttpClient();private var ws:WebSocket?=null;private var recorder:AudioRecord?=null;private var player:AudioTrack?=null;@Volatile private var recording=false;private val rate=24000
 fun connect(){if(tokenUrl.contains("example.invalid")){listener.onError("Voice backend is not configured yet.");return};listener.onStatus("Lumi is waking up…");http.newCall(Request.Builder().url(tokenUrl).build()).enqueue(object:Callback{
  override fun onFailure(c:Call,e:IOException)=listener.onError("Lumi cannot connect.")
  override fun onResponse(c:Call,r:Response){r.use{val j=runCatching{JSONObject(it.body?.string().orEmpty())}.getOrNull();val key=j?.optString("value").orEmpty().ifBlank{j?.optJSONObject("client_secret")?.optString("value").orEmpty()};if(key.isBlank())listener.onError("Lumi could not start a voice session.") else socket(key)}}})}
 private fun socket(key:String){ws=http.newWebSocket(Request.Builder().url("wss://api.openai.com/v1/realtime?model=gpt-realtime").header("Authorization","Bearer $key").build(),object:WebSocketListener(){
  override fun onOpen(w:WebSocket,r:Response){configure();startMic()}
  override fun onMessage(w:WebSocket,text:String){runCatching{handle(JSONObject(text))}}
  override fun onFailure(w:WebSocket,t:Throwable,r:Response?){listener.onError("Voice connection was lost.")}})}
 private fun configure(){val prompt="You are Lumi, a warm English-speaking robot friend for one child age 5-8. Use very simple natural English, one or two short sentences and one question per turn. Praise effort. Gently model corrections. Never ask for identifying information. Avoid adult, dangerous, political, commercial or frightening topics. Do not mention scores or rewards unless asked."
  val input=JSONObject().put("format",JSONObject().put("type","audio/pcm").put("rate",rate)).put("transcription",JSONObject().put("model","gpt-4o-mini-transcribe").put("language","en")).put("turn_detection",JSONObject().put("type","semantic_vad").put("eagerness","low").put("create_response",true).put("interrupt_response",true))
  val session=JSONObject().put("type","realtime").put("output_modalities",JSONArray().put("audio")).put("instructions",prompt).put("audio",JSONObject().put("input",input).put("output",JSONObject().put("format",JSONObject().put("type","audio/pcm").put("rate",rate)).put("voice","marin")))
  ws?.send(JSONObject().put("type","session.update").put("session",session).toString());listener.onReady(true)}
 private fun startMic(){val min=AudioRecord.getMinBufferSize(rate,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT);recorder=AudioRecord.Builder().setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION).setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_IN_MONO).build()).setBufferSizeInBytes(maxOf(min,9600)).build();recording=true;recorder?.startRecording();listener.onStatus("Just talk to Lumi!");thread{val b=ByteArray(4800);while(recording){val n=recorder?.read(b,0,b.size)?:-1;if(n>0)ws?.send(JSONObject().put("type","input_audio_buffer.append").put("audio",Base64.encodeToString(b,0,n,Base64.NO_WRAP)).toString())}}}
 private fun handle(e:JSONObject){when(e.optString("type")){"conversation.item.input_audio_transcription.completed"->listener.onUserTranscript(e.optString("transcript"));"response.output_audio_transcript.delta"->listener.onPikaTranscript(e.optString("delta"));"response.output_audio.delta"->play(Base64.decode(e.optString("delta"),Base64.DEFAULT));"response.output_audio.done","response.done"->listener.onSpeaking(false,0f)}}
 private fun play(b:ByteArray){if(player==null){val min=AudioTrack.getMinBufferSize(rate,AudioFormat.CHANNEL_OUT_MONO,AudioFormat.ENCODING_PCM_16BIT);player=AudioTrack.Builder().setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()).setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build()).setBufferSizeInBytes(maxOf(min,38400)).setTransferMode(AudioTrack.MODE_STREAM).build().also{it.play()}};player?.write(b,0,b.size);listener.onSpeaking(true,.5f)}
 fun close(){recording=false;try{recorder?.stop()}catch(_:Exception){};recorder?.release();player?.release();ws?.close(1000,"bye")}
}