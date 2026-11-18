package in.paisa.personal;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import org.json.*;
import java.util.*;

public class Store extends SQLiteOpenHelper {
    private static final Set<String> TABLES=new HashSet<>(Arrays.asList("entries","bills","accounts","subscriptions","meta","rules"));
    public Store(Context c) { super(c, "paisa.db", null, 2); initialise(c); }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE entries (id TEXT PRIMARY KEY, payload TEXT NOT NULL, fingerprint TEXT UNIQUE)");
        db.execSQL("CREATE TABLE bills (id TEXT PRIMARY KEY, payload TEXT NOT NULL)");
        extraTables(db);
    }
    private void extraTables(SQLiteDatabase db){for(String table:Arrays.asList("accounts","subscriptions","meta","rules"))db.execSQL("CREATE TABLE IF NOT EXISTS "+table+" (id TEXT PRIMARY KEY, payload TEXT NOT NULL)");db.execSQL("CREATE TABLE IF NOT EXISTS undo_log (id TEXT PRIMARY KEY, created INTEGER, payload TEXT NOT NULL)");db.execSQL("CREATE TABLE IF NOT EXISTS seen (fingerprint TEXT PRIMARY KEY, entryId TEXT NOT NULL)");}
    @Override public void onUpgrade(SQLiteDatabase db,int oldV,int newV) { extraTables(db); }
    private void initialise(Context context){
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try {
            if(get("accounts","cash")==null)put("accounts",new JSONObject().put("id","cash").put("name","Cash").put("type","cash").put("opening",0).put("openingAt",0));
            if(get("accounts","bank:default")==null)put("accounts",new JSONObject().put("id","bank:default").put("name","Bank").put("type","bank").put("opening",0).put("openingAt",0));
            JSONObject cfg=settings();
            if(!cfg.optBoolean("migratedV3")){
                JSONArray old=read("entries",true);for(int i=0;i<old.length();i++){JSONObject e=old.getJSONObject(i);if(e.optString("accountId").isEmpty()){String label=e.optString("account","Bank"),id=label.equals("Cash")?"cash":"legacy:"+label.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+","-");if(get("accounts",id)==null)put("accounts",new JSONObject().put("id",id).put("name",label).put("type",label.startsWith("Credit card")?"credit":label.equals("Cash")?"cash":"bank").put("opening",0).put("openingAt",0));e.put("accountId",id);if(e.optString("kind").equals("transfer")&&e.optString("toAccountId").isEmpty())e.put("unresolvedTransfer",true);put("entries",e);}}
                for(Map.Entry<String,?> item:context.getSharedPreferences("paisa",Context.MODE_PRIVATE).getAll().entrySet()){String key=item.getKey();if(!(item.getValue() instanceof String))continue;String id=key.startsWith("merchant-rule:")?key.substring(14):key.startsWith("rule:")?SmsParser.nameKey(key.substring(5)):"";if(!id.isEmpty()&&!(key.startsWith("rule:")&&get("rules",id)!=null))put("rules",new JSONObject().put("id",id).put("category",item.getValue()));}
                cfg.put("migratedV3",true);setSettings(cfg);
            }
            db.setTransactionSuccessful();
        }catch(Exception e){throw new IllegalStateException("Could not initialise local data",e);}finally{db.endTransaction();}
    }
    public synchronized JSONArray entries() { return read("entries",false); }
    public synchronized JSONArray bills() { return read("bills",false); }
    public synchronized JSONArray accounts() { return read("accounts",false); }
    public synchronized JSONArray subscriptions() { return read("subscriptions",false); }
    public synchronized JSONArray read(String table,boolean includeDeleted) {
        if(!TABLES.contains(table))throw new IllegalArgumentException("Unknown table");
        JSONArray out=new JSONArray();
        try(Cursor c=getReadableDatabase().query(table,new String[]{"payload"},null,null,null,null,"rowid DESC")) {
            while(c.moveToNext()) { try { JSONObject row=new JSONObject(c.getString(0));if(includeDeleted||!row.optBoolean("deleted"))out.put(row); } catch(JSONException ignored) {} }
        } return out;
    }
    public synchronized JSONObject get(String table,String id){String raw=raw(table,id);if(raw==null)return null;try{return new JSONObject(raw);}catch(JSONException e){throw new IllegalStateException("Invalid local record",e);}}
    private String raw(String table,String id){if(!TABLES.contains(table))throw new IllegalArgumentException("Unknown table");try(Cursor c=getReadableDatabase().query(table,new String[]{"payload"},"id=?",new String[]{id},null,null,null)){return c.moveToFirst()?c.getString(0):null;}}
    public synchronized void put(String table,JSONObject row)throws JSONException {if(!TABLES.contains(table))throw new IllegalArgumentException("Unknown table");ContentValues v=new ContentValues();v.put("id",row.getString("id"));v.put("payload",row.toString());if(get(table,row.getString("id"))==null)getWritableDatabase().insertOrThrow(table,null,v);else getWritableDatabase().update(table,v,"id=?",new String[]{row.getString("id")});}
    public synchronized JSONObject settings(){JSONObject row=get("meta","settings");if(row!=null)return row;try{return new JSONObject().put("id","settings").put("cycleStartDay",1).put("monthBudget",0).put("categoryBudgets",new JSONObject()).put("reminders",true).put("captureNotifications",false).put("emailApps",new JSONArray().put("com.google.android.gm")).put("bankApps",new JSONArray());}catch(JSONException e){throw new IllegalStateException(e);}}
    public synchronized void setSettings(JSONObject settings)throws JSONException{settings.put("id","settings");put("meta",settings);}
    public synchronized JSONObject snapshot()throws JSONException{return new JSONObject().put("version",3).put("entries",read("entries",true)).put("bills",bills()).put("accounts",accounts()).put("subscriptions",subscriptions()).put("settings",settings()).put("rules",read("rules",false));}
    public synchronized String learned(String key){JSONObject r=key.isEmpty()?null:get("rules",key);return r==null?"":r.optString("category");}
    public synchronized void remember(String key,String category,boolean on)throws JSONException{if(key.isEmpty())return;if(on)put("rules",new JSONObject().put("id",key).put("category",category));else getWritableDatabase().delete("rules","id=?",new String[]{key});}
    public synchronized boolean seen(String fingerprint){try(Cursor c=getReadableDatabase().query("seen",new String[]{"entryId"},"fingerprint=?",new String[]{fingerprint},null,null,null)){if(c.moveToFirst())return true;}if(fingerprint.startsWith("SMS:"))try(Cursor c=getReadableDatabase().query("entries",new String[]{"id"},"fingerprint=?",new String[]{fingerprint.substring(4)},null,null,null)){return c.moveToFirst();}return false;}
    public synchronized void markSeen(String fp,String id){ContentValues v=new ContentValues();v.put("fingerprint",fp);v.put("entryId",id);getWritableDatabase().insertWithOnConflict("seen",null,v,SQLiteDatabase.CONFLICT_IGNORE);}
    public synchronized JSONObject commit(String label,JSONArray patches)throws Exception{
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try {
            JSONObject prospective=snapshot();JSONArray changes=new JSONArray();
            for(int i=0;i<patches.length();i++){JSONObject patch=patches.getJSONObject(i);String table=patch.getString("table");JSONObject row=patch.getJSONObject("row");String id=row.getString("id");if(!Arrays.asList("entries","bills","accounts","subscriptions").contains(table))throw new IllegalArgumentException("Unsupported change");JSONArray list=prospective.getJSONArray(table);boolean found=false;for(int j=0;j<list.length();j++)if(list.getJSONObject(j).optString("id").equals(id)){list.put(j,row);found=true;break;}if(!found)list.put(row);String before=raw(table,id);changes.put(new JSONObject().put("table",table).put("id",id).put("before",before==null?JSONObject.NULL:before).put("after",row.toString()));}
            Ledger.validate(prospective);for(int i=0;i<patches.length();i++){JSONObject p=patches.getJSONObject(i);put(p.getString("table"),p.getJSONObject("row"));}
            String undoId=UUID.randomUUID().toString();JSONObject history=new JSONObject().put("id",undoId).put("label",label).put("changes",changes);ContentValues values=new ContentValues();values.put("id",undoId);values.put("created",System.currentTimeMillis());values.put("payload",history.toString());db.insertOrThrow("undo_log",null,values);db.execSQL("DELETE FROM undo_log WHERE id NOT IN (SELECT id FROM undo_log ORDER BY created DESC, rowid DESC LIMIT 20)");db.setTransactionSuccessful();return new JSONObject().put("ok",true).put("undoId",undoId);
        }finally{db.endTransaction();}
    }
    public synchronized JSONObject lastUndo(){try(Cursor c=getReadableDatabase().rawQuery("SELECT payload FROM undo_log ORDER BY created DESC,rowid DESC LIMIT 1",null)){return c.moveToFirst()?new JSONObject(c.getString(0)):null;}catch(JSONException e){return null;}}
    public synchronized void undo(String id)throws Exception {
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try(Cursor cursor=db.query("undo_log",new String[]{"payload"},"id=?",new String[]{id},null,null,null)){
            if(!cursor.moveToFirst())throw new IllegalArgumentException("Undo is no longer available");JSONObject history=new JSONObject(cursor.getString(0));JSONArray changes=history.getJSONArray("changes");JSONObject prospective=snapshot();
            for(int i=0;i<changes.length();i++){JSONObject p=changes.getJSONObject(i);String table=p.getString("table"),key=p.getString("id");if(!MutationGuard.unchanged(raw(table,key),p.getString("after")))throw new IllegalArgumentException("This record changed after that action. Undo would overwrite it.");JSONArray list=prospective.getJSONArray(table);for(int j=0;j<list.length();j++)if(list.getJSONObject(j).optString("id").equals(key)){JSONObject before=MutationGuard.restore(list.getJSONObject(j),p.isNull("before")?null:p.getString("before"));list.put(j,before);break;}}
            Ledger.validate(prospective);for(int i=0;i<changes.length();i++){JSONObject p=changes.getJSONObject(i);JSONObject current=get(p.getString("table"),p.getString("id"));put(p.getString("table"),MutationGuard.restore(current,p.isNull("before")?null:p.getString("before")));}db.delete("undo_log","id=?",new String[]{id});db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
    public synchronized void restore(JSONObject state)throws Exception{
        if(state.optInt("version")!=3)throw new IllegalArgumentException("Unsupported backup version");Ledger.validate(state);JSONArray rules=state.getJSONArray("rules");for(int i=0;i<rules.length();i++)if(rules.getJSONObject(i).getString("id").length()>120||rules.getJSONObject(i).optString("category").length()>40)throw new IllegalArgumentException("Invalid category rule");SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{for(String table:Arrays.asList("entries","bills","accounts","subscriptions","rules")){db.delete(table,null,null);JSONArray list=state.getJSONArray(table);for(int i=0;i<list.length();i++)put(table,list.getJSONObject(i));}setSettings(state.getJSONObject("settings").put("migratedV3",true));db.delete("undo_log",null,null);db.delete("seen",null,null);if(state.has("seen")){JSONArray seen=state.getJSONArray("seen");for(int i=0;i<seen.length();i++){JSONObject r=seen.getJSONObject(i);markSeen(r.getString("fingerprint"),r.getString("entryId"));}}db.setTransactionSuccessful();}finally{db.endTransaction();}
    }
    public synchronized void restoreIfUnchanged(JSONObject expected,JSONObject restored)throws Exception{SQLiteDatabase db=getWritableDatabase();db.beginTransaction();try{JSONObject current=backup(),before=new JSONObject(expected.toString());current.remove("createdAt");before.remove("createdAt");if(!MutationGuard.unchanged(current.toString(),before.toString()))throw new java.util.ConcurrentModificationException("New activity arrived during restore");restore(restored);db.setTransactionSuccessful();}finally{db.endTransaction();}}
    public synchronized JSONObject backup()throws JSONException{SQLiteDatabase db=getWritableDatabase();db.beginTransactionNonExclusive();try{JSONObject state=snapshot();JSONArray out=new JSONArray();try(Cursor c=getReadableDatabase().query("seen",new String[]{"fingerprint","entryId"},null,null,null,null,null)){while(c.moveToNext())out.put(new JSONObject().put("fingerprint",c.getString(0)).put("entryId",c.getString(1)));}try(Cursor c=getReadableDatabase().query("entries",new String[]{"fingerprint","id"},"fingerprint IS NOT NULL",null,null,null,null)){while(c.moveToNext())out.put(new JSONObject().put("fingerprint","SMS:"+c.getString(0)).put("entryId",c.getString(1)));}db.setTransactionSuccessful();return state.put("seen",out).put("createdAt",System.currentTimeMillis());}finally{db.endTransaction();}}
    public synchronized JSONObject find(String id) {
        try(Cursor c=getReadableDatabase().query("entries",new String[]{"payload"},"id=?",new String[]{id},null,null,null)) {
            if(c.moveToFirst()) return new JSONObject(c.getString(0));
        } catch(JSONException ignored) {} return null;
    }
    public synchronized boolean add(JSONObject item, String fingerprint) throws JSONException {
        if(item.optString("id").isEmpty()) item.put("id",UUID.randomUUID().toString());
        ContentValues v=new ContentValues(); v.put("id",item.getString("id")); v.put("payload",item.toString());
        if(fingerprint!=null) v.put("fingerprint",fingerprint);
        return getWritableDatabase().insertWithOnConflict("entries",null,v,SQLiteDatabase.CONFLICT_IGNORE)!=-1;
    }
    public synchronized void update(JSONObject item) throws JSONException {
        if(find(item.getString("id"))==null) throw new IllegalArgumentException("Entry not found");
        ContentValues v=new ContentValues(); v.put("payload",item.toString());
        getWritableDatabase().update("entries",v,"id=?",new String[]{item.getString("id")});
    }
    public synchronized void delete(String id) { getWritableDatabase().delete("entries","id=?",new String[]{id}); }
    public synchronized void bill(JSONObject item) throws JSONException {
        if(item.optString("id").isEmpty()) item.put("id",UUID.randomUUID().toString());
        ContentValues v=new ContentValues(); v.put("id",item.getString("id")); v.put("payload",item.toString());
        getWritableDatabase().insertWithOnConflict("bills",null,v,SQLiteDatabase.CONFLICT_REPLACE);
    }
}
