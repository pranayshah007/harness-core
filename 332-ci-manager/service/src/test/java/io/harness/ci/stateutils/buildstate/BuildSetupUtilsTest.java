/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.stateutils.buildstate;

import static io.harness.ci.executionplan.CIExecutionPlanTestHelper.GIT_CONNECTOR;
import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.rule.OwnerRule.HARSH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.joor.Reflect.on;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.beans.sweepingoutputs.K8PodDetails;
import io.harness.category.element.UnitTests;
import io.harness.ci.beans.entities.LogServiceConfig;
import io.harness.ci.beans.entities.TIServiceConfig;
import io.harness.ci.buildstate.BuildSetupUtils;
import io.harness.ci.buildstate.ConnectorUtils;
import io.harness.ci.buildstate.SecretUtils;
import io.harness.ci.executionplan.CIExecutionPlanTestHelper;
import io.harness.ci.executionplan.CIExecutionTestBase;
import io.harness.ci.integrationstage.K8InitializeTaskParamsBuilder;
import io.harness.ci.logserviceclient.CILogServiceUtils;
import io.harness.ci.tiserviceclient.TIServiceUtils;
import io.harness.delegate.beans.ci.CIInitializeTaskParams;
import io.harness.delegate.beans.ci.pod.ConnectorDetails;
import io.harness.delegate.beans.ci.pod.SecretVariableDetails;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.rule.Owner;
import io.harness.sto.beans.entities.STOServiceConfig;
import io.harness.stoserviceclient.STOServiceUtils;

import com.google.inject.Inject;
import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mock;

public class BuildSetupUtilsTest extends CIExecutionTestBase {
  @Inject private BuildSetupUtils buildSetupUtils;
  @Inject private CIExecutionPlanTestHelper ciExecutionPlanTestHelper;
  @Inject private K8InitializeTaskParamsBuilder k8InitializeTaskParamsBuilder;
  @Mock private ConnectorUtils connectorUtils;
  @Mock private SecretUtils secretUtils;
  @Mock private ExecutionSweepingOutputService executionSweepingOutputResolver;
  @Mock CILogServiceUtils logServiceUtils;
  @Mock TIServiceUtils tiServiceUtils;
  @Mock STOServiceUtils stoServiceUtils;

  private static final String CLUSTER_NAME = "K8";

  @Before
  public void setUp() {
    on(buildSetupUtils).set("k8InitializeTaskParamsBuilder", k8InitializeTaskParamsBuilder);
    on(k8InitializeTaskParamsBuilder).set("connectorUtils", connectorUtils);
    on(k8InitializeTaskParamsBuilder).set("executionSweepingOutputResolver", executionSweepingOutputResolver);
  }

  @Test
  @Owner(developers = HARSH)
  @Category(UnitTests.class)
  @Ignore("Recreate test object after pms integration")
  public void shouldFetBuildSetupTaskParams() {
    int buildID = 1;
    Map<String, String> setupAbstractions = new HashMap<>();
    setupAbstractions.put("accountId", "account");
    setupAbstractions.put("projectIdentifier", "project");
    setupAbstractions.put("orgIdentifier", "org");
    ExecutionMetadata executionMetadata = ExecutionMetadata.newBuilder()
                                              .setExecutionUuid(generateUuid())
                                              .setRunSequence(buildID)
                                              .setPipelineIdentifier("pipeline")
                                              .build();
    Ambiance ambiance =
        Ambiance.newBuilder().putAllSetupAbstractions(setupAbstractions).setMetadata(executionMetadata).build();

    HashMap<String, String> taskIds = new HashMap<>();
    HashMap<String, String> logKeys = new HashMap<>();
    when(connectorUtils.getConnectorDetails(any(), eq(GIT_CONNECTOR)))
        .thenReturn(ciExecutionPlanTestHelper.getGitConnector());
    when(connectorUtils.getConnectorDetailsWithConversionInfo(any(), any()))
        .thenReturn(ConnectorDetails.builder().identifier("connectorId").build());

    when(secretUtils.getSecretVariableDetails(any(), any())).thenReturn(SecretVariableDetails.builder().build());
    LogServiceConfig logServiceConfig = LogServiceConfig.builder().baseUrl("endpoint").globalToken("token").build();
    when(logServiceUtils.getLogServiceConfig()).thenReturn(logServiceConfig);
    when(logServiceUtils.getLogServiceToken(any())).thenReturn("token");
    TIServiceConfig tiServiceConfig = TIServiceConfig.builder().baseUrl("endpoint").globalToken("token").build();
    when(tiServiceUtils.getTiServiceConfig()).thenReturn(tiServiceConfig);
    when(tiServiceUtils.getTIServiceToken(any())).thenReturn("token");
    STOServiceConfig stoServiceConfig =
        STOServiceConfig.builder().baseUrl("endpoint").internalUrl("endpoint").globalToken("token").build();
    when(stoServiceUtils.getStoServiceConfig()).thenReturn(stoServiceConfig);
    when(stoServiceUtils.getSTOServiceToken(any())).thenReturn("token");
    when(executionSweepingOutputResolver.resolve(any(), any()))
        .thenReturn(K8PodDetails.builder().stageID("stage").build());

    CIInitializeTaskParams buildSetupTaskParams = buildSetupUtils.getBuildSetupTaskParams(
        ciExecutionPlanTestHelper.getExpectedLiteEngineTaskInfoOnFirstPodWithSetCallbackId(), ambiance, "test");
    assertThat(buildSetupTaskParams).isNotNull();
    verify(logServiceUtils, times(1)).getLogServiceConfig();
    verify(logServiceUtils, times(1)).getLogServiceToken(any());
    verify(tiServiceUtils, times(1)).getTiServiceConfig();
    verify(tiServiceUtils, times(1)).getTIServiceToken(any());
    verify(stoServiceUtils, times(1)).getStoServiceConfig();
    verify(stoServiceUtils, times(1)).getSTOServiceToken(any());
  }

