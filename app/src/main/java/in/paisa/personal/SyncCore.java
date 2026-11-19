package in.paisa.personal;

import org.json.*;
import java.util.*;

/** Credential-free, deterministic three-way merge shared with the companion's sync-core.js. */
public final class SyncCore {
 public static final String[] TABLES={"entries","accounts","bills","subscriptions","rules"};
 private static final String[] FIELDS={
  "id amount kind title category account accountId toAccountId creditDate transferPairId transferLinkedTo person loanId subId cardId billId date pending source sourcePackage sources note merchantKey reference bank parserRevision captureReview userEdited duplicateOf deleted unresolvedTransfer originalKind fingerprint",
  "id name type opening openingAt snapshot snapshotAt deleted","id name amount due kind accountId createdAt source deleted","id name amount cycle nextDue anchorDay category accountId active merchantKey lastPaidAt deleted","id category deleted"};
 private static final Set<String> MONEY=new HashSet<>(Arrays.asList("amount kind accountId toAccountId loanId person cardId billId subId date creditDate duplicateOf captureReview unresolvedTransfer transferPairId transferLinkedTo".split(" ")));
 private static final Object MISSING=new Object();
 public static final class Result {public JSONObject state;public JSONArray conflicts=new JSONArray();}
 private static Object get(Object o,String key){return o instanceof JSONObject&&((JSONObject)o).has(key)?((JSONObject)o).opt(key):MISSING;}
 private static Object copy(Object o)throws Exception{if(o instanceof JSONObject)return new JSONObject(o.toString());if(o instanceof JSONArray)return new JSONArray(o.toString());return o;}
 public static String canonical(Object o)throws Exception{
  if(o==MISSING)return "undefined";if(o==null||o==JSONObject.NULL)return "null";
  if(o instanceof JSONObject){JSONObject j=(JSONObject)o;TreeSet<String> keys=new TreeSet<>();Iterator<String> it=j.keys();while(it.hasNext())keys.add(it.next());StringJoiner s=new StringJoiner(",","{","}");for(String k:keys)s.add(JSONObject.quote(k)+":"+canonical(j.get(k)));return s.toString();}
  if(o instanceof JSONArray){StringJoiner s=new StringJoiner(",","[","]");JSONArray a=(JSONArray)o;for(int i=0;i<a.length();i++)s.add(canonical(a.get(i)));return s.toString();}
  if(o instanceof String)return JSONObject.quote((String)o);if(o instanceof Number)return JSONObject.numberToString((Number)o);return String.valueOf(o);
 }
 public static boolean same(Object a,Object b)throws Exception{return canonical(a).equals(canonical(b));}
 private static TreeSet<String> keys(Object... values){TreeSet<String> out=new TreeSet<>();for(Object v:values)if(v instanceof JSONObject){Iterator<String> it=((JSONObject)v).keys();while(it.hasNext())out.add(it.next());}return out;}
 private static JSONArray rows(JSONObject input,String table)throws Exception{Object value=input.opt(table);if(value instanceof JSONArray)return (JSONArray)value;JSONArray out=new JSONArray();if(value instanceof JSONObject){JSONObject j=(JSONObject)value;for(String key:keys(j))out.put(new JSONObject().put("id",key).put("category",j.get(key)));}return out;}
 public static JSONObject snapshot(JSONObject input)throws Exception{
  JSONObject out=new JSONObject().put("version",3);
  for(int t=0;t<TABLES.length;t++){TreeMap<String,JSONObject> sorted=new TreeMap<>();JSONArray array=rows(input,TABLES[t]);for(int i=0;i<array.length();i++){JSONObject row=array.getJSONObject(i),clean=new JSONObject();for(String k:FIELDS[t].split(" "))if(row.has(k))clean.put(k,copy(row.get(k)));if(sorted.containsKey(clean.getString("id")))throw new IllegalArgumentException("Repeated cloud record ID");sorted.put(clean.getString("id"),clean);}out.put(TABLES[t],new JSONArray(sorted.values()));}
  JSONObject settings=input.optJSONObject("settings");if(settings==null)settings=new JSONObject();out.put("settings",new JSONObject().put("cycleStartDay",settings.optInt("cycleStartDay",1)).put("monthBudget",settings.optLong("monthBudget")).put("categoryBudgets",copy(settings.optJSONObject("categoryBudgets")==null?new JSONObject():settings.getJSONObject("categoryBudgets"))));
  TreeMap<String,JSONObject> identities=new TreeMap<>();JSONArray seen=input.optJSONArray("seen");if(seen!=null)for(int i=0;i<seen.length();i++){JSONObject r=seen.getJSONObject(i);JSONObject old=identities.get(r.getString("fingerprint"));if(old!=null&&!old.getString("entryId").equals(r.getString("entryId")))throw new IllegalArgumentException("A capture identity points to different payments");identities.put(r.getString("fingerprint"),new JSONObject().put("fingerprint",r.getString("fingerprint")).put("entryId",r.getString("entryId")));}return out.put("seen",new JSONArray(identities.values()));
 }
 private static Object conflict(Object b,Object l,Object r,JSONObject meta,Result result,String resolution)throws Exception{JSONObject item=new JSONObject(meta.toString());item.put("base",b==MISSING?JSONObject.NULL:copy(b)).put("local",l==MISSING?JSONObject.NULL:copy(l)).put("remote",r==MISSING?JSONObject.NULL:copy(r));result.conflicts.put(item);return copy(resolution.equals("remote")?r:l);}
 private static Object value(Object b,Object l,Object r,JSONObject meta,Result result,String resolution)throws Exception{
  if(same(l,r))return copy(l);if(same(l,b))return copy(r);if(same(r,b))return copy(l);
  if(meta.optString("field").equals("sources")&&l instanceof JSONArray&&r instanceof JSONArray){TreeSet<String> names=new TreeSet<>();for(Object array:new Object[]{l,r}){JSONArray a=(JSONArray)array;for(int i=0;i<a.length();i++)names.add(a.getString(i));}return new JSONArray(names);}
  if(l instanceof JSONObject&&r instanceof JSONObject&&(b instanceof JSONObject||b==MISSING)){JSONObject out=new JSONObject();for(String k:keys(b,l,r)){String path=meta.optString("field");Object merged=value(get(b,k),get(l,k),get(r,k),new JSONObject(meta.toString()).put("field",path.isEmpty()?k:path+"."+k),result,resolution);if(merged!=MISSING)out.put(k,merged);}return out;}
  return conflict(b,l,r,meta,result,resolution);
 }
 private static Map<String,JSONObject> map(JSONArray rows)throws Exception{Map<String,JSONObject> out=new TreeMap<>();for(int i=0;i<rows.length();i++){JSONObject row=rows.getJSONObject(i);out.put(row.getString("id"),row);}return out;}
 private static JSONObject group(Object value)throws Exception{JSONObject out=new JSONObject();for(String k:MONEY){Object v=get(value,k);if(v!=MISSING)out.put(k,copy(v));}return out;}
 public static Result merge(JSONObject baseInput,JSONObject localInput,JSONObject remoteInput,String resolution)throws Exception{
  JSONObject base=snapshot(baseInput),local=snapshot(localInput),remote=snapshot(remoteInput),out=new JSONObject().put("version",3);Result result=new Result();
  for(String table:TABLES){Map<String,JSONObject> bm=map(base.getJSONArray(table)),lm=map(local.getJSONArray(table)),rm=map(remote.getJSONArray(table));TreeSet<String> ids=new TreeSet<>();ids.addAll(bm.keySet());ids.addAll(lm.keySet());ids.addAll(rm.keySet());JSONArray mergedRows=new JSONArray();
   for(String id:ids){Object b=bm.containsKey(id)?bm.get(id):MISSING,l=lm.containsKey(id)?lm.get(id):MISSING,r=rm.containsKey(id)?rm.get(id):MISSING;JSONObject label=l instanceof JSONObject?(JSONObject)l:r instanceof JSONObject?(JSONObject)r:new JSONObject();JSONObject meta=new JSONObject().put("table",table).put("id",id).put("label",label.optString("title",label.optString("name",id))).put("field","");Object row;
    if(table.equals("entries")&&l instanceof JSONObject&&r instanceof JSONObject&&!same(l,r)&&!same(l,b)&&!same(r,b)){
     if(((JSONObject)l).optBoolean("deleted")!=((JSONObject)r).optBoolean("deleted"))row=conflict(b,l,r,meta.put("field","$record"),result,resolution);
     else{JSONObject bg=group(b),lg=group(l),rg=group(r);if(!same(lg,bg)&&!same(rg,bg)&&!same(lg,rg)){
       JSONObject selected=(JSONObject)conflict(bg,lg,rg,new JSONObject(meta.toString()).put("field","$accounting"),result,resolution),record=new JSONObject();
       for(String k:keys(b,l,r))if(!MONEY.contains(k)){Object v=value(get(b,k),get(l,k),get(r,k),new JSONObject(meta.toString()).put("field",k),result,resolution);if(v!=MISSING)record.put(k,v);}for(String k:keys(selected))record.put(k,selected.get(k));row=record;
      }else row=value(b,l,r,meta,result,resolution);}
    }else row=value(b,l,r,meta,result,resolution);
    if(row!=MISSING)mergedRows.put(row);
   }out.put(table,mergedRows);
  }
  out.put("settings",value(base.getJSONObject("settings"),local.getJSONObject("settings"),remote.getJSONObject("settings"),new JSONObject().put("table","settings").put("id","settings").put("label","Budget and monthly cycle").put("field",""),result,resolution));
  TreeMap<String,JSONObject> seen=new TreeMap<>();for(JSONObject source:new JSONObject[]{base,local,remote}){JSONArray a=source.getJSONArray("seen");for(int i=0;i<a.length();i++){JSONObject row=a.getJSONObject(i),old=seen.get(row.getString("fingerprint"));if(old!=null&&!old.getString("entryId").equals(row.getString("entryId")))row=(JSONObject)conflict(MISSING,old,row,new JSONObject().put("table","seen").put("id",row.getString("fingerprint")).put("label","Captured-payment identity").put("field","entryId"),result,resolution);seen.put(row.getString("fingerprint"),row);}}
  out.put("seen",new JSONArray(seen.values()));result.state=snapshot(out);return result;
 }
 public static JSONObject compose(JSONObject local,JSONObject shared)throws Exception{JSONObject out=new JSONObject(local.toString()),clean=snapshot(shared);for(String t:TABLES)out.put(t,clean.getJSONArray(t));out.put("seen",clean.getJSONArray("seen"));JSONObject cfg=out.getJSONObject("settings"),sc=clean.getJSONObject("settings");for(String k:keys(sc))cfg.put(k,copy(sc.get(k)));return out;}
 public static JSONObject editBaseline(JSONObject row)throws Exception{JSONObject out=new JSONObject();for(String key:"amount kind title category note accountId toAccountId person loanId cardId billId subId date".split(" "))out.put(key,row.has(key)&&!row.isNull(key)?row.get(key):(key.equals("amount")||key.equals("date")?0:""));return out;}
 public static int count(JSONObject shared)throws Exception{int n=0;JSONArray a=shared.getJSONArray("entries");for(int i=0;i<a.length();i++)if(Ledger.active(a.getJSONObject(i)))n++;return n;}
}
