package io.harness.idp.secretmanager.service;

public interface SecretManagerService {
    String getSecretIdByEnvName(String envName, String accountIdentifier);
}
