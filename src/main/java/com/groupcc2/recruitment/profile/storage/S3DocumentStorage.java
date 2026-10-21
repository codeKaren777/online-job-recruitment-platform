package com.groupcc2.recruitment.profile.storage;
import java.nio.file.Path;
import java.net.URI;
import java.time.Duration;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
/** Bucket provisioned separately; denies operation if a bucket policy or ACL allows public access. */
public class S3DocumentStorage implements PrivateDocumentStorage {
 private final S3Client client;private final S3Presigner signer;private final String bucket;
 public S3DocumentStorage(S3Client client,S3Presigner signer,String bucket){this.client=client;this.signer=signer;this.bucket=bucket;}
 private void ensurePrivate(){
  try{client.getBucketPolicy(GetBucketPolicyRequest.builder().bucket(bucket).build());
   throw new IllegalStateException("Document bucket must have no bucket policy");
  }catch(S3Exception e){if(!"NoSuchBucketPolicy".equals(e.awsErrorDetails().errorCode()))throw e;}
  var acl=client.getBucketAcl(GetBucketAclRequest.builder().bucket(bucket).build());
  if(acl.grants().stream().anyMatch(g->g.grantee().type()==Type.GROUP))throw new IllegalStateException("Document bucket must be private");
 }
 public void upload(String key,Path path,String type){ensurePrivate();client.putObject(PutObjectRequest.builder().bucket(bucket).key(key).contentType(type).build(),RequestBody.fromFile(path));}
 public void delete(String key){client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());}
 public URI signGet(String key,Duration expiry){ensurePrivate();return URI.create(signer.presignGetObject(r->r.signatureDuration(expiry)
 .getObjectRequest(g->g.bucket(bucket).key(key).responseContentDisposition("attachment"))).url().toString());}
}
