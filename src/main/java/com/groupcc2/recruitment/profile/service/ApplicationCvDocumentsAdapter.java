package com.groupcc2.recruitment.profile.service;

import com.groupcc2.recruitment.application.service.ApplicationCvDocuments;
import com.groupcc2.recruitment.profile.dto.DocumentResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import java.util.UUID;

@Component
class ApplicationCvDocumentsAdapter implements ApplicationCvDocuments {
    private final ProfileService profiles;
    ApplicationCvDocumentsAdapter(ProfileService profiles) { this.profiles = profiles; }
    @Override public DocumentResponse storeForCurrentApplicant(MultipartFile file) {
        try { return profiles.uploadForApplication(file); }
        catch (ProfileFailure failure) {
            if (failure.code() == ProfileFailure.Code.NOT_FOUND)
                throw new com.groupcc2.recruitment.application.service.ApplicationFailure(
                        com.groupcc2.recruitment.application.service.ApplicationFailure.Code.PROFILE_INCOMPLETE);
            throw failure;
        }
    }
    @Override public void removeForCurrentApplicant(UUID documentId) {
        profiles.deleteOwn(documentId);
    }
    @Override public com.groupcc2.recruitment.profile.dto.DownloadResponse downloadForEmployer(UUID jobId, UUID applicantId, UUID documentId) {
        return profiles.downloadForEmployer(jobId, applicantId, documentId);
    }
    @Override public java.util.Map<UUID, DocumentResponse> metadataForEmployer(java.util.Set<UUID> documentIds) {
        return profiles.applicationCvMetadataForEmployer(documentIds);
    }
    @Override public java.util.Map<UUID, String> applicantNamesForEmployer(java.util.Set<UUID> applicantIds) {
        return profiles.applicantNamesForEmployer(applicantIds);
    }
    @Override public java.util.Map<UUID, com.groupcc2.recruitment.profile.dto.ProfileResponse> applicantProfilesForEmployer(java.util.Set<UUID> applicantIds) {
        return profiles.applicantProfilesForEmployer(applicantIds);
    }
}
