package io.harness.idp.service.environmentvariable;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.harness.idp.dtos.EnvironmentVariableDTO;
import io.harness.idp.entities.EnvironmentVariable;
import io.harness.idp.mappers.EnvironmentVariableMapper;
import io.harness.idp.repositories.environmentvariable.EnvironmentVariableRepository;
import lombok.AllArgsConstructor;

import java.util.Optional;

@Singleton
@AllArgsConstructor(onConstructor = @__({ @Inject }))
public class EnvironmentVariableServiceImpl implements EnvironmentVariableService {
    private EnvironmentVariableRepository environmentVariableRepository;

    @Override
    public Optional<EnvironmentVariableDTO> findByEnvName(String envName, String accountIdentifier) {
        Optional<EnvironmentVariable> environmentVariable
                = environmentVariableRepository.findByEnvNameAndAccountIdentifier(envName, accountIdentifier);
        return environmentVariable.map(EnvironmentVariableMapper::toDTO);
    }
}
