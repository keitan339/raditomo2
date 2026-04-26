package com.raditomo.cli;

import com.raditomo.user.entity.User;
import com.raditomo.user.service.UserAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.util.List;

@Component
@Command(name = "users",
        description = "許可リスト管理",
        subcommands = {
                UsersCommand.AddCommand.class,
                UsersCommand.RemoveCommand.class,
                UsersCommand.ListCommand.class
        })
public class UsersCommand implements Runnable {

    @Override
    public void run() {
        System.out.println("Subcommand required: add | remove | list");
    }

    @Component
    @Command(name = "add", description = "ユーザーを許可リストに追加（既に無効化されていれば再有効化）")
    @RequiredArgsConstructor
    static class AddCommand implements Runnable {

        private final UserAdminService userAdminService;

        @Parameters(index = "0", description = "Email")
        String email;

        @Override
        public void run() {
            UserAdminService.AddResult result = userAdminService.add(email);
            if (result.alreadyActive()) {
                System.out.printf("[SKIP] %s は既に許可済み (id=%d)%n", email, result.user().getId());
            } else if (result.created()) {
                System.out.printf("[ADD]  %s を新規追加 (id=%d)%n", email, result.user().getId());
            } else {
                System.out.printf("[ADD]  %s を再有効化 (id=%d)%n", email, result.user().getId());
            }
        }
    }

    @Component
    @Command(name = "remove", description = "ユーザーを許可リストから外す（論理削除）")
    @RequiredArgsConstructor
    static class RemoveCommand implements Runnable {

        private final UserAdminService userAdminService;

        @Parameters(index = "0", description = "Email")
        String email;

        @Override
        public void run() {
            UserAdminService.RemoveResult result = userAdminService.remove(email);
            if (!result.found()) {
                System.out.printf("[SKIP] %s は登録なし%n", email);
            } else if (result.alreadyInactive()) {
                System.out.printf("[SKIP] %s は既に無効化済み%n", email);
            } else {
                System.out.printf("[REMOVE] %s を無効化%n", email);
            }
        }
    }

    @Component
    @Command(name = "list", description = "ユーザー一覧表示")
    @RequiredArgsConstructor
    static class ListCommand implements Runnable {

        private final UserAdminService userAdminService;

        @Override
        public void run() {
            List<User> users = userAdminService.list();
            if (users.isEmpty()) {
                System.out.println("(no users)");
                return;
            }
            System.out.printf("%-6s %-40s %-20s %-8s%n", "ID", "EMAIL", "NAME", "ACTIVE");
            for (User u : users) {
                System.out.printf("%-6d %-40s %-20s %-8s%n",
                        u.getId(), u.getEmail(),
                        u.getName() == null ? "-" : u.getName(),
                        u.isActive() ? "yes" : "no");
            }
        }
    }
}
