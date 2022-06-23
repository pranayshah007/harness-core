/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.core.environment.services;

import io.harness.ng.core.environment.beans.Environment;
import io.harness.repositories.UpsertOptions;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;

public interface EnvironmentService {
  Environment create(Environment environment);

  Optional<Environment> get(
      String accountId, String orgIdentifier, String projectIdentifier, String environmentIdentifier, boolean deleted);

  // TODO(archit): make it transactional
  Environment update(Environment requestEnvironment);

  // TODO(archit): make it transactional
  Environment upsert(Environment requestEnvironment, UpsertOptions upsertOptions);

  Page<Environment> list(Criteria criteria, Pageable pageable);

  boolean delete(
      String accountId, String orgIdentifier, String projectIdentifier, String environmentIdentifier, Long version);

  boolean forceDeleteAllInProject(String accountId, String orgIdentifier, String projectIdentifier);

  List<Environment> listAccess(Criteria criteria);

  List<String> fetchesNonDeletedEnvIdentifiersFromList(
      String accountId, String orgIdentifier, String projectIdentifier, List<String> envIdentifierList);

  List<Environment> fetchesNonDeletedEnvironmentFromListOfIdentifiers(
      String accountId, String orgIdentifier, String projectIdentifier, List<String> envIdentifierList);

  String createEnvironmentInputsYaml(
      String accountId, String projectIdentifier, String orgIdentifier, String envIdentifier);
}
