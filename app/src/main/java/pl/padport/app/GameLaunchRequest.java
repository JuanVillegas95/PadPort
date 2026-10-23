package pl.padport.app;

import org.json.JSONException;
import org.json.JSONObject;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Decides which library game an external launch asks for: a frontend (Daijisho, ES-DE,
 * Beacon...), a home-screen shortcut or a plain {@code am start}.
 * Pure Java so it is unit-tested; {@link LaunchActivity} only reads the Intent.
 *
 * Accepted, strongest first:
 * <ol>
 *   <li>an id: extra {@code game} or {@code padport://game/<id>}</li>
 *   <li>a {@code .padport} shortcut file: JSON {@code {"id":...,"title":...}} or a bare id</li>
 *   <li>a title: extra {@code title}, the title inside the file, or the file name itself
 *       (from the URI or extra {@code file}, a plain path such as Cocoon's {@code {file.path}})</li>
 * </ol>
 * The id is a hash of the folder URI, so it changes when a folder is removed and added again;
 * the title fallback keeps old shortcut files working in that case.
 */
final class GameLaunchRequest {
    static final String ACTION="pl.padport.app.LAUNCH_GAME";
    static final String EXTRA_GAME="game",EXTRA_TITLE="title",EXTRA_FILE="file";
    static final String EXTENSION=".padport";
    private static final Pattern ID=Pattern.compile("[0-9a-f]{24}");
    private static final Pattern LINK=Pattern.compile("(?i)^padport://game/([0-9a-f]{24})/?$");
    private GameLaunchRequest(){}

    static boolean validId(String id){return id!=null&&ID.matcher(id).matches();}
    static String link(String id){
        if(!validId(id))throw new IllegalArgumentException("Invalid game id");
        return "padport://game/"+id;
    }
    static String idFromLink(String uri){
        if(uri==null)return null;
        Matcher m=LINK.matcher(uri.trim());
        return m.matches()?m.group(1):null;
    }
    /** {@code am start} line for frontends that take a custom command (e.g. Daijisho). */
    static String amStart(String id){
        if(!validId(id))throw new IllegalArgumentException("Invalid game id");
        return "am start -n pl.padport.app/.LaunchActivity -a "+ACTION+" -e "+EXTRA_GAME+" "+id;
    }

    /** Text of a shortcut file. Pretty JSON, so it is readable if someone opens it. */
    static String fileContent(JSONObject game){
        try{return new JSONObject().put("padport",1).put("id",game.optString("id")).put("title",game.optString("title")).toString(2)+"\n";}
        catch(JSONException e){throw new IllegalArgumentException(e);}
    }
    /** Suggested file name: the title (the frontend shows it) with characters FAT/Windows reject replaced. */
    static String fileName(String title){
        String safe=title==null?"":title.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]","_").trim();
        while(safe.endsWith("."))safe=safe.substring(0,safe.length()-1).trim();
        return (safe.isEmpty()?"game":safe)+EXTENSION;
    }
    static String idFromFile(String text){
        if(text==null)return null;
        String trimmed=text.trim();
        if(trimmed.startsWith("{")){
            JSONObject json=json(trimmed);
            String id=json==null?null:json.optString("id","").trim().toLowerCase(Locale.ROOT);
            return validId(id)?id:null;
        }
        String first=trimmed.isEmpty()?"":trimmed.split("\\s+",2)[0].toLowerCase(Locale.ROOT);
        return validId(first)?first:null;
    }
    static String titleFromFile(String text){
        if(text==null||!text.trim().startsWith("{"))return null;
        JSONObject json=json(text.trim());
        String title=json==null?null:json.optString("title","").trim();
        return title==null||title.isEmpty()?null:title;
    }
    private static JSONObject json(String text){
        try{return new JSONObject(text);}catch(JSONException e){return null;}
    }
    /** File name without folders and extension, also for SAF ids like {@code primary:ROMs/x/Game.padport}. */
    static String baseName(String path){
        if(path==null)return null;
        String name=path.substring(Math.max(path.lastIndexOf('/'),path.lastIndexOf(':'))+1);
        int dot=name.lastIndexOf('.');
        if(dot>0)name=name.substring(0,dot);
        name=name.trim();
        return name.isEmpty()?null:name;
    }
    /** Case, accents, spaces and punctuation do not matter: "Look Outside" = "look_outside.padport" base name. */
    static String normalizeTitle(String title){
        if(title==null)return "";
        String plain=Normalizer.normalize(title,Normalizer.Form.NFKD).replaceAll("\\p{M}+","");
        return plain.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+","");
    }
    /** The one library game with this title; null when none or several match. */
    static String idForTitle(List<JSONObject> games,String title){
        String wanted=normalizeTitle(title);
        if(wanted.isEmpty())return null;
        String found=null;
        for(JSONObject game:games){
            if(!normalizeTitle(game.optString("title")).equals(wanted))continue;
            String id=game.optString("id");
            if(found!=null&&!found.equals(id))return null;
            found=id;
        }
        return found;
    }
    private static boolean inLibrary(List<JSONObject> games,String id){
        if(!validId(id))return false;
        for(JSONObject game:games)if(id.equals(game.optString("id")))return true;
        return false;
    }

    /**
     * @param explicitId extra {@code game} or the id from a padport:// link
     * @param fileText   content of a shortcut file, null when there is none or it cannot be read
     * @param title      extra {@code title}
     * @param fileName   name/path of the shortcut file (used even when it cannot be read)
     * @return id of a game that is in the library, or null
     */
    static String resolve(List<JSONObject> games,String explicitId,String fileText,String title,String fileName){
        for(String id:new String[]{explicitId==null?null:explicitId.trim().toLowerCase(Locale.ROOT),idFromFile(fileText)})
            if(inLibrary(games,id))return id;
        for(String name:new String[]{title,titleFromFile(fileText),baseName(fileName)}){
            String id=idForTitle(games,name);
            if(id!=null)return id;
        }
        return null;
    }
}
