package in.paisa.personal;
import org.json.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
public final class WebBackupInterop {
 public static void main(String[] args)throws Exception{
  JSONObject expected=new JSONObject(new String(Files.readAllBytes(Paths.get(args[0])),StandardCharsets.UTF_8));
  JSONObject actual=new JSONObject(new String(BackupCipher.decrypt(Files.readAllBytes(Paths.get(args[1])),"local-test-only-password".toCharArray()),StandardCharsets.UTF_8));
  Ledger.validate(actual);
  if(!SyncCore.same(SyncCore.snapshot(expected),SyncCore.snapshot(actual)))throw new AssertionError("Web backup differs from actual local financial state");
  System.out.println("Actual web-to-Android encrypted backup interoperability passed");
 }
}
