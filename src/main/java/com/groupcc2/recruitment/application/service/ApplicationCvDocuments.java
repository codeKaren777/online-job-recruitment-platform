package com.groupcc2.recruitment.application.service;

import com.groupcc2.recruitment.profile.dto.DocumentResponse;
import com.groupcc2.recruitment.profile.dto.DownloadResponse;
import org.springframework.web.multipart.MultipartFile;
import java.util.UUID;
import java.util.Map;
import java.util.Set;

/** Application-owned contract for storing and compensating an applicant's submitted CV. */
public interface ApplicationCvDocuments {
    DocumentResponse storeForCurrentApplicant(MultipartFile file);
    void removeForCurrentApplicant(UUID documentId);
    DownloadResponse downloadForEmployer(UUID jobId, UUID applicantId, UUID documentId);
    Map<UUID, DocumentResponse> metadataForEmployer(Set<UUID> documentIds);
    Map<UUID, String> applicantNamesForEmployer(Set<UUID> applicantIds);
}
