/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.secret.service;

import io.harness.eventsframework.entity_crud.EntityChangeDTO;
import io.harness.spec.server.idp.v1.model.EnvironmentSecret;

import java.util.List;
import java.util.Optional;

public interface EnvironmentSecretService {
  Optional<EnvironmentSecret> findByIdAndAccountIdentifier(String identifier, String accountIdentifier);
  List<EnvironmentSecret> findByAccountIdentifier(String accountIdentifier);
  EnvironmentSecret saveAndSyncK8sSecret(EnvironmentSecret environmentSecret, String accountIdentifier);
  List<EnvironmentSecret> saveAndSyncK8sSecrets(List<EnvironmentSecret> requestSecrets, String harnessAccount);
  EnvironmentSecret updateAndSyncK8sSecret(EnvironmentSecret environmentSecret, String accountIdentifier);
  List<EnvironmentSecret> updateAndSyncK8sSecrets(List<EnvironmentSecret> requestSecrets, String accountIdentifier);
  void deleteMulti(List<String> secretIdentifiers, String accountIdentifier);
  void processSecretUpdate(EntityChangeDTO entityChangeDTO);
  void delete(String secretIdentifier, String harnessAccount);
  void syncK8sSecret(List<EnvironmentSecret> environmentSecrets, String accountIdentifier);
}
