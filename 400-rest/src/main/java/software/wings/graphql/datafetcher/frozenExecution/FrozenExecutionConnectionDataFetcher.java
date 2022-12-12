/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */
package software.wings.graphql.datafetcher.frozenExecution;

import static io.harness.annotations.dev.HarnessTeam.SPG;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.EnvironmentType;
import io.harness.beans.WorkflowType;
import io.harness.exception.GraphQLException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnexpectedException;
import io.harness.exception.WingsException;

import software.wings.beans.EnvSummary;
import software.wings.beans.WorkflowExecution;
import software.wings.beans.WorkflowExecution.WorkflowExecutionKeys;
import software.wings.graphql.datafetcher.AbstractConnectionV2DataFetcher;
import software.wings.graphql.datafetcher.execution.PipelineExecutionController;
import software.wings.graphql.datafetcher.execution.WorkflowExecutionController;
import software.wings.graphql.schema.query.QLPageQueryParameters;
import software.wings.graphql.schema.type.QLEnvSummary;
import software.wings.graphql.schema.type.QLEnvironmentType;
import software.wings.graphql.schema.type.QLFrozenExecution;
import software.wings.graphql.schema.type.QLFrozenExecution.QLFrozenExecutionBuilder;
import software.wings.graphql.schema.type.QLFrozenExecutionConnection;
import software.wings.graphql.schema.type.QLFrozenExecutionConnection.QLFrozenExecutionConnectionBuilder;
import software.wings.graphql.schema.type.QLPipelineExecution;
import software.wings.graphql.schema.type.QLPipelineExecution.QLPipelineExecutionBuilder;
import software.wings.graphql.schema.type.QLWorkflowExecution;
import software.wings.graphql.schema.type.QLWorkflowExecution.QLWorkflowExecutionBuilder;
import software.wings.graphql.schema.type.aggregation.QLIdFilter;
import software.wings.graphql.schema.type.aggregation.QLIdOperator;
import software.wings.graphql.schema.type.aggregation.QLNoOpSortCriteria;
import software.wings.graphql.utils.nameservice.NameService;
import software.wings.security.PermissionAttribute;
import software.wings.security.annotations.AuthRule;

