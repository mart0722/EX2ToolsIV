"""Production storage/matching and heating execution tests with a fake Android context.
JAVA_HOME=/path/jdk17 python3 tools/voice-commands/test_commands.py --json-jar /path/org-json.jar
"""
from pathlib import Path
import argparse, os, subprocess, tempfile
p=argparse.ArgumentParser();p.add_argument('--json-jar',required=True,type=Path);args=p.parse_args()
root=Path(__file__).resolve().parents[2]
src=root/'app/src/main/java'
voice=(src/'com/ex2tools/iv/voice/VoiceCommands.java').read_text()
def method(signature):
 start=voice.index(signature);pos=voice.index('{',start);depth=1;end=pos+1
 while depth:
  if voice[end]=='{':depth+=1
  if voice[end]=='}':depth-=1
  end+=1
 return voice[start:end]
files={
 'android/content/Context.java':'''package android.content; public class Context {
 public Context getApplicationContext(){return this;}
 public android.content.res.Resources getResources(){return new android.content.res.Resources();}}''',
 'android/os/Looper.java':'package android.os;public class Looper{public static Looper getMainLooper(){return new Looper();}}',
 'android/os/Handler.java':'package android.os;public class Handler{public Handler(Looper l){}public boolean post(Runnable r){r.run();return true;}}',
 'android/widget/Toast.java':'package android.widget;public class Toast{public static int LENGTH_LONG=1;public static Toast makeText(android.content.Context c,String s,int n){return new Toast();}public void show(){}}',
 'android/content/res/Resources.java':'''package android.content.res; public class Resources {
 public java.io.InputStream openRawResource(int id){try{return new java.io.FileInputStream(System.getProperty(id==2?"base":"seed"));}
 catch(Exception e){throw new RuntimeException(e);}}}''',
 'com/ex2tools/iv/R.java':'package com.ex2tools.iv;public class R {public static class raw{public static int default_heating_commands=1,voice_phrases=2;}}',
 'com/ex2tools/iv/LogStore.java':'package com.ex2tools.iv;public class LogStore{public static void add(android.content.Context c,String s){System.err.println(s);}}',
 'com/ex2tools/iv/Prefs.java':'''package com.ex2tools.iv;public class Prefs {public static String data="[]";public static boolean seeded;
 public static String voiceCustomCommands(android.content.Context c){return data;}
 public static boolean voiceHeatingDefaultsAdded(android.content.Context c){return seeded;}
 public static void setVoiceCommandsWithDefaults(android.content.Context c,String s){data=s;seeded=true;}
 public static void setVoiceCustomCommands(android.content.Context c,String s){data=s;}}''',
 'com/ex2tools/iv/voice/VoiceService.java':'package com.ex2tools.iv.voice;public class VoiceService{public static void commandsChanged(android.content.Context c){}}',
 'com/ex2tools/iv/vehicle/CommandDispatcher.java':'''package com.ex2tools.iv.vehicle;public class CommandDispatcher {public static class Result {
 public boolean success=true;public boolean ok(){return success;}}}''',
 'com/ex2tools/iv/climate/ClimateController.java':'''package com.ex2tools.iv.climate;
 import com.ex2tools.iv.vehicle.CommandDispatcher.Result;
 public class ClimateController {public static java.util.List<String> calls=new java.util.ArrayList<>();public static String fail="";
 public static ClimateController get(){return new ClimateController();}
 private Result send(String name,int v){calls.add(name+":"+v);Result r=new Result();r.success=!name.equals(fail);return r;}
 public Result setWheelHeat(boolean v){return send("wheel",v?1:0);}
 public Result setSeatHeat(int a,int l){return send(a==1?"driver":"passenger",l);}
 public Result setWindshieldHeat(boolean v){return send("windshield",v?1:0);}
 public Result setRearDefrost(boolean v){return send("rear",v?1:0);}}
''',
 'com/ex2tools/iv/voice/CommandCheck.java':'''package com.ex2tools.iv.voice;
 import android.content.Context;import com.ex2tools.iv.Prefs;import java.util.*;import com.ex2tools.iv.climate.ClimateController;
 public class CommandCheck {
 static int checks;static void check(boolean v,String label){checks++;if(!v)throw new AssertionError(label);}
 static class Result {boolean handled;String reply;Result(boolean h,String r){handled=h;reply=r;}}
 static Result ok(String s){return new Result(true,s);}static Result no(String s){return new Result(false,s);}
 static boolean openPackage(Context c,String pkg){return pkg.equals("existing.app");}
 '''+method('    private static Result openCustom(')+'''
 public static void main(String[] args)throws Exception {
 Context c=new Context();Prefs.data="[{\\"id\\":\\"old\\",\\"phrase\\":\\"открой музыку\\",\\"package\\":\\"existing.app\\",\\"label\\":\\"Музыка\\"}]";
 List<CustomVoiceCommands.Command> initial=CustomVoiceCommands.load(c);
 check(Prefs.seeded,"migration marker");check(initial.size()==17,"one app + 16 defaults");
 check(CustomVoiceCommands.load(c).size()==initial.size(),"seed once");
 check(CustomVoiceCommands.match(c,"открой музыку").packageName.equals("existing.app"),"legacy app preserved");
 check(openCustom(c,CustomVoiceCommands.match(c,"открой музыку")).handled,"app execution preserved");
 for(CustomVoiceCommands.Command command:initial)if(command.isHeating()){
  Set<String> unique=new HashSet<>();unique.add(VoiceText.digitize(VoiceText.normalize(command.phrase)));
  for(String alias:command.aliases)check(unique.add(VoiceText.digitize(VoiceText.normalize(alias))),"editable aliases must be unique: "+alias);
  check(CustomVoiceCommands.match(c,command.phrase).id.equals(command.id),"canonical match");
  for(String alias:command.aliases)check(CustomVoiceCommands.match(c,alias).id.equals(command.id),"alias match: "+alias);
 }
 CustomVoiceCommands.Command driver=CustomVoiceCommands.match(c,"подогрев водительского сиденья на два");
 check(driver.heating.get(0).level==2,"seat level");
 check(CustomVoiceCommands.match(c,"подогрев водительского сиденья на 2").id.equals(driver.id),"spoken/numeric equivalence");
 CustomVoiceCommands.Command front=CustomVoiceCommands.match(c,"включи подогрев лобового стекла");
 CustomVoiceCommands.Command rear=CustomVoiceCommands.match(c,"включи подогрев заднего стекла");
 check(front.heating.get(0).target.equals("windshield"),"front default");check(rear.heating.get(0).target.equals("rear"),"rear default");
 CustomVoiceCommands.setEnabled(c,front,false);
 check(CustomVoiceCommands.match(c,front.phrase)==null,"disable exact");
 for(String alias:front.aliases)check(CustomVoiceCommands.match(c,alias)==null,"disable alias");
 check(!CustomVoiceCommands.phrases(c).contains(front.phrase),"disabled grammar");
 CustomVoiceCommands.delete(c,rear.id);check(CustomVoiceCommands.match(c,rear.phrase)==null,"delete exact");
 for(String alias:rear.aliases)check(CustomVoiceCommands.match(c,alias)==null,"delete alias");
 check(CustomVoiceCommands.load(c).size()==16,"deleted default not reseeded");
 String grammar=VoiceGrammar.build(c);
 check(!new org.json.JSONArray(grammar).toList().contains(front.phrase),"disabled heater absent from actual grammar");
 check(!new org.json.JSONArray(grammar).toList().contains(rear.phrase),"deleted heater absent from actual grammar");
 List<CustomVoiceCommands.HeatingAction> actions=Arrays.asList(new CustomVoiceCommands.HeatingAction("wheel",1),
 new CustomVoiceCommands.HeatingAction("driver",3),new CustomVoiceCommands.HeatingAction("passenger",1));
 CustomVoiceCommands.upsert(c,"bundle","Мне холодно","","",true,Arrays.asList("Руль и включи подогрев сидений"),actions);
 CustomVoiceCommands.Command bundle=CustomVoiceCommands.matchExact(c,"мне холодно");
 check(bundle.heating.size()==3,"multiple actions persisted");
 check(CustomVoiceCommands.matchExact(c,"руль и включи подогрев сидений").id.equals("bundle"),"whole phrase before clause split");
 check(CustomVoiceCommands.phrases(c).contains("мне холодно"),"uppercase grammar normalized");
 check(new org.json.JSONArray(VoiceGrammar.build(c)).toList().contains("мне холодно"),"uppercase phrase present in actual grammar");
 ClimateController.calls.clear();check(HeatingVoiceExecutor.apply(bundle).startsWith("Команды отправлены"),"bundle success");
 check(ClimateController.calls.equals(Arrays.asList("wheel:1","driver:3","passenger:1")),"exact targets and levels");
 ClimateController.fail="driver";ClimateController.calls.clear();String partial=HeatingVoiceExecutor.apply(bundle);
 check(partial.startsWith("Не удалось") && partial.contains("Водитель"),"partial failure reported");check(ClimateController.calls.size()==3,"other actions still attempted");
 check(CustomVoiceCommands.match(c,"не мне холодно")==null,"heater not substring matched");
 CustomVoiceCommands.upsert(c,"bundle","Тепло мне","","",true,Collections.emptyList(),actions);
 check(CustomVoiceCommands.match(c,"мне холодно")==null,"renamed phrase removed");
 check(CustomVoiceCommands.matchExact(c,"открой музыку и включи подогрев руля")==null,"fuzzy app does not swallow compound phrase");
 check(CustomVoiceCommands.match(c,"подогрев водительского сиденья на три").heating.get(0).level==3,"level 3 distinct from level 1");
 boolean rejected=false;try{new CustomVoiceCommands.HeatingAction("wheel",3);}catch(IllegalArgumentException e){rejected=true;}check(rejected,"wheel only binary");
 CustomVoiceCommands.save(c,new ArrayList<>());check(CustomVoiceCommands.load(c).isEmpty(),"empty stays empty");
 Prefs.seeded=false;Prefs.data="[{\\"id\\":\\"conflict\\",\\"phrase\\":\\"включи подогрев руля\\",\\"package\\":\\"existing.app\\"}]";
 check(CustomVoiceCommands.match(c,"включи подогрев руля").id.equals("conflict"),"migration preserves conflicting user phrase");
 Prefs.seeded=false;Prefs.data="invalid json";CustomVoiceCommands.load(c);check(!Prefs.seeded && Prefs.data.equals("invalid json"),"corrupt data not overwritten");
 System.out.println("PASS: "+checks+" checks: migration, aliases, delete/disable, grammar, app compatibility, multi-heater execution and failure");
 }}
'''
}
with tempfile.TemporaryDirectory(prefix='ex2-custom-commands-') as tmp:
 d=Path(tmp)
 for name,text in files.items():
  path=d/name;path.parent.mkdir(parents=True,exist_ok=True);path.write_text(text)
 java=Path(os.environ['JAVA_HOME'])/'bin';jar=str(args.json_jar.resolve())
 subprocess.run([str(java/'javac'),'-cp',jar,'-d',tmp,*[str(p) for p in d.rglob('*.java')],str(src/'com/ex2tools/iv/voice/CustomVoiceCommands.java'),str(src/'com/ex2tools/iv/voice/VoiceText.java'),str(src/'com/ex2tools/iv/voice/HeatingVoiceExecutor.java'),str(src/'com/ex2tools/iv/voice/VoiceGrammar.java')],check=True)
 subprocess.run([str(java/'java'),'-cp',tmp+os.pathsep+jar,'-Dseed='+str(root/'app/src/main/res/raw/default_heating_commands.json'),'-Dbase='+str(root/'app/src/main/res/raw/voice_phrases.txt'),'com.ex2tools.iv.voice.CommandCheck'],check=True)
