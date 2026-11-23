"""JVM tests of the exact injected source, using a fake Android clock/settings."""
from pathlib import Path
import subprocess
import tempfile
import os

java_home = Path(os.environ['JAVA_HOME'])
root = Path(__file__).resolve().parent
stubs = {
 'android/content/ContentResolver.java': 'package android.content; public class ContentResolver {}',
 'android/content/Intent.java': '''package android.content; public class Intent { public int key;
 public Intent(String a) {} public Intent setPackage(String p) {return this;}
 public Intent putExtra(String k,int v){key=v;return this;} }''',
 'android/content/Context.java': '''package android.content; public class Context {
 public int count=0,last=0; public ContentResolver getContentResolver(){return new ContentResolver();}
 public void sendBroadcast(Intent i,String permission){count++;last=i.key;} }''',
 'android/os/SystemClock.java': 'package android.os; public class SystemClock { public static long now=10000; public static long elapsedRealtime(){return now;} }',
 'android/provider/Settings.java': '''package android.provider; public class Settings { public static class Global {
 public static long heartbeat=0,expiry=0;
 public static long getLong(android.content.ContentResolver r,String k,long d){return k.endsWith("heartbeat")?heartbeat:expiry;}}}''',
 'BridgeCheck.java': '''import com.ex2tools.bridge.ClimateKeyBridge;
 import android.content.Context; import android.os.SystemClock; import android.provider.Settings.Global;
 public class BridgeCheck {
 static int checks=0;
 static void eq(boolean want,boolean got){checks++;if(want!=got)throw new AssertionError("case "+checks);}
 static boolean key(Context c,int p,int s){return ClimateKeyBridge.intercept(c,p,s);}
 public static void main(String[] a){Context c=new Context();
 int[] raw={557884992,557884993,557884995,557884994};
 int[] adapted={1090650368,1090650880,1090650624,1090651136};
 for(int p:raw)eq(false,key(c,p,1));
 Global.heartbeat=10000;Global.expiry=25000;
 for(int i=0;i<4;i++){
  int before=c.count;
  eq(true,key(c,raw[i],1)); if(c.last!=i+1)throw new AssertionError("mapping");
  eq(true,key(c,raw[i],1)); eq(true,key(c,raw[i],2)); eq(true,key(c,raw[i],3));
  eq(false,key(c,raw[i],3)); if(c.count!=before+1)throw new AssertionError("repeat");
  eq(true,key(c,adapted[i],1));eq(true,key(c,adapted[i],2));
 }
 eq(false,key(c,557884981,1)); // voice never consumed
 eq(false,key(c,123,1));
 SystemClock.now=13000;eq(false,key(c,raw[0],1)); // dead app
 Global.heartbeat=14000;eq(false,key(c,raw[0],1)); // future heartbeat after reboot
 Global.heartbeat=13000; Global.expiry=13000;eq(false,key(c,raw[0],1)); // timeout
 Global.expiry=28000;eq(true,key(c,raw[0],1));
 SystemClock.now=29000;eq(true,key(c,raw[0],3)); // consumed release after timeout
 eq(false,key(c,raw[0],1)); // next press native
 System.out.println("PASS: "+checks+" bridge checks (mapping, repeats, timeout, crash, release)");
 }}'''
}
with tempfile.TemporaryDirectory(prefix='ex2-bridge-test-') as tmp:
 d=Path(tmp)
 for name,source in stubs.items():
  p=d/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(source)
 sources=[str(p) for p in d.rglob('*.java')]+[str(root/'src/com/ex2tools/bridge/ClimateKeyBridge.java')]
 subprocess.run([str(java_home/'bin/javac'),'-d',str(d),*sources],check=True)
 subprocess.run([str(java_home/'bin/java'),'-cp',str(d),'BridgeCheck'],check=True)
