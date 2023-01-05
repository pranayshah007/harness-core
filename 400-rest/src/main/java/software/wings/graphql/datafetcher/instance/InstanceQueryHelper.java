/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.instance;

import static io.harness.annotations.dev.HarnessTeam.DX;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;

import software.wings.beans.EntityType;
import software.wings.beans.Service;
import software.wings.beans.Service.ServiceKeys;
import software.wings.beans.Workflow;
import software.wings.beans.Workflow.WorkflowKeys;
import software.wings.beans.infrastructure.instance.Instance;
import software.wings.dl.WingsPersistence;
import software.wings.graphql.datafetcher.DataFetcherUtils;
import software.wings.graphql.datafetcher.tag.TagHelper;
import software.wings.graphql.schema.type.aggregation.QLIdFilter;
import software.wings.graphql.schema.type.aggregation.QLIdOperator;
import software.wings.graphql.schema.type.aggregation.QLStringFilter;
import software.wings.graphql.schema.type.aggregation.QLStringOperator;
import software.wings.graphql.schema.type.aggregation.QLTimeFilter;
import software.wings.graphql.schema.type.aggregation.environment.QLEnvironmentTypeFilter;
import software.wings.graphql.schema.type.aggregation.instance.QLInstanceFilter;
import software.wings.graphql.schema.type.aggregation.instance.QLInstanceTagFilter;
import software.wings.graphql.schema.type.aggregation.instance.QLInstanceTagType;
import software.wings.graphql.schema.type.aggregation.tag.QLTagInput;
import software.wings.graphql.schema.type.instance.QLInstanceType;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import dev.morphia.query.FieldEnd;
import dev.morphia.query.Query;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

/**
 * @author rktummala on 07/12/19
 */
@OwnedBy(DX)
@Singleton
@Slf4j
@TargetModule(HarnessModule._380_CG_GRAPHQL)
public class InstanceQueryHelper {
  @Inject protected DataFetcherUtils utils;
  @Inject protected TagHelper tagHelper;
  @Inject WingsPersistence wingsPersistence;

  public void setQuery(String accountId, List<QLInstanceFilter> filters, Query query) {
    if (isEmpty(filters)) {
      return;
    }

    filters.forEach(filter -> {
      FieldEnd<? extends Query<Instance>> field;

      if (filter.getApplication() != null) {
        field = query.field("appId");
        QLIdFilter applicationFilter = filter.getApplication();
        utils.setIdFilter(field, applicationFilter);
      }

      if (filter.getCloudProvider() != null) {
        field = query.field("computeProviderId");
        QLIdFilter cloudProviderFilter = filter.getCloudProvider();
        utils.setIdFilter(field, cloudProviderFilter);
      }

      if (filter.getEnvironment() != null) {
        field = query.field("envId");
        QLIdFilter envFilter = filter.getEnvironment();
        utils.setIdFilter(field, envFilter);
      }

      if (filter.getService() != null) {
        field = query.field("serviceId");
        QLIdFilter serviceFilter = filter.getService();
        utils.setIdFilter(field, serviceFilter);
      }

      if (filter.getCreatedAt() != null) {
        field = query.field("createdAt");
        QLTimeFilter createdAtFilter = filter.getCreatedAt();
        utils.setTimeFilter(field, createdAtFilter);
      }

      if (filter.getInstanceType() != null) {
        field = query.field("instanceType");
        QLInstanceType instanceTypeFilter = filter.getInstanceType();
        utils.setStringFilter(field,
            QLStringFilter.builder()
                .operator(QLStringOperator.EQUALS)
                .values(new String[] {instanceTypeFilter.name()})
                .build());
      }

      if (filter.getEnvironmentType() != null) {
        field = query.field("envType");
        QLEnvironmentTypeFilter envTypeFilter = filter.getEnvironmentType();
        utils.setEnumFilter(field, envTypeFilter);
      }

      if (filter.getDeploymentType() != null) {
        List<String> deploymentTypes = Arrays.stream(filter.getDeploymentType().getValues())
                                           .map(d -> d.name())
                                           .distinct()
                                           .collect(Collectors.toList());
        List<String> serviceIds = wingsPersistence.createQuery(Service.class)
                                      .filter(ServiceKeys.accountId, accountId)
                                      .field(ServiceKeys.deploymentType)
                                      .in(deploymentTypes)
                                      .asList()
                                      .stream()
                                      .map(service -> service.getUuid())
                                      .collect(Collectors.toList());
        if (!serviceIds.isEmpty()) {
          QLInstanceFilter newFilter =
              QLInstanceFilter.builder()
                  .service(
                      QLIdFilter.builder().operator(QLIdOperator.IN).values(serviceIds.toArray(new String[0])).build())
                  .build();
          field = query.field("serviceId");
          QLIdFilter newFilterService = newFilter.getService();
          utils.setIdFilter(field, newFilterService);
        }
      }

      if (filter.getOrchestrationWorkflowType() != null) {
        List<String> orchestrationWorkflowTypes = Arrays.stream(filter.getOrchestrationWorkflowType().getValues())
                                                      .map(d -> d.name())
                                                      .distinct()
                                                      .collect(Collectors.toList());
        List<String> workflowIds = wingsPersistence.createQuery(Workflow.class)
                                       .filter(WorkflowKeys.accountId, accountId)
                                       .field(WorkflowKeys.orchestrationWorkflowType)
                                       .in(orchestrationWorkflowTypes)
                                       .asList()
                                       .stream()
                                       .map(w -> w.getUuid())
                                       .collect(Collectors.toList());
        if (!workflowIds.isEmpty()) {
          QLInstanceFilter newFilter =
              QLInstanceFilter.builder()
                  .workflow(
                      QLIdFilter.builder().operator(QLIdOperator.IN).values(workflowIds.toArray(new String[0])).build())
                  .build();

          field = query.field("lastWorkflowExecutionId");
          QLIdFilter lastWorkflowExecutionIds = newFilter.getWorkflow();
          utils.setIdFilter(field, lastWorkflowExecutionIds);
        }
      }

      if (filter.getTag() != null) {
        QLInstanceTagFilter tagFilter = filter.getTag();
        List<QLTagInput> tags = tagFilter.getTags();
        Set<String> entityIds =
            tagHelper.getEntityIdsFromTags(accountId, tags, getEntityType(tagFilter.getEntityType()));
        switch (tagFilter.getEntityType()) {
          case APPLICATION:
            query.field("appId").in(entityIds);
            break;
          case SERVICE:
            query.field("serviceId").in(entityIds);
            break;
          case ENVIRONMENT:
            query.field("envId").in(entityIds);
            break;
          default:
            log.error("EntityType {} not supported in query", tagFilter.getEntityType());
            throw new InvalidRequestException("Error while compiling query", WingsException.USER);
        }
      }
    });
  }

  public EntityType getEntityType(QLInstanceTagType entityType) {
    switch (entityType) {
      case APPLICATION:
        return EntityType.APPLICATION;
      case SERVICE:
        return EntityType.SERVICE;
      case ENVIRONMENT:
        return EntityType.ENVIRONMENT;
      default:
        log.error("Unsupported entity type {} for tag ", entityType);
        throw new InvalidRequestException("Unsupported entity type " + entityType);
    }
  }
}
