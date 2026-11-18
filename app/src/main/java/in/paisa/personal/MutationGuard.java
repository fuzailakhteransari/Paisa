package in.paisa.personal;
import org.json.*;
import java.util.*;
/** Undo may preserve a later capture source, but never overwrite a later user edit. */
public final class MutationGuard {
 private static String canonical(Object value)throws JSONException{if(value instanceof JSONObject){JSONObject o=(JSONObject)value;TreeSet<String> keys=new TreeSet<>();for(Iterator<String> i=o.keys();i.hasNext();)keys.add(i.next());StringBuilder s=new StringBuilder("{");for(String key:keys)s.append(JSONObject.quote(key)).append(':').append(canonical(o.get(key))).append(',');return s.append('}').toString();}if(value instanceof JSONArray){JSONArray a=(JSONArray)value;StringBuilder s=new StringBuilder("[");for(int i=0;i<a.length();i++)s.append(canonical(a.get(i))).append(',');return s.append(']').toString();}return value instanceof String?JSONObject.quote((String)value):String.valueOf(value);}
 public static boolean unchanged(String current,String expected)throws JSONException{if(current==null)return false;JSONObject a=new JSONObject(current),b=new JSONObject(expected);a.remove("sources");b.remove("sources");return canonical(a).equals(canonical(b));}
 public static JSONObject restore(JSONObject current,String before)throws JSONException{JSONObject out=before==null?new JSONObject(current.toString()).put("deleted",true):new JSONObject(before);if(current.has("sources"))out.put("sources",current.get("sources"));return out;}
}
