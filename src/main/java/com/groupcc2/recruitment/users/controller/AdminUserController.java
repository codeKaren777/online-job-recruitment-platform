package com.groupcc2.recruitment.users.controller;

import com.groupcc2.recruitment.users.dto.AssignAdminRoleRequest;
import com.groupcc2.recruitment.users.dto.CreateAccountRequest;
import com.groupcc2.recruitment.users.dto.UserResponse;
import com.groupcc2.recruitment.users.service.UserService;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Users", description = "User accounts, employer upgrade requests, and administrator account operations")
@SecurityRequirement(name = "bearerAuth")
@ApiResponses({
    @ApiResponse(responseCode = "401", description = "A valid bearer token is required"),
    @ApiResponse(responseCode = "403", description = "The caller must have the ADMIN role")
})
public class AdminUserController {
    private final UserService users;

    public AdminUserController(UserService users) {
        this.users = users;
    }

    @GetMapping
    @Operation(summary = "List users", description = "Returns user accounts visible to the authenticated administrator. Requires an ADMIN bearer token.")
    @ApiResponses({@ApiResponse(responseCode="200",description="Returns user account details."),@ApiResponse(responseCode="401",description="A valid bearer token is required."),@ApiResponse(responseCode="403",description="The caller must have the ADMIN role.")})
    public List<UserResponse> list(@AuthenticationPrincipal Jwt principal) {
        UUID administratorId = userId(principal);
        return users.findAllForAdministrator(administratorId).stream().map(UserResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a job seeker or employer account",
            description = "Creates an active account for the selected role. Only JOB_SEEKER and EMPLOYER are allowed; ADMIN accounts must use the role-assignment workflow.")
    @ApiResponses({
        @ApiResponse(responseCode="201", description="The account was created."),
        @ApiResponse(responseCode="400", description="The email, password, or role is invalid."),
        @ApiResponse(responseCode="401", description="A valid bearer token is required."),
        @ApiResponse(responseCode="403", description="The caller must have the ADMIN role."),
        @ApiResponse(responseCode="409", description="The email is already registered.")
    })
    public UserResponse create(
            @AuthenticationPrincipal Jwt principal,
            @Valid @RequestBody CreateAccountRequest request) {
        return UserResponse.from(users.createAccount(userId(principal),
                request.email(), request.password(), request.role()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a user by ID", description = "Returns the account identified by id. Requires an ADMIN bearer token.")
    @Parameter(name="id",description="UUID of the user account.",required=true)
    @ApiResponses({@ApiResponse(responseCode="200",description="Returns the user account."),@ApiResponse(responseCode="401",description="A valid bearer token is required."),@ApiResponse(responseCode="403",description="The caller must have the ADMIN role."),@ApiResponse(responseCode="404",description="User was not found.")})
    public UserResponse get(@AuthenticationPrincipal Jwt principal, @PathVariable UUID id) {
        return UserResponse.from(users.findForAdministrator(userId(principal), id));
    }

    @PatchMapping("/{id}/suspend")
    @Operation(summary = "Suspend a user account", description = "Suspends the account identified by id. Requires an ADMIN bearer token.")
    @Parameter(name="id",description="UUID of the user account.",required=true)
    @ApiResponses({@ApiResponse(responseCode="200",description="Returns the updated user account."),@ApiResponse(responseCode="401",description="A valid bearer token is required."),@ApiResponse(responseCode="403",description="The caller must have the ADMIN role."),@ApiResponse(responseCode="404",description="User was not found.")})
    public UserResponse suspend(@AuthenticationPrincipal Jwt principal, @PathVariable UUID id) {
        users.suspend(userId(principal), id);
        return UserResponse.from(users.findForAdministrator(userId(principal), id));
    }

    @PatchMapping("/{id}/remove")
    @Operation(summary = "Remove a user account", description = "Removes the account identified by id. Requires an ADMIN bearer token.")
    @Parameter(name="id",description="UUID of the user account.",required=true)
    @ApiResponses({@ApiResponse(responseCode="200",description="Returns the resulting user account representation."),@ApiResponse(responseCode="401",description="A valid bearer token is required."),@ApiResponse(responseCode="403",description="The caller must have the ADMIN role."),@ApiResponse(responseCode="404",description="User was not found.")})
    public UserResponse remove(@AuthenticationPrincipal Jwt principal, @PathVariable UUID id) {
        users.remove(userId(principal), id);
        return UserResponse.from(users.findForAdministrator(userId(principal), id));
    }

    @PatchMapping("/{id}/role")
    @Operation(summary = "Assign administrator role", description = "Promotes the user identified by id to administrator. Requires an ADMIN bearer token.",requestBody=@io.swagger.v3.oas.annotations.parameters.RequestBody(description="The only supported role value is ADMIN.",required=true,content=@io.swagger.v3.oas.annotations.media.Content(examples=@ExampleObject(value="{\"role\":\"ADMIN\"}"))))
    @Parameter(name="id",description="UUID of the user account.",required=true)
    @ApiResponses({@ApiResponse(responseCode="200",description="Returns the updated user account."),@ApiResponse(responseCode="400",description="Request body is malformed or role is not ADMIN."),@ApiResponse(responseCode="401",description="A valid bearer token is required."),@ApiResponse(responseCode="403",description="The caller must have the ADMIN role."),@ApiResponse(responseCode="404",description="User was not found.")})
    public UserResponse assignRole(
            @AuthenticationPrincipal Jwt principal,
            @PathVariable UUID id,
            @Valid @RequestBody AssignAdminRoleRequest request) {
        return UserResponse.from(users.promoteToAdministrator(userId(principal), id));
    }

    private static UUID userId(Jwt principal) {
        return UUID.fromString(principal.getSubject());
    }
}
