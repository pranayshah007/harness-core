/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.execution;

import static io.harness.rule.OwnerRule.DEEPAK_PUTHRAYA;
import static io.harness.rule.OwnerRule.INDER;
import static io.harness.rule.OwnerRule.LUCAS_SALES;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.harness.category.element.UnitTests;
import io.harness.exception.InvalidRequestException;
import io.harness.ff.FeatureFlagService;
import io.harness.rule.Owner;

import software.wings.WingsBaseTest;
import software.wings.beans.EntityType;
import software.wings.beans.Service;
import software.wings.beans.WorkflowExecution.WorkflowExecutionKeys;
import software.wings.dl.WingsMongoPersistence;
import software.wings.graphql.datafetcher.DataFetcherUtils;
import software.wings.graphql.datafetcher.tag.TagHelper;
import software.wings.graphql.schema.type.aggregation.QLIdFilter;
import software.wings.graphql.schema.type.aggregation.QLIdOperator;
import software.wings.graphql.schema.type.aggregation.QLNumberFilter;
import software.wings.graphql.schema.type.aggregation.QLNumberOperator;
import software.wings.graphql.schema.type.aggregation.QLTimeFilter;
import software.wings.graphql.schema.type.aggregation.QLTimeOperator;
import software.wings.graphql.schema.type.aggregation.deployment.QLDeploymentTagFilter;
import software.wings.graphql.schema.type.aggregation.deployment.QLDeploymentTagType;
import software.wings.graphql.schema.type.aggregation.tag.QLTagInput;

import com.google.inject.Inject;
import dev.morphia.query.FieldEnd;
import dev.morphia.query.Query;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;

public class ExecutionQueryHelperTest extends WingsBaseTest {
  @Mock private DataFetcherUtils utils;
  @Mock private TagHelper tagHelper;
  @Mock private FeatureFlagService featureFlagService;
  @Mock private WingsMongoPersistence wingsMongoPersistence;
  @Inject @InjectMocks private ExecutionQueryHelper executionQueryHelper;

