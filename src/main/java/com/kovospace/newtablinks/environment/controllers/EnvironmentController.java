package com.kovospace.newtablinks.environment.controllers;

import com.kovospace.newtablinks.common.exceptions.ApiErrorResponseDto;
import com.kovospace.newtablinks.environment.dtos.EnvironmentDto;
import com.kovospace.newtablinks.environment.dtos.EnvironmentSaveRequestDto;
import com.kovospace.newtablinks.environment.services.EnvironmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP endpoints for environments.
 *
 * @since 0.0.1
 */
@RestController
@RequestMapping("/api/v1/environments")
@Tag(name = "Environments", description = "Workspaces grouping a user's link groups")
public class EnvironmentController {

    private final EnvironmentService environmentService;

    /**
     * Creates the controller.
     *
     * @param environmentService service holding the business logic
     */
    public EnvironmentController(final EnvironmentService environmentService) {
        this.environmentService = environmentService;
    }

    /**
     * Lists a user's environments in display order.
     *
     * @param ownerId identifier of the owning user
     * @return the owner's environments
     */
    @GetMapping
    @Operation(summary = "List a user's environments in display order")
    @ApiResponse(responseCode = "200", description = "The environments, possibly empty")
    public List<EnvironmentDto> listEnvironmentsOfOwner(@RequestParam final UUID ownerId) {
        return environmentService.findEnvironmentsByOwner(ownerId);
    }

    /**
     * Returns a single environment.
     *
     * @param environmentId identifier of the environment
     * @return the environment
     */
    @GetMapping("/{environmentId}")
    @Operation(summary = "Return a single environment")
    @ApiResponse(responseCode = "200", description = "The environment")
    @ApiResponse(responseCode = "404", description = "No environment has that identifier",
            content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    public EnvironmentDto getEnvironment(@PathVariable final UUID environmentId) {
        return environmentService.findEnvironmentById(environmentId);
    }

    /**
     * Creates an environment, appended after the owner's existing ones.
     *
     * @param saveRequest the environment to create
     * @return the created environment
     */
    @PostMapping
    @Operation(summary = "Create an environment, appended after the owner's existing ones")
    @ApiResponse(responseCode = "201", description = "The created environment")
    @ApiResponse(responseCode = "400", description = "The request body failed validation",
            content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    @ApiResponse(responseCode = "404", description = "The owning user does not exist",
            content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    public ResponseEntity<EnvironmentDto> createEnvironment(
            @Valid @RequestBody final EnvironmentSaveRequestDto saveRequest) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(environmentService.createEnvironment(saveRequest));
    }

    /**
     * Renames an environment.
     *
     * @param environmentId identifier of the environment to update
     * @param saveRequest   the values to store
     * @return the updated environment
     */
    @PutMapping("/{environmentId}")
    @Operation(summary = "Rename an environment")
    @ApiResponse(responseCode = "200", description = "The updated environment")
    @ApiResponse(responseCode = "400", description = "The request body failed validation",
            content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    @ApiResponse(responseCode = "404", description = "No environment has that identifier",
            content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    public EnvironmentDto updateEnvironment(
            @PathVariable final UUID environmentId,
            @Valid @RequestBody final EnvironmentSaveRequestDto saveRequest) {

        return environmentService.updateEnvironment(environmentId, saveRequest);
    }

    /**
     * Deletes an environment.
     *
     * @param environmentId identifier of the environment to delete
     * @return an empty response
     */
    @DeleteMapping("/{environmentId}")
    @Operation(summary = "Delete an environment")
    @ApiResponse(responseCode = "204", description = "The environment was deleted")
    @ApiResponse(responseCode = "404", description = "No environment has that identifier",
            content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    public ResponseEntity<Void> deleteEnvironment(@PathVariable final UUID environmentId) {
        environmentService.deleteEnvironment(environmentId);
        return ResponseEntity.noContent().build();
    }
}
