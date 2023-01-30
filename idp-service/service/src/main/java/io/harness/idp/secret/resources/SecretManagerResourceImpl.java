package io.harness.idp.secret.resources;

import com.google.inject.Inject;
import io.harness.idp.secret.beans.dto.EnvironmentVariableDTO;
import io.harness.idp.secret.resource.SecretManagerResource;
import io.harness.idp.secret.service.EnvironmentVariableService;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;

import javax.ws.rs.NotFoundException;
import java.util.Optional;

@AllArgsConstructor(access = AccessLevel.PACKAGE, onConstructor = @__({ @Inject}))
public class SecretManagerResourceImpl implements SecretManagerResource {
    private EnvironmentVariableService environmentVariableService;

    public String getSecretIdByEnvName(String envName, String accountIdentifier) {
        Optional<EnvironmentVariableDTO> environmentVariableDTOOpt
                = environmentVariableService.findByEnvName(envName, accountIdentifier);
        if (environmentVariableDTOOpt.isEmpty()) {
            throw new NotFoundException("Environment Variable with name " + envName + " not found");
        }
        return environmentVariableDTOOpt.get().getSecretIdentifier();
    }
}
