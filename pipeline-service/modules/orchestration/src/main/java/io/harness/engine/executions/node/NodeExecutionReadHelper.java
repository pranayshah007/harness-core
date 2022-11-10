/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.engine.executions.node;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import static org.springframework.data.mongodb.core.query.Criteria.where;
import static org.springframework.data.mongodb.core.query.Query.query;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.execution.NodeExecution;
import io.harness.execution.NodeExecution.NodeExecutionKeys;
import io.harness.pms.contracts.execution.Status;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.repository.support.PageableExecutionUtils;

@Slf4j
@OwnedBy(PIPELINE)
@Singleton
public class NodeExecutionReadHelper {
  private static int MAX_BATCH_SIZE = 1000;
  @Inject public MongoTemplate secondaryMongoTemplate;

  public long findCountByParentIdAndStatusIn(String parentId, Set<Status> flowingStatuses) {
    Query query = query(where(NodeExecutionKeys.parentId).is(parentId))
                      .addCriteria(where(NodeExecutionKeys.status).in(flowingStatuses))
                      .addCriteria(where(NodeExecutionKeys.oldRetry).is(false));
    return secondaryMongoTemplate.count(Query.of(query).limit(-1).skip(-1), NodeExecution.class);
  }

  public Page<NodeExecution> fetchNodeExecutions(Query givenQuery, Pageable pageable) {
    try {
      Query query = givenQuery.with(pageable);
      validateNodeExecutionListQueryObject(query);
      // Do not add directly the read helper inside the lambda, as secondary mongo reads were not going through if used
      // inside lambda in PageableExecutionUtils
      long count = findCount(query);
      List<NodeExecution> nodeExecutions = find(query);
      return PageableExecutionUtils.getPage(nodeExecutions, pageable, () -> count);
    } catch (IllegalArgumentException ex) {
      log.error(ex.getMessage(), ex);
      throw new InvalidRequestException("Not able to fetch NodeExecutions ", ex);
    }
  }

  public long findCount(Query query) {
    return secondaryMongoTemplate.count(Query.of(query).limit(-1).skip(-1), NodeExecution.class);
  }

  // Don't make this method public
  private List<NodeExecution> find(Query query) {
    validateNodeExecutionListQueryObject(query);
    return secondaryMongoTemplate.find(query, NodeExecution.class);
  }

  private void validateNodeExecutionListQueryObject(Query query) {
    if (query.getLimit() <= 0 || query.getLimit() > MAX_BATCH_SIZE) {
      throw new InvalidRequestException(
          "NodeExecution query should have limit within max batch size- " + MAX_BATCH_SIZE);
    }
    if (query.getFieldsObject().isEmpty()) {
      throw new InvalidRequestException("NodeExecution list query should have projection fields");
    }
  }
}
