package com.securevault.config;

import com.securevault.user.User;
import com.securevault.user.UserRepository;
import com.securevault.user.UserService;
import com.securevault.user.dto.UserRegisterRequest;
import com.securevault.vault.Category;
import com.securevault.vault.CredentialRepository;
import com.securevault.vault.CredentialService;
import com.securevault.vault.dto.CredentialCreateRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Local-dev-only seed data for S4.5's pagination/sorting/filtering demo (P4.5/M-34) — never runs
 * under any other profile, and @Profile("local") is an explicit allow-list, not a "!prod"
 * deny-list, so a new profile added later doesn't accidentally get seeded by default. Idempotent:
 * re-running with the seed user already at target count is a no-op, so restarting the app locally
 * never duplicates data. Goes through UserService/CredentialService, the same code path a real
 * request uses, not direct repository saves — so seeded rows get real password hashing, encryption,
 * and strength scoring exactly like production data would.
 */
@Component
@Profile("local")
@RequiredArgsConstructor
@Slf4j
public class DevDataSeeder implements CommandLineRunner {

    private static final String SEED_EMAIL = "seed.user@securevault.local";
    private static final String SEED_PASSWORD = "SeedUser123!";
    private static final int TARGET_CREDENTIAL_COUNT = 50;

    private final UserRepository userRepository;
    private final UserService userService;
    private final CredentialService credentialService;
    private final CredentialRepository credentialRepository;

    @Override
    public void run(String... args) {
        User seedUser = userRepository.findByEmail(SEED_EMAIL).orElseGet(this::createSeedUser);

        int existing = credentialRepository.findByUserIdAndDeletedFalse(seedUser.getId()).size();
        if (existing >= TARGET_CREDENTIAL_COUNT) {
            log.info(
                    "Dev seed data already present ({} credentials for {}) — skipping",
                    existing,
                    SEED_EMAIL);
            return;
        }

        int toCreate = TARGET_CREDENTIAL_COUNT - existing;
        log.info("Seeding {} dev credentials for {}", toCreate, SEED_EMAIL);
        Category[] categories = Category.values();
        for (int i = 0; i < toCreate; i++) {
            int n = existing + i + 1;
            CredentialCreateRequest request =
                    new CredentialCreateRequest(
                            "Seed Site " + n,
                            "seeduser" + n,
                            "SeedP@ssw0rd" + n,
                            "https://example" + n + ".test",
                            "Seeded for S4.5 pagination/sorting/filtering testing",
                            categories[i % categories.length]);
            credentialService.create(seedUser.getId(), request);
        }
        log.info(
                "Dev seed data complete: {} total credentials for {}",
                TARGET_CREDENTIAL_COUNT,
                SEED_EMAIL);
    }

    private User createSeedUser() {
        userService.register(new UserRegisterRequest("Seed User", SEED_EMAIL, SEED_PASSWORD));
        log.info("Created dev seed user {}", SEED_EMAIL);
        return userRepository.findByEmail(SEED_EMAIL).orElseThrow();
    }
}
