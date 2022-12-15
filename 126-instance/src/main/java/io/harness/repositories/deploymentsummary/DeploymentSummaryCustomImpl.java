/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.repositories.deploymentsummary;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.dtos.InfrastructureMappingDTO;
import io.harness.entities.DeploymentSummary;
import io.harness.entities.DeploymentSummary.DeploymentSummaryKeys;
import io.harness.exception.InvalidArgumentsException;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Optional;
import lombok.AllArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

@Singleton
@OwnedBy(HarnessTeam.DX)
@AllArgsConstructor(onConstructor = @__({ @Inject }))
public class DeploymentSummaryCustomImpl implements DeploymentSummaryCustom {
  private final MongoTemplate mongoTemplate;

  @Override
  public Optional<DeploymentSummary> fetchNthRecordFromNow(
      int N, String instanceSyncKey, InfrastructureMappingDTO infrastructureMappingDTO) {
    Criteria criteria = getCriteria(instanceSyncKey, infrastructureMappingDTO);
    Query query = getQuery(criteria);
    query.skip((long) N - 1);
    query.limit(1);
    return getDeploymentSummary(query);
  }

  @Override
  public Optional<DeploymentSummary> fetchLatestByInstanceKeyAndPipelineExecutionIdNot(
      String instanceSyncKey, InfrastructureMappingDTO infrastructureMappingDTO, String pipelineExecutionId) {
    Criteria criteria = getCriteria(instanceSyncKey, infrastructureMappingDTO);
    criteria.and(DeploymentSummaryKeys.pipelineExecutionId).ne(pipelineExecutionId);
    Query query = getQuery(criteria);
    query.limit(1);
    return getDeploymentSummary(query);
  }

  @NotNull
  private Optional<DeploymentSummary> getDeploymentSummary(Query query) {
    List<DeploymentSummary> deploymentSummaryList = mongoTemplate.find(query, DeploymentSummary.class);
    if (deploymentSummaryList.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(deploymentSummaryList.get(0));
  }

  @NotNull
  private Query getQuery(Criteria criteria) {
    Query query = new Query().addCriteria(criteria);
    query.with(Sort.by(Sort.Direction.DESC, DeploymentSummaryKeys.createdAt));
    return query;
  }

  @NotNull
  private Criteria getCriteria(String instanceSyncKey, InfrastructureMappingDTO infrastructureMappingDTO) {
    if (infrastructureMappingDTO == null) {
      throw new InvalidArgumentsException("InfrastructureMappingDTO is null for instanceKey: {}" + instanceSyncKey);
    }
    return Criteria.where(DeploymentSummaryKeys.instanceSyncKey)
        .is(instanceSyncKey)
        .and(DeploymentSummaryKeys.accountIdentifier)
        .is(infrastructureMappingDTO.getAccountIdentifier())
        .and(DeploymentSummaryKeys.orgIdentifier)
        .is(infrastructureMappingDTO.getOrgIdentifier())
        .and(DeploymentSummaryKeys.projectIdentifier)
        .is(infrastructureMappingDTO.getProjectIdentifier())
        .and(DeploymentSummaryKeys.infrastructureMappingId)
        .is(infrastructureMappingDTO.getId());
  }
}
