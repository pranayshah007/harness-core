/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.integrationstage;

import static io.harness.rule.OwnerRule.SHUBHAM;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

import io.harness.beans.steps.stepinfo.InitializeStepInfo;
import io.harness.beans.sweepingoutputs.ContextElement;
import io.harness.beans.sweepingoutputs.StageDetails;
import io.harness.beans.yaml.extended.infrastrucutre.OSType;
import io.harness.category.element.UnitTests;
import io.harness.ci.beans.entities.LogServiceConfig;
import io.harness.ci.beans.entities.TIServiceConfig;
import io.harness.delegate.beans.ci.vm.CIVmInitializeTaskParams;
import io.harness.executionplan.CIExecutionTestBase;
import io.harness.ff.CIFeatureFlagService;
import io.harness.logserviceclient.CILogServiceUtils;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.rule.Owner;
import io.harness.stateutils.buildstate.CodebaseUtils;
import io.harness.stateutils.buildstate.ConnectorUtils;
import io.harness.sto.beans.entities.STOServiceConfig;
import io.harness.stoserviceclient.STOServiceUtils;
import io.harness.tiserviceclient.TIServiceUtils;
import io.harness.util.CIVmSecretEvaluator;

import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class VmInitializeTaskParamsBuilderTest extends CIExecutionTestBase {
  @Mock private ExecutionSweepingOutputService executionSweepingOutputResolver;
  @Mock CILogServiceUtils logServiceUtils;
  @Mock private ExecutionSweepingOutputService executionSweepingOutputService;
  @Mock TIServiceUtils tiServiceUtils;
  @Mock STOServiceUtils stoServiceUtils;
  @Mock CodebaseUtils codebaseUtils;
  @Mock ConnectorUtils connectorUtils;
  @Mock CIVmSecretEvaluator ciVmSecretEvaluator;
  @Mock private CIFeatureFlagService featureFlagService;

  @Mock private VmInitializeUtils vmInitializeUtils;
  @InjectMocks VmInitializeTaskParamsBuilder vmInitializeTaskParamsBuilder;

  private Ambiance ambiance;
  private static final String accountId = "test";

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
  public void getVmInitializeTaskParams() {
    InitializeStepInfo initializeStepInfo = VmInitializeTaskParamsHelper.getInitializeStep();
    Map<String, String> volToMountPath = new HashMap<>();
    volToMountPath.put("shared-0", "/tmp");
    volToMountPath.put("harness", "/harness");

    String stageRuntimeId = "test";

    CIVmInitializeTaskParams expected = null;

    doNothing().when(vmInitializeUtils).validateStageConfig(any(), any());
    when(vmInitializeUtils.getOS(initializeStepInfo.getInfrastructure())).thenReturn(OSType.Linux);
    when(vmInitializeUtils.getVolumeToMountPath(any(), any())).thenReturn(volToMountPath);
    when(executionSweepingOutputService.resolveOptional(any(), any()))
        .thenReturn(OptionalSweepingOutput.builder().found(false).build());
    when(executionSweepingOutputResolver.consume(any(), any(), any(), any())).thenReturn("");
    when(executionSweepingOutputResolver.resolveOptional(
             ambiance, RefObjectUtils.getSweepingOutputRefObject(ContextElement.stageDetails)))
        .thenReturn(OptionalSweepingOutput.builder()
                        .found(true)
                        .output(StageDetails.builder().stageRuntimeID(stageRuntimeId).build())
                        .build());

    Map<String, String> m = new HashMap<>();
    when(codebaseUtils.getGitConnector(AmbianceUtils.getNgAccess(ambiance), initializeStepInfo.getCiCodebase(),
             initializeStepInfo.isSkipGitClone()))
        .thenReturn(null);
    when(codebaseUtils.getCodebaseVars(any(), any())).thenReturn(m);
    when(codebaseUtils.getGitEnvVariables(null, initializeStepInfo.getCiCodebase())).thenReturn(m);

    when(logServiceUtils.getLogServiceConfig()).thenReturn(LogServiceConfig.builder().baseUrl("1.1.1.1").build());
    when(logServiceUtils.getLogServiceToken(any())).thenReturn("test");
    when(tiServiceUtils.getTiServiceConfig()).thenReturn(TIServiceConfig.builder().baseUrl("1.1.1.2").build());
    when(tiServiceUtils.getTIServiceToken(any())).thenReturn("test");
    when(stoServiceUtils.getStoServiceConfig()).thenReturn(STOServiceConfig.builder().baseUrl("1.1.1.3").build());
    when(stoServiceUtils.getSTOServiceToken(any())).thenReturn("test");
    when(featureFlagService.isEnabled(any(), any())).thenReturn(false);

    CIVmInitializeTaskParams response =
        vmInitializeTaskParamsBuilder.getVmInitializeTaskParams(initializeStepInfo, ambiance, "");
    assertThat(response.getStageRuntimeId()).isEqualTo(stageRuntimeId);
  }
}