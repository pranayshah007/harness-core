package io.harness.service.secretmanager;

import com.google.inject.Inject;
import io.harness.dtos.EnvironmentVariableDTO;
import io.harness.service.environmentvariable.EnvironmentVariableService;

import javax.ws.rs.NotFoundException;
import java.util.Optional;

public class SecretManagerImpl implements SecretManager {
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
