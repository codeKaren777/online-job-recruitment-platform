package com.groupcc2.recruitment.profile;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockMultipartFile;
import com.groupcc2.recruitment.profile.storage.DocumentValidator;
import com.groupcc2.recruitment.profile.service.ProfileFailure;
import static org.assertj.core.api.Assertions.*;
class DocumentValidatorTest {
 DocumentValidator validator=new DocumentValidator();
 byte[] pdf="%PDF-1.7\n1 0 obj <<>> endobj\n%%EOF\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
 @Test void acceptsPdfAndRemovesTemporaryFile() throws Exception {
 var v=validator.validate(new MockMultipartFile("file","CV.pdf","application/pdf",pdf));
 assertThat(v.size()).isEqualTo(pdf.length);assertThat(v.path()).exists();v.close();assertThat(v.path()).doesNotExist();}
 @Test void rejectsUnsafeNames(){for(String name:new String[]{"../cv.pdf","C:\\cv.pdf","a/b.pdf","x..pdf","x.exe","a".repeat(181)+".pdf","cv\r.pdf"})
 assertThatThrownBy(()->validator.validate(new MockMultipartFile("file",name,"application/pdf",pdf))).isInstanceOf(ProfileFailure.class);}
 @Test void rejectsMimeAndContentSpoofing(){
 assertThatThrownBy(()->validator.validate(new MockMultipartFile("file","cv.pdf","text/plain",pdf))).isInstanceOf(ProfileFailure.class);
 assertThatThrownBy(()->validator.validate(new MockMultipartFile("file","cv.pdf","application/pdf","not a pdf but %%EOF".getBytes()))).isInstanceOf(ProfileFailure.class);
 assertThatThrownBy(()->validator.validate(new MockMultipartFile("file","cv.pdf","application/pdf","%PDF-1.7 missing trailer".getBytes()))).isInstanceOf(ProfileFailure.class);}
 @Test void actualStreamLimitDoesNotTrustReportedSize(){
 var file=new MockMultipartFile("file","cv.pdf","application/pdf",new byte[(int)DocumentValidator.MAX_BYTES+1]) {
  @Override public long getSize(){return 1;}
  @Override public boolean isEmpty(){return false;}
 };
 assertThatThrownBy(()->validator.validate(file)).isInstanceOfSatisfying(ProfileFailure.class,
  failure->assertThat(failure.code()).isEqualTo(ProfileFailure.Code.FILE_TOO_LARGE));}
 @Test void rejectsEmptyAndOversize(){for(byte[] data:new byte[][]{new byte[0],new byte[(int)DocumentValidator.MAX_BYTES+1]})
 assertThatThrownBy(()->validator.validate(new MockMultipartFile("file","cv.pdf","application/pdf",data)))
  .isInstanceOf(ProfileFailure.class);}
 @Test void reportsOversizeWithPayloadTooLargeCode(){
 assertThatThrownBy(()->validator.validate(new MockMultipartFile("file","cv.pdf","application/pdf",new byte[(int)DocumentValidator.MAX_BYTES+1])))
  .isInstanceOfSatisfying(ProfileFailure.class,failure->assertThat(failure.code()).isEqualTo(ProfileFailure.Code.FILE_TOO_LARGE));}
}
