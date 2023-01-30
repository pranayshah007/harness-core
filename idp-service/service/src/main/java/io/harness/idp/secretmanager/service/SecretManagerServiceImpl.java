package io.harness.idp.secretmanager.service;

import com.google.inject.Inject;
import io.harness.idp.environmentvariable.service.EnvironmentVariableService;
import io.harness.idp.environmentvariable.beans.dto.EnvironmentVariableDTO;

import javax.ws.rs.NotFoundException;
import java.util.Optional;

public class SecretManagerServiceImpl implements SecretManagerService {
    @Inject private EnvironmentVariableService environmentVariableService;

    @Override
    public String getSecretIdByEnvName(String envName, String accountIdentifier) {
        Optional<EnvironmentVariableDTO> environmentVariableDTOOpt
                = environmentVariableService.findByEnvName(envName, accountIdentifier);
        if (environmentVariableDTOOpt.isEmpty()) {
            throw new NotFoundException("Environment Variable with name " + envName + " not found");
        }
        return environmentVariableDTOOpt.get().getSecretIdentifier();
    }
}
