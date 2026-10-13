package com.freightfox.auth;

import com.freightfox.config.DataSourceConfig;
import com.freightfox.exception.DuplicateResourceException;
import com.freightfox.exception.InvalidInputException;
import com.freightfox.exception.ResourceNotFoundException;
import com.freightfox.security.JwtTokenProvider;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Service managing user registration, authentication, and bootstrap initialization.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    public static final String INSECURE_DEFAULT_ADMIN_PASSWORD = "Admin@FreightFox2026!";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordValidator passwordValidator;
    private final JwtTokenProvider jwtTokenProvider;
    private final Environment environment;

    @Value("${app.bootstrap-admin.enabled:true}")
    private boolean bootstrapAdminEnabled;

    @Value("${app.bootstrap-admin.username:admin}")
    private String bootstrapAdminUsername;

    @Value("${app.bootstrap-admin.email:admin@freightfox.com}")
    private String bootstrapAdminEmail;

    @Value("${app.bootstrap-admin.password:}")
    private String bootstrapAdminPassword;

    @Autowired
    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            PasswordValidator passwordValidator,
            JwtTokenProvider jwtTokenProvider,
            Environment environment
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.passwordValidator = passwordValidator;
        this.jwtTokenProvider = jwtTokenProvider;
        this.environment = environment;
    }

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            PasswordValidator passwordValidator,
            JwtTokenProvider jwtTokenProvider
    ) {
        this(userRepository, passwordEncoder, passwordValidator, jwtTokenProvider, null);
    }

    @PostConstruct
    @Transactional
    public void initBootstrapAdmin() {
        if (!bootstrapAdminEnabled) {
            log.info("Bootstrap administrator provisioning is disabled.");
            return;
        }

        boolean isProd = isProduction();

        // 1. Guard against known default password configured in production
        if (isProd && StringUtils.hasText(bootstrapAdminPassword) &&
                INSECURE_DEFAULT_ADMIN_PASSWORD.equals(bootstrapAdminPassword.trim())) {
            throw new IllegalStateException(
                    "Production environment ('prod') must not use the publicly known default bootstrap administrator password. " +
                    "Please configure a unique, secure 'BOOTSTRAP_ADMIN_PASSWORD' in your environment variables."
            );
        }

        // 2. Preserve existing account credentials and privileges if account already exists
        if (userRepository.existsByUsername(bootstrapAdminUsername)) {
            log.info("Bootstrap administrator account '{}' already exists. Preserving existing account credentials and privileges.", bootstrapAdminUsername);
            return;
        }

        if (userRepository.existsByEmail(bootstrapAdminEmail)) {
            log.warn("An account with email '{}' already exists under a different username. Skipping bootstrap administrator creation to prevent conflicts.", bootstrapAdminEmail);
            return;
        }

        // 3. Administrator account does not exist. We must provision it.
        if (isProd && !StringUtils.hasText(bootstrapAdminPassword)) {
            throw new IllegalStateException(
                    "Production environment ('prod') requires a valid, explicit 'BOOTSTRAP_ADMIN_PASSWORD' to create the initial administrator account. " +
                    "Missing or blank password configured."
            );
        }

        String passwordToUse = StringUtils.hasText(bootstrapAdminPassword)
                ? bootstrapAdminPassword.trim()
                : INSECURE_DEFAULT_ADMIN_PASSWORD;

        PasswordValidator.ValidationResult passValidation = passwordValidator.validate(passwordToUse);
        if (!passValidation.valid()) {
            throw new IllegalStateException("Bootstrap administrator password does not meet password policy requirements: " +
                    String.join(", ", passValidation.errorMessages()));
        }

        log.info("Bootstrapping initial administrator account: {}", bootstrapAdminUsername);
        UserEntity admin = new UserEntity(
                bootstrapAdminUsername,
                bootstrapAdminEmail,
                passwordEncoder.encode(passwordToUse),
                UserRole.ROLE_ADMIN
        );
        userRepository.save(admin);
        log.info("Bootstrap administrator account created successfully.");
    }

    private boolean isProduction() {
        if (environment != null) {
            for (String profile : environment.getActiveProfiles()) {
                if ("prod".equalsIgnoreCase(profile) || profile.toLowerCase().contains("prod")) {
                    return true;
                }
            }
        }
        return DataSourceConfig.isProduction((String) null);
    }

    void setBootstrapAdminProperties(boolean enabled, String username, String email, String password) {
        this.bootstrapAdminEnabled = enabled;
        this.bootstrapAdminUsername = username;
        this.bootstrapAdminEmail = email;
        this.bootstrapAdminPassword = password;
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
