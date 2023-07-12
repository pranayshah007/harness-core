/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pcf;

import static io.harness.pcf.model.PcfConstants.HARNESS__ACTIVE__IDENTIFIER;
import static io.harness.pcf.model.PcfConstants.HARNESS__STAGE__IDENTIFIER;
import static io.harness.pcf.model.PcfConstants.HARNESS__STATUS__IDENTIFIER;
import static io.harness.rule.OwnerRule.ADWAIT;
import static io.harness.rule.OwnerRule.ANIL;
import static io.harness.rule.OwnerRule.ARVIND;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.logging.LogCallback;
import io.harness.pcf.model.CfAppAutoscalarRequestData;
import io.harness.pcf.model.CfConfig;
import io.harness.pcf.model.CfCreateApplicationRequestData;
import io.harness.pcf.model.CfRenameRequest;
import io.harness.pcf.model.CfRequestConfig;
import io.harness.rule.Owner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.apache.commons.lang3.StringUtils;
import org.cloudfoundry.operations.applications.ApplicationDetail;
import org.cloudfoundry.operations.applications.ApplicationEnvironments;
import org.cloudfoundry.operations.applications.ApplicationSummary;
import org.cloudfoundry.operations.applications.InstanceDetail;
import org.cloudfoundry.operations.organizations.OrganizationSummary;
import org.cloudfoundry.operations.routes.Route;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.exec.StartedProcess;

