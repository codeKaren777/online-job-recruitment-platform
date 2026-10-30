package com.groupcc2.recruitment.profile.controller;
import java.util.*;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.HttpStatus;
import com.groupcc2.recruitment.profile.dto.*;
import com.groupcc2.recruitment.profile.service.ProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ExampleObject;
/** Authenticated job seeker endpoints; services enforce account and ownership. */
@org.springframework.security.access.prepost.PreAuthorize("hasRole('JOB_SEEKER')")
@RestController
@RequestMapping("/api/profile")
@io.swagger.v3.oas.annotations.tags.Tag(name = "Profiles", description = "Profile and private document operations")
@io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "bearerAuth")
public class ProfileController {
 private final ProfileService service;
 public ProfileController(ProfileService service){this.service=service;}
 @GetMapping @Operation(summary="Get my profile", description="Returns the profile belonging to the authenticated JOB_SEEKER. Requires a bearer token.") @ApiResponses({@ApiResponse(responseCode="200",description="Returns the job seeker's profile."),@ApiResponse(responseCode="401",description="A valid bearer token is required."),@ApiResponse(responseCode="403",description="The caller is not permitted to access a profile."),@ApiResponse(responseCode="404",description="Profile was not found."),@ApiResponse(responseCode="503",description="Profile service is unavailable.")}) public ProfileResponse read(){return service.readOwn();}
 @PutMapping @Operation(summary="Create or update my profile", description="Creates or replaces the authenticated JOB_SEEKER's profile using the request body. Requires a bearer token.",requestBody=@io.swagger.v3.oas.annotations.parameters.RequestBody(description="Profile display name and optional contact details.",required=true,content=@io.swagger.v3.oas.annotations.media.Content(examples=@ExampleObject(value="{\"displayName\":\"Jane Applicant\",\"phone\":\"+265999000111\",\"biography\":\"Backend developer with five years of experience.\"}")))) @ApiResponses({@ApiResponse(responseCode="200",description="Returns the saved profile."),@ApiResponse(responseCode="400",description="Request body is malformed or fails validation."),@ApiResponse(responseCode="401",description="A valid bearer token is required."),@ApiResponse(responseCode="403",description="The caller is not permitted to update a profile."),@ApiResponse(responseCode="503",description="Profile service is unavailable.")}) public ProfileResponse update(@Valid @RequestBody ProfileRequest request){return service.upsert(request);}
 @PostMapping(value="/documents",consumes="multipart/form-data") @ResponseStatus(HttpStatus.CREATED)
 @Operation(summary="Upload a profile document", description="Uploads a private document for the authenticated JOB_SEEKER using multipart form data. Requires a bearer token.",requestBody=@io.swagger.v3.oas.annotations.parameters.RequestBody(description="Multipart file part named file.",required=true,content=@io.swagger.v3.oas.annotations.media.Content(mediaType="multipart/form-data",examples=@ExampleObject(name="file",value="resume.pdf"))))
 @ApiResponses({@ApiResponse(responseCode="201",description="Document uploaded; returns its metadata."),@ApiResponse(responseCode="400",description="File is missing, invalid, or request is malformed."),@ApiResponse(responseCode="401",description="A valid bearer token is required."),@ApiResponse(responseCode="403",description="The caller is not permitted to upload documents."),@ApiResponse(responseCode="413",description="File exceeds the configured upload limit."),@ApiResponse(responseCode="503",description="Document storage is unavailable.")})
 public DocumentResponse upload(@RequestPart("file") MultipartFile file){return service.upload(file);}
 @GetMapping("/documents") @Operation(summary="List my documents", description="Returns private documents belonging to the authenticated JOB_SEEKER. Requires a bearer token.") @ApiResponses({@ApiResponse(responseCode="200",description="Returns the caller's document metadata."),@ApiResponse(responseCode="401",description="A valid bearer token is required."),@ApiResponse(responseCode="403",description="The caller is not permitted to access documents."),@ApiResponse(responseCode="503",description="Document storage is unavailable.")}) public List<DocumentResponse> documents(){return service.listOwn();}
 @GetMapping("/documents/{documentId}/download")
 @Operation(summary="Get a document download", description="Returns download details for documentId if it belongs to the authenticated JOB_SEEKER. Requires a bearer token.")
 @Parameter(name="documentId",description="UUID of the document.",required=true)
 @ApiResponses({@ApiResponse(responseCode="200",description="Returns document download details."),@ApiResponse(responseCode="401",description="A valid bearer token is required."),@ApiResponse(responseCode="403",description="The caller is not permitted to download this document."),@ApiResponse(responseCode="404",description="Document was not found."),@ApiResponse(responseCode="503",description="Document storage is unavailable.")})
 public DownloadResponse download(@PathVariable UUID documentId){return service.downloadOwn(documentId);}
 @DeleteMapping("/documents/{documentId}") @ResponseStatus(HttpStatus.NO_CONTENT)
 @Operation(summary="Delete a profile document", description="Deletes documentId if it belongs to the authenticated JOB_SEEKER. Requires a bearer token.")
 @Parameter(name="documentId",description="UUID of the document.",required=true)
 @ApiResponses({@ApiResponse(responseCode="204",description="Document deleted."),@ApiResponse(responseCode="401",description="A valid bearer token is required."),@ApiResponse(responseCode="403",description="The caller is not permitted to delete this document."),@ApiResponse(responseCode="404",description="Document was not found."),@ApiResponse(responseCode="503",description="Document storage is unavailable.")})
 public void delete(@PathVariable UUID documentId){service.deleteOwn(documentId);}
}
