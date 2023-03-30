/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.integrationstage;

import static io.harness.ci.integrationstage.K8InitializeTaskUtilsHelper.STAGE_ID;
import static io.harness.ci.integrationstage.K8InitializeTaskUtilsHelper.getAddonContainer;
import static io.harness.ci.integrationstage.K8InitializeTaskUtilsHelper.getLiteEngineContainer;
import static io.harness.rule.OwnerRule.SHUBHAM;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.beans.steps.stepinfo.InitializeStepInfo;
import io.harness.beans.sweepingoutputs.ContextElement;
import io.harness.beans.sweepingoutputs.K8PodDetails;
import io.harness.beans.sweepingoutputs.StageDetails;
import io.harness.beans.yaml.extended.infrastrucutre.OSType;
import io.harness.category.element.UnitTests;
import io.harness.ci.buildstate.CodebaseUtils;
import io.harness.ci.buildstate.ConnectorUtils;
import io.harness.ci.buildstate.SecretUtils;
import io.harness.ci.buildstate.providers.InternalContainerParamsProvider;
import io.harness.ci.executionplan.CIExecutionTestBase;
import io.harness.ci.utils.HarnessImageUtils;
import io.harness.delegate.beans.ci.k8s.CIK8InitializeTaskParams;
import io.harness.delegate.beans.ci.pod.ContainerSecurityContext;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.rule.Owner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class K8InitializeTaskParamsBuilderTest extends CIExecutionTestBase {
  @InjectMocks private K8InitializeTaskParamsBuilder k8InitializeTaskParamsBuilder;
  @Mock private ConnectorUtils connectorUtils;
  @Mock private K8InitializeStepUtils k8InitializeStepUtils;
  @Mock private K8InitializeServiceUtils k8InitializeServiceUtils;
  @Mock private ExecutionSweepingOutputService executionSweepingOutputResolver;
  @Mock private HarnessImageUtils harnessImageUtils;
  @Mock private InternalContainerParamsProvider internalContainerParamsProvider;
  @Mock private SecretUtils secretUtils;
  @Mock private CodebaseUtils codebaseUtils;
  @Mock private K8InitializeTaskUtils k8InitializeTaskUtils;

  private Ambiance ambiance;
  private static final String accountId = "test";
  private static final String podName = "test";

  @Before
  public void setUp() {
    Map<String, String> setupAbstractions = new HashMap<>();
    setupAbstractions.put("accountId", accountId);
    ambiance = Ambiance.newBuilder().putAllSetupAbstractions(setupAbstractions).build();
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = SHUBHAM)
  @Category(UnitTests.class)
  public void testGetK8InitializeTaskParams() {
    InitializeStepInfo initializeStepInfo = K8InitializeTaskUtilsHelper.getDirectK8Step();
    K8PodDetails k8PodDetails = K8PodDetails.builder().accountId(accountId).stageID(STAGE_ID).build();

    when(executionSweepingOutputResolver.resolveOptional(any(), any()))
        .thenReturn(OptionalSweepingOutput.builder().found(false).build());
    when(executionSweepingOutputResolver.resolve(
             ambiance, RefObjectUtils.getSweepingOutputRefObject(ContextElement.podDetails)))
        .thenReturn(k8PodDetails);
    when(executionSweepingOutputResolver.resolveOptional(
             ambiance, RefObjectUtils.getSweepingOutputRefObject(ContextElement.stageDetails)))
        .thenReturn(OptionalSweepingOutput.builder()
                        .found(true)
                        .output(StageDetails.builder().stageRuntimeID("test").build())
                        .build());
    when(k8InitializeTaskUtils.generatePodName(STAGE_ID)).thenReturn(podName);
    when(k8InitializeTaskUtils.getBuildLabels(ambiance, k8PodDetails)).thenReturn(new HashMap<>());
    when(k8InitializeTaskUtils.getSharedPaths(any())).thenReturn(new ArrayList<>());
    when(k8InitializeTaskUtils.getVolumeToMountPath(any(), any())).thenReturn(new HashMap<>());
    when(k8InitializeTaskUtils.getOS(any())).thenReturn(OSType.Linux);
    when(k8InitializeTaskUtils.getLogServiceEnvVariables(any(), any())).thenReturn(new HashMap<>());
    when(k8InitializeTaskUtils.getTIServiceEnvVariables(any())).thenReturn(new HashMap<>());
    when(k8InitializeTaskUtils.getSTOServiceEnvVariables(any())).thenReturn(new HashMap<>());
    when(codebaseUtils.getGitEnvVariables(any(), any(), eq(false))).thenReturn(new HashMap<>());
    when(k8InitializeTaskUtils.getCommonStepEnvVariables(any(), any(), any(), any(), any(), any()))
        .thenReturn(new HashMap<>());
    when(k8InitializeTaskUtils.getCacheEnvironmentVariable()).thenReturn(new HashMap<>());
    when(k8InitializeTaskUtils.getWorkDir()).thenReturn("/harness");
    when(k8InitializeTaskUtils.getCtrSecurityContext(any())).thenReturn(ContainerSecurityContext.builder().build());
    when(internalContainerParamsProvider.getSetupAddonContainerParams(any(), any(), any(), any(), any(), any()))
        .thenReturn(getAddonContainer());
    when(internalContainerParamsProvider.getLiteEngineContainerParams(
             any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
        .thenReturn(getLiteEngineContainer());
    when(k8InitializeStepUtils.getStageRequest(any(), any())).thenReturn(Pair.of(1024, 1024));
    when(k8InitializeServiceUtils.createServiceContainerDefinitions(any(), any(), any())).thenReturn(new ArrayList<>());
    when(
        k8InitializeStepUtils.createStepContainerDefinitions(any(), any(), any(), any(), any(), any(), any(), anyInt()))
        .thenReturn(Arrays.asList(K8InitializeTaskUtilsHelper.getRunStepContainer(0)));
    doNothing().when(k8InitializeTaskUtils).consumeSweepingOutput(any(), any(), any());
    doNothing().when(k8InitializeTaskUtils).consumeSweepingOutput(any(), any(), any());

    CIK8InitializeTaskParams response =
        k8InitializeTaskParamsBuilder.getK8InitializeTaskParams(initializeStepInfo, ambiance, "");
    assertThat(response.getCik8PodParams().getName()).isEqualTo(podName);
    verify(k8InitializeStepUtils, times(1))
        .createStepContainerDefinitions(any(), any(), any(), any(), any(), any(), any(), anyInt());
  }
}
