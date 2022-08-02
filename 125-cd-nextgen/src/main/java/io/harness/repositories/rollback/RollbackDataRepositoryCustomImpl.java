/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories.rollback;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.rollback.RollbackData;
import io.harness.cdng.rollback.RollbackData.RollbackDataKeys;
import io.harness.utils.StageStatus;

import com.google.inject.Inject;
import com.mongodb.client.result.UpdateResult;
import java.util.List;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@OwnedBy(HarnessTeam.CDP)
public class RollbackDataRepositoryCustomImpl implements RollbackDataRepositoryCustom {
  private final MongoTemplate mongoTemplate;

  @Override
  public UpdateResult updateStatus(String stageExecutionId, StageStatus status) {
    Query query = new Query();
    query.addCriteria(Criteria.where(RollbackDataKeys.stageExecutionId).is(stageExecutionId));
    Update update = new Update();
    update.set(RollbackDataKeys.stageStatus, status);
    return mongoTemplate.updateMulti(query, update, RollbackData.class);
  }

  public List<RollbackData> listRollbackDataOrderedByCreatedAt(String key, StageStatus status, int limit) {
    Query query = new Query();
    query.addCriteria(Criteria.where(RollbackDataKeys.stageStatus).is(status).and(RollbackDataKeys.key).is(key))
        .limit(limit)
        .with(Sort.by(Sort.Direction.DESC, RollbackDataKeys.createdAt));
    return mongoTemplate.find(query, RollbackData.class);
  }
}
