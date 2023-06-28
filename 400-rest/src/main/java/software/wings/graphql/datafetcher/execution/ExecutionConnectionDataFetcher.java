/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.execution;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.FeatureName;
import io.harness.beans.WorkflowType;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.UnexpectedException;
import io.harness.exception.WingsException;
import io.harness.ff.FeatureFlagService;

import software.wings.beans.WorkflowExecution;
import software.wings.beans.WorkflowExecution.WorkflowExecutionKeys;
import software.wings.graphql.datafetcher.AbstractConnectionV2DataFetcher;
import software.wings.graphql.schema.query.QLPageQueryParameters;
import software.wings.graphql.schema.type.QLExecutionConnection;
import software.wings.graphql.schema.type.QLExecutionConnection.QLExecutionConnectionBuilder;
import software.wings.graphql.schema.type.QLPipelineExecution;
import software.wings.graphql.schema.type.QLPipelineExecution.QLPipelineExecutionBuilder;
import software.wings.graphql.schema.type.QLWorkflowExecution;
import software.wings.graphql.schema.type.QLWorkflowExecution.QLWorkflowExecutionBuilder;
import software.wings.graphql.schema.type.aggregation.QLIdFilter;
import software.wings.graphql.schema.type.aggregation.QLIdOperator;
import software.wings.graphql.schema.type.aggregation.QLNoOpSortCriteria;
import software.wings.graphql.utils.nameservice.NameService;
import software.wings.security.PermissionAttribute.PermissionType;
import software.wings.security.annotations.AuthRule;
import software.wings.service.intfc.AppService;

import com.google.inject.Inject;
import dev.morphia.query.Query;
import dev.morphia.query.Sort;
import graphql.schema.DataFetchingEnvironment;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(CDC)
@TargetModule(HarnessModule._380_CG_GRAPHQL)
public class ExecutionConnectionDataFetcher
    extends AbstractConnectionV2DataFetcher<QLExecutionFilter, QLNoOpSortCriteria, QLExecutionConnection> {
  @Inject private WorkflowExecutionController workflowExecutionController;
  @Inject private PipelineExecutionController pipelineExecutionController;
  @Inject private ExecutionQueryHelper executionQueryHelper;
  @Inject private AppService appService;
  @Inject private FeatureFlagService featureFlagService;

  static final String INDIRECT_EXECUTION_FIELD = "includeIndirectExecutions";

  @Override
  @AuthRule(permissionType = PermissionType.LOGGED_IN)
  protected QLExecutionConnection fetchConnection(List<QLExecutionFilter> filters,
      QLPageQueryParameters pageQueryParameters, List<QLNoOpSortCriteria> sortCriteria) {
    Query<WorkflowExecution> query = populateFilters(wingsPersistence, filters, WorkflowExecution.class, true)
                                         .order(Sort.descending(WorkflowExecutionKeys.createdAt));

    Boolean includeIndirectExecutions =
        (Boolean) pageQueryParameters.getDataFetchingEnvironment().getArguments().get(INDIRECT_EXECUTION_FIELD);

    boolean includePipelineId = false;
    if (includeIndirectExecutions != null) {
      includePipelineId = includeIndirectExecutions;
    }

    /**
     * If we are querying the membegit rExecutions, then we need to explicitly mark this boolean so we do not include
     * the does not exist in the query
     */

    if (isNotEmpty(filters)) {
      for (QLExecutionFilter filter : filters) {
        includePipelineId = includePipelineId || (filter.getPipelineExecutionId() != null);
      }
    }
    if (!includePipelineId) {
      query.field(WorkflowExecutionKeys.pipelineExecutionId).doesNotExist();
    }

    QLExecutionConnectionBuilder connectionBuilder = QLExecutionConnection.builder();
    connectionBuilder.pageInfo(utils.populate(pageQueryParameters, query, null, execution -> {
      if (execution.getWorkflowType() == WorkflowType.PIPELINE) {
        final QLPipelineExecutionBuilder builder = QLPipelineExecution.builder();
        pipelineExecutionController.populatePipelineExecution(execution, builder);
        connectionBuilder.node(builder.build());
      } else if (execution.getWorkflowType() == WorkflowType.ORCHESTRATION) {
        final QLWorkflowExecutionBuilder builder1 = QLWorkflowExecution.builder();
        workflowExecutionController.populateWorkflowExecution(execution, builder1);
        connectionBuilder.node(builder1.build());
      } else {
        String errorMgs = "Unsupported execution type: " + execution.getWorkflowType();
        log.error(errorMgs);
        throw new UnexpectedException(errorMgs);
      }
    }));

    return connectionBuilder.build();
  }

  @Override
  protected void populateFilters(List<QLExecutionFilter> filters, Query query) {
    filters = addAppIdValidation(filters);
    executionQueryHelper.setQuery(filters, query, getAccountId());
  }

  private List<QLExecutionFilter> addAppIdValidation(List<QLExecutionFilter> filters) {
    List<QLExecutionFilter> updatedFilters = filters != null ? new ArrayList<>(filters) : new ArrayList<>();
    if (featureFlagService.isEnabled(FeatureName.GRAPHQL_WORKFLOW_EXECUTION_OPTIMIZATION, getAccountId())) {
      validateQlExecutionFiltersWhenOnlyOneAppIsAllowed(filters);
      return filters;
    }

    return updatedFilters;
  }

  private void validateQlExecutionFiltersWhenOnlyOneAppIsAllowed(List<QLExecutionFilter> filters) {
    boolean appIdFilterFound = false;
    boolean applicationFilterFound = false;
    if (isNotEmpty(filters)) {
      for (QLExecutionFilter filter : filters) {
        if (isNotEmpty(filter.getApplicationId())) {
          appIdFilterFound = true;
        }
        if (filter.getApplication() != null) {
          applicationFilterFound = true;
          if (!filter.getApplication().getOperator().equals(QLIdOperator.EQUALS)) {
            throw new InvalidRequestException("Application filter is required and should be EQUALS");
          }
        }
      }
    }
    if (applicationFilterFound && appIdFilterFound) {
      throw new InvalidRequestException("Only one of application Id and application filter can be passed.");
    } else if (!applicationFilterFound && !appIdFilterFound) {
      throw new InvalidRequestException("Application Id filter is required");
    }
  }

  @Override
  protected QLExecutionFilter generateFilter(DataFetchingEnvironment environment, String key, String value) {
    QLIdFilter idFilter = QLIdFilter.builder()
                              .operator(QLIdOperator.EQUALS)
                              .values(new String[] {(String) utils.getFieldValue(environment.getSource(), value)})
                              .build();

    if (NameService.application.equals(key)) {
      return QLExecutionFilter.builder().application(idFilter).build();
    } else if (NameService.service.equals(key)) {
      return QLExecutionFilter.builder().service(idFilter).build();
    } else if (NameService.environment.equals(key)) {
      return QLExecutionFilter.builder().environment(idFilter).build();
    } else if (NameService.cloudProvider.equals(key)) {
      return QLExecutionFilter.builder().cloudProvider(idFilter).build();
    } else if (NameService.pipelineExecution.equals(key)) {
      return QLExecutionFilter.builder().pipelineExecutionId(idFilter).build();
    }
    throw new WingsException("Unsupported field " + key + " while generating filter");
  }
}
