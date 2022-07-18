/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.execution.ExecutionInputInstance;
import io.harness.execution.ExecutionInputInstance.ExecutionInputInstanceKeys;

import com.google.inject.Inject;
import java.util.Collection;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

@AllArgsConstructor(access = AccessLevel.PRIVATE, onConstructor = @__({ @Inject }))
@OwnedBy(PIPELINE)
public class ExecutionInputRepositoryCustomImpl implements ExecutionInputRepositoryCustom {
  private final MongoTemplate mongoTemplate;

  public List<ExecutionInputInstance> findByNodeExecutionIds(Collection<String> nodeExecutionIds) {
    Query query = new Query();
    query.addCriteria(Criteria.where(ExecutionInputInstanceKeys.nodeExecutionId).in(nodeExecutionIds));
    return mongoTemplate.find(query, ExecutionInputInstance.class);
  }
}
