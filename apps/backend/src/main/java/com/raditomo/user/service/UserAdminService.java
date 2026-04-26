package com.raditomo.user.service;

import com.raditomo.user.entity.User;
import com.raditomo.user.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * 許可リスト管理。CLI から呼び出す。
 *
 * - add: 既存があれば is_active=true、なければ新規作成
 * - remove: is_active=false（論理削除）
 * - list: 全件
 */
@Service
@RequiredArgsConstructor
public class UserAdminService {

    private final UserRepository userRepository;

    @Transactional
    public AddResult add(String email) {
        Optional<User> existing = userRepository.findByEmail(email);
        if (existing.isPresent()) {
            User u = existing.get();
            if (u.isActive()) {
                return new AddResult(u, false, true);
            }
            u.setActive(true);
            userRepository.save(u);
            return new AddResult(u, false, false);
        }
        User created = userRepository.save(User.builder()
                .email(email)
                .active(true)
                .build());
        return new AddResult(created, true, false);
    }

    @Transactional
    public RemoveResult remove(String email) {
        Optional<User> existing = userRepository.findByEmail(email);
        if (existing.isEmpty()) {
            return new RemoveResult(false, false);
        }
        User u = existing.get();
        if (!u.isActive()) {
            return new RemoveResult(true, true);
        }
        u.setActive(false);
        userRepository.save(u);
        return new RemoveResult(true, false);
    }

    public List<User> list() {
        return userRepository.findAll();
    }

    public record AddResult(User user, boolean created, boolean alreadyActive) {}
    public record RemoveResult(boolean found, boolean alreadyInactive) {}
}
