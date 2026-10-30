package com.groupcc2.recruitment.users.service;

import java.util.Locale;
import java.util.List;
import java.util.UUID;

import com.groupcc2.recruitment.users.entity.User;
import com.groupcc2.recruitment.users.entity.UserRole;
import com.groupcc2.recruitment.users.entity.AdminRoleChange;
import com.groupcc2.recruitment.users.repository.UserRepository;
import com.groupcc2.recruitment.users.repository.AdminRoleChangeRepository;
import com.groupcc2.recruitment.security.RlsTransactionContext;
import jakarta.transaction.Transactional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserServiceImpl implements UserService {
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AdminRoleChangeRepository roleChanges;
    private final RlsTransactionContext rlsTransactionContext;

    public UserServiceImpl(UserRepository users, PasswordEncoder passwordEncoder,
                           AdminRoleChangeRepository roleChanges, RlsTransactionContext rlsTransactionContext) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.roleChanges = roleChanges;
        this.rlsTransactionContext = rlsTransactionContext;
    }

    @Override
    @Transactional
    public User register(String email, String rawPassword, UserRole role) {
        if (role != UserRole.JOB_SEEKER) {
            throw new RoleRegistrationNotAllowedException();
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
    public User findOwnById(UUID id) {
        if (id == null) {
            throw new AuthorizationDeniedException();
        }
        rlsTransactionContext.setSelfOnlyUser(id);
        User user = findById(id);
        requireActive(user);
        return user;
    }

    @Override
    @Transactional
    public List<User> findAllForAdministrator(UUID administratorId) {
        requireAdministrator(administratorId);
        return users.findAll();
    }

    @Override
    @Transactional
    public User findForAdministrator(UUID administratorId, UUID id) {
        requireAdministrator(administratorId);
        return findById(id);
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

    @Override
    @Transactional
    public User promoteToAdministrator(UUID administratorId, UUID id) {
        requireAdministrator(administratorId);
        if (administratorId.equals(id)) {
            throw new InvalidRoleChangeException("Administrators cannot change their own role");
        }
        User user = findById(id);
        UserRole previousRole = user.getRole();
        try {
            user.promoteToAdministrator();
        } catch (IllegalStateException exception) {
            throw new InvalidRoleChangeException(exception.getMessage());
        }
        roleChanges.save(new AdminRoleChange(administratorId, id, previousRole, UserRole.ADMIN));
        return user;
    }

    @Override
    @Transactional
    public User promoteToEmployer(UUID administratorId, UUID id) {
        requireAdministrator(administratorId);
        if (administratorId.equals(id)) {
            throw new InvalidRoleChangeException("Administrators cannot change their own role");
        }
        User user = findById(id);
        try {
            user.promoteToEmployer();
        } catch (IllegalStateException exception) {
            throw new InvalidRoleChangeException(exception.getMessage());
        }
        return user;
    }

    private void requireAdministrator(UUID administratorId) {
        if (administratorId == null) {
            throw new AuthorizationDeniedException();
        }
        rlsTransactionContext.setSelfOnlyUser(administratorId);
        User administrator = findById(administratorId);
        requireActive(administrator);
        if (administrator.getRole() != UserRole.ADMIN) {
            throw new AuthorizationDeniedException();
        }
        rlsTransactionContext.setAuthenticatedUser(administratorId, UserRole.ADMIN);
    }

    static String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email must not be blank");
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
