/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
import io.harness.execution.stage.StageExecutionEntity;
import io.harness.execution.stage.StageExecutionEntity.StageExecutionEntityKeys;
import io.harness.pms.contracts.execution.Status;

import com.google.inject.Inject;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;
import java.util.Map;
import lombok.AllArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@OwnedBy(HarnessTeam.CDP)
public class StageExecutionEntityRepositoryCustomImpl implements StageExecutionEntityRepositoryCustom {
  private final MongoTemplate mongoTemplate;

  @Override
  public UpdateResult update(Scope scope, String stageExecutionId, Map<String, Object> updates) {
    Criteria criteria = createScopeCriteria(scope).and(StageExecutionEntityKeys.stageExecutionId).is(stageExecutionId);
    Query query = new Query();
    query.addCriteria(criteria);
    Update update = new Update();
    updates.forEach(update::set);
    return mongoTemplate.updateMulti(query, update, StageExecutionEntity.class);
  }

  @Override
  public UpdateResult updateStatus(Scope scope, String stageExecutionId, Status status) {
    Criteria criteria = createScopeCriteria(scope).and(StageExecutionEntityKeys.stageExecutionId).is(stageExecutionId);

    Query query = new Query();
    query.addCriteria(criteria);

    Update update = new Update();
    update.set(StageExecutionEntityKeys.status, status);
    return mongoTemplate.updateMulti(query, update, StageExecutionEntity.class);
  }

  @Override
  public DeleteResult deleteAll(Criteria criteria) {
    Query query = new Query(criteria);
    return mongoTemplate.remove(query, StageExecutionEntity.class);
  }

  public Criteria createScopeCriteria(Scope scope) {
    Criteria criteria = new Criteria();
    criteria.and(StageExecutionEntityKeys.accountIdentifier).is(scope.getAccountIdentifier());
    criteria.and(StageExecutionEntityKeys.orgIdentifier).is(scope.getOrgIdentifier());
    criteria.and(StageExecutionEntityKeys.projectIdentifier).is(scope.getProjectIdentifier());
    return criteria;
  }

  @Override
  public StageExecutionEntity findByStageExecutionId(String stageExecutionId, Scope scope) {
    Criteria criteria = new Criteria();
    criteria.and(StageExecutionEntityKeys.accountIdentifier).is(scope.getAccountIdentifier());
    criteria.and(StageExecutionEntityKeys.orgIdentifier).is(scope.getOrgIdentifier());
    criteria.and(StageExecutionEntityKeys.projectIdentifier).is(scope.getProjectIdentifier());
    criteria.and(StageExecutionEntityKeys.stageExecutionId).is(stageExecutionId);
    Query query = new Query(criteria);
    return mongoTemplate.findOne(query, StageExecutionEntity.class);
  }
}
