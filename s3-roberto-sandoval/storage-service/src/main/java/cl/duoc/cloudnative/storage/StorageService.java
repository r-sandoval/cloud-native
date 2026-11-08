package cl.duoc.cloudnative.storage;
import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
@Service public class StorageService {
 private final S3Client s3;private final String bucket;private final Path temporary;
 public StorageService(S3Client s3,@Value("${storage.s3.bucket}") String bucket,
  @Value("${storage.efs.path:/mnt/efs}") String path) {
  this.s3=s3;this.bucket=bucket;this.temporary=Path.of(path).toAbsolutePath();
 }
 public Map<String,String> store(MultipartFile file,String clientId,LocalDate date) throws IOException {
  checkId(clientId);
  if(file.isEmpty()||file.getSize()>10*1024*1024)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"PDF vacío o demasiado grande");
  byte[] header=file.getInputStream().readNBytes(5);
  if (!Arrays.equals(header,new byte[]{'%','P','D','F','-'}))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Se requiere PDF");
  Files.createDirectories(temporary);
  String name="documento-"+UUID.randomUUID()+".pdf";
  String key="clientes/"+clientId+"/"+date.getYear()+"/"+String.format("%02d",date.getMonthValue())+"/"+String.format("%02d",date.getDayOfMonth())+"/"+name;
  Path path=Files.createTempFile(temporary,"documento-",".pdf");
  try {
   file.transferTo(path);
   s3.putObject(PutObjectRequest.builder().bucket(bucket).key(key).contentType("application/pdf").build(),RequestBody.fromFile(path));
  } finally {Files.deleteIfExists(path);}
  return Map.of("clientId",clientId,"date",date.toString(),"filename",name,"s3Key",key,"storage","S3", "temporaryStorage","EFS");
 }
 public byte[] load(String clientId,LocalDate date,String filename) {
  checkId(clientId);
  if(!filename.matches("documento-[a-f0-9-]{36}\\.pdf"))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Nombre inválido");
  String key="clientes/"+clientId+"/"+date.getYear()+"/"+String.format("%02d",date.getMonthValue())+"/"+String.format("%02d",date.getDayOfMonth())+"/"+filename;
  try {return s3.getObjectAsBytes(GetObjectRequest.builder().bucket(bucket).key(key).build()).asByteArray();}
  catch(NoSuchKeyException e){throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Documento no encontrado");}
 }
 private void checkId(String id){if(!id.matches("[A-Za-z0-9_-]{1,50}"))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Cliente inválido");}
}
