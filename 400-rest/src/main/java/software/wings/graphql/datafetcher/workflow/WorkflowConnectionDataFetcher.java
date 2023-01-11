/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.workflow;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import static software.wings.graphql.utils.nameservice.NameService.application;
import static software.wings.graphql.utils.nameservice.NameService.workflow;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.exception.WingsException;

import software.wings.beans.Workflow;
import software.wings.beans.Workflow.WorkflowKeys;
import software.wings.graphql.datafetcher.AbstractConnectionV2DataFetcher;
import software.wings.graphql.schema.query.QLPageQueryParameters;
import software.wings.graphql.schema.type.QLWorkflow;
import software.wings.graphql.schema.type.QLWorkflow.QLWorkflowBuilder;
import software.wings.graphql.schema.type.QLWorkflowConnection;
import software.wings.graphql.schema.type.QLWorkflowConnection.QLWorkflowConnectionBuilder;
import software.wings.graphql.schema.type.aggregation.QLIdFilter;
import software.wings.graphql.schema.type.aggregation.QLIdOperator;
import software.wings.graphql.schema.type.aggregation.QLNoOpSortCriteria;
import software.wings.graphql.schema.type.aggregation.workflow.QLWorkflowFilter;
import software.wings.security.PermissionAttribute.Action;
import software.wings.security.PermissionAttribute.PermissionType;
import software.wings.security.annotations.AuthRule;

import com.google.inject.Inject;
import dev.morphia.query.Query;
import dev.morphia.query.Sort;
import graphql.schema.DataFetchingEnvironment;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDC)
@Slf4j
@TargetModule(HarnessModule._380_CG_GRAPHQL)
public class WorkflowConnectionDataFetcher
    extends AbstractConnectionV2DataFetcher<QLWorkflowFilter, QLNoOpSortCriteria, QLWorkflowConnection> {
  @Inject WorkflowQueryHelper workflowQueryHelper;

  @Override
  @AuthRule(permissionType = PermissionType.WORKFLOW, action = Action.READ)
  public QLWorkflowConnection fetchConnection(List<QLWorkflowFilter> serviceFilters,
      QLPageQueryParameters pageQueryParameters, List<QLNoOpSortCriteria> sortCriteria) {
    Query<Workflow> query = populateFilters(wingsPersistence, serviceFilters, Workflow.class, true);
    query.order(Sort.descending(WorkflowKeys.createdAt));
    QLWorkflowConnectionBuilder connectionBuilder = QLWorkflowConnection.builder();
    connectionBuilder.pageInfo(utils.populate(pageQueryParameters, query, workflow -> {
      QLWorkflowBuilder builder = QLWorkflow.builder();
      WorkflowController.populateWorkflow(workflow, builder);
      connectionBuilder.node(builder.build());
    }));

    return connectionBuilder.build();
  }

  @Override
  protected void populateFilters(List<QLWorkflowFilter> filters, Query query) {
    workflowQueryHelper.setQuery(filters, query, getAccountId());
  }

  @Override
  protected QLWorkflowFilter generateFilter(DataFetchingEnvironment environment, String key, String value) {
    QLIdFilter idFilter = QLIdFilter.builder()
                              .operator(QLIdOperator.EQUALS)
                              .values(new String[] {(String) utils.getFieldValue(environment.getSource(), value)})
                              .build();
    if (application.equals(key)) {
      return QLWorkflowFilter.builder().application(idFilter).build();
    } else if (workflow.equals(key)) {
      return QLWorkflowFilter.builder().workflow(idFilter).build();
    }
    throw new WingsException("Unsupported field " + key + " while generating filter");
  }
}
