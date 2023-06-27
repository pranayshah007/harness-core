/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ci.integrationstage;

import static io.harness.rule.OwnerRule.SHUBHAM;
import static io.harness.rule.OwnerRule.VISTAAR;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

import io.harness.beans.FeatureName;
import io.harness.beans.execution.license.CILicenseService;
import io.harness.beans.steps.stepinfo.InitializeStepInfo;
import io.harness.beans.sweepingoutputs.ContextElement;
import io.harness.beans.sweepingoutputs.StageDetails;
import io.harness.beans.yaml.extended.infrastrucutre.OSType;
import io.harness.beans.yaml.extended.platform.ArchType;
import io.harness.beans.yaml.extended.platform.Platform;
import io.harness.category.element.UnitTests;
import io.harness.ci.beans.entities.LogServiceConfig;
import io.harness.ci.beans.entities.TIServiceConfig;
import io.harness.ci.buildstate.CodebaseUtils;
import io.harness.ci.buildstate.ConnectorUtils;
import io.harness.ci.config.CIExecutionServiceConfig;
import io.harness.ci.config.HostedVmConfig;
import io.harness.ci.executionplan.CIExecutionTestBase;
import io.harness.ci.ff.CIFeatureFlagService;
import io.harness.ci.logserviceclient.CILogServiceUtils;
import io.harness.ci.tiserviceclient.TIServiceUtils;
import io.harness.ci.utils.CIVmSecretEvaluator;
import io.harness.ci.utils.HostedVmSecretResolver;
import io.harness.delegate.beans.ci.vm.CIVmInitializeTaskParams;
import io.harness.delegate.beans.ci.vm.dlite.DliteVmInitializeTaskParams;
import io.harness.licensing.Edition;
import io.harness.licensing.LicenseType;
import io.harness.licensing.beans.summary.CILicenseSummaryDTO;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.data.OptionalSweepingOutput;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outputs.ExecutionSweepingOutputService;
import io.harness.pms.yaml.ParameterField;
import io.harness.rule.Owner;
import io.harness.ssca.client.SSCAServiceUtils;
import io.harness.sto.beans.entities.STOServiceConfig;
import io.harness.stoserviceclient.STOServiceUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
  @Mock CILicenseService ciLicenseService;
  @Mock private ExecutionSweepingOutputService executionSweepingOutputService;
  @Mock TIServiceUtils tiServiceUtils;
  @Mock STOServiceUtils stoServiceUtils;
  @Mock CodebaseUtils codebaseUtils;
  @Mock ConnectorUtils connectorUtils;
  @Mock CIVmSecretEvaluator ciVmSecretEvaluator;
  @Mock CIExecutionServiceConfig ciExecutionServiceConfig;
  @Mock HostedVmSecretResolver hostedVmSecretResolver;
  @Mock private CIFeatureFlagService featureFlagService;

  @Mock private VmInitializeUtils vmInitializeUtils;
  @InjectMocks VmInitializeTaskParamsBuilder vmInitializeTaskParamsBuilder;
  @Mock SSCAServiceUtils sscaServiceUtils;

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

    doNothing().when(vmInitializeUtils).validateStageConfig(any(), any());
    when(ciLicenseService.getLicenseSummary(any(String.class)))
        .thenReturn(CILicenseSummaryDTO.builder().licenseType(LicenseType.PAID).edition(Edition.FREE).build());
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
    when(codebaseUtils.getCodebaseVars(any(), any(), any())).thenReturn(m);
    when(
        codebaseUtils.getGitEnvVariables(null, initializeStepInfo.getCiCodebase(), initializeStepInfo.isSkipGitClone()))
        .thenReturn(m);

    when(logServiceUtils.getLogServiceConfig()).thenReturn(LogServiceConfig.builder().baseUrl("1.1.1.1").build());
    when(logServiceUtils.getLogServiceToken(any())).thenReturn("test");
    when(tiServiceUtils.getTiServiceConfig()).thenReturn(TIServiceConfig.builder().baseUrl("1.1.1.2").build());
    when(tiServiceUtils.getTIServiceToken(any())).thenReturn("test");
    when(stoServiceUtils.getStoServiceConfig()).thenReturn(STOServiceConfig.builder().baseUrl("1.1.1.3").build());
    when(stoServiceUtils.getSTOServiceToken(any())).thenReturn("test");
    when(featureFlagService.isEnabled(any(), any())).thenReturn(false);

    CIVmInitializeTaskParams response =
        vmInitializeTaskParamsBuilder.getDirectVmInitializeTaskParams(initializeStepInfo, ambiance);
    assertThat(response.getStageRuntimeId()).isEqualTo(stageRuntimeId);
  }

  @Test
  @Owner(developers = SHUBHAM)
  @Category(UnitTests.class)
  public void getHostedVmInitializeTaskParams() {
    InitializeStepInfo initializeStepInfo = VmInitializeTaskParamsHelper.getHostedVmInitializeStep();
    Map<String, String> volToMountPath = new HashMap<>();
    volToMountPath.put("shared-0", "/tmp");
    volToMountPath.put("harness", "/harness");

    String stageRuntimeId = "test";
    when(ciLicenseService.getLicenseSummary(any(String.class)))
        .thenReturn(CILicenseSummaryDTO.builder().licenseType(LicenseType.PAID).edition(Edition.FREE).build());
    doNothing().when(vmInitializeUtils).validateStageConfig(any(), any());
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
    when(codebaseUtils.getCodebaseVars(any(), any(), any())).thenReturn(m);
    when(
        codebaseUtils.getGitEnvVariables(null, initializeStepInfo.getCiCodebase(), initializeStepInfo.isSkipGitClone()))
        .thenReturn(m);

    when(logServiceUtils.getLogServiceConfig()).thenReturn(LogServiceConfig.builder().baseUrl("1.1.1.1").build());
    when(logServiceUtils.getLogServiceToken(any())).thenReturn("test");
    when(tiServiceUtils.getTiServiceConfig()).thenReturn(TIServiceConfig.builder().baseUrl("1.1.1.2").build());
    when(tiServiceUtils.getTIServiceToken(any())).thenReturn("test");
    when(stoServiceUtils.getStoServiceConfig()).thenReturn(STOServiceConfig.builder().baseUrl("1.1.1.3").build());
    when(stoServiceUtils.getSTOServiceToken(any())).thenReturn("test");
    when(featureFlagService.isEnabled(any(), any())).thenReturn(false);
    when(ciExecutionServiceConfig.getHostedVmConfig())
        .thenReturn(HostedVmConfig.builder().splitLinuxAmd64Pool(false).build());
    doNothing().when(hostedVmSecretResolver).resolve(any(), any());

    DliteVmInitializeTaskParams response =
        vmInitializeTaskParamsBuilder.getHostedVmInitializeTaskParams(initializeStepInfo, ambiance);
    assertThat(response.getSetupVmRequest().getId()).isEqualTo(stageRuntimeId);
    assertThat(response.getSetupVmRequest().getFallbackPoolIDs().isEmpty());
  }

  @Test
  @Owner(developers = VISTAAR)
  @Category(UnitTests.class)
  public void isBareMetalEnabledWithFFSet() {
    InitializeStepInfo initializeStepInfo = VmInitializeTaskParamsHelper.getHostedVmInitializeStep();
    String accountID = "account";
    Platform platform = Platform.builder()
                            .os(ParameterField.createValueField(OSType.Linux))
                            .arch(ParameterField.createValueField(ArchType.Amd64))
                            .build();

    when(featureFlagService.isEnabled(FeatureName.CI_ENABLE_BARE_METAL, accountID)).thenReturn(true);
    when(ciLicenseService.getLicenseSummary(any(String.class)))
        .thenReturn(CILicenseSummaryDTO.builder().licenseType(LicenseType.PAID).edition(Edition.FREE).build());
    boolean response = vmInitializeTaskParamsBuilder.isBareMetalEnabled(
        accountID, ParameterField.createValueField(platform), initializeStepInfo);
    assertThat(response).isEqualTo(true);
  }

  @Test
  @Owner(developers = VISTAAR)
  @Category(UnitTests.class)
  public void isBareMetalEnabledWithoutFFSet() {
    InitializeStepInfo initializeStepInfo = VmInitializeTaskParamsHelper.getHostedVmInitializeStep();
    String accountID = "account";
    List<String> internalAccounts = new ArrayList<>();
    internalAccounts.add("random-account");
    Platform platform = Platform.builder()
                            .os(ParameterField.createValueField(OSType.Linux))
                            .arch(ParameterField.createValueField(ArchType.Amd64))
                            .build();
    when(featureFlagService.isEnabled(FeatureName.CI_ENABLE_BARE_METAL, accountID)).thenReturn(false);
    when(ciExecutionServiceConfig.getHostedVmConfig())
        .thenReturn(HostedVmConfig.builder().internalAccounts(internalAccounts).build());
    when(ciLicenseService.getLicenseSummary(any(String.class)))
        .thenReturn(CILicenseSummaryDTO.builder().licenseType(LicenseType.PAID).edition(Edition.FREE).build());

    boolean response = vmInitializeTaskParamsBuilder.isBareMetalEnabled(
        accountID, ParameterField.createValueField(platform), initializeStepInfo);
    assertThat(response).isEqualTo(false);
  }

  @Test
  @Owner(developers = VISTAAR)
  @Category(UnitTests.class)
  public void isBareMetalEnabledWithFFSetNonLinux() {
    InitializeStepInfo initializeStepInfo = VmInitializeTaskParamsHelper.getHostedVmInitializeStep();
    String accountID = "account";
    List<String> internalAccounts = new ArrayList<>();
    internalAccounts.add("random-account");

    Platform platform = Platform.builder()
                            .os(ParameterField.createValueField(OSType.Windows))
                            .arch(ParameterField.createValueField(ArchType.Amd64))
                            .build();
    when(ciLicenseService.getLicenseSummary(any(String.class)))
        .thenReturn(CILicenseSummaryDTO.builder().licenseType(LicenseType.PAID).edition(Edition.FREE).build());
    when(featureFlagService.isEnabled(FeatureName.CI_ENABLE_BARE_METAL, accountID)).thenReturn(true);
    when(ciExecutionServiceConfig.getHostedVmConfig())
        .thenReturn(HostedVmConfig.builder().internalAccounts(internalAccounts).build());

    boolean response = vmInitializeTaskParamsBuilder.isBareMetalEnabled(
        accountID, ParameterField.createValueField(platform), initializeStepInfo);
    assertThat(response).isEqualTo(false);
  }

  @Test
  @Owner(developers = VISTAAR)
  @Category(UnitTests.class)
  public void getHostedVmInitializeTaskParamsWithBareMetalEnabled() {
    InitializeStepInfo initializeStepInfo = VmInitializeTaskParamsHelper.getHostedVmInitializeStep();
    Map<String, String> volToMountPath = new HashMap<>();
    List<String> internalAccounts = new ArrayList<>();
    internalAccounts.add("random-account");
    volToMountPath.put("shared-0", "/tmp");
    volToMountPath.put("harness", "/harness");

    String stageRuntimeId = "test";
    when(ciLicenseService.getLicenseSummary(any(String.class)))
        .thenReturn(CILicenseSummaryDTO.builder().licenseType(LicenseType.PAID).edition(Edition.FREE).build());
    doNothing().when(vmInitializeUtils).validateStageConfig(any(), any());
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
    when(codebaseUtils.getCodebaseVars(any(), any(), any())).thenReturn(m);
    when(
        codebaseUtils.getGitEnvVariables(null, initializeStepInfo.getCiCodebase(), initializeStepInfo.isSkipGitClone()))
        .thenReturn(m);

    when(logServiceUtils.getLogServiceConfig()).thenReturn(LogServiceConfig.builder().baseUrl("1.1.1.1").build());
    when(logServiceUtils.getLogServiceToken(any())).thenReturn("test");
    when(tiServiceUtils.getTiServiceConfig()).thenReturn(TIServiceConfig.builder().baseUrl("1.1.1.2").build());
    when(tiServiceUtils.getTIServiceToken(any())).thenReturn("test");
    when(stoServiceUtils.getStoServiceConfig()).thenReturn(STOServiceConfig.builder().baseUrl("1.1.1.3").build());
    when(stoServiceUtils.getSTOServiceToken(any())).thenReturn("test");
    when(featureFlagService.isEnabled(any(), any())).thenReturn(true);
    when(ciExecutionServiceConfig.getHostedVmConfig())
        .thenReturn(HostedVmConfig.builder().splitLinuxAmd64Pool(false).internalAccounts(internalAccounts).build());
    doNothing().when(hostedVmSecretResolver).resolve(any(), any());

    DliteVmInitializeTaskParams response =
        vmInitializeTaskParamsBuilder.getHostedVmInitializeTaskParams(initializeStepInfo, ambiance);
    assertThat(response.getSetupVmRequest().getId()).isEqualTo(stageRuntimeId);
    assertThat(response.getSetupVmRequest().getPoolID()).isEqualTo("linux-amd64-bare-metal");
    assertThat(response.getSetupVmRequest().getFallbackPoolIDs().contains("linux-amd64"));
  }
}