package com.groupcc2.recruitment.profile.controller;
import java.util.*;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.HttpStatus;
import com.groupcc2.recruitment.profile.dto.*;
import com.groupcc2.recruitment.profile.service.ProfileService;
/** Authenticated job seeker endpoints; services enforce account and ownership. */
@org.springframework.security.access.prepost.PreAuthorize("hasRole('JOB_SEEKER')")
@RestController
@RequestMapping("/api/profile")
@io.swagger.v3.oas.annotations.tags.Tag(name = "Profiles", description = "Profile and private document operations")
@io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "bearerAuth")
public class ProfileController {
 private final ProfileService service;
 public ProfileController(ProfileService service){this.service=service;}
 @GetMapping public ProfileResponse read(){return service.readOwn();}
 @PutMapping public ProfileResponse update(@Valid @RequestBody ProfileRequest request){return service.upsert(request);}
 @PostMapping(value="/documents",consumes="multipart/form-data") @ResponseStatus(HttpStatus.CREATED)
 public DocumentResponse upload(@RequestPart("file") MultipartFile file){return service.upload(file);}
 @GetMapping("/documents") public List<DocumentResponse> documents(){return service.listOwn();}
 @DeleteMapping("/documents/{documentId}") @ResponseStatus(HttpStatus.NO_CONTENT)
 public void delete(@PathVariable UUID documentId){service.deleteOwn(documentId);}
}
