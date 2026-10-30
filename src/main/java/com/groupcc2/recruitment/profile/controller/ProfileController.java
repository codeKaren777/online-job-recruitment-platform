package com.groupcc2.recruitment.profile.controller;
import java.util.*;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.HttpStatus;
import com.groupcc2.recruitment.profile.dto.*;
import com.groupcc2.recruitment.profile.service.ProfileService;
import io.swagger.v3.oas.annotations.Operation;
/** Authenticated job seeker endpoints; services enforce account and ownership. */
@org.springframework.security.access.prepost.PreAuthorize("hasRole('JOB_SEEKER')")
@RestController
@RequestMapping("/api/profile")
@io.swagger.v3.oas.annotations.tags.Tag(name = "Profiles", description = "Profile and private document operations")
@io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "bearerAuth")
public class ProfileController {
 private final ProfileService service;
 public ProfileController(ProfileService service){this.service=service;}
 @GetMapping @Operation(summary="Get my profile", description="Returns the authenticated job seeker's profile.") public ProfileResponse read(){return service.readOwn();}
 @PutMapping @Operation(summary="Update my profile", description="Creates or updates the authenticated job seeker's profile using the request body.") public ProfileResponse update(@Valid @RequestBody ProfileRequest request){return service.upsert(request);}
 @PostMapping(value="/documents",consumes="multipart/form-data") @ResponseStatus(HttpStatus.CREATED)
 @Operation(summary="Upload a profile document", description="Uploads a document supplied in the multipart file part named file.")
 public DocumentResponse upload(@RequestPart("file") MultipartFile file){return service.upload(file);}
 @GetMapping("/documents") @Operation(summary="List my documents", description="Returns the authenticated job seeker's private documents.") public List<DocumentResponse> documents(){return service.listOwn();}
 @GetMapping("/documents/{documentId}/download")
 @Operation(summary="Get a document download", description="Returns download details for the authenticated job seeker's document identified by documentId.")
 public DownloadResponse download(@PathVariable UUID documentId){return service.downloadOwn(documentId);}
 @DeleteMapping("/documents/{documentId}") @ResponseStatus(HttpStatus.NO_CONTENT)
 @Operation(summary="Delete a profile document", description="Deletes the authenticated job seeker's document identified by documentId.")
 public void delete(@PathVariable UUID documentId){service.deleteOwn(documentId);}
}
