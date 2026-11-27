package com.groupcc2.recruitment.profile;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.containers.wait.strategy.Wait;
import software.amazon.awssdk.auth.credentials.*;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import com.groupcc2.recruitment.profile.storage.S3DocumentStorage;
import static org.assertj.core.api.Assertions.*;
@Testcontainers
class MinioStorageIT {
 @Container static GenericContainer<?> minio=new GenericContainer<>(new ImageFromDockerfile().withFileFromPath("Dockerfile",Path.of("docker/minio.Dockerfile")))
 .withEnv("MINIO_ROOT_USER","integration-admin").withEnv("MINIO_ROOT_PASSWORD","integration-only-password")
 .withCommand("server","/data").withExposedPorts(9000).waitingFor(Wait.forHttp("/minio/health/ready").forPort(9000));
 @Test void privateBucketUploadSignedGetAndDelete()throws Exception {
 URI endpoint=URI.create("http://"+minio.getHost()+":"+minio.getMappedPort(9000));
 var credentials=StaticCredentialsProvider.create(AwsBasicCredentials.create("integration-admin","integration-only-password"));
 var config=S3Configuration.builder().pathStyleAccessEnabled(true).build();
 try(var client=S3Client.builder().endpointOverride(endpoint).credentialsProvider(credentials).region(Region.US_EAST_1).serviceConfiguration(config).build();
 var signer=S3Presigner.builder().endpointOverride(endpoint).credentialsProvider(credentials).region(Region.US_EAST_1).serviceConfiguration(config).build()) {
 client.createBucket(b->b.bucket("documents-test"));var storage=new S3DocumentStorage(client,signer,"documents-test");
 Path file=Files.createTempFile("minio-test-",".pdf");try {
 Files.writeString(file,"%PDF-1.7\ncontent\n%%EOF");storage.upload("documents/random-id",file,"application/pdf");
 var http=HttpClient.newHttpClient();
 assertThat(http.send(HttpRequest.newBuilder(endpoint.resolve("/documents-test/documents/random-id")).GET().build(),HttpResponse.BodyHandlers.discarding()).statusCode()).isEqualTo(403);
 URI url=storage.signGet("documents/random-id",Duration.ofSeconds(120),"resume.pdf","application/pdf");
 assertThat(url.getQuery()).contains("X-Amz-Expires=120")
  .contains("response-content-disposition")
  .contains("response-content-type");
 assertThat(http.send(HttpRequest.newBuilder(url).GET().build(),HttpResponse.BodyHandlers.ofString()).body()).contains("%PDF-1.7");
 var response=http.send(HttpRequest.newBuilder(url).GET().build(),HttpResponse.BodyHandlers.discarding());
 assertThat(response.statusCode()).isEqualTo(200);
 String signature=url.getRawQuery().replaceFirst(".*X-Amz-Signature=([0-9a-f]+).*", "$1");
 String replacement=(signature.charAt(0)=='0' ? "1" : "0")+signature.substring(1);
 String invalidQuery=url.getRawQuery().replace("X-Amz-Signature="+signature,"X-Amz-Signature="+replacement);
 URI invalidSignature=URI.create(url.toString().replace(url.getRawQuery(),invalidQuery));
 assertThat(http.send(HttpRequest.newBuilder(invalidSignature).GET().build(),HttpResponse.BodyHandlers.discarding()).statusCode()).isEqualTo(403);
 var publicSigner=S3Presigner.builder().endpointOverride(URI.create("https://files.mikhy.me"))
  .credentialsProvider(credentials).region(Region.US_EAST_1).serviceConfiguration(config).build();
 try(publicSigner) {
  URI external= new S3DocumentStorage(client,publicSigner,"documents-test")
   .signGetInline("documents/random-id",Duration.ofSeconds(120),"resume.pdf","application/pdf");
  assertThat(external.getHost()).isEqualTo("files.mikhy.me");
  assertThat(external.getRawQuery()).contains("X-Amz-SignedHeaders=host")
   .contains("response-content-disposition")
   .contains("inline");
  // Simulate the upstream request a reverse proxy must send: same GET target
  // and signed Host header, with TLS terminated before MinIO.
  ProxyResponse forwarded = proxyForwardedGet(endpoint, external);
  assertThat(forwarded.status()).isEqualTo(200);
  assertThat(forwarded.headers()).anyMatch(h -> h.equalsIgnoreCase("content-disposition: inline; filename=\"resume.pdf\""));
  assertThat(forwarded.headers()).anyMatch(h -> h.equalsIgnoreCase("content-type: application/pdf"));
 }
 URI expired=storage.signGet("documents/random-id",Duration.ofSeconds(1),"resume.pdf","application/pdf");
 Thread.sleep(1500);
 assertThat(http.send(HttpRequest.newBuilder(expired).GET().build(),HttpResponse.BodyHandlers.discarding()).statusCode()).isEqualTo(403);
 assertThat(http.send(HttpRequest.newBuilder(url).method("PUT",HttpRequest.BodyPublishers.ofString("overwrite")).build(),HttpResponse.BodyHandlers.discarding()).statusCode()).isEqualTo(403);
 storage.delete("documents/random-id");storage.delete("documents/random-id");
 }finally{Files.deleteIfExists(file);}
 }
 }

 private static ProxyResponse proxyForwardedGet(URI minioEndpoint, URI signedUrl) throws Exception {
  try (Socket socket = new Socket(minioEndpoint.getHost(), minioEndpoint.getPort())) {
   socket.setSoTimeout(5000);
   String target = signedUrl.getRawPath() + "?" + signedUrl.getRawQuery();
   String request = "GET " + target + " HTTP/1.1\r\n"
    + "Host: " + signedUrl.getHost() + "\r\n"
    + "Connection: close\r\n\r\n";
   socket.getOutputStream().write(request.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
   socket.getOutputStream().flush();
   var reader = new java.io.BufferedReader(new java.io.InputStreamReader(
    socket.getInputStream(), java.nio.charset.StandardCharsets.US_ASCII));
   String status = reader.readLine();
   var headers = new java.util.ArrayList<String>();
   for (String line; (line = reader.readLine()) != null && !line.isEmpty();) headers.add(line);
   return new ProxyResponse(Integer.parseInt(status.split(" ")[1]), headers);
  }
 }

 private record ProxyResponse(int status, java.util.List<String> headers) {}
}
