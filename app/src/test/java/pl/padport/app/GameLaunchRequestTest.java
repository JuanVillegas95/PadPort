package pl.padport.app;

import org.json.JSONObject;
import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class GameLaunchRequestTest {
    private static final String LOOK="0123456789abcdef01234567",FEAR="fedcba9876543210fedcba98",TTM="aaaaaaaaaaaaaaaaaaaaaaaa";
    private static JSONObject game(String id,String title) throws Exception{return new JSONObject().put("id",id).put("title",title);}
    private static List<JSONObject> library() throws Exception{
        return List.of(game(LOOK,"Look Outside"),game(FEAR,"Fear & Hunger"),game(TTM,"To the Moon"));
    }

    @Test public void explicitIdWins() throws Exception{
        assertEquals(FEAR,GameLaunchRequest.resolve(library(),FEAR,null,"Look Outside",null));
        assertEquals(FEAR,GameLaunchRequest.resolve(library()," "+FEAR.toUpperCase()+" ",null,null,null));
    }
    @Test public void linkCarriesTheId(){
        assertEquals(LOOK,GameLaunchRequest.idFromLink(GameLaunchRequest.link(LOOK)));
        assertEquals(LOOK,GameLaunchRequest.idFromLink("PADPORT://game/"+LOOK+"/"));
        assertNull(GameLaunchRequest.idFromLink("padport://game/../"+LOOK));
        assertNull(GameLaunchRequest.idFromLink("https://game/"+LOOK));
        assertThrows(IllegalArgumentException.class,()->GameLaunchRequest.link("../x"));
    }
    @Test public void shortcutFileRoundTrip() throws Exception{
        String text=GameLaunchRequest.fileContent(game(TTM,"To the Moon"));
        assertEquals(TTM,GameLaunchRequest.idFromFile(text));
        assertEquals("To the Moon",GameLaunchRequest.titleFromFile(text));
        assertEquals(TTM,GameLaunchRequest.resolve(library(),null,text,null,"whatever.padport"));
    }
    @Test public void bareIdFileIsAccepted() throws Exception{
        assertEquals(LOOK,GameLaunchRequest.resolve(library(),null,LOOK+"\n",null,null));
        assertNull(GameLaunchRequest.idFromFile("not an id"));
        assertNull(GameLaunchRequest.idFromFile("{broken json"));
    }
    @Test public void staleIdFallsBackToTitleInFile() throws Exception{
        // Folder removed and added again: new id, the old file still has the title.
        String text=new JSONObject().put("id","bbbbbbbbbbbbbbbbbbbbbbbb").put("title","Look Outside").toString();
        assertEquals(LOOK,GameLaunchRequest.resolve(library(),null,text,null,null));
    }
    @Test public void unreadableFileMatchesByFileName() throws Exception{
        assertEquals(FEAR,GameLaunchRequest.resolve(library(),null,null,null,"primary:ROMs/rpgmaker/Fear & Hunger.padport"));
        assertEquals(LOOK,GameLaunchRequest.resolve(library(),null,null,null,"/storage/emulated/0/ROMs/look_outside.padport"));
        assertEquals(TTM,GameLaunchRequest.resolve(library(),null,null,"TO THE MOON",null));
    }
    @Test public void accentsAndPunctuationDoNotMatter(){
        assertEquals(GameLaunchRequest.normalizeTitle("Pokémon: Ñandú!"),GameLaunchRequest.normalizeTitle("pokemon nandu"));
    }
    @Test public void ambiguousOrUnknownTitleLaunchesNothing() throws Exception{
        List<JSONObject> twins=List.of(game(LOOK,"Game"),game(FEAR,"game"));
        assertNull(GameLaunchRequest.idForTitle(twins,"Game"));
        assertNull(GameLaunchRequest.resolve(library(),null,null,"Unknown",null));
        assertNull(GameLaunchRequest.resolve(library(),"bbbbbbbbbbbbbbbbbbbbbbbb",null,null,null));
        assertNull(GameLaunchRequest.resolve(library(),null,null,"",""));
    }
    @Test public void fileNameIsSafeForSharedStorage(){
        assertEquals("Fear & Hunger.padport",GameLaunchRequest.fileName("Fear & Hunger"));
        assertEquals("A_B_ C.padport",GameLaunchRequest.fileName("A/B: C..."));
        assertEquals("game.padport",GameLaunchRequest.fileName("  "));
    }
    @Test public void amStartLineTargetsLaunchActivity(){
        assertEquals("am start -n pl.padport.app/.LaunchActivity -a pl.padport.app.LAUNCH_GAME -e game "+LOOK,GameLaunchRequest.amStart(LOOK));
    }
}