  @Test
  @Owner(developers = LUCAS_SALES)
  @Category(UnitTests.class)
  public void shouldSetQueryFiltersCorrectly_optimizationFFEnabled() {
    QLBaseExecutionFilter filter = new QLBaseExecutionFilter();

    Query query = mock(Query.class);
    FieldEnd fieldEnd = Mockito.mock(FieldEnd.class);
    Mockito.when(query.field(any())).thenReturn(fieldEnd);
    doReturn(query).when(wingsMongoPersistence).createAnalyticsQuery(any());
    doReturn(query).when(query).project(anyString(), anyBoolean());
    doReturn(List.of(Service.builder().appId("appId").build())).when(query).asList();
    filter.setService(QLIdFilter.builder().operator(QLIdOperator.EQUALS).values(new String[] {"SERVICE"}).build());

    Query query2 = Mockito.mock(Query.class);
    FieldEnd fieldEnd2 = Mockito.mock(FieldEnd.class);
    Mockito.when(query2.field(any())).thenReturn(fieldEnd2);
    executionQueryHelper.setBaseQuery(Collections.singletonList(filter), query2, "ACCOUNT_ID");
    verify(query2, Mockito.times(1)).field(WorkflowExecutionKeys.appId);
  }

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void shouldSetQueryFiltersCorrectlyQLBaseExecutionFilter() {
    QLBaseExecutionFilter filter = new QLBaseExecutionFilter();
    filter.setExecution(QLIdFilter.builder().operator(QLIdOperator.EQUALS).values(new String[] {"EXEC"}).build());
    filter.setApplication(QLIdFilter.builder().operator(QLIdOperator.EQUALS).values(new String[] {"APP"}).build());
    filter.setService(QLIdFilter.builder().operator(QLIdOperator.EQUALS).values(new String[] {"SERVICE"}).build());
    filter.setCloudProvider(
        QLIdFilter.builder().operator(QLIdOperator.EQUALS).values(new String[] {"CLOUD_PROVIDER"}).build());
    filter.setEnvironment(QLIdFilter.builder().operator(QLIdOperator.EQUALS).values(new String[] {"ENV"}).build());
    filter.setStatus(QLIdFilter.builder().operator(QLIdOperator.EQUALS).values(new String[] {"STATUS"}).build());
    filter.setEndTime(QLTimeFilter.builder().operator(QLTimeOperator.EQUALS).value(12345L).build());
    filter.setStartTime(QLTimeFilter.builder().operator(QLTimeOperator.EQUALS).value(0L).build());
    filter.setCreationTime(QLTimeFilter.builder().operator(QLTimeOperator.EQUALS).value(123L).build());
    filter.setDuration(QLNumberFilter.builder().operator(QLNumberOperator.EQUALS).values(new Integer[] {100}).build());
    filter.setTag(QLDeploymentTagFilter.builder()
                      .entityType(QLDeploymentTagType.ENVIRONMENT)
                      .tags(Collections.singletonList(QLTagInput.builder().name("name").value("value").build()))
                      .build());
    filter.setTriggeredBy(QLIdFilter.builder().operator(QLIdOperator.EQUALS).values(new String[] {"TRIG_BY"}).build());
    filter.setTrigger(QLIdFilter.builder().operator(QLIdOperator.EQUALS).values(new String[] {"TRIGGER"}).build());
    filter.setWorkflow(QLIdFilter.builder().operator(QLIdOperator.EQUALS).values(new String[] {"WF"}).build());
    filter.setPipeline(QLIdFilter.builder().operator(QLIdOperator.EQUALS).values(new String[] {"PIPELINE"}).build());
    filter.setArtifactBuildNo(
        QLIdFilter.builder().operator(QLIdOperator.EQUALS).values(new String[] {"ARTIFACT_ID"}).build());
    filter.setHelmChartVersion(
        QLIdFilter.builder().operator(QLIdOperator.EQUALS).values(new String[] {"HELM_CHART_VERSION"}).build());

    Query query = Mockito.mock(Query.class);
    FieldEnd fieldEnd = Mockito.mock(FieldEnd.class);
    Mockito.when(query.field(any())).thenReturn(fieldEnd);
    executionQueryHelper.setBaseQuery(Collections.singletonList(filter), query, "ACCOUNT_ID");
    Mockito.verify(query, Mockito.times(1)).field(WorkflowExecutionKeys.uuid);
    Mockito.verify(query, Mockito.times(1)).field(WorkflowExecutionKeys.appId);
    Mockito.verify(query, Mockito.times(1)).field(WorkflowExecutionKeys.deployedCloudProviders);
    Mockito.verify(query, Mockito.times(1)).field(WorkflowExecutionKeys.duration);
    Mockito.verify(query, Mockito.times(1)).field(WorkflowExecutionKeys.endTs);
    Mockito.verify(query, Mockito.times(2)).field(WorkflowExecutionKeys.workflowId);
    Mockito.verify(query, Mockito.times(1)).field(WorkflowExecutionKeys.serviceIds);
    Mockito.verify(query, Mockito.times(1)).field(WorkflowExecutionKeys.startTs);
    Mockito.verify(query, Mockito.times(1)).field(WorkflowExecutionKeys.status);
    Mockito.verify(query, Mockito.times(1)).field(WorkflowExecutionKeys.deploymentTriggerId);
    Mockito.verify(query, Mockito.times(1)).field(WorkflowExecutionKeys.triggeredByID);
    Mockito.verify(query, Mockito.times(1)).field(WorkflowExecutionKeys.createdAt);
    Mockito.verify(query, Mockito.times(2)).field(WorkflowExecutionKeys.envIds);
    Mockito.verify(query, Mockito.never()).field(WorkflowExecutionKeys.pipelineExecutionId);
    Mockito.verify(query, Mockito.times(1)).field(WorkflowExecutionKeys.executionArgs_artifacts_buildNo);
    Mockito.verify(query, Mockito.times(1)).field(WorkflowExecutionKeys.executionArgs_helmCharts_displayName);
  }

