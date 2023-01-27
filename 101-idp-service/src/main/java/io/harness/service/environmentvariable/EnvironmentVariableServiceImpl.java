package io.harness.service.environmentvariable;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.harness.dtos.EnvironmentVariableDTO;
import io.harness.entities.EnvironmentVariable;
import io.harness.mappers.EnvironmentVariableMapper;
import io.harness.repositories.environmentvariable.EnvironmentVariableRepository;
import lombok.AllArgsConstructor;

import java.util.Optional;

@Singleton
@AllArgsConstructor(onConstructor = @__({ @Inject }))
public class EnvironmentVariableServiceImpl implements EnvironmentVariableService {
    @Inject private EnvironmentVariableRepository environmentVariableRepository;

    @Override
    public Optional<EnvironmentVariableDTO> findByEnvName(String envName, String accountIdentifier) {
        Optional<EnvironmentVariable> environmentVariable
                = environmentVariableRepository.findByEnvNameAndAccountIdentifier(envName, accountIdentifier);
        return environmentVariable.map(EnvironmentVariableMapper::toDTO);
    }
}
