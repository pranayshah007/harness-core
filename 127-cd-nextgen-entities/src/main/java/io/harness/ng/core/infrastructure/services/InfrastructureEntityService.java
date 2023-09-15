/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.core.infrastructure.services;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.service.beans.ServiceDefinitionType;
import io.harness.ng.core.infrastructure.dto.InfrastructureInputsMergedResponseDto;
import io.harness.ng.core.infrastructure.dto.InfrastructureYamlMetadata;
import io.harness.ng.core.infrastructure.dto.NoInputMergeInputAction;
import io.harness.ng.core.infrastructure.entity.InfrastructureEntity;
import io.harness.persistence.HIterator;
import io.harness.repositories.UpsertOptions;

import com.mongodb.client.result.UpdateResult;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Update;

@OwnedBy(HarnessTeam.CDC)
public interface InfrastructureEntityService {
  InfrastructureEntity create(@NotNull InfrastructureEntity infrastructureEntity);

  Optional<InfrastructureEntity> get(@NotEmpty String accountId, @NotEmpty String orgIdentifier,
      @NotEmpty String projectIdentifier, @NotEmpty String envIdentifier, @NotEmpty String infraIdentifier);

  InfrastructureEntity update(@NotNull InfrastructureEntity requestInfra);

  InfrastructureEntity upsert(@NotNull InfrastructureEntity requestInfra, UpsertOptions upsertOptions);

  Page<InfrastructureEntity> list(@NotNull Criteria criteria, @NotNull Pageable pageable);

  HIterator<InfrastructureEntity> listIterator(@NotEmpty String accountId, @NotEmpty String orgIdentifier,
      @NotEmpty String projectIdentifier, @NotEmpty String envIdentifier, Collection<String> identifiers);

  boolean delete(@NotEmpty String accountId, @NotEmpty String orgIdentifier, @NotEmpty String projectIdentifier,
      @NotEmpty String envIdentifier, @NotEmpty String infraIdentifier, boolean forceDelete);

  boolean forceDeleteAllInEnv(@NotEmpty String accountId, @NotEmpty String orgIdentifier,
      @NotEmpty String projectIdentifier, @NotEmpty String envIdentifier);

  boolean forceDeleteAllInProject(
      @NotEmpty String accountId, @NotEmpty String orgIdentifier, @NotEmpty String projectIdentifier);

  /**
   * Deletes all infrastructures linked to a particular environment at org level.
   * @param accountId  the account id
   * @param orgIdentifier the organization identifier
   * @return boolean to indicate if deletion was successful
   */
  boolean forceDeleteAllInOrg(@NotEmpty String accountId, @NotEmpty String orgIdentifier);

  Page<InfrastructureEntity> bulkCreate(
      @NotEmpty String accountId, @NotNull List<InfrastructureEntity> infrastructureEntities);

  List<InfrastructureEntity> getAllInfrastructureFromIdentifierList(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String envIdentifier, List<String> infraIdentifier);

  List<InfrastructureEntity> getAllInfrastructureFromEnvRef(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String envIdentifier);

  List<InfrastructureEntity> getAllInfrastructureFromEnvRefAndDeploymentType(String accountIdentifier,
      String orgIdentifier, String projectIdentifier, String envIdentifier, ServiceDefinitionType deploymentType);

  List<InfrastructureEntity> getInfrastructures(
      String accountIdentifier, String orgIdentifier, String projectIdentifier);

  String createInfrastructureInputsFromYaml(String accountId, String orgIdentifier, String projectIdentifier,
      String environmentIdentifier, List<String> infraIdentifiers, boolean deployToAll,
      NoInputMergeInputAction noInputMergeInputAction);

  UpdateResult batchUpdateInfrastructure(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String envIdentifier, List<String> infraIdentifier, Update update);

  List<InfrastructureYamlMetadata> createInfrastructureYamlMetadata(String accountId, String orgIdentifier,
      String projectIdentifier, String environmentIdentifier, List<String> infraIds);

  String createInfrastructureInputsFromYaml(String accountId, String orgIdentifier, String projectIdentifier,
      String environmentIdentifier, String infraIdentifier);

  InfrastructureInputsMergedResponseDto mergeInfraStructureInputs(String accountId, String orgIdentifier,
      String projectIdentifier, String envIdentifier, String infraIdentifier, String oldInfrastructureInputsYaml);

  Page<InfrastructureEntity> getScopedInfrastructures(
      Page<InfrastructureEntity> infrastructureEntities, List<String> serviceRefs);

  List<String> filterServicesByScopedInfrastructures(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, List<String> serviceRefs, Map<String, List<String>> envRefInfraRefsMapping);
}
