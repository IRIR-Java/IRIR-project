package com.chuka.irir.service;

import com.chuka.irir.model.User;
import com.chuka.irir.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.stream.Collectors;

/**
 * Custom implementation of Spring Security's {@link UserDetailsService}.
 *
 * Loads user details from the database by email address for authentication.
 * Maps IRIR {@link com.chuka.irir.model.Role} enums to Spring Security
 * {@link GrantedAuthority} objects with the "ROLE_" prefix.
 *
 * <p>This service is used by Spring Security's authentication manager
 * during the login process.</p>
 */
@Service
public class CustomUserDetailsService implements UserDetailsService {

    private static final Logger logger = LoggerFactory.getLogger(CustomUserDetailsService.class);

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Loads a user by email address for Spring Security authentication.
     *
     * @param email the email address entered in the login form
     * @return a Spring Security {@link UserDetails} object
     * @throws UsernameNotFoundException if no user exists with the given email
     */
    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        logger.debug("Attempting to authenticate user: {}", email);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> {
                    logger.warn("Authentication failed — user not found: {}", email);
                    return new UsernameNotFoundException("User not found with email: " + email);
                });

        // Map IRIR roles to Spring Security granted authorities
        Collection<? extends GrantedAuthority> authorities = user.getRoles().stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.name()))
                .collect(Collectors.toSet());

        logger.debug("User '{}' authenticated successfully with roles: {}", email, authorities);

        return new org.springframework.security.core.userdetails.User(
                user.getEmail(),
                user.getPassword(),
                user.isEnabled(),
                true,                    // accountNonExpired
                true,                    // credentialsNonExpired
                user.isAccountNonLocked(),
                authorities
        );
    }
}
