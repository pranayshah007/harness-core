/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories.executions;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
import io.harness.cdng.execution.ExecutionInfoKey;
import io.harness.cdng.execution.StageExecutionInfo;
import io.harness.cdng.execution.StageExecutionInfo.StageExecutionInfoKeys;
import io.harness.utils.StageStatus;

import com.google.inject.Inject;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@OwnedBy(HarnessTeam.CDP)
public class StageExecutionInfoRepositoryCustomImpl implements StageExecutionInfoRepositoryCustom {
  private final MongoTemplate mongoTemplate;

  @Override
  public UpdateResult update(Scope scope, String stageExecutionId, Map<String, Object> updates) {
    Criteria criteria = createScopeCriteria(scope).and(StageExecutionInfoKeys.stageExecutionId).is(stageExecutionId);
    Query query = new Query();
    query.addCriteria(criteria);
    Update update = new Update();
    updates.forEach(update::set);
    return mongoTemplate.updateMulti(query, update, StageExecutionInfo.class);
  }

  @Override
  public UpdateResult updateStatus(Scope scope, String stageExecutionId, StageStatus status) {
    Criteria criteria = createScopeCriteria(scope).and(StageExecutionInfoKeys.stageExecutionId).is(stageExecutionId);

    Query query = new Query();
    query.addCriteria(criteria);

    Update update = new Update();
    update.set(StageExecutionInfoKeys.stageStatus, status);
    return mongoTemplate.updateMulti(query, update, StageExecutionInfo.class);
  }

  @Override
  public List<StageExecutionInfo> listSucceededStageExecutionNotIncludeCurrent(
      ExecutionInfoKey executionInfoKey, final String executionId, int limit) {
    Criteria criteria = createScopeCriteria(executionInfoKey.getScope());
    criteria.and(StageExecutionInfoKeys.envIdentifier).is(executionInfoKey.getEnvIdentifier());
    criteria.and(StageExecutionInfoKeys.infraIdentifier).is(executionInfoKey.getInfraIdentifier());
    criteria.and(StageExecutionInfoKeys.serviceIdentifier).is(executionInfoKey.getServiceIdentifier());
    if (!isEmpty(executionInfoKey.getDeploymentIdentifier())) {
      criteria.and(StageExecutionInfoKeys.deploymentIdentifier).is(executionInfoKey.getDeploymentIdentifier());
    }
    criteria.and(StageExecutionInfoKeys.stageExecutionId).ne(executionId);
    criteria.and(StageExecutionInfoKeys.stageStatus).is(StageStatus.SUCCEEDED.getName());

    Query query = new Query();
    query.addCriteria(criteria);
    query.limit(limit).with(Sort.by(Sort.Direction.DESC, StageExecutionInfoKeys.createdAt));
    return mongoTemplate.find(query, StageExecutionInfo.class);
  }

  @Override
  public DeleteResult deleteAll(Criteria criteria) {
    Query query = new Query(criteria);
    return mongoTemplate.remove(query, StageExecutionInfo.class);
  }

  public Criteria createScopeCriteria(Scope scope) {
    Criteria criteria = new Criteria();
    criteria.and(StageExecutionInfoKeys.accountIdentifier).is(scope.getAccountIdentifier());
    criteria.and(StageExecutionInfoKeys.orgIdentifier).is(scope.getOrgIdentifier());
    criteria.and(StageExecutionInfoKeys.projectIdentifier).is(scope.getProjectIdentifier());
    return criteria;
  }

  @Override
  public StageExecutionInfo findByStageExecutionId(String stageExecutionId, Scope scope) {
    Criteria criteria = new Criteria();
    criteria.and(StageExecutionInfoKeys.accountIdentifier).is(scope.getAccountIdentifier());
    criteria.and(StageExecutionInfoKeys.orgIdentifier).is(scope.getOrgIdentifier());
    criteria.and(StageExecutionInfoKeys.projectIdentifier).is(scope.getProjectIdentifier());
    criteria.and(StageExecutionInfoKeys.stageExecutionId).is(stageExecutionId);
    Query query = new Query(criteria);
    return mongoTemplate.findOne(query, StageExecutionInfo.class);
  }
}
