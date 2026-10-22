package com.groupcc2.recruitment.profile.storage;
import java.io.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import com.groupcc2.recruitment.profile.service.ProfileFailure;
/** Conservative PDF-only allowlist, bounded streaming to disk. Signature is not malware scanning. */
@Component
public class DocumentValidator {
 private static final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(DocumentValidator.class);
 public static final long MAX_BYTES=5L*1024*1024;
 public record Validated(Path path,String filename,long size) implements AutoCloseable {
  public void close() throws IOException {Files.deleteIfExists(path);}
 }
 public Validated validate(MultipartFile file) {
  String name=file.getOriginalFilename();
  if(name==null || name.length()>180 || !name.matches("[A-Za-z0-9][A-Za-z0-9 _().-]*")
   || name.contains("..") || !name.toLowerCase(Locale.ROOT).endsWith(".pdf")
   || !"application/pdf".equals(file.getContentType()) || file.isEmpty()) throw invalid();
  if(file.getSize()>MAX_BYTES)throw tooLarge();
  Path temp=null;boolean success=false;
  try {
   temp=Files.createTempFile("profile-document-",".pdf");
   long size=0;
   try(InputStream in=file.getInputStream(); OutputStream out=Files.newOutputStream(temp)) {
    byte[] buffer=new byte[8192];int n;
    while((n=in.read(buffer))!=-1) {size+=n;if(size>MAX_BYTES)throw tooLarge();out.write(buffer,0,n);}
   }
   if(size<12)throw invalid();
   try(var in=Files.newInputStream(temp)) {
    if(!new String(in.readNBytes(8),StandardCharsets.US_ASCII).matches("%PDF-[12]\\.[0-9]"))throw invalid();
   }
   try(var channel=java.nio.channels.FileChannel.open(temp)) {
    int length=(int)Math.min(1024,size);var tail=java.nio.ByteBuffer.allocate(length);
    channel.position(size-length);while(tail.hasRemaining() && channel.read(tail)!=-1) {}
    if(!new String(tail.array(),StandardCharsets.US_ASCII).stripTrailing().endsWith("%%EOF"))throw invalid();
   }
   success=true;return new Validated(temp,name,size);
  }catch(IOException e) {throw invalid();}
  catch(RuntimeException e) {throw e;}
  finally { if(!success && temp!=null)try{Files.deleteIfExists(temp);}catch(IOException ignored){log.warn("Failed to remove rejected document temporary file");}
  }
 }
 private ProfileFailure invalid(){return new ProfileFailure(ProfileFailure.Code.INVALID_FILE);}
 private ProfileFailure tooLarge(){return new ProfileFailure(ProfileFailure.Code.FILE_TOO_LARGE);}
}
