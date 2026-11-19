package com.ex2tools.iv.voice;

import android.content.Context;
import com.ex2tools.iv.LogStore;
import com.ex2tools.iv.Prefs;
import com.ex2tools.iv.R;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public final class CustomVoiceCommands {
    public static final class HeatingAction {
        public final String target;
        public final int level;
        public HeatingAction(String target, int level) {
            int max = "driver".equals(target) || "passenger".equals(target) ? 3 : 1;
            if (!("wheel".equals(target) || "driver".equals(target) || "passenger".equals(target)
                    || "windshield".equals(target) || "rear".equals(target)) || level < 0 || level > max) {
                throw new IllegalArgumentException("Invalid heating action");
            }
            this.target = target; this.level = level;
        }
        public String description() {
            String name = "wheel".equals(target) ? "Руль" : "driver".equals(target) ? "Водитель"
                    : "passenger".equals(target) ? "Пассажир" : "windshield".equals(target) ? "Лобовое" : "Заднее стекло";
            return name + ": " + (level == 0 ? "выкл." : ("driver".equals(target) || "passenger".equals(target))
                    ? "уровень " + level : "вкл.");
        }
    }
    public static final class Command {
        public final String id, phrase, packageName, label;
        public final boolean enabled;
        public final List<String> aliases;
        public final List<HeatingAction> heating;
        Command(String id, String phrase, String pkg, String label, boolean enabled,
                List<String> aliases, List<HeatingAction> heating) {
            this.id=id; this.phrase=phrase; this.packageName=pkg; this.label=label; this.enabled=enabled;
            this.aliases=Collections.unmodifiableList(new ArrayList<>(aliases));
            this.heating=Collections.unmodifiableList(new ArrayList<>(heating));
        }
        public boolean isHeating() { return !heating.isEmpty(); }
        public String description() {
            if (!isHeating()) return label + " · " + packageName;
            StringBuilder s=new StringBuilder();
            for (HeatingAction a: heating) { if (s.length()>0) s.append("; "); s.append(a.description()); }
            return s.toString();
        }
    }
    private CustomVoiceCommands() {}

    private static List<Command> decode(JSONArray array) {
        List<Command> out=new ArrayList<>();
        for(int i=0;i<array.length();i++) {
            JSONObject o=array.optJSONObject(i); if(o==null) continue;
            String phrase=o.optString("phrase", "").trim(); if(phrase.isEmpty()) continue;
            String pkg=o.optString("package", "").trim();
            List<HeatingAction> actions=new ArrayList<>();
            JSONArray aa=o.optJSONArray("heating");
            if(aa!=null) for(int j=0;j<aa.length();j++) {
                JSONObject a=aa.optJSONObject(j); if(a==null) continue;
                try {
                    HeatingAction action=new HeatingAction(a.getString("target"),a.getInt("level"));
                    boolean duplicate=false;for(HeatingAction old:actions) if(old.target.equals(action.target))duplicate=true;
                    if(!duplicate)actions.add(action);
                } catch(Exception ignored) { }
            }
            if(actions.isEmpty() && pkg.isEmpty()) continue;
            List<String> aliases=new ArrayList<>();JSONArray al=o.optJSONArray("aliases");
            if(al!=null)for(int j=0;j<al.length();j++){String a=al.optString(j, "").trim();if(!a.isEmpty())aliases.add(a);}
            String id=o.optString("id", "");if(id.isEmpty())id=UUID.randomUUID().toString();
            out.add(new Command(id,phrase,actions.isEmpty()?pkg:"",o.optString("label",pkg),
                    o.optBoolean("enabled",true),aliases,actions));
        }
        return out;
    }
    public static synchronized List<Command> load(Context c) {
        try {
            List<Command> out=decode(new JSONArray(Prefs.voiceCustomCommands(c)));
            if(!Prefs.voiceHeatingDefaultsAdded(c)) {
                try(InputStream in=c.getResources().openRawResource(R.raw.default_heating_commands);
                        ByteArrayOutputStream bytes=new ByteArrayOutputStream()) {
                    byte[] buffer=new byte[4096];int n;while((n=in.read(buffer))!=-1)bytes.write(buffer,0,n);
                    for(Command seed:decode(new JSONArray(new String(bytes.toByteArray(),StandardCharsets.UTF_8)))) {
                        // Existing user phrases take precedence over imported defaults.
                        List<String> available=new ArrayList<>();
                        List<String> all=new ArrayList<>();all.add(seed.phrase);all.addAll(seed.aliases);
                        for(String phrase:all) if(!containsPhrase(out,phrase,null)) available.add(phrase);
                        if(!available.isEmpty())out.add(new Command(seed.id,available.get(0),"","",true,
                                available.subList(1,available.size()),seed.heating));
                    }
                    Prefs.setVoiceCommandsWithDefaults(c,encode(out).toString());
                }
            }
            return out;
        } catch(Exception error) {
            LogStore.add(c,"voice commands load: "+error);
            return new ArrayList<>();
        }
    }
    private static JSONArray encode(List<Command> commands) {
        JSONArray array=new JSONArray();
        for(Command command:commands) {
            try {
                JSONObject o=new JSONObject();o.put("id",command.id);o.put("phrase",command.phrase);
                o.put("package",command.packageName);o.put("label",command.label);o.put("enabled",command.enabled);
                JSONArray aliases=new JSONArray();for(String a:command.aliases)aliases.put(a);o.put("aliases",aliases);
                JSONArray actions=new JSONArray();for(HeatingAction a:command.heating) {
                    JSONObject item=new JSONObject();item.put("target",a.target);item.put("level",a.level);actions.put(item);
                }
                o.put("heating",actions);array.put(o);
            } catch(Exception error) { throw new IllegalArgumentException(error); }
        }
        return array;
    }
    public static synchronized void save(Context c,List<Command> commands) {
        Prefs.setVoiceCustomCommands(c,encode(commands).toString());
        VoiceService.commandsChanged(c);
    }
    public static void upsert(Context c,String id,String phrase,String pkg,String label) {
        upsert(c,id,phrase,pkg,label,true,Collections.emptyList(),Collections.emptyList());
    }
    public static synchronized void upsert(Context c,String id,String phrase,String pkg,String label,
            boolean enabled,List<String> aliases,List<HeatingAction> actions) {
        List<Command> list=load(c);String key=id==null||id.isEmpty()?UUID.randomUUID().toString():id;
        Command value=new Command(key,phrase.trim(),actions.isEmpty()?pkg:"",label,enabled,aliases,actions);
        for(int i=0;i<list.size();i++)if(list.get(i).id.equals(key)){list.set(i,value);save(c,list);return;}
        list.add(value);save(c,list);
    }
    public static synchronized void setEnabled(Context c,Command command,boolean enabled) {
        upsert(c,command.id,command.phrase,command.packageName,command.label,enabled,command.aliases,command.heating);
    }
    public static synchronized void delete(Context c,String id) {
        List<Command> list=load(c);list.removeIf(command->command.id.equals(id));save(c,list);
    }
    private static String key(String phrase) { return VoiceText.digitize(VoiceText.normalize(phrase)); }
    private static boolean containsPhrase(List<Command> list,String text,String exceptId) {
        String normalized=key(text);if(normalized.isEmpty())return false;
        for(Command command:list) {
            if(command.id.equals(exceptId))continue;
            if(key(command.phrase).equals(normalized))return true;
            for(String a:command.aliases)if(key(a).equals(normalized))return true;
        }
        return false;
    }
    public static boolean exists(Context c,String phrase,String exceptId) { return containsPhrase(load(c),phrase,exceptId); }
    public static Command matchExact(Context c,String text) {
        String normalized=key(text);if(normalized.isEmpty())return null;
        return exact(load(c),normalized);
    }
    private static Command exact(List<Command> list,String normalized) {
        for(Command command:list)if(command.enabled) {
            if(normalized.equals(key(command.phrase)))return command;
            for(String alias:command.aliases)if(normalized.equals(key(alias)))return command;
        }
        return null;
    }
    public static Command match(Context c,String text) {
        String normalized=key(text);if(normalized.isEmpty())return null;
        List<Command> list=load(c);
        // Exact phrases first: no substring or token matching for heater levels or negations.
        Command exact=exact(list,normalized);if(exact!=null)return exact;
        // Retain permissive application launching, but never revive a deleted heater phrase.
        if(isHeatingRequest(normalized))return null;
        for(Command command:list)if(command.enabled&&!command.isHeating()) {
            String phrase=key(command.phrase);
            if(normalized.contains(phrase))return command;
            boolean all=true;int useful=0;
            for(String token:phrase.split(" "))if(token.length()>=3){useful++;if(!normalized.contains(token)){all=false;break;}}
            if(useful>0&&all)return command;
        }
        return null;
    }
    public static boolean isHeatingRequest(String text) {
        return VoiceText.has(text,"подогрев","обогрев","нагрев","греть","грей","размороз")
                && (VoiceText.has(text,"рул","сиден","сидуш","крес","водител","пассаж","лобов","ветров","задн","зеркал")
                    || !VoiceText.has(text,"салон","быстр","максимальн"));
    }
    public static List<String> phrases(Context c) {
        List<String> out=new ArrayList<>();
        for(Command command:load(c))if(command.enabled){
            out.add(VoiceText.normalize(command.phrase));
            for(String alias:command.aliases)out.add(VoiceText.normalize(alias));
        }
        return out;
    }
}