  @Test
  @Owner(developers = HARSH)
  @Category(UnitTests.class)
  @Ignore("Recreate test object after pms integration")
  public void shouldFetBuildSetupTaskParamsWithAccountConnector() {
    int buildID = 1;
    Map<String, String> setupAbstractions = new HashMap<>();
    setupAbstractions.put("accountId", "account");
    setupAbstractions.put("projectIdentifier", "project");
    setupAbstractions.put("orgIdentifier", "org");
    ExecutionMetadata executionMetadata = ExecutionMetadata.newBuilder()
                                              .setExecutionUuid(generateUuid())
                                              .setRunSequence(buildID)
                                              .setPipelineIdentifier("pipeline")
                                              .build();
    Ambiance ambiance =
        Ambiance.newBuilder().putAllSetupAbstractions(setupAbstractions).setMetadata(executionMetadata).build();

    HashMap<String, String> taskIds = new HashMap<>();
    HashMap<String, String> logKeys = new HashMap<>();
    when(connectorUtils.getConnectorDetails(any(), eq(GIT_CONNECTOR)))
        .thenReturn(ciExecutionPlanTestHelper.getGitAccountConnector());
    when(connectorUtils.getConnectorDetailsWithConversionInfo(any(), any()))
        .thenReturn(ConnectorDetails.builder().identifier("connectorId").build());

    when(secretUtils.getSecretVariableDetails(any(), any())).thenReturn(SecretVariableDetails.builder().build());
    LogServiceConfig logServiceConfig = LogServiceConfig.builder().baseUrl("endpoint").globalToken("token").build();
    when(logServiceUtils.getLogServiceConfig()).thenReturn(logServiceConfig);
    when(logServiceUtils.getLogServiceToken(any())).thenReturn("token");
    TIServiceConfig tiServiceConfig = TIServiceConfig.builder().baseUrl("endpoint").globalToken("token").build();
    when(tiServiceUtils.getTiServiceConfig()).thenReturn(tiServiceConfig);
    when(tiServiceUtils.getTIServiceToken(any())).thenReturn("token");
    STOServiceConfig stoServiceConfig = STOServiceConfig.builder().baseUrl("endpoint").globalToken("token").build();
    when(stoServiceUtils.getStoServiceConfig()).thenReturn(stoServiceConfig);
    when(stoServiceUtils.getSTOServiceToken(any())).thenReturn("token");
    when(executionSweepingOutputResolver.resolve(any(), any()))
        .thenReturn(K8PodDetails.builder().stageID("stage").build());

    CIInitializeTaskParams buildSetupTaskParams = buildSetupUtils.getBuildSetupTaskParams(
        ciExecutionPlanTestHelper.getExpectedLiteEngineTaskInfoOnFirstPodWithSetCallbackIdReponameSet(), ambiance,
        "test");
    assertThat(buildSetupTaskParams).isNotNull();
    verify(logServiceUtils, times(1)).getLogServiceConfig();
    verify(logServiceUtils, times(1)).getLogServiceToken(any());
    verify(tiServiceUtils, times(1)).getTiServiceConfig();
    verify(tiServiceUtils, times(1)).getTIServiceToken(any());
    verify(stoServiceUtils, times(1)).getStoServiceConfig();
    verify(stoServiceUtils, times(1)).getSTOServiceToken(any());
  }
}
