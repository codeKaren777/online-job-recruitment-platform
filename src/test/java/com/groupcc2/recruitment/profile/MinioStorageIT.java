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
 URI url=storage.signGet("documents/random-id",Duration.ofSeconds(120));assertThat(url.getQuery()).contains("X-Amz-Expires=120");
 assertThat(http.send(HttpRequest.newBuilder(url).GET().build(),HttpResponse.BodyHandlers.ofString()).body()).contains("%PDF-1.7");
 assertThat(http.send(HttpRequest.newBuilder(url).method("PUT",HttpRequest.BodyPublishers.ofString("overwrite")).build(),HttpResponse.BodyHandlers.discarding()).statusCode()).isEqualTo(403);
 storage.delete("documents/random-id");storage.delete("documents/random-id");
 }finally{Files.deleteIfExists(file);}
 }
 }
}
