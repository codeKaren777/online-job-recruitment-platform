package com.groupcc2.recruitment.users.service;

import java.util.Locale;
import java.util.UUID;

import com.groupcc2.recruitment.users.entity.User;
import com.groupcc2.recruitment.users.entity.UserRole;
import com.groupcc2.recruitment.users.repository.UserRepository;
import jakarta.transaction.Transactional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserServiceImpl implements UserService {
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;

    public UserServiceImpl(UserRepository users, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public User register(String email, String rawPassword, UserRole role) {
        if (role == null || role == UserRole.ADMIN) {
            throw new AdminRegistrationNotAllowedException();
        }
        String normalizedEmail = normalizeEmail(email);
        if (users.existsByEmail(normalizedEmail)) {
            throw new DuplicateEmailException();
        }
        String passwordHash = passwordEncoder.encode(rawPassword);
        if (!(passwordEncoder instanceof BCryptPasswordEncoder)
                || !passwordHash.matches("^\\$2[aby]\\$\\d{2}\\$.{53}$")) {
            throw new IllegalStateException("Only BCrypt password hashes are supported");
        }
        try {
            return users.save(new User(normalizedEmail, passwordHash, role));
        } catch (DataIntegrityViolationException exception) {
            throw new DuplicateEmailException();
        }
    }

    @Override
    @Transactional
    public User findById(UUID id) {
        return users.findById(id).orElseThrow(UserNotFoundException::new);
    }

    @Override
    @Transactional
    public User findByNormalizedEmail(String email) {
        return users.findByEmail(normalizeEmail(email)).orElseThrow(UserNotFoundException::new);
    }

    @Override
    @Transactional
    public User findForAuthentication(String email) {
        User user = findByNormalizedEmail(email);
        requireActive(user);
        return user;
    }

    @Override
    public void requireActive(User user) {
        if (!user.canAuthenticate()) {
            throw new InactiveAccountException();
        }
    }

    @Override
    @Transactional
    public void suspend(UUID administratorId, UUID id) {
        requireAdministrator(administratorId);
        User user = findById(id);
        user.suspend();
    }

    @Override
    @Transactional
    public void remove(UUID administratorId, UUID id) {
        requireAdministrator(administratorId);
        User user = findById(id);
        user.remove();
    }

    private void requireAdministrator(UUID administratorId) {
        if (findById(administratorId).getRole() != UserRole.ADMIN) {
            throw new AuthorizationDeniedException();
        }
    }

    static String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email must not be blank");
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
