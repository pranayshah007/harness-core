/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.migrations.timescaledb.data;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.RUSHABH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.beans.EmbeddedUser;
import io.harness.beans.EnvironmentType;
import io.harness.beans.ExecutionStatus;
import io.harness.category.element.UnitTests;
import io.harness.event.usagemetrics.UsageMetricsTestUtils.UsageMetricsTestKeys;
import io.harness.persistence.HPersistence;
import io.harness.rule.Owner;
import io.harness.timescaledb.TimeScaleDBService;

import software.wings.WingsBaseTest;
import software.wings.beans.EnvSummary;
import software.wings.beans.PipelineExecution.Builder;
import software.wings.beans.PipelineStageExecution;
import software.wings.beans.WorkflowExecution;
import software.wings.service.intfc.WorkflowService;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;

public class UpdateEnvSvcCPInDeploymentTest extends WingsBaseTest {
  @Mock TimeScaleDBService timeScaleDBService;
  @Mock ResultSet resultSet;
  @Inject WorkflowService workflowService;
  @Inject private HPersistence persistence;

  @Spy @Inject @InjectMocks UpdateEnvSvcCPInDeployment updateEnvSvcCPInDeployment;

  @Test
  @Owner(developers = RUSHABH)
  @Category(UnitTests.class)
  public void testMigrate() throws SQLException {
    String workflowExecutionId = generateUuid();
    WorkflowExecution workflowExecution = createWorkflowExecution(0);
    persistence.save(workflowExecution);

    when(timeScaleDBService.isValid()).thenReturn(true);
    Connection connection = mock(Connection.class);
    when(timeScaleDBService.getDBConnection()).thenReturn(connection);
    PreparedStatement preparedStatement = mock(PreparedStatement.class);
    when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
    when(resultSet.next()).thenReturn(true, false);
    when(preparedStatement.executeQuery()).thenReturn(resultSet);
    when(resultSet.getString(1)).thenReturn(UsageMetricsTestKeys.EXECUTIONID + 0);
    when(resultSet.getString(2)).thenReturn(UsageMetricsTestKeys.APPID);

    boolean migrated = updateEnvSvcCPInDeployment.migrate();
    assertThat(migrated).isTrue();
    verify(preparedStatement, times(1)).execute();
  }

  @NotNull
  private WorkflowExecution createWorkflowExecution(int number) {
    return WorkflowExecution.builder()
        .uuid(UsageMetricsTestKeys.EXECUTIONID + number)
        .accountId(UsageMetricsTestKeys.ACCOUNTID)
        .appId(UsageMetricsTestKeys.APPID)
        .status(ExecutionStatus.SUCCESS)
        .workflowId(UsageMetricsTestKeys.WORKFLOWID)
        .startTs(System.currentTimeMillis() - 10 * 3600 * 1000L)
        .endTs(System.currentTimeMillis() - 9 * 3600 * 1000L)
        .cloudProviderIds(Lists.newArrayList(UsageMetricsTestKeys.CLOUDPROVIDER1, UsageMetricsTestKeys.CLOUDPROVIDER2))
        .environments(Lists.newArrayList(EnvSummary.builder()
                                             .environmentType(EnvironmentType.PROD)
                                             .uuid(UsageMetricsTestKeys.ENV1)
                                             .name(UsageMetricsTestKeys.ENV1NAME)
                                             .build()))
        .pipelineExecution(
            Builder.aPipelineExecution()
                .withPipelineId(UsageMetricsTestKeys.PIPELINEID)
                .withPipelineStageExecutions(
                    Arrays.asList(PipelineStageExecution.builder()
                                      .workflowExecutions(Arrays.asList(
                                          WorkflowExecution.builder()
                                              .envId(UsageMetricsTestKeys.ENV1)
                                              .envName(UsageMetricsTestKeys.ENV1NAME)
                                              .envType(EnvironmentType.PROD)
                                              .serviceIds(Arrays.asList(UsageMetricsTestKeys.SERVICE1))
                                              .cloudProviderIds(Lists.newArrayList(UsageMetricsTestKeys.CLOUDPROVIDER1))
                                              .build()))
                                      .build()))
                .build())
        .pipelineExecutionId(UsageMetricsTestKeys.PIPELINEEXECUTIONID)
        .serviceIds(Arrays.asList(UsageMetricsTestKeys.SERVICE1))
        .triggeredBy(EmbeddedUser.builder().uuid(UsageMetricsTestKeys.USER1).build())
        .deploymentTriggerId(UsageMetricsTestKeys.TRIGGER1)
        .duration(3600 * 1000L)
        .rollbackDuration(0L)
        .build();
  }
}
