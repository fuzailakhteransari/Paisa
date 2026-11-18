package in.paisa.personal;
import javax.crypto.*;
import javax.crypto.spec.*;
import java.security.*;
import java.io.*;
import java.util.*;
import java.util.zip.*;

public final class BackupCipher {
 private static final byte[] MAGIC={'P','A','I','S','A',3};
 private static final int ROUNDS=600000,MAX=32*1024*1024;
 private static SecretKey key(char[] password,byte[] salt,int rounds)throws Exception{PBEKeySpec spec=new PBEKeySpec(password,salt,rounds,256);try{return new SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded(),"AES");}finally{spec.clearPassword();}}
 public static byte[] encrypt(byte[] plain,char[] password)throws Exception{
  if(password.length<8)throw new IllegalArgumentException("Backup password needs at least 8 characters");if(plain.length>MAX)throw new IllegalArgumentException("Backup exceeds 32 MB");
  byte[] salt=new byte[16],iv=new byte[12];SecureRandom r=new SecureRandom();r.nextBytes(salt);r.nextBytes(iv);ByteArrayOutputStream compressed=new ByteArrayOutputStream();try(GZIPOutputStream gz=new GZIPOutputStream(compressed)){gz.write(plain);}Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key(password,salt,ROUNDS),new GCMParameterSpec(128,iv));cipher.updateAAD(MAGIC);byte[] encrypted=cipher.doFinal(compressed.toByteArray());ByteArrayOutputStream result=new ByteArrayOutputStream();try(DataOutputStream out=new DataOutputStream(result)){out.write(MAGIC);out.writeInt(ROUNDS);out.write(salt);out.write(iv);out.write(encrypted);}if(result.size()>MAX)throw new IllegalArgumentException("Backup exceeds file-size limit");return result.toByteArray();
 }
 public static byte[] decrypt(byte[] input,char[] password)throws Exception{
  if(input.length<54||input.length>MAX)throw new IllegalArgumentException("Not a valid Paisa backup");try(DataInputStream in=new DataInputStream(new ByteArrayInputStream(input))){byte[] magic=new byte[6];in.readFully(magic);if(!Arrays.equals(magic,MAGIC))throw new IllegalArgumentException("Unsupported backup format");int rounds=in.readInt();if(rounds<100000||rounds>2000000)throw new IllegalArgumentException("Invalid backup parameters");byte[] salt=new byte[16],iv=new byte[12];in.readFully(salt);in.readFully(iv);byte[] encrypted=new byte[in.available()];in.readFully(encrypted);Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.DECRYPT_MODE,key(password,salt,rounds),new GCMParameterSpec(128,iv));c.updateAAD(MAGIC);byte[] compressed;try{compressed=c.doFinal(encrypted);}catch(AEADBadTagException e){throw new IllegalArgumentException("Wrong password or damaged backup");}ByteArrayOutputStream result=new ByteArrayOutputStream();try(GZIPInputStream gz=new GZIPInputStream(new ByteArrayInputStream(compressed))){byte[] block=new byte[8192];int n;while((n=gz.read(block))!=-1){if(result.size()+n>MAX)throw new IllegalArgumentException("Backup exceeds size limit");result.write(block,0,n);}}return result.toByteArray();}
 }
}
