/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.outcome;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.beans.PageRequest.PageRequestBuilder.aPageRequest;
import static io.harness.beans.SearchFilter.Operator.IN;
import static io.harness.data.structure.CollectionUtils.emptyIfNull;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.FeatureName;
import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.ff.FeatureFlagService;

import software.wings.beans.ElementExecutionSummary;
import software.wings.beans.Environment;
import software.wings.beans.InfrastructureMapping;
import software.wings.beans.WorkflowExecution;
import software.wings.graphql.datafetcher.AbstractConnectionDataFetcher;
import software.wings.graphql.datafetcher.environment.EnvironmentController;
import software.wings.graphql.datafetcher.execution.ExecutionController;
import software.wings.graphql.datafetcher.execution.WorkflowExecutionController;
import software.wings.graphql.schema.query.QLEnvironmentQueryParameters.QLEnvironmentQueryParametersKeys;
import software.wings.graphql.schema.query.QLExecutionQueryParameters.QLExecutionQueryParametersKeys;
import software.wings.graphql.schema.query.QLInfrastructureDefinitionQueryParameters.QLInfrastructureDefinitionQueryParametersKeys;
import software.wings.graphql.schema.query.QLOutcomesQueryParameters;
import software.wings.graphql.schema.query.QLServiceQueryParameters.QLServiceQueryParametersKeys;
import software.wings.graphql.schema.type.QLDeploymentOutcome;
import software.wings.graphql.schema.type.QLDeploymentOutcome.QLDeploymentOutcomeBuilder;
import software.wings.graphql.schema.type.QLEnvironment;
import software.wings.graphql.schema.type.QLEnvironment.QLEnvironmentBuilder;
import software.wings.graphql.schema.type.QLOutcomeConnection;
import software.wings.graphql.schema.type.QLOutcomeConnection.QLOutcomeConnectionBuilder;
import software.wings.graphql.schema.type.QLPageInfo;
import software.wings.graphql.schema.type.QLPageInfo.QLPageInfoBuilder;
import software.wings.security.PermissionAttribute.PermissionType;
import software.wings.security.annotations.AuthRule;
import software.wings.service.intfc.InfrastructureMappingService;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDC)
@Slf4j
@TargetModule(HarnessModule._380_CG_GRAPHQL)
public class OutcomeConnectionDataFetcher
    extends AbstractConnectionDataFetcher<QLOutcomeConnection, QLOutcomesQueryParameters> {
  @Inject private WorkflowExecutionController workflowExecutionController;
  @Inject private ExecutionController executionController;
  @Inject private FeatureFlagService featureFlagService;
  @Inject private InfrastructureMappingService infrastructureMappingService;

  @Override
  @AuthRule(permissionType = PermissionType.LOGGED_IN)
  public QLOutcomeConnection fetchConnection(QLOutcomesQueryParameters qlQuery) {
    WorkflowExecution execution = persistence.get(WorkflowExecution.class, qlQuery.getExecutionId());

    QLOutcomeConnectionBuilder connectionBuilder = QLOutcomeConnection.builder();
    QLPageInfoBuilder pageInfoBuilder = QLPageInfo.builder().hasMore(false).offset(0).limit(0).total(0);

    final Environment environment = persistence.get(Environment.class, execution.getEnvId());
    QLEnvironmentBuilder environmentBuilder = QLEnvironment.builder();
    if (environment != null) {
      EnvironmentController.populateEnvironment(environment, environmentBuilder);
    }
    if (featureFlagService.isEnabled(FeatureName.OUTCOME_GRAPHQL_WITH_INFRA_DEF, execution.getAccountId())) {
      getOutcomeWithInfraDef(qlQuery, execution, connectionBuilder);
    } else {
      if (isNotEmpty(execution.getServiceExecutionSummaries())) {
        pageInfoBuilder.total(execution.getServiceExecutionSummaries().size())
            .limit(execution.getServiceExecutionSummaries().size());
        for (ElementExecutionSummary summary : execution.getServiceExecutionSummaries()) {
          QLDeploymentOutcomeBuilder deployment = QLDeploymentOutcome.builder().context(
              ImmutableMap.<String, Object>builder()
                  .put(QLExecutionQueryParametersKeys.executionId, qlQuery.getExecutionId())
                  .put(QLServiceQueryParametersKeys.serviceId, summary.getContextElement().getUuid())
                  .put(QLEnvironmentQueryParametersKeys.environmentId, execution.getEnvId())
                  .build());
          connectionBuilder.node(deployment.build());
        }
      }
    }

    return connectionBuilder.pageInfo(pageInfoBuilder.build()).build();
  }

  private void getOutcomeWithInfraDef(
      QLOutcomesQueryParameters qlQuery, WorkflowExecution execution, QLOutcomeConnectionBuilder connectionBuilder) {
    final List<String> infraMappingIds = emptyIfNull(execution.getInfraMappingIds());
    final PageRequest pageRequest =
        aPageRequest().addFilter(InfrastructureMapping.ID, IN, infraMappingIds.toArray()).build();
    final PageResponse<InfrastructureMapping> infraMappings = infrastructureMappingService.list(pageRequest);
    for (InfrastructureMapping infraMapping : infraMappings) {
      QLDeploymentOutcomeBuilder deployment = QLDeploymentOutcome.builder().context(
          ImmutableMap.<String, Object>builder()
              .put(QLExecutionQueryParametersKeys.executionId, qlQuery.getExecutionId())
              .put(QLServiceQueryParametersKeys.serviceId, infraMapping.getServiceId())
              .put(QLEnvironmentQueryParametersKeys.environmentId, execution.getEnvId())
              .put(QLInfrastructureDefinitionQueryParametersKeys.infrastructureId,
                  infraMapping.getInfrastructureDefinitionId())
              .build());
      connectionBuilder.node(deployment.build());
    }
  }
}
