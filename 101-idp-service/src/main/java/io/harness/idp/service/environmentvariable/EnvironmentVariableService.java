package io.harness.idp.service.environmentvariable;

import io.harness.idp.dtos.EnvironmentVariableDTO;

import java.util.Optional;

public interface EnvironmentVariableService {
    Optional<EnvironmentVariableDTO> findByEnvName(String envName, String accountIdentifier);
}
