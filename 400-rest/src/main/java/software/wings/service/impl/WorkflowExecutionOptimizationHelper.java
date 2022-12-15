/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.beans.SearchFilter.Operator.IN;

import static software.wings.beans.Environment.EnvironmentKeys;
import static software.wings.beans.Service.ServiceKeys;

import static java.util.Arrays.asList;

import io.harness.beans.FeatureName;
import io.harness.beans.PageRequest;
import io.harness.beans.SearchFilter;
import io.harness.beans.SearchFilter.SearchFilterBuilder;
import io.harness.ff.FeatureFlagService;
import io.harness.persistence.HPersistence;

import software.wings.beans.Environment;
import software.wings.beans.Pipeline;
import software.wings.beans.Pipeline.PipelineKeys;
import software.wings.beans.Service;
import software.wings.beans.Workflow;
import software.wings.beans.Workflow.WorkflowKeys;
import software.wings.beans.WorkflowExecution;
import software.wings.beans.WorkflowExecution.WorkflowExecutionKeys;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.DatastoreImpl;
import org.mongodb.morphia.mapping.Mapper;

@Singleton
@Slf4j
public class WorkflowExecutionOptimizationHelper {
  @Inject HPersistence hPersistence;
  @Inject FeatureFlagService featureFlagService;

  public void enforceAppIdFromChildrenEntities(PageRequest<WorkflowExecution> pageRequest, String accountId) {
    if (!featureFlagService.isEnabled(FeatureName.SPG_OPTIMIZE_WORKFLOW_EXECUTIONS_LISTING, accountId)) {
      return;
    }

    Set<String> appIds = new HashSet<>();

    PageRequest<WorkflowExecution> dummyPageRequest = populatePageFilters(pageRequest);
    dummyPageRequest.getFilters().forEach(filter -> {
      if (WorkflowExecutionKeys.envIds.equals(filter.getFieldName())) {
        List<Environment> environments = hPersistence.createQuery(Environment.class)
                                             .filter(EnvironmentKeys.accountId, accountId)
                                             .field(EnvironmentKeys.uuid)
                                             .in(asList(filter.getFieldValues()))
                                             .asList();
        environments.forEach(environment -> appIds.add(environment.getAppId()));
      } else if (WorkflowExecutionKeys.serviceIds.equals(filter.getFieldName())) {
        List<Service> services = hPersistence.createQuery(Service.class)
                                     .filter(ServiceKeys.accountId, accountId)
                                     .field(ServiceKeys.uuid)
                                     .in(asList(filter.getFieldValues()))
                                     .asList();
        services.forEach(service -> appIds.add(service.getAppId()));
      } else if (WorkflowExecutionKeys.pipelineSummary_pipelineId.equals(filter.getFieldName())) {
        List<Pipeline> pipelines = hPersistence.createQuery(Pipeline.class)
                                       .filter(PipelineKeys.accountId, accountId)
                                       .field(PipelineKeys.uuid)
                                       .in(asList(filter.getFieldValues()))
                                       .asList();
        pipelines.forEach(pipeline -> appIds.add(pipeline.getAppId()));
      } else if (WorkflowExecutionKeys.workflowId.equals(filter.getFieldName())) {
        List<Workflow> workflows = hPersistence.createQuery(Workflow.class)
                                       .filter(WorkflowKeys.accountId, accountId)
                                       .field(WorkflowKeys.uuid)
                                       .in(asList(filter.getFieldValues()))
                                       .asList();
        workflows.forEach(workflow -> appIds.add(workflow.getAppId()));
      }
    });

    if (!appIds.isEmpty()) {
      final SearchFilterBuilder filterBuilder = SearchFilter.builder();
      filterBuilder.fieldName(WorkflowExecutionKeys.appId).fieldValues(appIds.toArray()).op(IN);
      List<SearchFilter> searchFilters = pageRequest.getFilters();
      searchFilters.add(filterBuilder.build());
      pageRequest.setFilters(searchFilters);
    }
  }

  private PageRequest<WorkflowExecution> populatePageFilters(PageRequest<WorkflowExecution> pageRequest) {
    Mapper mapper = ((DatastoreImpl) hPersistence.getDatastore(WorkflowExecution.class)).getMapper();
    PageRequest<WorkflowExecution> copiedPageRequest = pageRequest.deepCopy();
    copiedPageRequest.populateFilters(
        copiedPageRequest.getUriInfo().getQueryParameters(), mapper.getMappedClass(WorkflowExecution.class), mapper);
    return copiedPageRequest;
  }
}