import com.google.inject.Inject;
import graphql.schema.DataFetchingEnvironment;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.Sort;
@Slf4j
@OwnedBy(SPG)
@TargetModule(HarnessModule._380_CG_GRAPHQL)
public class FrozenExecutionConnectionDataFetcher
    extends AbstractConnectionV2DataFetcher<QLFrozenExecutionFilter, QLNoOpSortCriteria, QLFrozenExecutionConnection> {
  @Inject private WorkflowExecutionController workflowExecutionController;
  @Inject private PipelineExecutionController pipelineExecutionController;

  @Override
  @AuthRule(permissionType = PermissionAttribute.PermissionType.LOGGED_IN)
  protected QLFrozenExecutionConnection fetchConnection(List<QLFrozenExecutionFilter> filters,
      QLPageQueryParameters pageQueryParameters, List<QLNoOpSortCriteria> sortCriteria) {
    Query<WorkflowExecution> query = populateFilters(wingsPersistence, filters, WorkflowExecution.class, true)
                                         .order(Sort.descending(WorkflowExecutionKeys.createdAt));
    QLFrozenExecutionConnectionBuilder connectionBuilder = QLFrozenExecutionConnection.builder();
    connectionBuilder.pageInfo(utils.populate(pageQueryParameters, query, execution -> {
      final QLFrozenExecutionBuilder builder = QLFrozenExecution.builder();
      if (execution.getWorkflowType() == WorkflowType.PIPELINE) {
        final QLPipelineExecutionBuilder pipelineBuilder = QLPipelineExecution.builder();
        pipelineExecutionController.populatePipelineExecution(execution, pipelineBuilder);
        builder.execution(pipelineBuilder.build());
      } else if (execution.getWorkflowType() == WorkflowType.ORCHESTRATION) {
        final QLWorkflowExecutionBuilder workflowBuilder = QLWorkflowExecution.builder();
        workflowExecutionController.populateWorkflowExecution(execution, workflowBuilder);
        builder.execution(workflowBuilder.build());
      } else {
        String errorMgs = "Unsupported execution type: " + execution.getWorkflowType();
        log.error(errorMgs);
        throw new UnexpectedException(errorMgs);
      }
      List<QLEnvSummary> environments = Collections.emptyList();
      if (execution.getEnvironments() != null) {
        environments = execution.getEnvironments().stream().map(this::getQLEnvSummary).collect(Collectors.toList());
      }
      connectionBuilder.node(builder.environments(environments)
                                 .serviceIds(execution.getServiceIds())
                                 .rejectedByFreezeWindowIds(execution.getRejectedByFreezeWindowIds())
                                 .rejectedByFreezeWindowNames(execution.getRejectedByFreezeWindowNames())
                                 .pipelineExecutionId(execution.getPipelineExecutionId())
                                 .build());
    }));
    QLFrozenExecutionConnection result = connectionBuilder.build();
    if (result.getNodes().isEmpty()) {
      throw new GraphQLException("No rejected executions found with the selected filters", null);
    }
    return connectionBuilder.build();
  }

  @Override
  protected void populateFilters(List<QLFrozenExecutionFilter> filters, Query query) {
    boolean rejectedByFreezeWindowFilterFound = false;
    if (isNotEmpty(filters)) {
      for (QLFrozenExecutionFilter filter : filters) {
        if (filter.getRejectedByFreezeWindow() != null) {
          rejectedByFreezeWindowFilterFound = true;
          QLIdFilter idFilter = filter.getRejectedByFreezeWindow();
          utils.setIdFilter(query.field(WorkflowExecutionKeys.rejectedByFreezeWindowIds), idFilter);
        }
        if (filter.getEnvironment() != null) {
          QLIdFilter idFilter = filter.getEnvironment();
          utils.setIdFilter(query.field(WorkflowExecutionKeys.envIds), idFilter);
        }
        if (filter.getService() != null) {
          QLIdFilter idFilter = filter.getService();
          utils.setIdFilter(query.field(WorkflowExecutionKeys.serviceIds), idFilter);
        }
      }
    }
    if (!rejectedByFreezeWindowFilterFound) {
      throw new InvalidRequestException("rejectedByFreezeWindow filter is required");
    }
  }

  @Override
  protected QLFrozenExecutionFilter generateFilter(DataFetchingEnvironment environment, String key, String value) {
    QLIdFilter idFilter = QLIdFilter.builder()
                              .operator(QLIdOperator.EQUALS)
                              .values(new String[] {(String) utils.getFieldValue(environment.getSource(), value)})
                              .build();

    if (NameService.service.equals(key)) {
      return QLFrozenExecutionFilter.builder().service(idFilter).build();
    } else if (NameService.environment.equals(key)) {
      return QLFrozenExecutionFilter.builder().environment(idFilter).build();
    } else if (NameService.rejectedByFreezeWindow.equals(key)) {
      return QLFrozenExecutionFilter.builder().rejectedByFreezeWindow(idFilter).build();
    }
    throw new WingsException("Unsupported field " + key + " while generating filter");
  }

  private QLEnvSummary getQLEnvSummary(EnvSummary envSummary) {
    EnvironmentType envType = envSummary.getEnvironmentType();
    QLEnvironmentType qlEnvType = null;
    if (envType == EnvironmentType.PROD) {
      qlEnvType = QLEnvironmentType.PROD;
    } else if (envType == EnvironmentType.NON_PROD) {
      qlEnvType = QLEnvironmentType.NON_PROD;
    }
    return QLEnvSummary.builder().id(envSummary.getUuid()).name(envSummary.getName()).type(qlEnvType).build();
  }
}