@OwnedBy(HarnessTeam.CDP)
public class CfDeploymentManagerImplTest extends CategoryTest {
  @Mock private CfCliClient cliClient;
  @Mock private CfSdkClient sdkClient;
  @Mock private LogCallback logCallback;
  @InjectMocks @Spy private CfDeploymentManagerImpl deploymentManager;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testGetOrganizations() throws Exception {
    OrganizationSummary summary1 = OrganizationSummary.builder().id("1").name("org1").build();
    OrganizationSummary summary2 = OrganizationSummary.builder().id("2").name("org2").build();

    when(sdkClient.getOrganizations(any())).thenReturn(Arrays.asList(summary1, summary2));
    List<String> orgs = deploymentManager.getOrganizations(null);
    assertThat(orgs).isNotNull();
    assertThat(orgs).containsExactly("org1", "org2");
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void getAppPrefixByRemovingNumber() {
    assertThat(StringUtils.EMPTY).isEqualTo(deploymentManager.getAppPrefixByRemovingNumber(null));
    assertThat("a_b_c").isEqualTo(deploymentManager.getAppPrefixByRemovingNumber("a_b_c__4"));
    assertThat("a_b_c").isEqualTo(deploymentManager.getAppPrefixByRemovingNumber("a_b_c"));
    assertThat("a_b_c").isEqualTo(deploymentManager.getAppPrefixByRemovingNumber("a_b_c__INACTIVE"));
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void getMatchesPrefix() {
    ApplicationSummary applicationSummary = ApplicationSummary.builder()
                                                .id("id1")
                                                .name("a__b__c__1")
                                                .diskQuota(1)
                                                .instances(1)
                                                .memoryLimit(1)
                                                .requestedState("RUNNING")
                                                .runningInstances(0)
                                                .build();

    assertThat(deploymentManager.matchesPrefix("a__b__c", applicationSummary)).isTrue();

    applicationSummary = ApplicationSummary.builder()
                             .id("id1")
                             .name("A__b__c__1")
                             .diskQuota(1)
                             .instances(1)
                             .memoryLimit(1)
                             .requestedState("RUNNING")
                             .runningInstances(0)
                             .build();

    assertThat(deploymentManager.matchesPrefix("a__b__C", applicationSummary)).isTrue();

    applicationSummary = ApplicationSummary.builder()
                             .id("id1")
                             .name("a__b__c__2")
                             .diskQuota(1)
                             .instances(1)
                             .memoryLimit(1)
                             .requestedState("RUNNING")
                             .runningInstances(0)
                             .build();
    assertThat(deploymentManager.matchesPrefix("a__b__c", applicationSummary)).isTrue();

    applicationSummary = ApplicationSummary.builder()
                             .id("id1")
                             .name("a__b__c__d__2")
                             .diskQuota(1)
                             .instances(1)
                             .memoryLimit(1)
                             .requestedState("RUNNING")
                             .runningInstances(0)
                             .build();
    assertThat(deploymentManager.matchesPrefix("a__b__c", applicationSummary)).isFalse();

    applicationSummary = ApplicationSummary.builder()
                             .id("id1")
                             .name("a__b__2")
                             .diskQuota(1)
                             .instances(1)
                             .memoryLimit(1)
                             .requestedState("RUNNING")
                             .runningInstances(0)
                             .build();
    assertThat(deploymentManager.matchesPrefix("a__b__c", applicationSummary)).isFalse();

    applicationSummary = ApplicationSummary.builder()
                             .id("id1")
                             .name("BG__1_vars.yml")
                             .diskQuota(1)
                             .instances(1)
                             .memoryLimit(1)
                             .requestedState("RUNNING")
                             .runningInstances(0)
                             .build();
    assertThat(deploymentManager.matchesPrefix("BG", applicationSummary)).isFalse();
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testChangeAutoscalarState() throws Exception {
    reset(cliClient);
    doReturn(false).doReturn(true).when(cliClient).checkIfAppHasAutoscalerWithExpectedState(any(), any());

    doNothing().when(cliClient).changeAutoscalerState(any(), any(), anyBoolean());

    doNothing().when(logCallback).saveExecutionLog(anyString());
    deploymentManager.changeAutoscalarState(CfAppAutoscalarRequestData.builder().build(), logCallback, true);
    verify(cliClient, never()).changeAutoscalerState(any(), any(), anyBoolean());

    deploymentManager.changeAutoscalarState(CfAppAutoscalarRequestData.builder().build(), logCallback, true);
    verify(cliClient, times(1)).changeAutoscalerState(any(), any(), anyBoolean());
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testPerformConfigureAutoscalar() throws Exception {
    reset(cliClient);
    doReturn(false).doReturn(true).when(cliClient).checkIfAppHasAutoscalerAttached(any(), any());
    doNothing().when(cliClient).performConfigureAutoscaler(any(), any());

    doNothing().when(logCallback).saveExecutionLog(anyString());
    deploymentManager.performConfigureAutoscalar(CfAppAutoscalarRequestData.builder().build(), logCallback);
    verify(cliClient, never()).performConfigureAutoscaler(any(), any());

    deploymentManager.performConfigureAutoscalar(CfAppAutoscalarRequestData.builder().build(), logCallback);
    verify(cliClient, times(1)).performConfigureAutoscaler(any(), any());
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testReachedDesiredState() {
    ApplicationDetail applicationDetail = generateApplicationDetail(1, new InstanceDetail[] {});
    assertThat(deploymentManager.reachedDesiredState(applicationDetail, 2)).isFalse();

    InstanceDetail instanceDetail1 = InstanceDetail.builder()
                                         .cpu(1.0)
                                         .diskQuota((long) 1.23)
                                         .diskUsage((long) 1.23)
                                         .index("0")
                                         .memoryQuota((long) 1)
                                         .memoryUsage((long) 1)
                                         .state("CRASHED")
                                         .build();

    InstanceDetail instanceDetail2 = InstanceDetail.builder()
                                         .cpu(1.0)
                                         .diskQuota((long) 1.23)
                                         .diskUsage((long) 1.23)
                                         .index("0")
                                         .memoryQuota((long) 1)
                                         .memoryUsage((long) 1)
                                         .state("RUNNING")
                                         .build();

    applicationDetail = generateApplicationDetail(1, new InstanceDetail[] {instanceDetail1});
    assertThat(deploymentManager.reachedDesiredState(applicationDetail, 1)).isFalse();

    applicationDetail = generateApplicationDetail(2, new InstanceDetail[] {instanceDetail1, instanceDetail2});
    assertThat(deploymentManager.reachedDesiredState(applicationDetail, 2)).isFalse();

    applicationDetail = generateApplicationDetail(2, new InstanceDetail[] {instanceDetail2, instanceDetail2});
    assertThat(deploymentManager.reachedDesiredState(applicationDetail, 2)).isTrue();
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testUpsizeApplicationWithSteadyStateCheck() throws Exception {
    StartedProcess startedProcess = mock(StartedProcess.class);
    Process process = mock(Process.class);

    doReturn(startedProcess).when(deploymentManager).startTailingLogsIfNeeded(any(), any(), any());
    doReturn(process).when(startedProcess).getProcess();
    doReturn(process).when(process).destroyForcibly();
    doNothing().when(process).destroy();

    CfRequestConfig cfRequestConfig = CfRequestConfig.builder().desiredCount(1).timeOutIntervalInMins(1).build();
    InstanceDetail instanceDetail1 = InstanceDetail.builder()
                                         .cpu(2.0)
                                         .diskQuota((long) 2.23)
                                         .diskUsage((long) 1.23)
                                         .index("0")
                                         .memoryQuota((long) 2)
                                         .memoryUsage((long) 2)
                                         .state("RUNNING")
                                         .build();
    ApplicationDetail applicationDetail = generateApplicationDetail(1, new InstanceDetail[] {instanceDetail1});
    doReturn(applicationDetail).when(sdkClient).getApplicationByName(any());
    doNothing().when(sdkClient).scaleApplications(any());
    ApplicationDetail applicationDetail1 =
        deploymentManager.upsizeApplicationWithSteadyStateCheck(cfRequestConfig, logCallback);
    assertThat(applicationDetail).isEqualTo(applicationDetail1);
    verify(process, times(1)).destroy();

    InstanceDetail instanceDetail2 = InstanceDetail.builder()
                                         .cpu(1.0)
                                         .diskQuota((long) 1.23)
                                         .diskUsage((long) 1.23)
                                         .index("0")
                                         .memoryQuota((long) 1)
                                         .memoryUsage((long) 1)
                                         .state("CRASHED")
                                         .build();

    try {
      reset(startedProcess);
      reset(process);
      applicationDetail = generateApplicationDetail(1, new InstanceDetail[] {instanceDetail2});
      doReturn(applicationDetail).when(sdkClient).getApplicationByName(any());
      deploymentManager.upsizeApplicationWithSteadyStateCheck(cfRequestConfig, logCallback);
    } catch (PivotalClientApiException e) {
      assertThat(e.getMessage().contains("Failed to reach steady state")).isTrue();
    }
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testStartTailingLogsIfNeeded() throws Exception {
    reset(cliClient);
    StartedProcess startedProcess = mock(StartedProcess.class);
    Process process = mock(Process.class);

    CfRequestConfig cfRequestConfig = CfRequestConfig.builder().build();
    cfRequestConfig.setUseCFCLI(true);
    doReturn(startedProcess).when(cliClient).tailLogsForPcf(any(), any());
    // startedProcess = null
    deploymentManager.startTailingLogsIfNeeded(cfRequestConfig, logCallback, null);
    verify(cliClient, times(1)).tailLogsForPcf(any(), any());

    reset(cliClient);
    doReturn(startedProcess).when(cliClient).tailLogsForPcf(any(), any());
    doReturn(null).when(startedProcess).getProcess();
    // startedProcess.getProcess() = null
    deploymentManager.startTailingLogsIfNeeded(cfRequestConfig, logCallback, startedProcess);
    verify(cliClient, times(1)).tailLogsForPcf(any(), any());

    reset(cliClient);
    doReturn(process).when(startedProcess).getProcess();
    doReturn(false).when(process).isAlive();
    deploymentManager.startTailingLogsIfNeeded(cfRequestConfig, logCallback, startedProcess);
    verify(cliClient, times(1)).tailLogsForPcf(any(), any());

    reset(cliClient);
    doReturn(true).when(process).isAlive();
    deploymentManager.startTailingLogsIfNeeded(cfRequestConfig, logCallback, startedProcess);
    verify(cliClient, never()).tailLogsForPcf(any(), any());

    reset(cliClient);
    cfRequestConfig.setUseCFCLI(false);
    deploymentManager.startTailingLogsIfNeeded(cfRequestConfig, logCallback, null);
    verify(cliClient, never()).tailLogsForPcf(any(), any());

    reset(cliClient);
    cfRequestConfig.setUseCFCLI(true);
    doAnswer(invocation -> { throw new PivotalClientApiException(""); }).when(cliClient).tailLogsForPcf(any(), any());
    deploymentManager.startTailingLogsIfNeeded(cfRequestConfig, logCallback, null);
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testSetEnvironmentVariableForAppStatus() throws Exception {
    reset(cliClient);
    ArgumentCaptor<Map> mapCaptor = ArgumentCaptor.forClass(Map.class);
    try {
      deploymentManager.setEnvironmentVariableForAppStatus(CfRequestConfig.builder().build(), true, logCallback);
    } catch (Exception e) {
      verify(cliClient, times(1)).setEnvVariablesForApplication(mapCaptor.capture(), any(), any());
      Map map = mapCaptor.getValue();

      assertThat(map).isNotNull();
      assertThat(map.size()).isEqualTo(1);
      assertThat(map.get(HARNESS__STATUS__IDENTIFIER)).isEqualTo(HARNESS__ACTIVE__IDENTIFIER);
    }

    try {
      deploymentManager.setEnvironmentVariableForAppStatus(CfRequestConfig.builder().build(), false, logCallback);
    } catch (Exception e) {
      verify(cliClient, times(2)).setEnvVariablesForApplication(mapCaptor.capture(), any(), any());
      Map map = mapCaptor.getValue();

      assertThat(map).isNotNull();
      assertThat(map.size()).isEqualTo(1);
      assertThat(map.get(HARNESS__STATUS__IDENTIFIER)).isEqualTo(HARNESS__STAGE__IDENTIFIER);
    }
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testUnsetEnvironmentVariableForAppStatus() throws Exception {
    reset(sdkClient);
    Map<String, String> userProvided = new HashMap<>();
    userProvided.put(HARNESS__STATUS__IDENTIFIER, HARNESS__STAGE__IDENTIFIER);
    ApplicationEnvironments applicationEnvironments =
        ApplicationEnvironments.builder().userProvided(userProvided).build();

    doReturn(applicationEnvironments).when(sdkClient).getApplicationEnvironmentsByName(any());
    try {
      deploymentManager.unsetEnvironmentVariableForAppStatus(CfRequestConfig.builder().build(), logCallback);
    } catch (Exception e) {
      ArgumentCaptor<List> listCaptor = ArgumentCaptor.forClass(List.class);
      verify(cliClient).unsetEnvVariablesForApplication(listCaptor.capture(), any(), any());
      List list = listCaptor.getValue();

      assertThat(list).isNotNull();
      assertThat(list.size()).isEqualTo(1);
      assertThat(list).containsExactly(HARNESS__STATUS__IDENTIFIER);
    }
  }

  @Test
  @Owner(developers = ADWAIT, intermittent = true)
  @Category(UnitTests.class)
  public void testdestroyProcess() throws Exception {
    StartedProcess startedProcess = mock(StartedProcess.class);
    Process process = mock(Process.class);

    doReturn(process).when(startedProcess).getProcess();
    doReturn(null).when(startedProcess).getFuture();

    reset(deploymentManager);
    doNothing().when(process).destroy();
    doReturn(false).when(process).isAlive();
    deploymentManager.destroyProcess(startedProcess);
    verify(process, times(1)).destroy();
    verify(process, never()).destroyForcibly();

    reset(process);
    doNothing().when(process).destroy();
    doReturn(true).when(process).isAlive();
    deploymentManager.destroyProcess(startedProcess);
    verify(process, times(1)).destroy();
    verify(process, times(1)).destroyForcibly();

    // Test with Real ProcessExecutor
    ProcessExecutor processExecutor =
        new ProcessExecutor().timeout(2, TimeUnit.MINUTES).command("/bin/sh", "-c", "echo \"\"");

    StartedProcess start = processExecutor.start();
    deploymentManager.destroyProcess(start);
    assertThat(start.getFuture().isDone()).isTrue();
    assertThat(start.getProcess().isAlive()).isFalse();
  }

  private ApplicationDetail generateApplicationDetail(int runningCount, InstanceDetail[] instanceDetails) {
    return ApplicationDetail.builder()
        .id("id")
        .name("app")
        .diskQuota(1)
        .stack("stack")
        .instances(runningCount)
        .memoryLimit(1)
        .requestedState("RUNNING")
        .runningInstances(runningCount)
        .instanceDetails(instanceDetails)
        .build();
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testGetOrganizationsFail() throws Exception {
    doAnswer(invocation -> { throw new Exception(); }).when(sdkClient).getOrganizations(any());
    assertThatThrownBy(() -> deploymentManager.getOrganizations(CfRequestConfig.builder().build()))
        .isInstanceOf(PivotalClientApiException.class);
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testGetSpacesForOrganization() throws Exception {
    when(sdkClient.getSpacesForOrganization(any())).thenReturn(Arrays.asList("space1", "space2"));
    List<String> spaces = deploymentManager.getSpacesForOrganization(CfRequestConfig.builder().build());
    assertThat(spaces).isNotNull();
    assertThat(spaces).containsExactly("space1", "space2");
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testGetSpacesForOrganizationFail() throws Exception {
    doAnswer(invocation -> { throw new Exception(); }).when(sdkClient).getSpacesForOrganization(any());
    assertThatThrownBy(() -> deploymentManager.getSpacesForOrganization(CfRequestConfig.builder().build()))
        .isInstanceOf(PivotalClientApiException.class);
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testGetRouteMaps() throws Exception {
    when(sdkClient.getRoutesForSpace(any())).thenReturn(Arrays.asList("route1", "route2"));
    List<String> routeMaps = deploymentManager.getRouteMaps(CfRequestConfig.builder().build());
    assertThat(routeMaps).isNotNull();
    assertThat(routeMaps).containsExactly("route1", "route2");
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testGetRouteMapsFail() throws Exception {
    doAnswer(invocation -> { throw new Exception(); }).when(sdkClient).getRoutesForSpace(any());
    assertThatThrownBy(() -> deploymentManager.getRouteMaps(CfRequestConfig.builder().build()))
        .isInstanceOf(PivotalClientApiException.class);
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testCreateApplication() throws Exception {
    String appName = "App_1";
    CfRequestConfig cfRequestConfig = CfRequestConfig.builder().applicationName(appName).build();
    CfCreateApplicationRequestData pcfCreateApplicationRequestData =
        CfCreateApplicationRequestData.builder().cfRequestConfig(cfRequestConfig).manifestFilePath("").build();
    ApplicationDetail applicationDetail = ApplicationDetail.builder()
                                              .name(appName)
                                              .stack("stack")
                                              .diskQuota(1)
                                              .id("1")
                                              .instances(2)
                                              .memoryLimit(512)
                                              .requestedState("running")
                                              .runningInstances(2)
                                              .build();

    doNothing().when(sdkClient).pushAppBySdk(eq(cfRequestConfig), any(), eq(logCallback));
    when(sdkClient.getApplicationByName(eq(cfRequestConfig))).thenReturn(applicationDetail);

    ApplicationDetail application = deploymentManager.createApplication(pcfCreateApplicationRequestData, logCallback);
    assertThat(application).isNotNull();
    assertThat(application.getName()).isEqualTo(appName);
    assertThat(application.getStack()).isEqualTo("stack");
    assertThat(application.getDiskQuota()).isEqualTo(1);
    assertThat(application.getInstances()).isEqualTo(2);
    assertThat(application.getMemoryLimit()).isEqualTo(512);
    assertThat(application.getRunningInstances()).isEqualTo(2);
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testCreateApplicationPushApplicationUsingManifestFail() throws Exception {
    doAnswer(invocation -> { throw new Exception(); }).when(sdkClient).pushAppBySdk(any(), any(), any());
    assertThatThrownBy(
        ()
            -> deploymentManager.createApplication(
                CfCreateApplicationRequestData.builder().cfRequestConfig(CfRequestConfig.builder().build()).build(),
                logCallback))
        .isInstanceOf(PivotalClientApiException.class);
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testCreateApplicationGetApplicationByNameFail() throws Exception {
    doAnswer(invocation -> { throw new Exception(); }).when(sdkClient).getApplicationByName(any());
    assertThatThrownBy(
        () -> deploymentManager.createApplication(CfCreateApplicationRequestData.builder().build(), logCallback))
        .isInstanceOf(PivotalClientApiException.class);
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testResizeApplication() throws Exception {
    String appName = "App_1";
    CfRequestConfig cfRequestConfig = CfRequestConfig.builder().applicationName(appName).desiredCount(2).build();
    ApplicationDetail applicationDetail = ApplicationDetail.builder()
                                              .name(appName)
                                              .stack("stack")
                                              .diskQuota(1)
                                              .id("1")
                                              .instances(0)
                                              .memoryLimit(512)
                                              .requestedState("running")
                                              .runningInstances(2)
                                              .build();
    when(sdkClient.getApplicationByName(eq(cfRequestConfig))).thenReturn(applicationDetail);
    ApplicationDetail application = deploymentManager.resizeApplication(cfRequestConfig, logCallback);
    assertThat(application).isNotNull();
    assertThat(application.getName()).isEqualTo(appName);

    cfRequestConfig.setDesiredCount(0);
    application = deploymentManager.resizeApplication(cfRequestConfig, logCallback);
    assertThat(application).isNotNull();
    assertThat(application.getName()).isEqualTo(appName);
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testResizeApplicationFail() throws Exception {
    doAnswer(invocation -> { throw new Exception(); }).when(sdkClient).scaleApplications(any());
    assertThatThrownBy(() -> deploymentManager.resizeApplication(CfRequestConfig.builder().build(), logCallback))
        .isInstanceOf(PivotalClientApiException.class);
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testUnmapRouteMapForApplication() throws Exception {
    CfRequestConfig cfRequestConfig = CfRequestConfig.builder().useCFCLI(true).build();
    List<String> paths = Arrays.asList("path1", "path2");
    deploymentManager.unmapRouteMapForApplication(cfRequestConfig, paths, logCallback);
    verify(cliClient, times(1)).unmapRoutesForApplicationUsingCli(eq(cfRequestConfig), eq(paths), eq(logCallback));

    reset(cliClient);
    cfRequestConfig.setUseCFCLI(false);
    deploymentManager.unmapRouteMapForApplication(cfRequestConfig, paths, logCallback);
    verify(sdkClient, times(1)).unmapRoutesForApplication(eq(cfRequestConfig), eq(paths));

    reset(sdkClient);
    doAnswer(invocation -> { throw new Exception(); })
        .when(sdkClient)
        .unmapRoutesForApplication(eq(cfRequestConfig), eq(paths));
    assertThatThrownBy(() -> deploymentManager.unmapRouteMapForApplication(cfRequestConfig, paths, logCallback))
        .isInstanceOf(PivotalClientApiException.class);
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testMapRouteMapForApplication() throws Exception {
    CfRequestConfig cfRequestConfig = CfRequestConfig.builder().useCFCLI(true).build();
    List<String> paths = Arrays.asList("path1", "path2");
    deploymentManager.mapRouteMapForApplication(cfRequestConfig, paths, logCallback);
    verify(cliClient, times(1)).mapRoutesForApplicationUsingCli(eq(cfRequestConfig), eq(paths), eq(logCallback));

    reset(sdkClient);
    cfRequestConfig.setUseCFCLI(false);
    deploymentManager.mapRouteMapForApplication(cfRequestConfig, paths, logCallback);
    verify(sdkClient, times(1)).mapRoutesForApplication(eq(cfRequestConfig), eq(paths));

    reset(sdkClient);
    doAnswer(invocation -> { throw new Exception(); })
        .when(sdkClient)
        .mapRoutesForApplication(eq(cfRequestConfig), eq(paths));
    assertThatThrownBy(() -> deploymentManager.mapRouteMapForApplication(cfRequestConfig, paths, logCallback))
        .isInstanceOf(PivotalClientApiException.class);
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testGetDeployedServicesWithNonZeroInstances() throws Exception {
    String prefix = "app";
    CfRequestConfig cfRequestConfig = CfRequestConfig.builder().build();

    when(sdkClient.getApplications(eq(cfRequestConfig))).thenReturn(Collections.emptyList());
    List<ApplicationSummary> deployedServicesWithNonZeroInstances =
        deploymentManager.getDeployedServicesWithNonZeroInstances(cfRequestConfig, prefix);
    assertThat(deployedServicesWithNonZeroInstances).isNotNull();
    assertThat(deployedServicesWithNonZeroInstances.size()).isEqualTo(0);

    reset(sdkClient);
    ApplicationSummary appSummary1 = getApplicationSummary(prefix + CfDeploymentManagerImpl.DELIMITER + 1, 2);
    ApplicationSummary appSummary2 = getApplicationSummary(prefix + CfDeploymentManagerImpl.DELIMITER + 2, 2);
    List<ApplicationSummary> applicationSummaries = Arrays.asList(appSummary1, appSummary2);
    when(sdkClient.getApplications(eq(cfRequestConfig))).thenReturn(applicationSummaries);
    deployedServicesWithNonZeroInstances =
        deploymentManager.getDeployedServicesWithNonZeroInstances(cfRequestConfig, prefix);
    assertThat(deployedServicesWithNonZeroInstances).isNotNull();
    assertThat(deployedServicesWithNonZeroInstances.size()).isEqualTo(2);

    reset(sdkClient);
    ApplicationSummary appSummary3 = getApplicationSummary(prefix + CfDeploymentManagerImpl.DELIMITER + 1, 0);
    ApplicationSummary appSummary4 = getApplicationSummary(prefix + CfDeploymentManagerImpl.DELIMITER + 2, 2);
    when(sdkClient.getApplications(eq(cfRequestConfig))).thenReturn(Arrays.asList(appSummary3, appSummary4));
    deployedServicesWithNonZeroInstances =
        deploymentManager.getDeployedServicesWithNonZeroInstances(cfRequestConfig, prefix);
    assertThat(deployedServicesWithNonZeroInstances).isNotNull();
    assertThat(deployedServicesWithNonZeroInstances.size()).isEqualTo(1);

    reset(sdkClient);
    doAnswer(invocation -> { throw new Exception(); }).when(sdkClient).getApplications(eq(cfRequestConfig));
    assertThatThrownBy(() -> deploymentManager.getDeployedServicesWithNonZeroInstances(cfRequestConfig, prefix))
        .isInstanceOf(PivotalClientApiException.class);
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testGetPreviousReleases() throws Exception {
    String prefix = "app";
    CfRequestConfig cfRequestConfig = CfRequestConfig.builder().build();

    when(sdkClient.getApplications(eq(cfRequestConfig))).thenReturn(Collections.emptyList());
    List<ApplicationSummary> previousReleasesApplication =
        deploymentManager.getPreviousReleases(cfRequestConfig, prefix);
    assertThat(previousReleasesApplication).isNotNull();
    assertThat(previousReleasesApplication.size()).isEqualTo(0);

    reset(sdkClient);
    ApplicationSummary appSummary1 = getApplicationSummary(prefix + CfDeploymentManagerImpl.DELIMITER + 1, 2);
    ApplicationSummary appSummary2 = getApplicationSummary(prefix + CfDeploymentManagerImpl.DELIMITER + 2, 2);
    List<ApplicationSummary> applicationSummaries = Arrays.asList(appSummary1, appSummary2);
    when(sdkClient.getApplications(eq(cfRequestConfig))).thenReturn(applicationSummaries);
    previousReleasesApplication = deploymentManager.getPreviousReleases(cfRequestConfig, prefix);
    assertThat(previousReleasesApplication).isNotNull();
    assertThat(previousReleasesApplication.size()).isEqualTo(2);

    reset(sdkClient);
    ApplicationSummary appSummary3 = getApplicationSummary(prefix + CfDeploymentManagerImpl.DELIMITER + 1, 0);
    ApplicationSummary appSummary4 = getApplicationSummary("filter" + CfDeploymentManagerImpl.DELIMITER + 2, 2);
    when(sdkClient.getApplications(eq(cfRequestConfig))).thenReturn(Arrays.asList(appSummary3, appSummary4));
    previousReleasesApplication = deploymentManager.getPreviousReleases(cfRequestConfig, prefix);
    assertThat(previousReleasesApplication).isNotNull();
    assertThat(previousReleasesApplication.size()).isEqualTo(1);

    // non-versioned
    reset(sdkClient);
    List<ApplicationSummary> summaries = new ArrayList<>();
    summaries.add(getApplicationSummary(prefix, 0));
    when(sdkClient.getApplications(eq(cfRequestConfig))).thenReturn(summaries);
    List<ApplicationSummary> releases = deploymentManager.getPreviousReleases(cfRequestConfig, prefix);
    assertThat(releases.size()).isEqualTo(1);
    assertThat(releases.stream().map(ApplicationSummary::getName)).containsExactly(prefix);

    summaries.add(getApplicationSummary(prefix + "__INACTIVE", 0));
    releases = deploymentManager.getPreviousReleases(cfRequestConfig, prefix);
    assertThat(releases.size()).isEqualTo(2);
    assertThat(releases.stream().map(ApplicationSummary::getName)).containsExactly(prefix + "__INACTIVE", prefix);

    summaries.add(getApplicationSummary(prefix + "__1", 0));
    releases = deploymentManager.getPreviousReleases(cfRequestConfig, prefix);
    assertThat(releases.size()).isEqualTo(3);
    assertThat(releases.stream().map(ApplicationSummary::getName))
        .containsExactly(prefix + "__1", prefix + "__INACTIVE", prefix);

    summaries.add(getApplicationSummary(prefix + "__2", 0));
    releases = deploymentManager.getPreviousReleases(cfRequestConfig, prefix);
    assertThat(releases.size()).isEqualTo(4);
    assertThat(releases.stream().map(ApplicationSummary::getName))
        .containsExactly(prefix + "__1", prefix + "__2", prefix + "__INACTIVE", prefix);

    reset(sdkClient);
    doAnswer(invocation -> { throw new Exception(); }).when(sdkClient).getApplications(eq(cfRequestConfig));
    assertThatThrownBy(() -> deploymentManager.getPreviousReleases(cfRequestConfig, prefix))
        .isInstanceOf(PivotalClientApiException.class);
  }

  private ApplicationSummary getApplicationSummary(String appName, int instances) {
    return ApplicationSummary.builder()
        .name(appName)
        .diskQuota(1)
        .id("1")
        .memoryLimit(512)
        .requestedState("running")
        .runningInstances(instances)
        .instances(instances)
        .build();
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testDeleteApplication() throws Exception {
    CfRequestConfig cfRequestConfig = CfRequestConfig.builder().build();
    deploymentManager.deleteApplication(cfRequestConfig);
    verify(sdkClient, times(1)).deleteApplication(eq(cfRequestConfig));

    reset(sdkClient);
    doAnswer(invocation -> { throw new Exception(); }).when(sdkClient).deleteApplication(eq(cfRequestConfig));
    assertThatThrownBy(() -> deploymentManager.deleteApplication(cfRequestConfig))
        .isInstanceOf(PivotalClientApiException.class);
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testStopApplication() throws Exception {
    String appName = "app_1";
    CfRequestConfig cfRequestConfig = CfRequestConfig.builder().applicationName(appName).build();
    ApplicationDetail applicationDetail = ApplicationDetail.builder()
                                              .name(appName)
                                              .stack("stack")
                                              .diskQuota(1)
                                              .id("1")
                                              .instances(2)
                                              .memoryLimit(512)
                                              .requestedState("running")
                                              .runningInstances(2)
                                              .build();
    when(sdkClient.getApplicationByName(eq(cfRequestConfig))).thenReturn(applicationDetail);

    String message = deploymentManager.stopApplication(cfRequestConfig);
    verify(sdkClient, times(1)).stopApplication(eq(cfRequestConfig));
    assertThat(message.contains(appName)).isEqualTo(true);

    reset(sdkClient);
    doAnswer(invocation -> { throw new Exception(); }).when(sdkClient).stopApplication(eq(cfRequestConfig));
    assertThatThrownBy(() -> deploymentManager.stopApplication(cfRequestConfig))
        .isInstanceOf(PivotalClientApiException.class);
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testCreateRouteMap() throws Exception {
    CfRequestConfig cfRequestConfig = CfRequestConfig.builder().build();
    String host = "localhost";
    String domain = "harness";
    String path = "/console.pivotal";
    int port = 8080;
    String tcpRouteNonRandomPortPath = domain + ":" + port;

    // tcpRoute without random port
    Optional<Route> route = Optional.of(Route.builder().domain(domain).host(host).id("1").space("test").build());
    when(sdkClient.getRouteMap(eq(cfRequestConfig), eq(tcpRouteNonRandomPortPath))).thenReturn(route);
    String routeMap = deploymentManager.createRouteMap(cfRequestConfig, host, domain, path, true, false, port);
    assertThat(routeMap).isNotNull();
    assertThat(routeMap.equalsIgnoreCase(tcpRouteNonRandomPortPath)).isEqualTo(true);

    reset(sdkClient);
    when(sdkClient.getRouteMap(eq(cfRequestConfig), eq(tcpRouteNonRandomPortPath))).thenReturn(Optional.empty());
    assertThatThrownBy(() -> deploymentManager.createRouteMap(cfRequestConfig, host, domain, path, true, false, port))
        .isInstanceOf(PivotalClientApiException.class);

    // tcpRoute with RandomPort
    reset(sdkClient);
    String tcpRouteRandomPortPath = domain;
    when(sdkClient.getRouteMap(eq(cfRequestConfig), eq(tcpRouteRandomPortPath))).thenReturn(route);
    routeMap = deploymentManager.createRouteMap(cfRequestConfig, host, domain, path, true, true, null);
    assertThat(routeMap).isNotNull();
    assertThat(routeMap.equalsIgnoreCase(tcpRouteRandomPortPath)).isEqualTo(true);

    // nonTcpRoute with nonBlankPath
    reset(sdkClient);
    String nonTcpRouteNonBlankPath = host + "." + domain + path;
    when(sdkClient.getRouteMap(eq(cfRequestConfig), eq(nonTcpRouteNonBlankPath))).thenReturn(route);
    routeMap = deploymentManager.createRouteMap(cfRequestConfig, host, domain, path, false, true, null);
    assertThat(routeMap).isNotNull();
    assertThat(routeMap.equalsIgnoreCase(nonTcpRouteNonBlankPath)).isEqualTo(true);

    reset(sdkClient);
    String emptyDomain = "";
    assertThatThrownBy(
        () -> deploymentManager.createRouteMap(cfRequestConfig, host, emptyDomain, path, true, false, port))
        .isInstanceOf(PivotalClientApiException.class);

    reset(sdkClient);
    String emptyHost = "";
    assertThatThrownBy(
        () -> deploymentManager.createRouteMap(cfRequestConfig, emptyHost, domain, path, false, false, port))
        .isInstanceOf(PivotalClientApiException.class);

    reset(sdkClient);
    assertThatThrownBy(() -> deploymentManager.createRouteMap(cfRequestConfig, host, domain, path, true, false, null))
        .isInstanceOf(PivotalClientApiException.class);
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testCheckConnectivity() throws Exception {
    CfConfig pcfConfig = CfConfig.builder().username("user".toCharArray()).password("test".toCharArray()).build();
    when(sdkClient.getOrganizations(any())).thenReturn(Collections.emptyList());
    String message = deploymentManager.checkConnectivity(pcfConfig, false);
    verify(sdkClient, times(1)).getOrganizations(any());
    assertThat(message.equalsIgnoreCase("SUCCESS")).isEqualTo(true);

    reset(sdkClient);
    doAnswer(invocation -> { throw new Exception(); }).when(sdkClient).getOrganizations(any());
    message = deploymentManager.checkConnectivity(pcfConfig, false);
    assertThat(message.equalsIgnoreCase("SUCCESS")).isEqualTo(false);
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testCheckIfAppHasAutoscalarAttached() throws Exception {
    deploymentManager.checkIfAppHasAutoscalarAttached(CfAppAutoscalarRequestData.builder().build(), logCallback);
    verify(cliClient, times(1)).checkIfAppHasAutoscalerAttached(any(), any());
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testIsActiveApplication() throws Exception {
    CfRequestConfig cfRequestConfig = CfRequestConfig.builder().applicationName("app_1").build();
    Map<String, String> userProvider = new HashMap<>();
    userProvider.put(HARNESS__STATUS__IDENTIFIER, HARNESS__ACTIVE__IDENTIFIER);
    ApplicationEnvironments environments = ApplicationEnvironments.builder().userProvided(userProvider).build();

    when(sdkClient.getApplicationEnvironmentsByName(eq(cfRequestConfig))).thenReturn(environments);
    assertThat(deploymentManager.isActiveApplication(cfRequestConfig, logCallback)).isEqualTo(true);

    reset(sdkClient);
    when(sdkClient.getApplicationEnvironmentsByName(eq(cfRequestConfig)))
        .thenReturn(ApplicationEnvironments.builder().build());
    assertThat(deploymentManager.isActiveApplication(cfRequestConfig, logCallback)).isEqualTo(false);
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testUpSizeApplicationWithSteadyStateCheckFail() throws Exception {
    StartedProcess startedProcess = mock(StartedProcess.class);
    Process process = mock(Process.class);

    doReturn(startedProcess).when(deploymentManager).startTailingLogsIfNeeded(any(), any(), any());
    doReturn(process).when(startedProcess).getProcess();
    doReturn(process).when(process).destroyForcibly();
    doNothing().when(process).destroy();

    CfRequestConfig cfRequestConfig = CfRequestConfig.builder().desiredCount(1).timeOutIntervalInMins(1).build();
    InstanceDetail instanceDetail1 = InstanceDetail.builder()
                                         .cpu(2.0)
                                         .diskQuota((long) 2.23)
                                         .diskUsage((long) 1.23)
                                         .index("0")
                                         .memoryQuota((long) 2)
                                         .memoryUsage((long) 2)
                                         .state("RUNNING")
                                         .build();
    ApplicationDetail applicationDetail = generateApplicationDetail(1, new InstanceDetail[] {instanceDetail1});
    doReturn(applicationDetail).when(deploymentManager).resizeApplication(cfRequestConfig, logCallback);
    doAnswer(invocation -> { throw new InterruptedException(); })
        .when(sdkClient)
        .getApplicationByName(eq(cfRequestConfig));
    assertThatThrownBy(() -> deploymentManager.upsizeApplicationWithSteadyStateCheck(cfRequestConfig, logCallback))
        .isInstanceOf(PivotalClientApiException.class);

    reset(sdkClient);
    doReturn(applicationDetail).when(sdkClient).getApplicationByName(eq(cfRequestConfig));
    doAnswer(invocation -> { throw new Exception(); }).when(deploymentManager).destroyProcess(eq(startedProcess));
    ApplicationDetail applicationDetail1 =
        deploymentManager.upsizeApplicationWithSteadyStateCheck(cfRequestConfig, logCallback);
    assertThat(applicationDetail1).isNotNull();
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testPushApplicationUsingManifest() throws Exception {
    doNothing().when(cliClient).pushAppByCli(any(), any());
    doNothing().when(sdkClient).pushAppBySdk(any(), any(), any());

    doNothing().when(logCallback).saveExecutionLog(anyString());

    CfRequestConfig cfRequestConfig = CfRequestConfig.builder().useCFCLI(true).build();

    CfCreateApplicationRequestData requestData =
        CfCreateApplicationRequestData.builder().manifestFilePath("path").cfRequestConfig(cfRequestConfig).build();
    // actual call
    deploymentManager.createApplication(requestData, logCallback);

    ArgumentCaptor<CfCreateApplicationRequestData> captor =
        ArgumentCaptor.forClass(CfCreateApplicationRequestData.class);
    verify(cliClient).pushAppByCli(captor.capture(), any());
    CfCreateApplicationRequestData captorValue = captor.getValue();
    assertThat(captorValue).isEqualTo(requestData);

    cfRequestConfig.setUseCFCLI(false);
    // actual call
    deploymentManager.createApplication(requestData, logCallback);

    ArgumentCaptor<CfRequestConfig> cfRequestCaptor = ArgumentCaptor.forClass(CfRequestConfig.class);
    verify(sdkClient).pushAppBySdk(cfRequestCaptor.capture(), any(), any());
    CfRequestConfig captorValueConfig = cfRequestCaptor.getValue();
    assertThat(captorValueConfig).isEqualTo(cfRequestConfig);
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testRenameApplication() throws Exception {
    CfRenameRequest request = Mockito.mock(CfRenameRequest.class);
    when(request.getName()).thenReturn("app");
    when(request.getNewName()).thenReturn("app", "app1");
    deploymentManager.renameApplication(request, logCallback);
    verify(sdkClient, times(0)).renameApplication(request);
    deploymentManager.renameApplication(request, logCallback);
    verify(sdkClient).renameApplication(request);
  }

  @Test
  @Owner(developers = ARVIND)
  @Category(UnitTests.class)
  public void testMatchesPrefix2() {
    String prefix = "A__B__c";
    assertThat(deploymentManager.matchesPrefix2(prefix, "A__b__c__34")).isTrue();
    assertThat(deploymentManager.matchesPrefix2(prefix, "A__b__c__INACTIVE")).isTrue();
    assertThat(deploymentManager.matchesPrefix2(prefix, "A__b__c__inactive")).isTrue();
    assertThat(deploymentManager.matchesPrefix2(prefix, "A__b__cinactive")).isFalse();
    assertThat(deploymentManager.matchesPrefix2(prefix, "A__b__c__387")).isTrue();
    assertThat(deploymentManager.matchesPrefix2(prefix, "A__b__c__387b")).isFalse();
    assertThat(deploymentManager.matchesPrefix2(prefix, "A__b__c__hello387b")).isFalse();
    assertThat(deploymentManager.matchesPrefix2(prefix, "A__b__c__hello__387b")).isFalse();
    assertThat(deploymentManager.matchesPrefix2(prefix, "A__b__c__hello__7b")).isFalse();
    assertThat(deploymentManager.matchesPrefix2(prefix, "a__b__c")).isTrue();
    assertThat(deploymentManager.matchesPrefix2(prefix, "a__B__c")).isTrue();
    assertThat(deploymentManager.matchesPrefix2(prefix, "B__B__c")).isFalse();
  }
}
