"""Exercise the production tap/timeout state machine with a deterministic clock."""
from pathlib import Path
import os, subprocess, tempfile
root=Path(__file__).resolve().parents[2]
sources={
'android/content/ContentResolver.java':'package android.content; public class ContentResolver {}',
'android/content/Context.java':'package android.content; public class Context {public ContentResolver getContentResolver(){return new ContentResolver();}}',
'android/os/SystemClock.java':'package android.os; public class SystemClock {public static long now=10000;public static long elapsedRealtime(){return now;}}',
'android/os/Handler.java':'''package android.os; import java.util.*; public class Handler {
 private final Map<Runnable,Long> jobs=new HashMap<>();
 public boolean post(Runnable r){return postDelayed(r,0);}
 public boolean postDelayed(Runnable r,long d){jobs.put(r,SystemClock.now+d);return true;}
 public void removeCallbacks(Runnable r){jobs.remove(r);}
 public void advance(long d){long target=SystemClock.now+d; while(true){Runnable r=null;long next=Long.MAX_VALUE;
 for(Map.Entry<Runnable,Long> e:jobs.entrySet())if(e.getValue()<next){r=e.getKey();next=e.getValue();}
 if(next>target)break; SystemClock.now=next;jobs.remove(r);r.run();}SystemClock.now=target;}}
''',
'android/provider/Settings.java':'''package android.provider; import java.util.*;public class Settings {public static class Global {
 public static Map<String,Long> values=new HashMap<>();
 public static boolean putLong(android.content.ContentResolver r,String k,long v){values.put(k,v);return true;}}}''',
'android/widget/Toast.java':'''package android.widget; public class Toast {public static final int LENGTH_SHORT=0;
 public static Toast makeText(android.content.Context c,String s,int d){return new Toast();}public void show(){}public void cancel(){}}''',
'com/ex2tools/iv/LogStore.java':'package com.ex2tools.iv;public class LogStore {public static void add(android.content.Context c,String s){}}',
'com/ex2tools/iv/Prefs.java':'''package com.ex2tools.iv;public class Prefs {public static boolean enabled=true;
 public static boolean steeringClimate(android.content.Context c){return enabled;}
 public static boolean voiceEnabled(android.content.Context c){return false;}
 public static boolean voiceMicKey(android.content.Context c){return false;}}''',
'com/ex2tools/iv/vehicle/CommandDispatcher.java':'''package com.ex2tools.iv.vehicle;public class CommandDispatcher {public static class Result{public boolean ok(){return true;}}}''',
'com/ex2tools/iv/climate/ClimateController.java':'''package com.ex2tools.iv.climate;import com.ex2tools.iv.vehicle.CommandDispatcher.Result;
 public class ClimateController {public static ClimateController get(){return new ClimateController();}
 public Result adjustTempFromSteering(float d){return new Result();}public Result adjustFanFromSteering(int d){return new Result();}}''',
'com/ex2tools/iv/voice/ModeCheck.java':'''package com.ex2tools.iv.voice;
 import android.os.*;import android.content.Context;import android.provider.Settings.Global;import com.ex2tools.iv.Prefs;
 public class ModeCheck {
 static int voice=0,checks=0;static void check(boolean ok){checks++;if(!ok)throw new AssertionError("case "+checks);}
 static long expiry(){return Global.values.getOrDefault("ex2_climate_expiry",0L);}
 public static void main(String[] args){Handler h=new Handler();Runnable v=()->voice++;SteeringClimate m=new SteeringClimate(new Context(),h,v);
 m.tap(v);h.advance(401);check(voice==1);check(expiry()==0);
 m.tap(v);h.advance(200);m.tap(v);check(expiry()>SystemClock.now);h.advance(500);check(voice==1);
 m.tap(v);h.advance(200);m.tap(v);check(expiry()==0);h.advance(500);check(voice==1);
 m.tap(v);h.advance(200);m.tap(v);h.advance(15001);check(expiry()==0); // idle timeout
 m.tap(v);h.advance(200);m.tap(v);h.advance(10000);m.key(1);h.advance(6000);check(expiry()>SystemClock.now); // activity renews
 h.advance(10000);check(expiry()==0);
 m.tap(v);h.advance(200);m.tap(v);Prefs.enabled=false;h.advance(1001);check(expiry()==0);
 m.tap(v);check(voice==2); // disabled preserves immediate single click
 Prefs.enabled=true;m.tap(v);m.close();h.advance(1000);check(voice==2);check(expiry()==0);
 check(Global.values.getOrDefault("ex2_climate_heartbeat",-1L)==0);
 System.out.println("PASS: "+checks+" mode checks (single/double tap, idle, renewal, disable, close)");}}
'''
}
with tempfile.TemporaryDirectory(prefix='ex2-mode-test-') as tmp:
 d=Path(tmp)
 for n,s in sources.items():
  p=d/n;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(s)
 java=Path(os.environ['JAVA_HOME'])/'bin'
 subprocess.run([str(java/'javac'),'-d',str(d),*[str(p) for p in d.rglob('*.java')],str(root/'app/src/main/java/com/ex2tools/iv/voice/SteeringClimate.java')],check=True)
 subprocess.run([str(java/'java'),'-cp',str(d),'com.ex2tools.iv.voice.ModeCheck'],check=True)
