package com.groupcc2.recruitment.users.service;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

import com.groupcc2.recruitment.users.entity.AccountStatus;
import com.groupcc2.recruitment.users.entity.User;
import com.groupcc2.recruitment.users.entity.UserRole;
import com.groupcc2.recruitment.users.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class UserServiceTest {
    private final UserRepository repository = mock(UserRepository.class);
    private final UserServiceImpl service = new UserServiceImpl(repository, new BCryptPasswordEncoder());

    @Test
    void normalizesEmailAndStoresOnlyBcryptHash() {
        when(repository.existsByEmail("person@example.com")).thenReturn(false);
        when(repository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User user = service.register(" Person@Example.COM ", "correct horse battery", UserRole.JOB_SEEKER);

        assertThat(user.getEmail()).isEqualTo("person@example.com");
        assertThat(user.passwordHashForAuthentication()).startsWith("$2");
        assertThat(user.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
        verify(repository).existsByEmail("person@example.com");
    }

    @Test
    void rejectsAdministratorRegistrationAndDuplicateEmail() {
        assertThatThrownBy(() -> service.register("admin@example.com", "correct horse battery", UserRole.ADMIN))
                .isInstanceOf(AdminRegistrationNotAllowedException.class);

        when(repository.existsByEmail("person@example.com")).thenReturn(true);
        assertThatThrownBy(() -> service.register("person@example.com", "correct horse battery", UserRole.EMPLOYER))
                .isInstanceOf(DuplicateEmailException.class);
    }

    @Test
    void seedCreatesAdministratorAndIsIdempotentForThatAdministrator() {
        when(repository.findByEmail("admin@example.com")).thenReturn(Optional.empty());
        when(repository.existsByEmail("admin@example.com")).thenReturn(false);
        when(repository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User administrator = service.seedAdministrator(" Admin@Example.COM ", "correct horse battery");

        assertThat(administrator.getEmail()).isEqualTo("admin@example.com");
        assertThat(administrator.getRole()).isEqualTo(UserRole.ADMIN);
        assertThat(administrator.passwordHashForAuthentication()).startsWith("$2");

        when(repository.findByEmail("admin@example.com")).thenReturn(Optional.of(administrator));
        assertThat(service.seedAdministrator("admin@example.com", "another password")).isSameAs(administrator);
        verify(repository, times(1)).save(any(User.class));
    }

    @Test
    void seedRefusesToUseAnExistingNonAdministratorEmail() {
        User employer = new User("admin@example.com", "hash", UserRole.EMPLOYER);
        when(repository.findByEmail("admin@example.com")).thenReturn(Optional.of(employer));

        assertThatThrownBy(() -> service.seedAdministrator("admin@example.com", "correct horse battery"))
                .isInstanceOf(DuplicateEmailException.class);
        verify(repository, never()).save(any(User.class));
    }

    @Test
    void suspendedAndRemovedUsersCannotAuthenticate() {
        User user = new User("person@example.com", "hash", UserRole.JOB_SEEKER);
        User administrator = new User("admin@example.com", "hash", UserRole.ADMIN);
        when(repository.findById(administrator.getId())).thenReturn(Optional.of(administrator));
        when(repository.findById(user.getId())).thenReturn(Optional.of(user));
        when(repository.findByEmail("person@example.com")).thenReturn(Optional.of(user));

        service.suspend(administrator.getId(), user.getId());
        assertThatThrownBy(() -> service.findForAuthentication("PERSON@example.com"))
                .isInstanceOf(InactiveAccountException.class);

        service.remove(administrator.getId(), user.getId());
        assertThat(user.getAccountStatus()).isEqualTo(AccountStatus.REMOVED);
        assertThatThrownBy(() -> service.requireActive(user)).isInstanceOf(InactiveAccountException.class);
    }

    @Test
    void missingUserIsNotSilentlyAccepted() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.findById(id)).isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void administratorCanListAndReadUsersThroughTheServiceContract() {
        User administrator = new User("admin@example.com", "hash", UserRole.ADMIN);
        User user = new User("person@example.com", "hash", UserRole.JOB_SEEKER);
        when(repository.findById(administrator.getId())).thenReturn(Optional.of(administrator));
        when(repository.findAll()).thenReturn(List.of(administrator, user));
        when(repository.findById(user.getId())).thenReturn(Optional.of(user));

        assertThat(service.findAllForAdministrator(administrator.getId())).containsExactly(administrator, user);
        assertThat(service.findForAdministrator(administrator.getId(), user.getId())).isSameAs(user);
        verify(repository).findAll();
    }

    @Test
    void nonAdministratorCannotChangeAccountState() {
        User user = new User("person@example.com", "hash", UserRole.JOB_SEEKER);
        when(repository.findById(user.getId())).thenReturn(Optional.of(user));
        assertThatThrownBy(() -> service.suspend(user.getId(), user.getId()))
                .isInstanceOf(AuthorizationDeniedException.class);
    }
}