  @Test
  @Owner(developers = DEEPAK_PUTHRAYA)
  @Category(UnitTests.class)
  public void shouldSetQueryFiltersCorrectlyQLExecutionFilter() {
    QLExecutionFilter filter = new QLExecutionFilter();
    filter.setExecution(QLIdFilter.builder().operator(QLIdOperator.EQUALS).values(new String[] {"EXEC"}).build());
    filter.setApplication(QLIdFilter.builder().operator(QLIdOperator.EQUALS).values(new String[] {"APP"}).build());
    filter.setService(QLIdFilter.builder().operator(QLIdOperator.EQUALS).values(new String[] {"SERVICE"}).build());
    filter.setCloudProvider(
        QLIdFilter.builder().operator(QLIdOperator.EQUALS).values(new String[] {"CLOUD_PROVIDER"}).build());
    filter.setEnvironment(QLIdFilter.builder().operator(QLIdOperator.EQUALS).values(new String[] {"ENV"}).build());
    filter.setStatus(QLIdFilter.builder().operator(QLIdOperator.EQUALS).values(new String[] {"STATUS"}).build());
    filter.setEndTime(QLTimeFilter.builder().operator(QLTimeOperator.EQUALS).value(12345L).build());
    filter.setStartTime(QLTimeFilter.builder().operator(QLTimeOperator.EQUALS).value(0L).build());
    filter.setCreationTime(QLTimeFilter.builder().operator(QLTimeOperator.EQUALS).value(123L).build());
    filter.setDuration(QLNumberFilter.builder().operator(QLNumberOperator.EQUALS).values(new Integer[] {100}).build());
    filter.setTag(QLDeploymentTagFilter.builder()
                      .entityType(QLDeploymentTagType.ENVIRONMENT)
                      .tags(Collections.singletonList(QLTagInput.builder().name("name").value("value").build()))
                      .build());
    filter.setTriggeredBy(QLIdFilter.builder().operator(QLIdOperator.EQUALS).values(new String[] {"TRIG_BY"}).build());
    filter.setTrigger(QLIdFilter.builder().operator(QLIdOperator.EQUALS).values(new String[] {"TRIGGER"}).build());
    filter.setWorkflow(QLIdFilter.builder().operator(QLIdOperator.EQUALS).values(new String[] {"WF"}).build());
    filter.setPipeline(QLIdFilter.builder().operator(QLIdOperator.EQUALS).values(new String[] {"PIPELINE"}).build());
    filter.setPipelineExecutionId(
        QLIdFilter.builder().operator(QLIdOperator.EQUALS).values(new String[] {"PIPE_EXEC_ID"}).build());
    filter.setArtifactBuildNo(
        QLIdFilter.builder().operator(QLIdOperator.EQUALS).values(new String[] {"ARTIFACT_ID"}).build());
    filter.setHelmChartVersion(
        QLIdFilter.builder().operator(QLIdOperator.EQUALS).values(new String[] {"HELM_CHART_VERSION"}).build());

    Query query = Mockito.mock(Query.class);
    FieldEnd fieldEnd = Mockito.mock(FieldEnd.class);
    Mockito.when(query.field(any())).thenReturn(fieldEnd);
    executionQueryHelper.setQuery(Collections.singletonList(filter), query, "ACCOUNT_ID");
    Mockito.verify(query, Mockito.times(1)).field(WorkflowExecutionKeys.uuid);
    Mockito.verify(query, Mockito.times(1)).field(WorkflowExecutionKeys.appId);
    Mockito.verify(query, Mockito.times(1)).field(WorkflowExecutionKeys.deployedCloudProviders);
    Mockito.verify(query, Mockito.times(1)).field(WorkflowExecutionKeys.duration);
    Mockito.verify(query, Mockito.times(1)).field(WorkflowExecutionKeys.endTs);
    Mockito.verify(query, Mockito.times(2)).field(WorkflowExecutionKeys.workflowId);
    Mockito.verify(query, Mockito.times(1)).field(WorkflowExecutionKeys.startTs);
    Mockito.verify(query, Mockito.times(1)).field(WorkflowExecutionKeys.status);
    Mockito.verify(query, Mockito.times(1)).field(WorkflowExecutionKeys.deploymentTriggerId);
    Mockito.verify(query, Mockito.times(1)).field(WorkflowExecutionKeys.triggeredByID);
    Mockito.verify(query, Mockito.times(1)).field(WorkflowExecutionKeys.createdAt);
    Mockito.verify(query, Mockito.times(2)).field(WorkflowExecutionKeys.envIds);
    Mockito.verify(query, Mockito.times(1)).field(WorkflowExecutionKeys.pipelineExecutionId);
    Mockito.verify(utils, Mockito.times(1))
        .setIdFilter(eq(query.field(WorkflowExecutionKeys.pipelineExecutionId)),
            eq(QLIdFilter.builder().operator(QLIdOperator.EQUALS).values(new String[] {"PIPE_EXEC_ID"}).build()));
    Mockito.verify(query, Mockito.times(1)).field(WorkflowExecutionKeys.executionArgs_artifacts_buildNo);
    Mockito.verify(query, Mockito.times(1)).field(WorkflowExecutionKeys.executionArgs_helmCharts_displayName);
  }

  @Test
  @Owner(developers = INDER)
  @Category(UnitTests.class)
  public void shouldGetEntityType() {
    assertThatThrownBy(() -> executionQueryHelper.getEntityType(null))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage("Please provide entity type");

    QLDeploymentTagType entityType = QLDeploymentTagType.APPLICATION;
    EntityType entityType1 = executionQueryHelper.getEntityType(entityType);
    assertThat(entityType1).isEqualTo(EntityType.APPLICATION);

    entityType = QLDeploymentTagType.SERVICE;
    entityType1 = executionQueryHelper.getEntityType(entityType);
    assertThat(entityType1).isEqualTo(EntityType.SERVICE);

    entityType = QLDeploymentTagType.ENVIRONMENT;
    entityType1 = executionQueryHelper.getEntityType(entityType);
    assertThat(entityType1).isEqualTo(EntityType.ENVIRONMENT);

    entityType = QLDeploymentTagType.DEPLOYMENT;
    entityType1 = executionQueryHelper.getEntityType(entityType);
    assertThat(entityType1).isEqualTo(EntityType.DEPLOYMENT);
  }
}
