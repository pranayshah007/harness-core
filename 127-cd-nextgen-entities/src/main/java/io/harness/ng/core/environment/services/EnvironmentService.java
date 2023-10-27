/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.core.environment.services;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.ng.core.environment.beans.Environment;
import io.harness.ng.core.environment.beans.EnvironmentInputSetYamlAndServiceOverridesMetadataDTO;
import io.harness.ng.core.environment.beans.EnvironmentInputsMergedResponseDto;
import io.harness.repositories.UpsertOptions;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_SERVICE_ENVIRONMENT})
public interface EnvironmentService {
  Environment create(Environment environment);

  Optional<Environment> get(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String environmentIdentifier, boolean deleted);

  /**
   * this method will return the environment entity as stored in the MongoDB
   * database itself. No additional data (YAML) will be fetched from the source code repository.
   * @return An Optional containing the retrieved or fetched Environment, or an empty Optional if
   *         no matching entity is found.
   */
  Optional<Environment> getMetadata(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String environmentIdentifier, boolean deleted);

  Optional<Environment> get(String accountId, String orgIdentifier, String projectIdentifier,
      String environmentIdentifier, boolean deleted, boolean loadFromCache, boolean loadFromFallbackBranch);

  // TODO(archit): make it transactional
  Environment update(Environment requestEnvironment);

  // TODO(archit): make it transactional
  Environment upsert(Environment requestEnvironment, UpsertOptions upsertOptions);

  Page<Environment> list(Criteria criteria, Pageable pageable);

  boolean delete(String accountId, String orgIdentifier, String projectIdentifier, String environmentIdentifier,
      Long version, boolean forceDelete);

  boolean forceDeleteAllInProject(String accountId, String orgIdentifier, String projectIdentifier);

  /**
   * Deletes all environments linked to a particular harness org.
   *
   * @param accountId     the account id
   * @param orgIdentifier the organization identifier
   * @return boolean to indicate if deletion was successful
   */
  boolean forceDeleteAllInOrg(String accountId, String orgIdentifier);

  List<Environment> listAccess(Criteria criteria);

  List<String> fetchesNonDeletedEnvIdentifiersFromList(
      String accountId, String orgIdentifier, String projectIdentifier, List<String> envIdentifierList);

  List<Environment> fetchesNonDeletedEnvironmentFromListOfIdentifiers(
      String accountId, String orgIdentifier, String projectIdentifier, List<String> envIdentifierList);

  List<Environment> fetchesNonDeletedEnvironmentFromListOfRefs(
      String accountId, String orgIdentifier, String projectIdentifier, List<String> envRefs);

  String createEnvironmentInputsYaml(
      String accountId, String orgIdentifier, String projectIdentifier, String envIdentifier, String gitBranch);

  String createEnvironmentInputsYaml(String envIdentifier, String environmentYaml);

  List<Map<String, String>> getAttributes(
      String accountId, String orgIdentifier, String projectIdentifier, List<String> envIdentifiers);

  EnvironmentInputSetYamlAndServiceOverridesMetadataDTO getEnvironmentsInputYamlAndServiceOverridesMetadata(
      String accountId, String orgIdentifier, String projectIdentifier, List<String> envRefs, List<String> serviceRefs,
      boolean isServiceOverrideV2Enabled);

  EnvironmentInputSetYamlAndServiceOverridesMetadataDTO getEnvironmentsInputYamlAndServiceOverridesMetadata(
      String accountId, String orgIdentifier, String projectIdentifier, List<String> envRefs,
      Map<String, String> environmentRefBranchMap, List<String> serviceRefs, boolean isServiceOverrideV2Enabled,
      boolean loadFromCache);

  EnvironmentInputsMergedResponseDto mergeEnvironmentInputs(
      String accountId, String orgId, String projectId, String serviceId, String oldEnvironmentInputsYaml);
}
