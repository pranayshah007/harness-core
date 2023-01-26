package io.harness.idp.service.secretmanager;

public interface SecretManager {
    String getSecretIdByEnvName(String envName, String accountIdentifier);
}
