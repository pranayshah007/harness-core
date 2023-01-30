package io.harness.idp.secret.service;

import io.harness.idp.secret.beans.dto.EnvironmentVariableDTO;

import java.util.Optional;

public interface EnvironmentVariableService {
    Optional<EnvironmentVariableDTO> findByEnvName(String envName, String accountIdentifier);
}
