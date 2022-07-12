/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.repositories.executions;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.pms.plan.execution.beans.PipelineExecutionSummaryEntity;

import com.mongodb.client.result.UpdateResult;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@OwnedBy(PIPELINE)
public interface PmsExecutionSummaryRepositoryCustom {
  PipelineExecutionSummaryEntity update(Query query, Update update);
  UpdateResult deleteAllExecutionsWhenPipelineDeleted(Query query, Update update);
  Page<PipelineExecutionSummaryEntity> findAll(Criteria criteria, Pageable pageable);
  PipelineExecutionSummaryEntity findFirst(Criteria criteria);
  String fetchRootRetryExecutionId(String planExecutionId);
  List<PipelineExecutionSummaryEntity> fetchPipelineSummaryEntityFromRootParentId(String rootParentId);
}
