package io.github.hello09x.fakeplayer.core.storage;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.github.hello09x.fakeplayer.core.Main;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.SecureRandom;
import java.util.logging.Logger;

@Singleton
public class SecretStore {

    private final static Logger log = Main.getInstance().getLogger();

    private final static char[] PASSWORD_CHARS =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789".toCharArray();

    private final static int PASSWORD_LENGTH = 20;

    private final File file;

    private YamlConfiguration data = new YamlConfiguration();

    private final String authmePassword;

    @Inject
    public SecretStore() {
        var folder = Main.getInstance().getDataFolder();
        if (!folder.exists() && !folder.mkdirs()) {
            throw new IllegalStateException("Could not create data folder " + folder.getAbsolutePath());
        }

        this.file = new File(folder, "secrets.yml");
        if (file.exists()) {
            try {
                data.load(file);
            } catch (Exception e) {
                // 密码变化会导致已注册的假人无法登录, 因此先备份再生成, 并在控制台告警
                var backup = new File(folder, file.getName() + ".corrupt-" + System.currentTimeMillis());
                try {
                    Files.move(file.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException moveError) {
                    throw new IllegalStateException("Could not read or back up secrets.yml, refusing to overwrite it", e);
                }
                log.severe("Could not load secrets.yml (%s); the broken file was moved to %s. "
                                   .formatted(e.getMessage(), backup.getName())
                                   + "A new AuthMe password was generated, existing fake players may need to re-register");
                data = new YamlConfiguration();
            }
        }

        var password = data.getString("authme-password");
        if (password == null || password.isBlank()) {
            if (file.exists()) {
                log.warning("secrets.yml does not contain a valid authme-password; generating a new one. "
                                    + "Existing fake players may need to re-register");
            }
            password = generatePassword();
            data.set("authme-password", password);
            this.save();
        }
        this.authmePassword = password;
    }

    private static @NotNull String generatePassword() {
        var random = new SecureRandom();
        var password = new char[PASSWORD_LENGTH];
        for (int i = 0; i < password.length; i++) {
            password[i] = PASSWORD_CHARS[random.nextInt(PASSWORD_CHARS.length)];
        }
        return new String(password);
    }

    private void save() {
        var tmp = new File(file.getParentFile(), file.getName() + ".tmp");
        try {
            data.save(tmp);
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not save secrets.yml", e);
        }
    }

    public @NotNull String getAuthmePassword() {
        return authmePassword;
    }

}
