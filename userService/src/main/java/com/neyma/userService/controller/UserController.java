package com.neyma.userService.controller;

import com.neyma.userService.dto.RegisterRequest;
import com.neyma.userService.dto.UserResponse;
import com.neyma.userService.exception.InvalidCredentialsException;
import com.neyma.userService.exception.UserAlreadyExistsException;
import com.neyma.userService.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/users")
@Tag(name = "User Management", description = "API for user registration and authentication")
public class UserController {

    private final UserService userService;

    @Autowired
    public UserController(UserService userService) {
        this.userService = userService;
    }

    @Operation(summary = "Register a new user", description = "Creates a new user account")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "User created successfully", content = @Content(schema = @Schema(implementation = UserResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid input or validation error"),
            @ApiResponse(responseCode = "409", description = "User already exists")
    })
    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(
            @RequestHeader("Authorization") String authHeader,
            @Valid @RequestBody RegisterRequest request) {
        String[] credentials = extractCredentials(authHeader);
        UserResponse user = userService.register(request.getName(), credentials[0], credentials[1]);
        return new ResponseEntity<>(user, HttpStatus.CREATED);
    }

    @Operation(summary = "Login user", description = "Authenticates a user and returns their details")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Login successful", content = @Content(schema = @Schema(implementation = UserResponse.class))),
            @ApiResponse(responseCode = "401", description = "Invalid credentials")
    })
    @PostMapping("/login")
    public ResponseEntity<UserResponse> login(@RequestHeader("Authorization") String authHeader) {
        String[] credentials = extractCredentials(authHeader);
        UserResponse user = userService.login(credentials[0], credentials[1]);
        return new ResponseEntity<>(user, HttpStatus.OK);
    }

    private String[] extractCredentials(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Basic ")) {
            throw new InvalidCredentialsException("Missing or invalid Authorization header");
        }
        String base64Credentials = authHeader.substring(6);
        byte[] decodedBytes = java.util.Base64.getDecoder().decode(base64Credentials);
        String decodedString = new String(decodedBytes, java.nio.charset.StandardCharsets.UTF_8);
        String[] credentials = decodedString.split(":", 2);
        if (credentials.length != 2) {
            throw new InvalidCredentialsException("Invalid authentication credentials");
        }
        return credentials;
    }

    @Operation(summary = "Search users", description = "Find users by name or email similarity (limit 20)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Users found", content = @Content(schema = @Schema(implementation = UserResponse.class)))
    })
    @GetMapping("/search")
    public ResponseEntity<java.util.List<UserResponse>> searchUsers(@RequestParam String query) {
        java.util.List<UserResponse> users = userService.findUsers(query);
        return new ResponseEntity<>(users, HttpStatus.OK);
    }

    @Operation(summary = "Get user by ID", description = "Retrieves user details by their ID")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "User found", content = @Content(schema = @Schema(implementation = UserResponse.class))),
            @ApiResponse(responseCode = "404", description = "User not found")
    })
    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> getUserById(@PathVariable java.util.UUID id) {
        UserResponse user = userService.getUserById(id);
        return new ResponseEntity<>(user, HttpStatus.OK);
    }

    @ExceptionHandler(UserAlreadyExistsException.class)
    public ResponseEntity<String> handleUserAlreadyExists(UserAlreadyExistsException ex) {
        return new ResponseEntity<>(ex.getMessage(), HttpStatus.CONFLICT);
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<String> handleInvalidCredentials(InvalidCredentialsException ex) {
        return new ResponseEntity<>(ex.getMessage(), HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(com.neyma.userService.exception.UserNotFoundException.class)
    public ResponseEntity<String> handleUserNotFound(com.neyma.userService.exception.UserNotFoundException ex) {
        return new ResponseEntity<>(ex.getMessage(), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(org.springframework.web.HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<String> handleMediaTypeNotSupported(
            org.springframework.web.HttpMediaTypeNotSupportedException ex) {
        return new ResponseEntity<>(
                "Content-Type 'text/plain;charset=UTF-8' is not supported. Please use 'Content-Type: application/json'",
                HttpStatus.UNSUPPORTED_MEDIA_TYPE);
    }

    @ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException.class)
    public ResponseEntity<java.util.Map<String, String>> handleValidationExceptions(
            org.springframework.web.bind.MethodArgumentNotValidException ex) {
        java.util.Map<String, String> errors = new java.util.HashMap<>();
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((org.springframework.validation.FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        return new ResponseEntity<>(errors, HttpStatus.BAD_REQUEST);
    }
}
