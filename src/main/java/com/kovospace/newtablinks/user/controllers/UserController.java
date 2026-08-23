package com.kovospace.newtablinks.user.controllers;

import com.kovospace.newtablinks.common.exceptions.ApiErrorResponseDto;
import com.kovospace.newtablinks.user.dtos.UserDto;
import com.kovospace.newtablinks.user.dtos.UserSaveRequestDto;
import com.kovospace.newtablinks.user.services.UserService;
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
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP endpoints for users.
 *
 * @since 0.0.1
 */
@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "Users", description = "Owners of the stored link hierarchy")
public class UserController {

    private final UserService userService;

    /**
     * Creates the controller.
     *
     * @param userService service holding the business logic
     */
    public UserController(final UserService userService) {
        this.userService = userService;
    }

    /**
     * Lists every user.
     *
     * @return all users
     */
    @GetMapping
    @Operation(summary = "List every user")
    @ApiResponse(responseCode = "200", description = "The users, possibly empty")
    public List<UserDto> listUsers() {
        return userService.findAllUsers();
    }

    /**
     * Returns a single user.
     *
     * @param userId identifier of the user
     * @return the user
     */
    @GetMapping("/{userId}")
    @Operation(summary = "Return a single user")
    @ApiResponse(responseCode = "200", description = "The user")
    @ApiResponse(responseCode = "404", description = "No user has that identifier",
            content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    public UserDto getUser(@PathVariable final UUID userId) {
        return userService.findUserById(userId);
    }

    /**
     * Creates a user.
     *
     * @param saveRequest the user to create
     * @return the created user
     */
    @PostMapping
    @Operation(summary = "Create a user")
    @ApiResponse(responseCode = "201", description = "The created user")
    @ApiResponse(responseCode = "400", description = "The request body failed validation",
            content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    public ResponseEntity<UserDto> createUser(@Valid @RequestBody final UserSaveRequestDto saveRequest) {
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.createUser(saveRequest));
    }

    /**
     * Replaces the changeable fields of a user.
     *
     * @param userId      identifier of the user to update
     * @param saveRequest the values to store
     * @return the updated user
     */
    @PutMapping("/{userId}")
    @Operation(summary = "Replace the changeable fields of a user")
    @ApiResponse(responseCode = "200", description = "The updated user")
    @ApiResponse(responseCode = "400", description = "The request body failed validation",
            content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    @ApiResponse(responseCode = "404", description = "No user has that identifier",
            content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    public UserDto updateUser(
            @PathVariable final UUID userId,
            @Valid @RequestBody final UserSaveRequestDto saveRequest) {

        return userService.updateUser(userId, saveRequest);
    }

    /**
     * Deletes a user.
     *
     * @param userId identifier of the user to delete
     * @return an empty response
     */
    @DeleteMapping("/{userId}")
    @Operation(summary = "Delete a user")
    @ApiResponse(responseCode = "204", description = "The user was deleted")
    @ApiResponse(responseCode = "404", description = "No user has that identifier",
            content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
    public ResponseEntity<Void> deleteUser(@PathVariable final UUID userId) {
        userService.deleteUser(userId);
        return ResponseEntity.noContent().build();
    }
}
