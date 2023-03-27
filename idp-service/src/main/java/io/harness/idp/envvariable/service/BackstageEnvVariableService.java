/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.envvariable.service;

import io.harness.eventsframework.entity_crud.EntityChangeDTO;
import io.harness.spec.server.idp.v1.model.BackstageEnvVariable;

import java.util.List;
import java.util.Optional;

public interface BackstageEnvVariableService {
  Optional<BackstageEnvVariable> findByIdAndAccountIdentifier(String identifier, String accountIdentifier);
  List<BackstageEnvVariable> findByAccountIdentifier(String accountIdentifier);
  BackstageEnvVariable create(BackstageEnvVariable environmentSecret, String accountIdentifier);
  List<BackstageEnvVariable> createMulti(List<BackstageEnvVariable> requestSecrets, String harnessAccount);
  BackstageEnvVariable update(BackstageEnvVariable environmentSecret, String accountIdentifier);
  List<BackstageEnvVariable> updateMulti(List<BackstageEnvVariable> requestSecrets, String accountIdentifier);
  void deleteMulti(List<String> secretIdentifiers, String accountIdentifier);
  void processSecretUpdate(EntityChangeDTO entityChangeDTO);
  void delete(String secretIdentifier, String harnessAccount);
  void sync(List<BackstageEnvVariable> environmentSecrets, String accountIdentifier);
}
