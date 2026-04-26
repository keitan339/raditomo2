package com.raditomo.user.service;

import com.raditomo.user.entity.User;
import com.raditomo.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserAdminServiceTest {

    @Mock UserRepository userRepository;
    @InjectMocks UserAdminService service;

    User existingActive;
    User existingInactive;

    @BeforeEach
    void setup() {
        existingActive = User.builder().id(1L).email("a@example.com").active(true).build();
        existingInactive = User.builder().id(2L).email("b@example.com").active(false).build();
    }

    @Test
    void add_createsNewUserWhenNotFound() {
        when(userRepository.findByEmail("c@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(3L);
            return u;
        });
        UserAdminService.AddResult r = service.add("c@example.com");
        assertThat(r.created()).isTrue();
        assertThat(r.alreadyActive()).isFalse();
        assertThat(r.user().getId()).isEqualTo(3L);
        assertThat(r.user().isActive()).isTrue();
    }

    @Test
    void add_skipsWhenAlreadyActive() {
        when(userRepository.findByEmail("a@example.com")).thenReturn(Optional.of(existingActive));
        UserAdminService.AddResult r = service.add("a@example.com");
        assertThat(r.created()).isFalse();
        assertThat(r.alreadyActive()).isTrue();
        verify(userRepository, never()).save(any());
    }

    @Test
    void add_reactivatesInactiveUser() {
        when(userRepository.findByEmail("b@example.com")).thenReturn(Optional.of(existingInactive));
        when(userRepository.save(existingInactive)).thenReturn(existingInactive);
        UserAdminService.AddResult r = service.add("b@example.com");
        assertThat(r.created()).isFalse();
        assertThat(r.alreadyActive()).isFalse();
        assertThat(existingInactive.isActive()).isTrue();
    }

    @Test
    void remove_returnsNotFoundWhenMissing() {
        when(userRepository.findByEmail("x@example.com")).thenReturn(Optional.empty());
        UserAdminService.RemoveResult r = service.remove("x@example.com");
        assertThat(r.found()).isFalse();
    }

    @Test
    void remove_skipsWhenAlreadyInactive() {
        when(userRepository.findByEmail("b@example.com")).thenReturn(Optional.of(existingInactive));
        UserAdminService.RemoveResult r = service.remove("b@example.com");
        assertThat(r.found()).isTrue();
        assertThat(r.alreadyInactive()).isTrue();
        verify(userRepository, never()).save(any());
    }

    @Test
    void remove_deactivatesActiveUser() {
        when(userRepository.findByEmail("a@example.com")).thenReturn(Optional.of(existingActive));
        when(userRepository.save(existingActive)).thenReturn(existingActive);
        UserAdminService.RemoveResult r = service.remove("a@example.com");
        assertThat(r.found()).isTrue();
        assertThat(r.alreadyInactive()).isFalse();
        assertThat(existingActive.isActive()).isFalse();
    }

    @Test
    void list_returnsAll() {
        when(userRepository.findAll()).thenReturn(List.of(existingActive, existingInactive));
        assertThat(service.list()).hasSize(2);
    }
}
