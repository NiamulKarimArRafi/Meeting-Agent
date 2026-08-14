package com.niamulkarim.meetingagent

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private lateinit var record: Button; private lateinit var stop: Button; private lateinit var status: TextView; private lateinit var result: TextView; private lateinit var progress: ProgressBar; private lateinit var groq: EditText; private lateinit var router: EditText
    private var recorder: MediaRecorder?=null; private var recording=false; private val chunks=mutableListOf<File>(); private var index=0
    private val handler=Handler(Looper.getMainLooper()); private val executor=Executors.newSingleThreadExecutor()
    private val rotate=object:Runnable{override fun run(){if(recording){nextChunk();handler.postDelayed(this,300_000)}}}
    override fun onCreate(b:Bundle?){super.onCreate(b);setContentView(R.layout.activity_main)
        record=findViewById(R.id.recordButton);stop=findViewById(R.id.stopButton);status=findViewById(R.id.status);result=findViewById(R.id.result);progress=findViewById(R.id.progress);groq=findViewById(R.id.groqKey);router=findViewById(R.id.routerKey)
        val p=getSharedPreferences("keys",0);groq.setText(p.getString("groq",""));router.setText(p.getString("router",""))
        findViewById<Button>(R.id.saveKeys).setOnClickListener{p.edit().putString("groq",groq.text.toString().trim()).putString("router",router.text.toString().trim()).apply();Toast.makeText(this,"Keys saved",Toast.LENGTH_SHORT).show()}
        record.setOnClickListener{startMeeting()};stop.setOnClickListener{finishMeeting()}
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED)requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO),10)
    }
    private fun startMeeting(){if(groq.text.isBlank()||router.text.isBlank()){status.text="Enter and save both API keys first.";return};chunks.clear();index=0;result.text="";recording=true;record.isEnabled=false;stop.isEnabled=true;status.text="Recording… automatically splitting into 5-minute chunks.";nextChunk();handler.postDelayed(rotate,300_000)}
    private fun nextChunk(){if(!recording)return;try{recorder?.stop()}catch(_:Exception){};recorder?.release();val f=File(cacheDir,"chunk_${System.currentTimeMillis()}_${index++}.m4a");chunks.add(f);recorder=MediaRecorder(this).apply{setAudioSource(MediaRecorder.AudioSource.MIC);setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);setAudioEncoder(MediaRecorder.AudioEncoder.AAC);setAudioSamplingRate(44100);setAudioEncodingBitRate(128000);setOutputFile(f.absolutePath);prepare();start()}}
    private fun finishMeeting(){if(!recording)return;recording=false;handler.removeCallbacks(rotate);try{recorder?.stop()}catch(_:Exception){};recorder?.release();recorder=null;record.isEnabled=true;stop.isEnabled=false;progress.visibility=View.VISIBLE;val files=chunks.toList();val g=groq.text.toString().trim();val r=router.text.toString().trim();executor.execute{try{val t=StringBuilder();files.forEachIndexed{i,f->runOnUiThread{status.text="Transcribing ${i+1}/${files.size}…"};t.append("\n[Chunk ${i+1}]\n").append(transcribe(f,g)).append("\n")};runOnUiThread{status.text="Summarizing…"};val s=summarize(t.toString(),r);runOnUiThread{progress.visibility=View.GONE;status.text="Done.";result.text=s}}catch(e:Exception){runOnUiThread{progress.visibility=View.GONE;status.text="Error: ${e.message}"}}finally{files.forEach{it.delete()}}}}
    private fun transcribe(f:File,key:String):String{val boundary="----MeetingAgent${System.currentTimeMillis()}";val c=URL("https://api.groq.com/openai/v1/audio/transcriptions").openConnection() as HttpURLConnection;c.requestMethod="POST";c.doOutput=true;c.setRequestProperty("Authorization","Bearer $key");c.setRequestProperty("Content-Type","multipart/form-data; boundary=$boundary");c.outputStream.use{o->fun w(s:String)=o.write(s.toByteArray());w("--$boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"audio.m4a\"\r\nContent-Type: audio/mp4\r\n\r\n");f.inputStream().use{it.copyTo(o)};w("\r\n--$boundary\r\nContent-Disposition: form-data; name=\"model\"\r\n\r\nwhisper-large-v3\r\n--$boundary--\r\n")};return JSONObject(response(c)).optString("text")}
    private fun summarize(t:String,key:String):String{val prompt="""You are a precise multilingual meeting analyst. The transcript may mix Bangla, English and Banglish. Do not invent facts. Return:\nEXECUTIVE SUMMARY\nKEY DISCUSSION\nDECISIONS\nACTION ITEMS (owner and deadline if explicitly stated)\nOPEN QUESTIONS\nRISKS/CONCERNS\n\nTRANSCRIPT:\n$t""";val body=JSONObject().put("model","openrouter/free").put("messages",JSONArray().put(JSONObject().put("role","user").put("content",prompt))).toString();val c=URL("https://openrouter.ai/api/v1/chat/completions").openConnection() as HttpURLConnection;c.requestMethod="POST";c.doOutput=true;c.setRequestProperty("Authorization","Bearer $key");c.setRequestProperty("Content-Type","application/json");c.outputStream.use{it.write(body.toByteArray())};return JSONObject(response(c)).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")}
    private fun response(c:HttpURLConnection):String{val s=if(c.responseCode in 200..299)c.inputStream else c.errorStream;val x=s.bufferedReader().use{it.readText()};if(c.responseCode !in 200..299)throw Exception("HTTP ${c.responseCode}: $x");return x}
    override fun onDestroy(){recording=false;handler.removeCallbacksAndMessages(null);recorder?.release();executor.shutdownNow();super.onDestroy()}
}
