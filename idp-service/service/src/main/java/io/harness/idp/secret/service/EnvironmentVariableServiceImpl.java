/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.secret.service;

import io.harness.idp.secret.beans.dto.EnvironmentVariableDTO;
import io.harness.idp.secret.beans.entity.EnvironmentVariable;
import io.harness.idp.secret.mappers.EnvironmentVariableMapper;
import io.harness.idp.secret.repositories.EnvironmentVariableRepository;

import com.google.inject.Inject;
import java.util.Optional;

public class EnvironmentVariableServiceImpl implements EnvironmentVariableService {
  @Inject private EnvironmentVariableRepository environmentVariableRepository;

  @Override
  public Optional<EnvironmentVariableDTO> findByEnvName(String envName, String accountIdentifier) {
    Optional<EnvironmentVariable> environmentVariable =
        environmentVariableRepository.findByEnvNameAndAccountIdentifier(envName, accountIdentifier);
    return environmentVariable.map(EnvironmentVariableMapper::toDTO);
  }
}
