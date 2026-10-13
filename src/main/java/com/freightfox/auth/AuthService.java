package com.freightfox.auth;

import com.freightfox.exception.DuplicateResourceException;
import com.freightfox.exception.InvalidInputException;
import com.freightfox.exception.ResourceNotFoundException;
import com.freightfox.security.JwtTokenProvider;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service managing user registration, authentication, and bootstrap initialization.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordValidator passwordValidator;
    private final JwtTokenProvider jwtTokenProvider;

    @Value("${app.bootstrap-admin.enabled:true}")
    private boolean bootstrapAdminEnabled;

    @Value("${app.bootstrap-admin.username:admin}")
    private String bootstrapAdminUsername;

    @Value("${app.bootstrap-admin.email:admin@freightfox.com}")
    private String bootstrapAdminEmail;

    @Value("${app.bootstrap-admin.password:Admin@FreightFox2026!}")
    private String bootstrapAdminPassword;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            PasswordValidator passwordValidator,
            JwtTokenProvider jwtTokenProvider
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.passwordValidator = passwordValidator;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @PostConstruct
    @Transactional
    public void initBootstrapAdmin() {
        if (!bootstrapAdminEnabled) {
            return;
        }

        if (!userRepository.existsByUsername(bootstrapAdminUsername)) {
            log.info("Bootstrapping initial administrator account: {}", bootstrapAdminUsername);
            UserEntity admin = new UserEntity(
                    bootstrapAdminUsername,
                    bootstrapAdminEmail,
                    passwordEncoder.encode(bootstrapAdminPassword),
                    UserRole.ROLE_ADMIN
            );
            userRepository.save(admin);
            log.info("Bootstrap administrator account created successfully.");
        }
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String trimmedUsername = request.username().trim();
        String normalizedEmail = request.email().trim().toLowerCase();

        if (userRepository.existsByUsername(trimmedUsername)) {
            throw new DuplicateResourceException("Username '" + trimmedUsername + "' is already registered.");
        }
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new DuplicateResourceException("Email '" + normalizedEmail + "' is already registered.");
        }

        PasswordValidator.ValidationResult passValidation = passwordValidator.validate(request.password());
        if (!passValidation.valid()) {
            throw new InvalidInputException(String.join(" ", passValidation.errorMessages()));
        }

        UserEntity user = new UserEntity(
                trimmedUsername,
                normalizedEmail,
                passwordEncoder.encode(request.password()),
                UserRole.ROLE_USER
        );

        UserEntity saved = userRepository.save(user);
        String token = jwtTokenProvider.generateToken(saved.getUsername(), saved.getRole().name());

        return AuthResponse.of(
                token,
                jwtTokenProvider.getTtlSeconds(),
                UserDto.fromEntity(saved),
                "User registered successfully."
        );
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String identifier = request.usernameOrEmail().trim();

        UserEntity user = userRepository.findByUsernameOrEmail(identifier, identifier.toLowerCase())
                .orElseThrow(() -> new BadCredentialsException("Invalid username or password."));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid username or password.");
        }

        if (!user.isEnabled()) {
            throw new BadCredentialsException("Account is disabled. Please contact administrator.");
        }

        String token = jwtTokenProvider.generateToken(user.getUsername(), user.getRole().name());

        return AuthResponse.of(
                token,
                jwtTokenProvider.getTtlSeconds(),
                UserDto.fromEntity(user),
                "Authentication successful."
        );
    }

    @Transactional(readOnly = true)
    public UserDto getCurrentUser(String username) {
        UserEntity user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));
        return UserDto.fromEntity(user);
    }
}
