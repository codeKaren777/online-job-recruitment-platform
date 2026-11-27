package com.groupcc2.recruitment.profile;
import java.util.*;
import jakarta.validation.Validation;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.*;
import org.springframework.transaction.support.*;
import com.groupcc2.recruitment.profile.service.*;
import com.groupcc2.recruitment.profile.repository.*;
import com.groupcc2.recruitment.profile.entity.*;
import com.groupcc2.recruitment.profile.dto.*;
import com.groupcc2.recruitment.profile.storage.*;
import com.groupcc2.recruitment.users.service.*;
import com.groupcc2.recruitment.users.entity.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;
class ProfileServiceTest {
 UserService users=mock(UserService.class);ProfileRls rls=mock(ProfileRls.class);
 ProfileRepository profiles=mock(ProfileRepository.class);DocumentRepository documents=mock(DocumentRepository.class);
 PrivateDocumentStorage storage=mock(PrivateDocumentStorage.class);EmployerApplicationAccess access=mock(EmployerApplicationAccess.class);
 User seeker=new User("s@example.test","hash",UserRole.JOB_SEEKER);UUID owner=seeker.getId();
 TestTx manager=new TestTx();ProfileService service;
 static class TestTx extends AbstractPlatformTransactionManager {
 boolean failCommit;
 protected Object doGetTransaction(){return new Object();}
 protected void doBegin(Object t,TransactionDefinition d){}
 protected void doCommit(DefaultTransactionStatus s){if(failCommit)throw new TransactionSystemException("test commit failure");}
 protected void doRollback(DefaultTransactionStatus s){}
 }
 <T> ObjectProvider<T> provider(T value){ObjectProvider<T> p=mock(ObjectProvider.class);when(p.getIfAvailable()).thenReturn(value);return p;}
 @BeforeEach void setup(){manager.setRollbackOnCommitFailure(true);service=new ProfileService(provider(()->owner),provider(access),provider(storage),users,rls,profiles,documents,
 new DocumentValidator(),new ProfileImageValidator(),Validation.buildDefaultValidatorFactory().getValidator(),manager,120);
 when(users.findById(owner)).thenReturn(seeker);
 when(profiles.findById(owner)).thenReturn(Optional.of(new Profile(owner,new ProfileRequest("Name",null,null))));
 when(profiles.saveAndFlush(any())).thenAnswer(i->i.getArgument(0));when(documents.saveAndFlush(any())).thenAnswer(i->i.getArgument(0));}
 MockMultipartFile file(){return new MockMultipartFile("file","cv.pdf","application/pdf","%PDF-1.7\ncontent\n%%EOF".getBytes());}
 @Test void optionalProfileImageCanBeUploadedAndIsMarkedAvailable(){
  byte[] png={(byte)0x89,'P','N','G',13,10,26,10,1,2,3,4};
  MockMultipartFile image=new MockMultipartFile("file","avatar.png","image/png",png);
  ProfileResponse response=service.uploadProfileImage(image);
  assertThat(response.profileImageAvailable()).isTrue();
  verify(storage).upload(matches("profile-images/[a-f0-9-]{36}"),any(),eq("image/png"));
  verify(profiles).saveAndFlush(argThat(p->((Profile)p).getProfileImageStorageKey()!=null));
 }
 @Test void profileImageIsReturnedAsPrivateShortLivedUrl(){
  Profile p=new Profile(owner,new ProfileRequest("Name",null,null));p.setProfileImage("profile-images/random","image/png");
  when(profiles.findById(owner)).thenReturn(Optional.of(p));
  when(storage.signGetInline("profile-images/random",java.time.Duration.ofSeconds(120),"profile-image.png","image/png"))
   .thenReturn(java.net.URI.create("https://files.example.test/signed"));
  assertThat(service.viewOwnProfileImage().url().getHost()).isEqualTo("files.example.test");
 }
 @Test void employerCanViewImageOnlyAfterApplicationAccessIsVerified(){
  UUID employer=owner,job=UUID.randomUUID(),applicant=UUID.randomUUID();
  when(users.findById(owner)).thenReturn(new User("employer@example.test","hash",UserRole.EMPLOYER));
  Profile p=new Profile(applicant,new ProfileRequest("Applicant",null,null));p.setProfileImage("profile-images/random","image/jpeg");
  when(profiles.findById(applicant)).thenReturn(Optional.of(p));
  when(storage.signGetInline("profile-images/random",java.time.Duration.ofSeconds(120),"profile-image.jpg","image/jpeg"))
   .thenReturn(java.net.URI.create("https://files.example.test/signed"));
  service.viewProfileImageForEmployer(job,applicant);
  verify(access).requireSubmittedApplication(employer,job,applicant);
 }
 @Test void createUpdateRead(){when(profiles.findById(owner)).thenReturn(Optional.empty());assertThat(service.upsert(new ProfileRequest("Created",null,null)).displayName()).isEqualTo("Created");
 when(profiles.findById(owner)).thenReturn(Optional.of(new Profile(owner,new ProfileRequest("Old",null,null))));
 assertThat(service.upsert(new ProfileRequest("Updated","123","bio")).displayName()).isEqualTo("Updated");assertThat(service.readOwn().displayName()).isEqualTo("Updated");}
 @Test void deniesInactive(){doThrow(new InactiveAccountException()).when(users).requireActive(seeker);
 assertThatThrownBy(()->service.readOwn()).isInstanceOf(InactiveAccountException.class);verifyNoInteractions(storage);}
 @Test void deniesWrongRole(){when(users.findById(owner)).thenReturn(new User("e@test","hash",UserRole.EMPLOYER));assertThatThrownBy(()->service.readOwn()).isInstanceOf(ProfileFailure.class);}
 @Test void documentIdCannotOverrideOwnership(){UUID foreign=UUID.randomUUID();when(documents.findByIdAndProfileUserId(foreign,owner)).thenReturn(Optional.empty());
 assertThatThrownBy(()->service.deleteOwn(foreign)).isInstanceOf(ProfileFailure.class);verifyNoInteractions(storage);}
 @Test void foreignDocumentCannotBeSigned(){UUID foreign=UUID.randomUUID();when(documents.findByIdAndProfileUserId(foreign,owner)).thenReturn(Optional.empty());
 assertThatThrownBy(()->service.downloadOwn(foreign)).isInstanceOf(ProfileFailure.class);verifyNoInteractions(storage);}
 @Test void authorizedDocumentSignsShortLivedGet(){UUID id=UUID.randomUUID();when(documents.findByIdAndProfileUserId(id,owner)).thenReturn(Optional.of(new Document(owner,"generated-key","cv.pdf","application/pdf",20)));
 when(storage.signGet("generated-key",java.time.Duration.ofSeconds(120),"cv.pdf","application/pdf")).thenReturn(java.net.URI.create("http://example.test/signed"));
 assertThat(service.downloadOwn(id).url().getHost()).isEqualTo("example.test");
 verify(storage).signGet("generated-key",java.time.Duration.ofSeconds(120),"cv.pdf","application/pdf");}
 @Test void authorizedOwnerCanRequestInlineViewWithoutChangingDownloadBehavior(){UUID id=UUID.randomUUID();when(documents.findByIdAndProfileUserId(id,owner)).thenReturn(Optional.of(new Document(owner,"generated-key","cv.pdf","application/pdf",20)));
 when(storage.signGetInline("generated-key",java.time.Duration.ofSeconds(120),"cv.pdf","application/pdf")).thenReturn(java.net.URI.create("https://files.mikhy.me/signed"));
 assertThat(service.viewOwn(id).url().getHost()).isEqualTo("files.mikhy.me");
 verify(storage).signGetInline("generated-key",java.time.Duration.ofSeconds(120),"cv.pdf","application/pdf");}
 @Test void foreignDocumentCannotBeViewed(){UUID foreign=UUID.randomUUID();when(documents.findByIdAndProfileUserId(foreign,owner)).thenReturn(Optional.empty());
 assertThatThrownBy(()->service.viewOwn(foreign)).isInstanceOf(ProfileFailure.class);verifyNoInteractions(storage);}
 @Test void employerCanViewOnlyTheApplicantsDocumentForAQualifyingApplication(){UUID employer=owner,job=UUID.randomUUID(),applicant=UUID.randomUUID(),documentId=UUID.randomUUID();
 when(users.findById(owner)).thenReturn(new User("employer@example.test","hash",UserRole.EMPLOYER));
 when(documents.findByIdAndProfileUserId(documentId,applicant)).thenReturn(Optional.of(new Document(applicant,"generated-key","cv.pdf","application/pdf",20)));
 when(storage.signGetInline("generated-key",java.time.Duration.ofSeconds(120),"cv.pdf","application/pdf"))
  .thenReturn(java.net.URI.create("https://files.mikhy.me/signed"));
 assertThat(service.viewForEmployer(job,applicant,documentId).url().getHost()).isEqualTo("files.mikhy.me");
 verify(access).requireSubmittedApplication(employer,job,applicant);
 verify(storage).signGetInline("generated-key",java.time.Duration.ofSeconds(120),"cv.pdf","application/pdf");
 }
 @Test void employerCannotViewCvForUnrelatedApplication(){UUID employer=owner,job=UUID.randomUUID(),applicant=UUID.randomUUID(),documentId=UUID.randomUUID();
 when(users.findById(owner)).thenReturn(new User("employer@example.test","hash",UserRole.EMPLOYER));
 doThrow(new ProfileFailure(ProfileFailure.Code.FORBIDDEN)).when(access).requireSubmittedApplication(employer,job,applicant);
 assertThatThrownBy(()->service.viewForEmployer(job,applicant,documentId)).isInstanceOf(ProfileFailure.class);
 verify(documents,never()).findByIdAndProfileUserId(any(),any());verifyNoInteractions(storage);
 }
 @Test void uploadReturnsSafeMetadata(){DocumentResponse d=service.upload(file());assertThat(d.originalFilename()).isEqualTo("cv.pdf");
 verify(storage).upload(matches("documents/[a-f0-9-]{36}"),any(),eq("application/pdf"));verify(storage,never()).delete(any());}
 @Test void storageFailureNeverPersists(){doThrow(new IllegalStateException()).when(storage).upload(any(),any(),any());assertThatThrownBy(()->service.upload(file())).isInstanceOf(ProfileFailure.class);verify(documents,never()).saveAndFlush(any());verify(storage).delete(any());}
 @Test void flushFailureCleansObject(){when(documents.saveAndFlush(any())).thenThrow(new IllegalStateException());assertThatThrownBy(()->service.upload(file())).isInstanceOf(IllegalStateException.class);verify(storage).delete(any());}
 @Test void commitFailureCleansObject(){manager.failCommit=true;assertThatThrownBy(()->service.upload(file())).isInstanceOf(TransactionSystemException.class);verify(storage).delete(any());}
 @Test void unknownCommitOutcomeRequiresReconciliationInsteadOfDeletingPotentiallyCommittedData(){manager.setRollbackOnCommitFailure(false);manager.failCommit=true;
 assertThatThrownBy(()->service.upload(file())).isInstanceOf(TransactionSystemException.class);verify(storage,never()).delete(any());}
 @Test void cleanupFailureDoesNotHideOriginal(){manager.failCommit=true;doThrow(new IllegalStateException("sensitive")).when(storage).delete(any());assertThatThrownBy(()->service.upload(file())).isInstanceOf(TransactionSystemException.class);}
 @Test void deleteFailureRetainsMetadata(){UUID id=UUID.randomUUID();when(documents.findByIdAndProfileUserId(id,owner)).thenReturn(Optional.of(new Document(owner,"internal","cv.pdf","application/pdf",20)));
 doThrow(new IllegalStateException()).when(storage).delete(any());assertThatThrownBy(()->service.deleteOwn(id)).isInstanceOf(ProfileFailure.class);verify(documents,never()).delete(any());}
 @Test void submittedApplicationCvCannotBeDeletedFromProfileLibrary(){UUID id=UUID.randomUUID();when(documents.findByIdAndProfileUserId(id,owner)).thenReturn(Optional.of(new Document(owner,"internal","cv.pdf","application/pdf",20)));
 when(documents.existsApplicationReference(id)).thenReturn(true);assertThatThrownBy(()->service.deleteOwn(id)).isInstanceOf(IllegalArgumentException.class);
 verify(storage,never()).delete(any());verify(documents,never()).delete(any());}
 @Test void employerMustPassSpecificJobApplicationCheck(){when(users.findById(owner)).thenReturn(new User("e@test","hash",UserRole.EMPLOYER));UUID job=UUID.randomUUID(),applicant=UUID.randomUUID();
 doThrow(new ProfileFailure(ProfileFailure.Code.FORBIDDEN)).when(access).requireSubmittedApplication(owner,job,applicant);
 assertThatThrownBy(()->service.readForEmployer(job,applicant)).isInstanceOf(ProfileFailure.class);verify(profiles,never()).findById(applicant);
 reset(access);when(profiles.findById(applicant)).thenReturn(Optional.of(new Profile(applicant,new ProfileRequest("Applicant",null,null))));assertThat(service.readForEmployer(job,applicant).displayName()).isEqualTo("Applicant");}
}
