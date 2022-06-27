/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.stateutils.buildstate;

import static io.harness.rule.OwnerRule.SHUBHAM;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;

import io.harness.beans.environment.VmBuildJobInfo;
import io.harness.beans.steps.stepinfo.InitializeStepInfo;
import io.harness.beans.sweepingoutputs.ContextElement;
import io.harness.beans.sweepingoutputs.StageDetails;
import io.harness.beans.yaml.extended.infrastrucutre.VmInfraYaml;
import io.harness.beans.yaml.extended.infrastrucutre.VmPoolYaml;
import io.harness.beans.yaml.extended.infrastrucutre.VmPoolYaml.VmPoolYamlSpec;
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
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.sto.beans.entities.STOServiceConfig;
import io.harness.stoserviceclient.STOServiceUtils;
import io.harness.tiserviceclient.TIServiceUtils;

import java.util.HashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class VmInitializeTaskUtilsTest extends CIExecutionTestBase {
  @Mock private ExecutionSweepingOutputService executionSweepingOutputResolver;
  @Mock ExecutionSweepingOutputService executionSweepingOutputService;
  @InjectMocks private VmInitializeTaskUtils vmInitializeTaskUtils;
  @Mock private CILogServiceUtils logServiceUtils;
  @Mock private TIServiceUtils tiServiceUtils;
  @Mock private STOServiceUtils stoServiceUtils;
  @Mock private CodebaseUtils codebaseUtils;
  @Mock private CIFeatureFlagService featureFlagService;

  private Ambiance ambiance;

  @Before
  public void setUp() {
    Map<String, String> setupAbstractions = new HashMap<>();
    setupAbstractions.put("accountId", "accountId");
    ambiance = Ambiance.newBuilder().putAllSetupAbstractions(setupAbstractions).build();
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = SHUBHAM)
  @Category(UnitTests.class)
  public void getInitializeTaskParamsWithName() {
    String poolName = "test";
    VmPoolYaml vmPoolYaml = VmPoolYaml.builder()
                                .spec(VmPoolYamlSpec.builder()
                                          .harnessImageConnectorRef(ParameterField.<String>builder().build())
                                          .poolName(ParameterField.createValueField(poolName))
                                          .build())
                                .build();

    String stageRuntimeId = "test";
    InitializeStepInfo initializeStepInfo = InitializeStepInfo.builder()
                                                .infrastructure(VmInfraYaml.builder().spec(vmPoolYaml).build())
                                                .buildJobEnvInfo(VmBuildJobInfo.builder().build())
                                                .build();

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
    CIVmInitializeTaskParams response = vmInitializeTaskUtils.getInitializeTaskParams(initializeStepInfo, ambiance, "");
    assertThat(response.getStageRuntimeId()).isEqualTo(stageRuntimeId);
  }

  @Test
  @Owner(developers = SHUBHAM)
  @Category(UnitTests.class)
  public void getInitializeTaskParamsWithId() {
    String poolId = "test";
    VmPoolYaml vmPoolYaml = VmPoolYaml.builder()
                                .spec(VmPoolYamlSpec.builder()
                                          .identifier(poolId)
                                          .harnessImageConnectorRef(ParameterField.<String>builder().build())
                                          .poolName(ParameterField.createValueField(null))
                                          .build())
                                .build();

    String stageRuntimeId = "test";
    InitializeStepInfo initializeStepInfo = InitializeStepInfo.builder()
                                                .infrastructure(VmInfraYaml.builder().spec(vmPoolYaml).build())
                                                .buildJobEnvInfo(VmBuildJobInfo.builder().build())
                                                .build();

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
    CIVmInitializeTaskParams response = vmInitializeTaskUtils.getInitializeTaskParams(initializeStepInfo, ambiance, "");
    assertThat(response.getStageRuntimeId()).isEqualTo(stageRuntimeId);
  }
}
