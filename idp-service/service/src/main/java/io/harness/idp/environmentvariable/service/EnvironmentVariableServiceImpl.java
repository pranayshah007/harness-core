package io.harness.idp.environmentvariable.service;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.harness.beans.entity.EnvironmentVariable;
import io.harness.idp.environmentvariable.beans.EnvironmentVariableDTO;
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
