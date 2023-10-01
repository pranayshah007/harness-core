/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories.instance;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
import io.harness.cdng.execution.ExecutionInfoKey;
import io.harness.cdng.instance.InstanceDeploymentInfo;
import io.harness.cdng.instance.InstanceDeploymentInfo.InstanceDeploymentInfoKeys;
import io.harness.cdng.instance.InstanceDeploymentInfoStatus;
import io.harness.data.structure.EmptyPredicate;
import io.harness.entities.ArtifactDetails;
import io.harness.entities.ArtifactDetails.ArtifactDetailsKeys;
import io.harness.exception.ExceptionUtils;
import io.harness.springdata.PersistenceUtils;

import com.google.inject.Inject;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;
import java.util.List;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@OwnedBy(HarnessTeam.CDP)
@Slf4j
public class InstanceDeploymentInfoRepositoryCustomImpl implements InstanceDeploymentInfoRepositoryCustom {
  private final MongoTemplate mongoTemplate;

  @Override
  public UpdateResult updateStatus(ExecutionInfoKey executionInfoKey, InstanceDeploymentInfoStatus status) {
    Criteria criteria = createExecutionCriteria(executionInfoKey);

    Query query = new Query();
    query.addCriteria(criteria);

    Update update = new Update();
    update.set(InstanceDeploymentInfoKeys.status, status);
    return mongoTemplate.updateMulti(query, update, InstanceDeploymentInfoKeys.class);
  }

  @Override
  public UpdateResult updateStatus(Scope scope, String stageExecutionId, InstanceDeploymentInfoStatus status) {
    Criteria criteria =
        createScopeCriteria(scope).and(InstanceDeploymentInfoKeys.stageExecutionId).is(stageExecutionId);

    Query query = new Query();
    query.addCriteria(criteria);

    Update update = new Update();
    update.set(InstanceDeploymentInfoKeys.status, status);
    return mongoTemplate.updateMulti(query, update, InstanceDeploymentInfo.class);
  }

  @Override
  public UpdateResult updateArtifactAndStatus(ExecutionInfoKey executionInfoKey, List<String> hosts,
      ArtifactDetails artifactDetails, InstanceDeploymentInfoStatus status, String stageExecutionId) {
    Criteria criteria =
        createExecutionCriteria(executionInfoKey).and(InstanceDeploymentInfoKeys.instanceInfo + ".host").in(hosts);

    Query query = new Query();
    query.addCriteria(criteria);

    Update update = new Update();
    update.set(InstanceDeploymentInfoKeys.status, status);
    update.set(InstanceDeploymentInfoKeys.artifactDetails, artifactDetails);
    update.set(InstanceDeploymentInfoKeys.stageExecutionId, stageExecutionId);
    return mongoTemplate.updateMulti(query, update, InstanceDeploymentInfo.class);
  }

  @Override
  public DeleteResult deleteByExecutionInfoKey(ExecutionInfoKey executionInfoKey) {
    Criteria criteria = createExecutionCriteria(executionInfoKey);

    Query query = new Query();
    query.addCriteria(criteria);

    return mongoTemplate.remove(query, InstanceDeploymentInfo.class);
  }

  @Override
  public List<InstanceDeploymentInfo> listByHosts(ExecutionInfoKey executionInfoKey, List<String> hosts) {
    Criteria criteria =
        createExecutionCriteria(executionInfoKey).and(InstanceDeploymentInfoKeys.instanceInfo + ".host").in(hosts);

    Query query = new Query();
    query.addCriteria(criteria);

    return mongoTemplate.find(query, InstanceDeploymentInfo.class);
  }

  @Override
  public List<InstanceDeploymentInfo> listByHostsAndArtifact(ExecutionInfoKey executionInfoKey, List<String> hosts,
      ArtifactDetails artifactDetails, InstanceDeploymentInfoStatus... statuses) {
    Criteria criteria = createExecutionCriteria(executionInfoKey);
    criteria.and(InstanceDeploymentInfoKeys.instanceInfo + ".host").in(hosts);

    criteria.and(format("%s.%s", InstanceDeploymentInfoKeys.artifactDetails, ArtifactDetailsKeys.artifactId))
        .is(artifactDetails.getArtifactId());
    criteria.and(format("%s.%s", InstanceDeploymentInfoKeys.artifactDetails, ArtifactDetailsKeys.displayName))
        .is(artifactDetails.getDisplayName());
    criteria.and(format("%s.%s", InstanceDeploymentInfoKeys.artifactDetails, ArtifactDetailsKeys.tag))
        .is(artifactDetails.getTag());

    criteria.and(InstanceDeploymentInfoKeys.status).in(statuses);

    Query query = new Query();
    query.addCriteria(criteria);

    return mongoTemplate.find(query, InstanceDeploymentInfo.class);
  }

  @Override
  public boolean deleteByScope(Scope scope) {
    Criteria scopeCriteria = createScopeCriteria(scope);
    try {
      Failsafe.with(getDeleteRetryPolicy())
          .get(() -> mongoTemplate.remove(new Query(scopeCriteria), InstanceDeploymentInfo.class));
      return true;
    } catch (Exception e) {
      log.warn(format("Error while deleting InstanceDeploymentInfo for Criteria [%s] : %s", scopeCriteria,
                   ExceptionUtils.getMessage(e)),
          e);
      return false;
    }
  }

  private Criteria createExecutionCriteria(ExecutionInfoKey key) {
    Criteria criteria = new Criteria();
    criteria.and(InstanceDeploymentInfoKeys.accountIdentifier).is(key.getScope().getAccountIdentifier());
    criteria.and(InstanceDeploymentInfoKeys.orgIdentifier).is(key.getScope().getOrgIdentifier());
    criteria.and(InstanceDeploymentInfoKeys.projectIdentifier).is(key.getScope().getProjectIdentifier());
    criteria.and(InstanceDeploymentInfoKeys.envIdentifier).is(key.getEnvIdentifier());
    criteria.and(InstanceDeploymentInfoKeys.serviceIdentifier).is(key.getServiceIdentifier());
    criteria.and(InstanceDeploymentInfoKeys.infraIdentifier).is(key.getInfraIdentifier());
    if (EmptyPredicate.isNotEmpty(key.getDeploymentIdentifier())) {
      criteria.and(InstanceDeploymentInfoKeys.deploymentIdentifier).is(key.getDeploymentIdentifier());
    }
    return criteria;
  }

  private Criteria createScopeCriteria(@NotNull Scope scope) {
    Criteria criteria = new Criteria();
    criteria.and(InstanceDeploymentInfoKeys.accountIdentifier).is(scope.getAccountIdentifier());
    criteria.and(InstanceDeploymentInfoKeys.orgIdentifier).is(scope.getOrgIdentifier());
    criteria.and(InstanceDeploymentInfoKeys.projectIdentifier).is(scope.getProjectIdentifier());
    return criteria;
  }

  private RetryPolicy<Object> getDeleteRetryPolicy() {
    return PersistenceUtils.getRetryPolicy("[Retrying]: Failed deleting InstanceDeploymentInfo; attempt: {}",
        "[Failed]: Failed InstanceDeploymentInfo; attempt: {}");
  }
}
