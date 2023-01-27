package io.harness.service.environmentvariable;

import io.harness.dtos.EnvironmentVariableDTO;

import java.util.Optional;

public interface EnvironmentVariableService {
    Optional<EnvironmentVariableDTO> findByEnvName(String envName, String accountIdentifier);
}
