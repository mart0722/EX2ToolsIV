"""Exercise steering mode, saved assignments and drive/regen cycles on the JVM."""
from pathlib import Path
import os, re, subprocess, tempfile
root=Path(__file__).resolve().parents[2]
production = root / 'app/src/main/java/com/ex2tools/iv'
classes = [production / name for name in (
 'voice/SteeringMode.java', 'voice/SteeringButton.java', 'drive/DriveController.java', 'car/CarProps.java',
)]
resources = sorted(set(name for source in classes for name in re.findall(r'R\.string\.(\w+)', source.read_text())))
sources={
'android/content/ContentResolver.java':'package android.content; public class ContentResolver {}',
'android/content/Context.java':'''package android.content; public class Context {
 public ContentResolver getContentResolver(){return new ContentResolver();}
 public String getString(int id,Object... args){return String.valueOf(id);}}''',
'android/content/SharedPreferences.java':'''package android.content; import java.util.*;
 public class SharedPreferences {
 private final Map<String,String> values=new HashMap<>();
 public String getString(String key,String fallback){return values.getOrDefault(key,fallback);}
 public SharedPreferences edit(){return this;}
 public SharedPreferences putString(String key,String value){values.put(key,value);return this;}
 public void apply(){} }''',
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
'com/ex2tools/iv/R.java': 'package com.ex2tools.iv; public class R {public static class string {'
 + ''.join(f'public static final int {name}={index};' for index,name in enumerate(resources,1)) + '}}',
'com/ex2tools/iv/Prefs.java':'''package com.ex2tools.iv;
 import android.content.*;import com.ex2tools.iv.car.CarProps;
 public class Prefs {public static boolean enabled=true;
 private static final SharedPreferences prefs=new SharedPreferences();
 private static int drive=CarProps.DRIVE_COMFORT,regen=CarProps.REGEN_MEDIUM;
 public static SharedPreferences get(Context c){return prefs;}
 public static boolean steeringClimate(android.content.Context c){return enabled;}
 public static int drive(Context c){return drive;}public static int regen(Context c){return regen;}
 public static void setDrive(Context c,int value){drive=value;}
 public static void setRegen(Context c,int value){regen=value;} }''',
'com/ex2tools/iv/state/VehicleState.java':'''package com.ex2tools.iv.state;public class VehicleState {
 public static VehicleState get(){return new VehicleState();}
 public Integer intValue(int id,int area){return null;}public Integer intAny(int id,int[] areas){return null;} }''',
'com/ex2tools/iv/vehicle/PropertySpec.java':'''package com.ex2tools.iv.vehicle;
 public class PropertySpec {public enum Type { INT }}''',
'com/ex2tools/iv/vehicle/VhalGateway.java':'''package com.ex2tools.iv.vehicle;
 import java.util.*;
 public class VhalGateway {
 private static final VhalGateway gateway=new VhalGateway();
 public final Map<Integer,Integer> values=new HashMap<>();
 public boolean reject;
 public static VhalGateway get(){return gateway;}
 public int readInt(int id,int area){return values.getOrDefault(id,Integer.MIN_VALUE);} }''',
'com/ex2tools/iv/vehicle/CommandDispatcher.java':'''package com.ex2tools.iv.vehicle;
 public class CommandDispatcher {
 public enum Status { APPLIED, SENT, DRY_RUN, REJECTED }
 public record Command(int id,int area,PropertySpec.Type type,int value) {}
 public static class Result {
  public final Status status; public final String message="";
  public Result(){this(Status.APPLIED);}public Result(Status s){status=s;}
  public boolean ok(){return status!=Status.REJECTED;}
 }
 private final VhalGateway gateway;
 public CommandDispatcher(VhalGateway gateway,boolean readOnly){this.gateway=gateway;}
 public Result submit(Command c){
  if(gateway.reject)return new Result(Status.REJECTED);
  gateway.values.put(c.id(),c.value());return new Result();
 } }''',
'com/ex2tools/iv/climate/ClimateController.java':'''package com.ex2tools.iv.climate;import com.ex2tools.iv.vehicle.CommandDispatcher.Result;
 public class ClimateController {
 public static String last;
 public static ClimateController get(){return new ClimateController();}
 private Result record(String action){last=action;return new Result();}
 public Result adjustTempInteractive(float d){return record("temp:"+d);}
 public Result adjustFanInteractive(int d){return record("fan:"+d);}
 public Result toggleWheelHeat(){return record("wheel");}
 public Result toggleWindshieldHeat(){return record("windshield");}
 public Result toggleRearDefrost(){return record("rear");}
 public Result toggleFrontMaxDefrost(){return record("front max");} }''',
'com/ex2tools/iv/widgets/WidgetsCommon.java':'''package com.ex2tools.iv.widgets;
 import android.content.Context;import com.ex2tools.iv.vehicle.CommandDispatcher.Result;
 public class WidgetsCommon {
 public static int lastSeat;public static Result cycleSeat(Context c,int area){lastSeat=area;return new Result();} }''',
'com/ex2tools/iv/voice/ModeCheck.java':'''package com.ex2tools.iv.voice;
 import android.os.*;import android.content.Context;import android.provider.Settings.Global;import com.ex2tools.iv.Prefs;
 import com.ex2tools.iv.car.CarProps;import com.ex2tools.iv.climate.ClimateController;
 import com.ex2tools.iv.vehicle.VhalGateway;import com.ex2tools.iv.widgets.WidgetsCommon;
 import com.ex2tools.iv.voice.SteeringButton.Action;
 public class ModeCheck {
 static int voice=0,checks=0;static void check(boolean ok){checks++;if(!ok)throw new AssertionError("case "+checks);}
 static long expiry(){return Global.values.getOrDefault("ex2_climate_expiry",0L);}
 public static void main(String[] args){Handler h=new Handler();Runnable v=()->voice++;SteeringMode m=new SteeringMode(new Context(),h,v);
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
 assignments();
 System.out.println("PASS: "+checks+" mode, assignment and cycle checks");}
 static void assignments(){
  Context c=new Context();
  Action[] defaults={Action.TEMP_UP,Action.TEMP_DOWN,Action.FAN_UP,Action.FAN_DOWN};
  String[] commands={"temp:1.0","temp:-1.0","fan:1","fan:-1"};
  for(int key=1;key<=4;key++){
   SteeringButton button=SteeringButton.fromCode(key);
   check(button.action(c)==defaults[key-1]);
   check(button.action(c).execute(c).ok());check(commands[key-1].equals(ClimateController.last));
  }
  check(SteeringButton.fromCode(0)==null);check(SteeringButton.fromCode(5)==null);
  SteeringButton button=SteeringButton.VOLUME_UP;
  button.setAction(c,Action.PASSENGER_HEAT);
  check(button.action(c)==Action.PASSENGER_HEAT);check(button.action(c).execute(c).ok());
  check(WidgetsCommon.lastSeat==4);check(SteeringButton.VOLUME_DOWN.action(c)==Action.TEMP_DOWN);
  check(Action.DRIVER_HEAT.execute(c).ok());check(WidgetsCommon.lastSeat==1);
  Action[] toggles={Action.WHEEL_HEAT,Action.WINDSHIELD_HEAT,Action.REAR_HEAT,Action.FRONT_MAX_DEFROST};
  String[] heaters={"wheel","windshield","rear","front max"};
  for(int i=0;i<toggles.length;i++){
   check(toggles[i].execute(c).ok());check(heaters[i].equals(ClimateController.last));
  }
  Prefs.get(c).edit().putString("steering_action_1","removed-action").apply();
  check(button.action(c)==Action.TEMP_UP);
  VhalGateway gateway=VhalGateway.get();
  cycle(c,Action.DRIVE_MODE,CarProps.DRIVE_MODE,CarProps.DRIVE_SPORT,
   CarProps.DRIVE_ECO,CarProps.DRIVE_COMFORT,CarProps.DRIVE_SPORT);
  cycle(c,Action.REGEN_MODE,CarProps.REGEN_MODE,CarProps.REGEN_STRONG,
   CarProps.REGEN_WEAK,CarProps.REGEN_MEDIUM,CarProps.REGEN_STRONG);
  check(Prefs.drive(c)==CarProps.DRIVE_SPORT);check(Prefs.regen(c)==CarProps.REGEN_STRONG);
  gateway.reject=true;
  check(!Action.DRIVE_MODE.execute(c).ok());check(Prefs.drive(c)==CarProps.DRIVE_SPORT);
 }
 static void cycle(Context c,Action action,int property,int initial,int... expected){
  VhalGateway gateway=VhalGateway.get();
  gateway.values.put(property,initial);
  for(int value:expected){check(action.execute(c).ok());check(gateway.readInt(property,0)==value);}
 }}
'''
}
with tempfile.TemporaryDirectory(prefix='ex2-mode-test-') as tmp:
 d=Path(tmp)
 for n,s in sources.items():
  p=d/n;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(s)
 java=Path(os.environ['JAVA_HOME'])/'bin'
 subprocess.run([str(java/'javac'),'-d',str(d),*[str(p) for p in d.rglob('*.java')],
                 *map(str,classes)],check=True)
 subprocess.run([str(java/'java'),'-cp',str(d),'com.ex2tools.iv.voice.ModeCheck'],check=True)
