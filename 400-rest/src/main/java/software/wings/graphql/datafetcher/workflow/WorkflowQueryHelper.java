/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.workflow;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;

import software.wings.beans.EntityType;
import software.wings.beans.Workflow;
import software.wings.graphql.datafetcher.DataFetcherUtils;
import software.wings.graphql.datafetcher.tag.TagHelper;
import software.wings.graphql.schema.type.aggregation.QLIdFilter;
import software.wings.graphql.schema.type.aggregation.tag.QLTagInput;
import software.wings.graphql.schema.type.aggregation.workflow.QLOrchestrationWorkflowTypeFilter;
import software.wings.graphql.schema.type.aggregation.workflow.QLWorkflowFilter;
import software.wings.graphql.schema.type.aggregation.workflow.QLWorkflowTagFilter;
import software.wings.graphql.schema.type.aggregation.workflow.QLWorkflowTagType;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import dev.morphia.query.FieldEnd;
import dev.morphia.query.Query;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

/**
 * @author rktummala on 07/12/19
 */
@Singleton
@Slf4j
@OwnedBy(CDC)
@TargetModule(HarnessModule._380_CG_GRAPHQL)
public class WorkflowQueryHelper {
  @Inject protected DataFetcherUtils utils;
  @Inject protected TagHelper tagHelper;

  public void setQuery(List<QLWorkflowFilter> filters, Query query, String accountId) {
    if (isEmpty(filters)) {
      return;
    }

    filters.forEach(filter -> {
      FieldEnd<? extends Query<Workflow>> field;

      if (filter.getApplication() != null) {
        field = query.field("appId");
        QLIdFilter applicationFilter = filter.getApplication();
        utils.setIdFilter(field, applicationFilter);
      }

      if (filter.getWorkflow() != null) {
        field = query.field("_id");
        QLIdFilter workflowFilter = filter.getWorkflow();
        utils.setIdFilter(field, workflowFilter);
      }

      if (filter.getOrchestrationWorkflowType() != null) {
        field = query.field("orchestration.orchestrationWorkflowType");
        QLOrchestrationWorkflowTypeFilter orchestrationWorkflowType = filter.getOrchestrationWorkflowType();
        utils.setEnumFilter(field, orchestrationWorkflowType);
      }

      if (filter.getTag() != null) {
        QLWorkflowTagFilter triggerTagFilter = filter.getTag();
        List<QLTagInput> tags = triggerTagFilter.getTags();
        Set<String> entityIds =
            tagHelper.getEntityIdsFromTags(accountId, tags, getEntityType(triggerTagFilter.getEntityType()));
        switch (triggerTagFilter.getEntityType()) {
          case APPLICATION:
            query.field("appId").in(entityIds);
            break;
          case WORKFLOW:
            query.field("_id").in(entityIds);
            break;
          default:
            log.error("EntityType {} not supported in query", triggerTagFilter.getEntityType());
            throw new InvalidRequestException("Error while compiling query", WingsException.USER);
        }
      }
    });
  }

  public EntityType getEntityType(QLWorkflowTagType entityType) {
    switch (entityType) {
      case APPLICATION:
        return EntityType.APPLICATION;
      case WORKFLOW:
        return EntityType.WORKFLOW;
      default:
        log.error("Unsupported entity type {} for tag ", entityType);
        throw new InvalidRequestException("Unsupported entity type " + entityType);
    }
  }
}
