package com.groupcc2.recruitment.profile.storage;
import java.net.URI;
import org.springframework.context.annotation.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import software.amazon.awssdk.auth.credentials.*;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
@Configuration
@ConditionalOnProperty(name="app.profile.storage.enabled",havingValue="true")
public class StorageConfiguration {
 @Bean(destroyMethod="close") S3Client profileS3Client(@Value("${app.profile.storage.endpoint}") URI endpoint,
 @Value("${app.profile.storage.region}") String region,@Value("${app.profile.storage.access-key}") String access,
 @Value("${app.profile.storage.secret-key}") String secret,@Value("${app.profile.storage.path-style:true}") boolean pathStyle){
 return S3Client.builder().endpointOverride(endpoint).region(Region.of(region)).credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(access,secret)))
 .overrideConfiguration(c->c.apiCallTimeout(java.time.Duration.ofSeconds(30)).apiCallAttemptTimeout(java.time.Duration.ofSeconds(10)))
 .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(pathStyle).build()).build();}
 @Bean(destroyMethod="close") S3Presigner profilePresigner(@Value("${app.profile.storage.public-endpoint}") URI endpoint,
 @Value("${app.profile.storage.region}") String region,@Value("${app.profile.storage.access-key}") String access,
 @Value("${app.profile.storage.secret-key}") String secret,@Value("${app.profile.storage.path-style:true}") boolean pathStyle){
 return S3Presigner.builder().endpointOverride(endpoint).region(Region.of(region)).credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(access,secret)))
 .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(pathStyle).build()).build();}
 @Bean PrivateDocumentStorage privateDocumentStorage(S3Client client,S3Presigner signer,@Value("${app.profile.storage.bucket}") String bucket){return new S3DocumentStorage(client,signer,bucket);}
}
