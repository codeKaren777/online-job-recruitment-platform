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
 new DocumentValidator(),Validation.buildDefaultValidatorFactory().getValidator(),manager,120);
 when(users.findById(owner)).thenReturn(seeker);
 when(profiles.findById(owner)).thenReturn(Optional.of(new Profile(owner,new ProfileRequest("Name",null,null))));
 when(profiles.saveAndFlush(any())).thenAnswer(i->i.getArgument(0));when(documents.saveAndFlush(any())).thenAnswer(i->i.getArgument(0));}
 MockMultipartFile file(){return new MockMultipartFile("file","cv.pdf","application/pdf","%PDF-1.7\ncontent\n%%EOF".getBytes());}
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
 when(storage.signGet("generated-key",java.time.Duration.ofSeconds(120))).thenReturn(java.net.URI.create("http://example.test/signed"));
 assertThat(service.downloadOwn(id).url().getHost()).isEqualTo("example.test");verify(storage).signGet("generated-key",java.time.Duration.ofSeconds(120));}
 @Test void signingFailureReturnsSafeStorageError(){UUID id=UUID.randomUUID();when(documents.findByIdAndProfileUserId(id,owner)).thenReturn(Optional.of(new Document(owner,"generated-key","cv.pdf","application/pdf",20)));
 when(storage.signGet(any(),any())).thenThrow(new IllegalStateException("sensitive storage details"));
 assertThatThrownBy(()->service.downloadOwn(id)).isInstanceOfSatisfying(ProfileFailure.class,
  failure->assertThat(failure.code()).isEqualTo(ProfileFailure.Code.STORAGE_UNAVAILABLE));}
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
 @Test void metadataDeleteFailureCanBeRetriedAfterIdempotentObjectDelete(){UUID id=UUID.randomUUID();Document document=new Document(owner,"generated-key","cv.pdf","application/pdf",20);
 when(documents.findByIdAndProfileUserId(id,owner)).thenReturn(Optional.of(document));doThrow(new IllegalStateException()).doNothing().when(documents).delete(document);
 assertThatThrownBy(()->service.deleteOwn(id)).isInstanceOfSatisfying(ProfileFailure.class,
  failure->assertThat(failure.code()).isEqualTo(ProfileFailure.Code.INTEGRATION_UNAVAILABLE));service.deleteOwn(id);
 verify(storage,times(2)).delete("generated-key");verify(documents,times(2)).delete(document);verify(documents,times(1)).flush();}
 @Test void employerMustPassSpecificJobApplicationCheck(){when(users.findById(owner)).thenReturn(new User("e@test","hash",UserRole.EMPLOYER));UUID job=UUID.randomUUID(),applicant=UUID.randomUUID();
 doThrow(new ProfileFailure(ProfileFailure.Code.FORBIDDEN)).when(access).requireSubmittedApplication(owner,job,applicant);
 assertThatThrownBy(()->service.readForEmployer(job,applicant)).isInstanceOf(ProfileFailure.class);verify(profiles,never()).findById(applicant);
 reset(access);when(profiles.findById(applicant)).thenReturn(Optional.of(new Profile(applicant,new ProfileRequest("Applicant",null,null))));assertThat(service.readForEmployer(job,applicant).displayName()).isEqualTo("Applicant");}
}
