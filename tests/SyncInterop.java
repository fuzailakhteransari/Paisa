package in.paisa.personal;
import org.json.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
/** Executes actual-data merge cases prepared independently by the JavaScript client. */
public final class SyncInterop {
 public static void main(String[] args)throws Exception{
  JSONArray cases=new JSONArray(new String(Files.readAllBytes(Paths.get(args[0])),StandardCharsets.UTF_8)),out=new JSONArray();
  for(int i=0;i<cases.length();i++){JSONObject c=cases.getJSONObject(i);SyncCore.Result r=SyncCore.merge(c.getJSONObject("base"),c.getJSONObject("local"),c.getJSONObject("remote"),c.optString("resolution"));Ledger.validate(SyncCore.compose(c.getJSONObject("local"),r.state));out.put(new JSONObject().put("state",r.state).put("conflicts",r.conflicts.length()).put("edit",SyncCore.editBaseline(c.getJSONObject("edit"))));}
  Files.write(Paths.get(args[1]),out.toString().getBytes(StandardCharsets.UTF_8));System.out.println("Native cloud interoperability cases passed: "+cases.length());
 }
}
