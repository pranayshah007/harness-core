package io.harness.idp.environmentvariable.service;

import io.harness.idp.environmentvariable.beans.dto.EnvironmentVariableDTO;

import java.util.Optional;

public interface EnvironmentVariableService {
    Optional<EnvironmentVariableDTO> findByEnvName(String envName, String accountIdentifier);
}
