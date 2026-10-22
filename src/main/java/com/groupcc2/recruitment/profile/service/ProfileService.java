package com.groupcc2.recruitment.profile.service;
import java.util.*;
import java.time.*;
import org.slf4j.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.*;
import org.springframework.transaction.support.*;
import jakarta.validation.Validator;
import com.groupcc2.recruitment.users.service.UserService;
import com.groupcc2.recruitment.users.entity.UserRole;
import com.groupcc2.recruitment.profile.dto.*;
import com.groupcc2.recruitment.profile.entity.*;
import com.groupcc2.recruitment.profile.repository.*;
import com.groupcc2.recruitment.profile.storage.*;
import org.springframework.web.multipart.MultipartFile;
@Service
public class ProfileService {
 private static final Logger log=LoggerFactory.getLogger(ProfileService.class);
 private final ObjectProvider<ProfileIdentity> identities;
 private final ObjectProvider<EmployerApplicationAccess> employerAccess;
 private final ObjectProvider<PrivateDocumentStorage> storage;
 private final UserService users;private final ProfileRls rls;
 private final ProfileRepository profiles;private final DocumentRepository documents;
 private final DocumentValidator files;private final Validator validator;private final TransactionTemplate tx;
 private final Duration expiry;
 public ProfileService(ObjectProvider<ProfileIdentity> identities,ObjectProvider<EmployerApplicationAccess> employerAccess,
 ObjectProvider<PrivateDocumentStorage> storage,UserService users,ProfileRls rls,ProfileRepository profiles,
 DocumentRepository documents,DocumentValidator files,Validator validator,PlatformTransactionManager manager,
 @org.springframework.beans.factory.annotation.Value("${app.profile.download-seconds:120}") long seconds) {
 this.identities=identities;this.employerAccess=employerAccess;this.storage=storage;this.users=users;this.rls=rls;
 this.profiles=profiles;this.documents=documents;this.files=files;this.validator=validator;tx=new TransactionTemplate(manager);
 // Each operation owns its commit, including compensations. Never silently join caller transactions.
 tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
 if(seconds<30||seconds>300)throw new IllegalArgumentException("Download expiry must be 30..300 seconds");expiry=Duration.ofSeconds(seconds);
 }
 private UUID actor(UserRole role) {
 ProfileIdentity identity=identities.getIfAvailable();if(identity==null)throw failure(ProfileFailure.Code.INTEGRATION_UNAVAILABLE);
 UUID id=identity.authenticatedUserId();if(id==null)throw failure(ProfileFailure.Code.FORBIDDEN);
 // Bootstrap only self visibility; derive role from persisted active User, never token/client role claims.
 rls.establish(id,"");var user=users.findById(id);users.requireActive(user);
 if(user.getRole()!=role)throw failure(ProfileFailure.Code.FORBIDDEN);rls.establish(id,role.name());return id;
 }
 public ProfileResponse readOwn() {return tx.execute(s->profile(actor(UserRole.JOB_SEEKER)).response());}
 public ProfileResponse upsert(ProfileRequest request) {
 if(request==null || !validator.validate(request).isEmpty())throw new IllegalArgumentException("Invalid profile fields");
 return tx.execute(s->{UUID id=actor(UserRole.JOB_SEEKER);Profile p=profiles.findById(id).orElseGet(()->new Profile(id,request));
 p.update(request);return profiles.saveAndFlush(p).response();});
 }
 public List<DocumentResponse> listOwn() {return tx.execute(s->list(actor(UserRole.JOB_SEEKER)));}
 public DocumentResponse upload(MultipartFile file) {
 // Authorize before consuming/uploading content. Entire DB commit is inside this boundary.
 return tx.execute(s->{UUID id=actor(UserRole.JOB_SEEKER);profile(id);PrivateDocumentStorage store=store();
 String operation=UUID.randomUUID().toString();String key="documents/"+operation;
 try(var validated=files.validate(file)) {
  TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){
   @Override public void afterCompletion(int status){if(status==STATUS_ROLLED_BACK)cleanup(store,key,operation);
    else if(status==STATUS_UNKNOWN)log.error("Profile upload commit outcome unknown; reconciliation operation {}",operation);}
  });
  try{store.upload(key,validated.path(),"application/pdf");}catch(RuntimeException e){throw failure(ProfileFailure.Code.STORAGE_UNAVAILABLE);}
  return documents.saveAndFlush(new Document(id,key,validated.filename(),"application/pdf",validated.size())).response();
 }catch(java.io.IOException e){throw failure(ProfileFailure.Code.STORAGE_UNAVAILABLE);}
 });
 }
 public void deleteOwn(UUID documentId) {
 tx.executeWithoutResult(s->{UUID id=actor(UserRole.JOB_SEEKER);Document d=document(documentId,id);PrivateDocumentStorage store=store();
 // Object deletion first. On failure retain metadata so an owner can retry. On commit failure
 // metadata may refer to a missing object; repeated S3 deletion is idempotent and completes recovery.
 try{store.delete(d.storageKey());}catch(RuntimeException e){throw failure(ProfileFailure.Code.STORAGE_UNAVAILABLE);}
 documents.delete(d);documents.flush();});
 }
 public ProfileResponse readForEmployer(UUID jobId,UUID applicant) {
 return tx.execute(s->{employer(jobId,applicant);return profile(applicant).response();});
 }
 public List<DocumentResponse> listForEmployer(UUID jobId,UUID applicant) {
 return tx.execute(s->{employer(jobId,applicant);return list(applicant);});
 }
 public DownloadResponse downloadForEmployer(UUID jobId,UUID applicant,UUID documentId) {
 return tx.execute(s->{employer(jobId,applicant);return sign(document(documentId,applicant));});
 }
 public DownloadResponse downloadOwn(UUID documentId) {
 return tx.execute(s->sign(document(documentId,actor(UserRole.JOB_SEEKER))));
 }
 private void employer(UUID jobId,UUID applicant){UUID employer=actor(UserRole.EMPLOYER);
 EmployerApplicationAccess access=employerAccess.getIfAvailable();if(access==null)throw failure(ProfileFailure.Code.INTEGRATION_UNAVAILABLE);
 access.requireSubmittedApplication(employer,jobId,applicant);
 }
 private DownloadResponse sign(Document d){return new DownloadResponse(store().signGet(d.storageKey(),expiry),Instant.now().plus(expiry));}
 private Profile profile(UUID id){return profiles.findById(id).orElseThrow(()->failure(ProfileFailure.Code.NOT_FOUND));}
 private Document document(UUID id,UUID owner){return documents.findByIdAndProfileUserId(id,owner).orElseThrow(()->failure(ProfileFailure.Code.NOT_FOUND));}
 private List<DocumentResponse> list(UUID id){profile(id);return documents.findAllByProfileUserIdOrderByCreatedAtDesc(id).stream().map(Document::response).toList();}
 private PrivateDocumentStorage store(){PrivateDocumentStorage s=storage.getIfAvailable();if(s==null)throw failure(ProfileFailure.Code.INTEGRATION_UNAVAILABLE);return s;}
 private void cleanup(PrivateDocumentStorage store,String key,String operation){try{store.delete(key);}catch(RuntimeException e){
 // No SDK exception: it may include credentials/URLs. Operation UUID allows restricted operator reconciliation.
 log.error("Profile upload rollback cleanup failed; reconciliation operation {}",operation);}}
 private ProfileFailure failure(ProfileFailure.Code code){return new ProfileFailure(code);}
}
