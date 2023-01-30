package io.harness.idp.secret.service;

import com.google.inject.Inject;
import io.harness.idp.secret.beans.dto.EnvironmentVariableDTO;
import io.harness.idp.secret.beans.entity.EnvironmentVariable;
import io.harness.idp.secret.repositories.EnvironmentVariableRepository;
import io.harness.idp.secret.mappers.EnvironmentVariableMapper;

import java.util.Optional;

public class EnvironmentVariableServiceImpl implements EnvironmentVariableService {
    @Inject private EnvironmentVariableRepository environmentVariableRepository;

    @Override
    public Optional<EnvironmentVariableDTO> findByEnvName(String envName, String accountIdentifier) {
        Optional<EnvironmentVariable> environmentVariable
                = environmentVariableRepository.findByEnvNameAndAccountIdentifier(envName, accountIdentifier);
        return environmentVariable.map(EnvironmentVariableMapper::toDTO);
    }
}
