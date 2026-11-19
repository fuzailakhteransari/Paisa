package in.paisa.personal;

import android.content.*;
import android.security.keystore.*;
import android.util.*;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import javax.crypto.*;
import javax.crypto.spec.*;

/** Secrets and sync baselines never enter WebView JavaScript or financial exports. */
public final class CloudVault {
 private static final String ALIAS="paisa-cloud-v1";
 private final Context context;
 public CloudVault(Context c){context=c.getApplicationContext();}
 private File file(String name){if(!Arrays.asList("meta","base","pending","safety","conflict").contains(name))throw new IllegalArgumentException("Invalid private sync file");return new File(context.getFilesDir(),"cloud-"+name+".enc");}
 private SecretKey key()throws Exception{KeyStore ks=KeyStore.getInstance("AndroidKeyStore");ks.load(null);if(!ks.containsAlias(ALIAS)){KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");generator.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setRandomizedEncryptionRequired(true).build());generator.generateKey();}return (SecretKey)ks.getKey(ALIAS,null);}
 public synchronized JSONObject read(String name)throws Exception{File f=file(name);if(!exists(name))return new JSONObject();byte[] all;try(FileInputStream in=new AtomicFile(f).openRead();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){if(out.size()+n>40*1024*1024)throw new IOException("Private sync file is too large");out.write(b,0,n);}all=out.toByteArray();}if(all.length<29)throw new IOException("Private sync state is damaged");Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Arrays.copyOfRange(all,0,12)));cipher.updateAAD(("in.paisa.personal|cloud|"+name).getBytes(StandardCharsets.UTF_8));return new JSONObject(new String(cipher.doFinal(Arrays.copyOfRange(all,12,all.length)),StandardCharsets.UTF_8));}
 public synchronized void write(String name,JSONObject value)throws Exception{Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key());cipher.updateAAD(("in.paisa.personal|cloud|"+name).getBytes(StandardCharsets.UTF_8));byte[] encrypted=cipher.doFinal(value.toString().getBytes(StandardCharsets.UTF_8));AtomicFile atomic=new AtomicFile(file(name));FileOutputStream out=atomic.startWrite();try{out.write(cipher.getIV());out.write(encrypted);atomic.finishWrite(out);}catch(Exception e){atomic.failWrite(out);throw e;}if(name.equals("meta"))context.getSharedPreferences("cloud-flags",Context.MODE_PRIVATE).edit().putBoolean("scheduled",value.optBoolean("enabled")||value.has("pair")).apply();}
 public boolean exists(String name){File f=file(name);return f.isFile()||new File(f.getPath()+".bak").isFile();}
 public void remove(String name){new AtomicFile(file(name)).delete();}
}
