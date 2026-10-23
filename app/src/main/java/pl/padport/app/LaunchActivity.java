package pl.padport.app;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Exported, invisible entry point for quick launch from outside the library:
 * frontends (Daijisho, ES-DE, Beacon...), home-screen shortcuts and {@code am start}.
 * The request is resolved by {@link GameLaunchRequest}; this class only reads the Intent.
 *
 * It only starts games that are already in the library: the folder permission comes
 * from the library, never from the caller, so a launch cannot open anything new.
 */
public class LaunchActivity extends LocalizedActivity {
    private static final String TAG="PadPort";
    private static final int MAX_FILE=16*1024;

    @Override protected void onCreate(Bundle state){
        super.onCreate(state);
        // Theme.NoDisplay: finish() before onResume, no UI of our own.
        List<JSONObject> games=Library.all(this);
        String id=null;
        try{id=resolve(getIntent(),games);}
        catch(RuntimeException e){Log.w(TAG,"Cannot read launch request",e);}
        JSONObject game=id==null?null:Library.get(this,id);
        if(game==null){
            Log.w(TAG,"External launch: no matching game for "+getIntent());
            Toast.makeText(this,R.string.shortcut_game_missing,Toast.LENGTH_LONG).show();
            startActivity(new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        }else{
            Intent play=new Intent(this,GameEngine.player(this,game)).putExtra("game",id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            // MV/MZ: replace whatever PadPort had open, so a game left in the background
            // is not kept alive under the new one (two browser engines = memory).
            // XP/VX/VX Ace: RgssActivity is singleTask and ends its process with System.exit
            // on destroy, so it is not cleared here; a second RGSS game gets the same
            // "another game is running" notice as from the library.
            if(GameEngine.isWebGame(game))play.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(play);
            Library.markPlayed(this,id);
        }
        finish();
    }

    private String resolve(Intent intent,List<JSONObject> games){
        if(intent==null)return null;
        String explicit=intent.getStringExtra(GameLaunchRequest.EXTRA_GAME);
        String title=intent.getStringExtra(GameLaunchRequest.EXTRA_TITLE);
        String fileText=null,fileName=null;
        Uri data=intent.getData();
        if(data!=null){
            String link=GameLaunchRequest.idFromLink(data.toString());
            if(link!=null)explicit=link;
            else{
                fileName=data.getLastPathSegment();
                fileText=read(data);
            }
        }
        // Frontends that pass {file.path} (Cocoon/Daijisho) instead of a readable URI:
        // the file name alone is enough to find the game.
        if(fileName==null)fileName=intent.getStringExtra(GameLaunchRequest.EXTRA_FILE);
        return GameLaunchRequest.resolve(games,explicit,fileText,title,fileName);
    }

    /** Shortcut file text, or null: file:// paths usually cannot be read without storage permission. */
    private String read(Uri uri){
        String scheme=uri.getScheme();
        if(!"content".equals(scheme)&&!"file".equals(scheme))return null;
        try(InputStream in=getContentResolver().openInputStream(uri)){
            if(in==null)return null;
            ByteArrayOutputStream out=new ByteArrayOutputStream();
            byte[] buffer=new byte[4096];int n;
            while((n=in.read(buffer))>0){
                out.write(buffer,0,n);
                if(out.size()>MAX_FILE)return null;   // not a shortcut file
            }
            return out.toString(StandardCharsets.UTF_8.name());
        }catch(Exception e){
            Log.i(TAG,"Shortcut file not readable, matching by file name: "+uri+" ("+e+")");
            return null;
        }
    }
}
