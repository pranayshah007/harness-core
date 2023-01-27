package io.harness.service.secretmanager;

public interface SecretManager {
    String getSecretIdByEnvName(String envName, String accountIdentifier);
}
