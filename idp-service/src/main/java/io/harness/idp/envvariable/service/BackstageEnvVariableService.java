/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.envvariable.service;

import io.harness.eventsframework.entity_crud.EntityChangeDTO;
import io.harness.spec.server.idp.v1.model.BackstageEnvSecretVariable;
import io.harness.spec.server.idp.v1.model.BackstageEnvVariable;

import java.util.List;
import java.util.Optional;

public interface BackstageEnvVariableService {
  Optional<BackstageEnvVariable> findByIdAndAccountIdentifier(String identifier, String accountIdentifier);
  Optional<BackstageEnvVariable> findByEnvNameAndAccountIdentifier(String envName, String accountIdentifier);
  List<BackstageEnvVariable> findByAccountIdentifier(String accountIdentifier);
  BackstageEnvVariable create(BackstageEnvVariable envVariable, String accountIdentifier);
  BackstageEnvVariable update(BackstageEnvVariable envVariable, String accountIdentifier);
  List<BackstageEnvVariable> createOrUpdate(List<BackstageEnvVariable> requestVariables, String accountIdentifier);
  void deleteMulti(List<String> secretIdentifiers, String accountIdentifier);
  void processSecretUpdate(EntityChangeDTO entityChangeDTO);
  void delete(String secretIdentifier, String harnessAccount);
  void findAndSync(String accountIdentifier);
  List<BackstageEnvSecretVariable> getAllSecretIdentifierForMultipleEnvVariablesInAccount(
      String accountIdentifier, List<String> envVariables);
  void deleteMultiUsingEnvNames(List<String> envNames, String accountIdentifier);
}
