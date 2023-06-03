/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.cf;

import static io.harness.delegate.cf.CfTestConstants.ACCOUNT_ID;
import static io.harness.delegate.cf.CfTestConstants.APP_ID;
import static io.harness.delegate.cf.CfTestConstants.APP_NAME;
import static io.harness.delegate.cf.CfTestConstants.ORG;
import static io.harness.delegate.cf.CfTestConstants.RUNNING;
import static io.harness.delegate.cf.CfTestConstants.SPACE;
import static io.harness.delegate.cf.CfTestConstants.STOPPED;
import static io.harness.delegate.cf.CfTestConstants.getPcfConfig;
import static io.harness.rule.OwnerRule.ADWAIT;
import static io.harness.rule.OwnerRule.ANIL;
import static io.harness.rule.OwnerRule.BOJANA;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.beans.pcf.CfAppSetupTimeDetails;
import io.harness.delegate.beans.pcf.CfServiceData;
import io.harness.delegate.beans.pcf.ResizeStrategy;
import io.harness.delegate.task.pcf.CfCommandRequest;
import io.harness.delegate.task.pcf.request.CfCommandRollbackRequest;
import io.harness.delegate.task.pcf.response.CfCommandExecutionResponse;
import io.harness.delegate.task.pcf.response.CfDeployCommandResponse;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.pcf.CfDeploymentManager;
import io.harness.pcf.PivotalClientApiException;
import io.harness.pcf.model.CfAppAutoscalarRequestData;
import io.harness.pcf.model.CfRenameRequest;
import io.harness.pcf.model.CfRequestConfig;
import io.harness.pcf.model.PcfConstants;
import io.harness.rule.Owner;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.SecretDecryptionService;

import com.google.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.cloudfoundry.operations.applications.ApplicationDetail;
import org.cloudfoundry.operations.applications.ApplicationSummary;
import org.cloudfoundry.operations.applications.InstanceDetail;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;

@OwnedBy(HarnessTeam.CDP)
@RunWith(MockitoJUnitRunner.class)
public class PcfRollbackCommandTaskHandlerTest extends CategoryTest {
  @Mock LogCallback executionLogCallback;
  @Mock ILogStreamingTaskClient logStreamingTaskClient;
  @Mock CfDeploymentManager cfDeploymentManager;
  @Mock SecretDecryptionService encryptionService;
  @Mock EncryptedDataDetail encryptedDataDetail;

  @InjectMocks @Spy PcfCommandTaskBaseHelper pcfCommandTaskHelper;
  @InjectMocks @Inject PcfRollbackCommandTaskHandler pcfRollbackCommandTaskHandler;

  @Before
  public void setUp() {
    doReturn(executionLogCallback).when(logStreamingTaskClient).obtainLogCallback(anyString());
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testPerformRollback() throws PivotalClientApiException, IOException {
    CfCommandRequest cfCommandRequest =
        CfCommandRollbackRequest.builder()
            .pcfCommandType(CfCommandRequest.PcfCommandType.ROLLBACK)
            .pcfConfig(getPcfConfig())
            .accountId(ACCOUNT_ID)
            .instanceData(
                Arrays.asList(CfServiceData.builder().name("a_s_e__6").previousCount(2).desiredCount(0).build(),
                    CfServiceData.builder().name("a_s_e__4").previousCount(0).desiredCount(2).build()))
            .resizeStrategy(ResizeStrategy.DOWNSIZE_OLD_FIRST)
            .organization(ORG)
            .space(SPACE)
            .timeoutIntervalInMin(5)
            .newApplicationDetails(
                CfAppSetupTimeDetails.builder().applicationName("a_s_e__6").urls(Collections.emptyList()).build())
            .build();

    doReturn(ApplicationDetail.builder()
                 .id("Guid:a_s_e__6")
                 .diskQuota(1)
                 .instances(0)
                 .memoryLimit(1)
                 .name("a_s_e__")
                 .requestedState(STOPPED)
                 .stack("")
                 .runningInstances(0)
                 .build())
        .doReturn(ApplicationDetail.builder()
                      .id("Guid:a_s_e__4")
                      .diskQuota(1)
                      .instances(1)
                      .memoryLimit(1)
                      .name("a_s_e__4")
                      .requestedState(STOPPED)
                      .stack("")
                      .runningInstances(0)
                      .build())
        .when(cfDeploymentManager)
        .getApplicationByName(any());

    ApplicationDetail applicationDetailDownsize = ApplicationDetail.builder()
                                                      .id("Guid:a_s_e__6")
                                                      .diskQuota(1)
                                                      .instances(0)
                                                      .memoryLimit(1)
                                                      .name("a_s_e__6")
                                                      .requestedState(STOPPED)
                                                      .stack("")
                                                      .runningInstances(0)
                                                      .build();

    doReturn(ApplicationDetail.builder()
                 .instanceDetails(Arrays.asList(InstanceDetail.builder()
                                                    .cpu(1.0)
                                                    .diskQuota((long) 1.23)
                                                    .diskUsage((long) 1.23)
                                                    .index("0")
                                                    .memoryQuota((long) 1)
                                                    .memoryUsage((long) 1)
                                                    .build(),
                     InstanceDetail.builder()
                         .cpu(1.0)
                         .diskQuota((long) 1.23)
                         .diskUsage((long) 1.23)
                         .index("1")
                         .memoryQuota((long) 1)
                         .memoryUsage((long) 1)
                         .build()))
                 .id("Guid:a_s_e__4")
                 .diskQuota(1)
                 .instances(1)
                 .memoryLimit(1)
                 .name("a_s_e__4")
                 .requestedState("RUNNING")
                 .stack("")
                 .runningInstances(1)
                 .build())
        .doReturn(applicationDetailDownsize)
        .when(cfDeploymentManager)
        .upsizeApplicationWithSteadyStateCheck(any(), any());

    doReturn(ApplicationDetail.builder()
                 .instanceDetails(Arrays.asList(InstanceDetail.builder()
                                                    .cpu(1.0)
                                                    .diskQuota((long) 1.23)
                                                    .diskUsage((long) 1.23)
                                                    .index("1")
                                                    .memoryQuota((long) 1)
                                                    .memoryUsage((long) 1)
                                                    .build(),
                     InstanceDetail.builder()
                         .cpu(1.0)
                         .diskQuota((long) 1.23)
                         .diskUsage((long) 1.23)
                         .index("0")
                         .memoryQuota((long) 1)
                         .memoryUsage((long) 1)
                         .build()))
                 .id("Guid:a_s_e__4")
                 .diskQuota(1)
                 .requestedState("RUNNING")
                 .instances(1)
                 .memoryLimit(1)
                 .name("a_s_e__4")
                 .stack("")
                 .runningInstances(1)
                 .build())
        .doReturn(applicationDetailDownsize)
        .when(cfDeploymentManager)
        .resizeApplication(any(), any());

    CfCommandExecutionResponse cfCommandExecutionResponse =
        pcfRollbackCommandTaskHandler.executeTaskInternal(cfCommandRequest, null, logStreamingTaskClient, false);

    assertThat(cfCommandExecutionResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    CfDeployCommandResponse pcfDeployCommandResponse =
        (CfDeployCommandResponse) cfCommandExecutionResponse.getPcfCommandResponse();

    assertThat(pcfDeployCommandResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    assertThat(pcfDeployCommandResponse.getPcfInstanceElements()).isNotNull();
    assertThat(pcfDeployCommandResponse.getPcfInstanceElements()).hasSize(2);

    Set<String> pcfInstanceElements = new HashSet<>();
    ((CfDeployCommandResponse) cfCommandExecutionResponse.getPcfCommandResponse())
        .getPcfInstanceElements()
        .forEach(pcfInstanceElement
            -> pcfInstanceElements.add(
                pcfInstanceElement.getApplicationId() + ":" + pcfInstanceElement.getInstanceIndex()));
    assertThat(pcfInstanceElements.contains("Guid:a_s_e__4:0")).isTrue();
    assertThat(pcfInstanceElements.contains("Guid:a_s_e__4:1")).isTrue();

    // Test Exception flow
    doThrow(new IOException("")).when(pcfCommandTaskHelper).generateWorkingDirectoryForDeployment();
    cfCommandExecutionResponse =
        pcfRollbackCommandTaskHandler.executeTaskInternal(cfCommandRequest, null, logStreamingTaskClient, false);
    assertThat(cfCommandExecutionResponse.getErrorMessage()).isEqualTo("IOException: ");
    assertThat(cfCommandExecutionResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.FAILURE);
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testEnableAutoscalarIfNeeded() throws PivotalClientApiException {
    reset(cfDeploymentManager);

    CfServiceData cfServiceData = CfServiceData.builder().name(APP_NAME).id(APP_ID).build();
    List<CfServiceData> upsizeList = Collections.singletonList(cfServiceData);

    CfAppAutoscalarRequestData pcfAppAutoscalarRequestData =
        CfAppAutoscalarRequestData.builder().configPathVar("path").build();
    doReturn(true).when(cfDeploymentManager).changeAutoscalarState(any(), any(), anyBoolean());

    pcfRollbackCommandTaskHandler.enableAutoscalarIfNeeded(
        emptyList(), pcfAppAutoscalarRequestData, executionLogCallback);
    verify(cfDeploymentManager, never()).changeAutoscalarState(any(), any(), anyBoolean());

    pcfRollbackCommandTaskHandler.enableAutoscalarIfNeeded(
        upsizeList, pcfAppAutoscalarRequestData, executionLogCallback);
    verify(cfDeploymentManager, never()).changeAutoscalarState(any(), any(), anyBoolean());

    cfServiceData.setDisableAutoscalarPerformed(true);
    pcfRollbackCommandTaskHandler.enableAutoscalarIfNeeded(
        upsizeList, pcfAppAutoscalarRequestData, executionLogCallback);
    verify(cfDeploymentManager, times(1)).changeAutoscalarState(any(), any(), anyBoolean());
  }

  @Test
  @Owner(developers = BOJANA)
  @Category(UnitTests.class)
  public void testRestoreRoutesForOldApplication() throws PivotalClientApiException {
    String appName = APP_NAME;
    List<String> urls = Collections.singletonList("url1");
    ApplicationDetail applicationDetail = ApplicationDetail.builder()
                                              .id("10")
                                              .diskQuota(1)
                                              .instances(1)
                                              .memoryLimit(1)
                                              .name("app1")
                                              .requestedState(STOPPED)
                                              .stack("")
                                              .runningInstances(1)
                                              .build();
    when(cfDeploymentManager.getApplicationByName(any())).thenReturn(applicationDetail);
    CfRequestConfig cfRequestConfig = CfRequestConfig.builder().build();

    // map route maps
    CfCommandRollbackRequest pcfCommandRequest = createPcfCommandRollbackRequest(true, appName, urls);
    pcfRollbackCommandTaskHandler.restoreRoutesForOldApplication(
        pcfCommandRequest, cfRequestConfig, executionLogCallback);
    assertThat(cfRequestConfig.getApplicationName()).isEqualTo(appName);
    verify(pcfCommandTaskHelper).mapRouteMaps(appName, urls, cfRequestConfig, executionLogCallback);
  }

  @Test
  @Owner(developers = BOJANA)
  @Category(UnitTests.class)
  public void testRestoreRoutesForOldApplicationNoAppsToDownsize() throws PivotalClientApiException {
    ApplicationDetail applicationDetail = ApplicationDetail.builder()
                                              .id("10")
                                              .diskQuota(1)
                                              .instances(1)
                                              .memoryLimit(1)
                                              .name("app1")
                                              .requestedState(STOPPED)
                                              .stack("")
                                              .runningInstances(1)
                                              .build();
    when(cfDeploymentManager.getApplicationByName(any())).thenReturn(applicationDetail);
    CfRequestConfig cfRequestConfig = CfRequestConfig.builder().build();

    CfCommandRollbackRequest pcfCommandRequest = createPcfCommandRollbackRequest(false, null, null);
    pcfRollbackCommandTaskHandler.restoreRoutesForOldApplication(
        pcfCommandRequest, cfRequestConfig, executionLogCallback);
    verify(pcfCommandTaskHelper, never())
        .mapRouteMaps(anyString(), anyList(), any(CfRequestConfig.class), any(LogCallback.class));
  }

  @Test
  @Owner(developers = BOJANA)
  @Category(UnitTests.class)
  public void testRestoreRoutesForOldApplicationEmptyUrls() throws PivotalClientApiException {
    String appName = APP_NAME;
    ApplicationDetail applicationDetail = ApplicationDetail.builder()
                                              .id("10")
                                              .diskQuota(1)
                                              .instances(1)
                                              .memoryLimit(1)
                                              .name("app1")
                                              .requestedState(STOPPED)
                                              .stack("")
                                              .runningInstances(1)
                                              .build();
    when(cfDeploymentManager.getApplicationByName(any())).thenReturn(applicationDetail);
    CfRequestConfig cfRequestConfig = CfRequestConfig.builder().build();

    CfCommandRollbackRequest pcfCommandRequest = createPcfCommandRollbackRequest(true, appName, null);
    pcfRollbackCommandTaskHandler.restoreRoutesForOldApplication(
        pcfCommandRequest, cfRequestConfig, executionLogCallback);
    assertThat(cfRequestConfig.getApplicationName()).isEqualTo(appName);
    verify(pcfCommandTaskHelper, never())
        .mapRouteMaps(anyString(), anyList(), any(CfRequestConfig.class), any(LogCallback.class));
  }

  @Test
  @Owner(developers = BOJANA)
  @Category(UnitTests.class)
  public void testRestoreRoutesForOldApplication2() throws PivotalClientApiException {
    String appName = "appName";
    ApplicationDetail applicationDetail = ApplicationDetail.builder()
                                              .id("10")
                                              .diskQuota(1)
                                              .instances(1)
                                              .memoryLimit(1)
                                              .name("app1")
                                              .requestedState(STOPPED)
                                              .stack("")
                                              .urls(Collections.singletonList("url1"))
                                              .runningInstances(1)
                                              .build();
    when(cfDeploymentManager.getApplicationByName(any())).thenReturn(applicationDetail);
    CfRequestConfig cfRequestConfig = CfRequestConfig.builder().build();

    List<String> urls = Collections.singletonList("url2");
    CfCommandRollbackRequest pcfCommandRequest = createPcfCommandRollbackRequest(true, appName, urls);
    pcfRollbackCommandTaskHandler.restoreRoutesForOldApplication(
        pcfCommandRequest, cfRequestConfig, executionLogCallback);
    assertThat(cfRequestConfig.getApplicationName()).isEqualTo(appName);
    verify(pcfCommandTaskHelper).mapRouteMaps(appName, urls, cfRequestConfig, executionLogCallback);
  }

  private CfCommandRollbackRequest createPcfCommandRollbackRequest(
      boolean downsizeApps, String appName, List<String> urls) {
    return CfCommandRollbackRequest.builder()
        .isStandardBlueGreenWorkflow(false)
        .appsToBeDownSized(downsizeApps
                ? Collections.singletonList(
                    CfAppSetupTimeDetails.builder().applicationName(appName).initialInstanceCount(1).urls(urls).build())
                : null)
        .build();
  }

  @Test
  @Owner(developers = BOJANA)
  @Category(UnitTests.class)
  public void testUnmapRoutesFromNewAppAfterDownsize() throws PivotalClientApiException {
    CfRequestConfig cfRequestConfig = CfRequestConfig.builder().build();

    // standard BG workflow
    CfCommandRollbackRequest commandRollbackRequest =
        CfCommandRollbackRequest.builder().isStandardBlueGreenWorkflow(true).build();
    pcfRollbackCommandTaskHandler.unmapRoutesFromNewAppAfterDownsize(
        executionLogCallback, commandRollbackRequest, cfRequestConfig);
    verify(pcfCommandTaskHelper, never()).unmapExistingRouteMaps(any(), any(), any());

    // no new applications
    commandRollbackRequest =
        CfCommandRollbackRequest.builder().isStandardBlueGreenWorkflow(false).newApplicationDetails(null).build();
    pcfRollbackCommandTaskHandler.unmapRoutesFromNewAppAfterDownsize(
        executionLogCallback, commandRollbackRequest, cfRequestConfig);
    verify(pcfCommandTaskHelper, never()).unmapExistingRouteMaps(any(), any(), any());

    // empty name of new application details
    commandRollbackRequest = CfCommandRollbackRequest.builder()
                                 .isStandardBlueGreenWorkflow(false)
                                 .newApplicationDetails(CfAppSetupTimeDetails.builder().build())
                                 .build();
    pcfRollbackCommandTaskHandler.unmapRoutesFromNewAppAfterDownsize(
        executionLogCallback, commandRollbackRequest, cfRequestConfig);
    verify(pcfCommandTaskHelper, never()).unmapExistingRouteMaps(any(), any(), any());

    String appName = "appName";
    ApplicationDetail applicationDetail = ApplicationDetail.builder()
                                              .id("10")
                                              .diskQuota(1)
                                              .instances(0)
                                              .memoryLimit(1)
                                              .name("app1")
                                              .requestedState(STOPPED)
                                              .stack("")
                                              .urls(Collections.singletonList("url1"))
                                              .runningInstances(1)
                                              .build();
    when(cfDeploymentManager.getApplicationByName(any())).thenReturn(applicationDetail);
    commandRollbackRequest =
        CfCommandRollbackRequest.builder()
            .isStandardBlueGreenWorkflow(false)
            .newApplicationDetails(CfAppSetupTimeDetails.builder().applicationName(appName).build())
            .build();

    pcfRollbackCommandTaskHandler.unmapRoutesFromNewAppAfterDownsize(
        executionLogCallback, commandRollbackRequest, cfRequestConfig);
    assertThat(cfRequestConfig.getApplicationName()).isEqualTo("app1");
    verify(pcfCommandTaskHelper).unmapExistingRouteMaps(applicationDetail, cfRequestConfig, executionLogCallback);
  }

  @Test()
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  // Ideally we should assert the PCF app name which is passed to
  // upSizeApplicationWithSteadyStateCheck
  // mapRouteMapForApplication
  // unmapRouteMapForApplication
  // deleteApplication
  //
  // But we are not able to do so because we create one object of CfRequestConfig and update it property -
  // "applicationName" before calling each of the above method.
  // Hence using captor we will get the same object and value of app name will be the last one which was set
  public void testVersionToVersionBasicCanaryRollback() throws PivotalClientApiException {
    List<String> prodRoutes = Arrays.asList("harness-prod1-pcf.com", "harness-prod2-pcf.com");
    String cfAppNamePrefix = "PaymentApp";
    String newAppName = cfAppNamePrefix + "__6";
    String prevActiveAppName = cfAppNamePrefix + "__5";
    String inActiveAppName = cfAppNamePrefix + "__4";

    CfAppSetupTimeDetails newApp = CfAppSetupTimeDetails.builder()
                                       .applicationName(newAppName)
                                       .applicationGuid("6")
                                       .initialInstanceCount(0)
                                       .urls(prodRoutes)
                                       .build();

    CfAppSetupTimeDetails prevActiveApp = CfAppSetupTimeDetails.builder()
                                              .applicationName(prevActiveAppName)
                                              .applicationGuid("5")
                                              .initialInstanceCount(2)
                                              .urls(prodRoutes)
                                              .build();

    CfAppSetupTimeDetails existingInActiveApp = CfAppSetupTimeDetails.builder()
                                                    .applicationName(inActiveAppName)
                                                    .applicationGuid("4")
                                                    .initialInstanceCount(2)
                                                    .urls(Collections.emptyList())
                                                    .build();

    CfCommandRollbackRequest cfCommandRequest =
        getRollbackRequest(newApp, prevActiveApp, existingInActiveApp, cfAppNamePrefix, false, false, -1);

    mockGetApplicationByName(prodRoutes, newAppName, prevActiveAppName, newApp, prevActiveApp);

    ApplicationDetail prevActiveDetailsAfterUpSize = ApplicationDetail.builder()
                                                         .id(prevActiveApp.getApplicationGuid())
                                                         .instances(2)
                                                         .diskQuota(1)
                                                         .memoryLimit(1)
                                                         .stack("Java")
                                                         .memoryLimit(1)
                                                         .name(prevActiveApp.getApplicationName())
                                                         .requestedState(RUNNING)
                                                         .runningInstances(2)
                                                         .build();

    doReturn(Collections.singletonList(newApp.getApplicationName()))
        .when(pcfCommandTaskHelper)
        .getAppNameBasedOnGuid(any(), eq(cfAppNamePrefix), eq(newApp.getApplicationGuid()));
    doReturn(prevActiveDetailsAfterUpSize)
        .when(cfDeploymentManager)
        .upsizeApplicationWithSteadyStateCheck(any(), any());
    doReturn(prevActiveDetailsAfterUpSize).when(cfDeploymentManager).resizeApplication(any(), any());

    CfCommandExecutionResponse cfCommandExecutionResponse =
        pcfRollbackCommandTaskHandler.executeTaskInternal(cfCommandRequest, null, logStreamingTaskClient, false);

    assertThat(cfCommandExecutionResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    CfDeployCommandResponse pcfDeployCommandResponse =
        (CfDeployCommandResponse) cfCommandExecutionResponse.getPcfCommandResponse();

    assertThat(pcfDeployCommandResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);

    verify(cfDeploymentManager, times(1)).upsizeApplicationWithSteadyStateCheck(any(), any());

    ArgumentCaptor<List> routesCaptor = ArgumentCaptor.forClass(List.class);
    ArgumentCaptor<CfRequestConfig> cfRequestCaptor = ArgumentCaptor.forClass(CfRequestConfig.class);

    verify(cfDeploymentManager, times(1))
        .mapRouteMapForApplication(cfRequestCaptor.capture(), routesCaptor.capture(), any());
    List routesCaptorValue = routesCaptor.getValue();
    assertThat(routesCaptorValue.size()).isEqualTo(2);
    assertThat(routesCaptorValue.get(0)).isEqualTo(prodRoutes.get(0));
    assertThat(routesCaptorValue.get(1)).isEqualTo(prodRoutes.get(1));

    verify(cfDeploymentManager, times(1)).resizeApplication(any(), any());

    ArgumentCaptor<List> unMapRoutesCaptor = ArgumentCaptor.forClass(List.class);
    ArgumentCaptor<CfRequestConfig> unMapRequestCaptor = ArgumentCaptor.forClass(CfRequestConfig.class);
    verify(cfDeploymentManager, times(1))
        .unmapRouteMapForApplication(unMapRequestCaptor.capture(), unMapRoutesCaptor.capture(), any());
    routesCaptorValue = routesCaptor.getValue();
    assertThat(routesCaptorValue.size()).isEqualTo(2);
    assertThat(routesCaptorValue.get(0)).isEqualTo(prodRoutes.get(0));
    assertThat(routesCaptorValue.get(1)).isEqualTo(prodRoutes.get(1));

    verify(cfDeploymentManager, times(1)).deleteApplication(any());
    verify(cfDeploymentManager, times(0)).renameApplication(any(), any());
  }

  @Test()
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  // Before the start of the deployment following apps were present
  //{PaymentApp_5, Guid = 5, Active}
  //{PaymentApp_4, Guid = 4, InActive}
  //{PaymentApp_3, Guid = 3}
  //
  // After App Setup step the apps would have been renamed to
  //{PaymentApp,          Guid = 6, Active} --> New App
  //{PaymentApp_INACTIVE, Guid = 5, InActive}
  //{PaymentApp_4,        Guid = 4}
  //{PaymentApp_3,        Guid = 3}
  public void testVersionToNonVersionBasicCanaryRollback() throws PivotalClientApiException {
    List<String> prodRoutes = Arrays.asList("harness-prod1-pcf.com", "harness-prod2-pcf.com");
    String cfAppNamePrefix = "PaymentApp";
    String prevActiveAppName = cfAppNamePrefix + "__INACTIVE";
    String preInActiveAppName = cfAppNamePrefix + "__4";

    CfAppSetupTimeDetails newApp = CfAppSetupTimeDetails.builder()
                                       .applicationName(cfAppNamePrefix)
                                       .applicationGuid("6")
                                       .initialInstanceCount(0)
                                       .urls(prodRoutes)
                                       .build();

    CfAppSetupTimeDetails prevActiveApp = CfAppSetupTimeDetails.builder()
                                              .applicationName(prevActiveAppName)
                                              .applicationGuid("5")
                                              .initialInstanceCount(2)
                                              .urls(prodRoutes)
                                              .build();

    CfAppSetupTimeDetails existingInActiveApp = CfAppSetupTimeDetails.builder()
                                                    .applicationName(preInActiveAppName)
                                                    .applicationGuid("4")
                                                    .initialInstanceCount(2)
                                                    .urls(Collections.emptyList())
                                                    .build();

    CfCommandRollbackRequest cfCommandRequest =
        getRollbackRequest(newApp, prevActiveApp, existingInActiveApp, cfAppNamePrefix, true, true, 5);

    mockGetApplicationByName(prodRoutes, cfAppNamePrefix, prevActiveAppName, newApp, prevActiveApp);

    ApplicationDetail prevActiveDetailsAfterUpSize = ApplicationDetail.builder()
                                                         .id(prevActiveApp.getApplicationGuid())
                                                         .instances(2)
                                                         .diskQuota(1)
                                                         .memoryLimit(1)
                                                         .stack("Java")
                                                         .memoryLimit(1)
                                                         .name(prevActiveAppName)
                                                         .requestedState(RUNNING)
                                                         .runningInstances(2)
                                                         .build();

    doReturn(Collections.singletonList(newApp.getApplicationName()))
        .when(pcfCommandTaskHelper)
        .getAppNameBasedOnGuid(any(), eq(cfAppNamePrefix), eq(newApp.getApplicationGuid()));
    doReturn(prevActiveDetailsAfterUpSize)
        .when(cfDeploymentManager)
        .upsizeApplicationWithSteadyStateCheck(any(), any());

    doReturn(prevActiveDetailsAfterUpSize).when(cfDeploymentManager).resizeApplication(any(), any());
    doReturn(getPreviousReleasesAfterNonVersioningRenaming(cfCommandRequest))
        .when(cfDeploymentManager)
        .getPreviousReleases(any(), eq(cfAppNamePrefix));

    CfCommandExecutionResponse cfCommandExecutionResponse =
        pcfRollbackCommandTaskHandler.executeTaskInternal(cfCommandRequest, null, logStreamingTaskClient, false);

    assertThat(cfCommandExecutionResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    CfDeployCommandResponse pcfDeployCommandResponse =
        (CfDeployCommandResponse) cfCommandExecutionResponse.getPcfCommandResponse();

    assertThat(pcfDeployCommandResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);

    verify(cfDeploymentManager, times(1)).upsizeApplicationWithSteadyStateCheck(any(), any());

    ArgumentCaptor<List> routesCaptor = ArgumentCaptor.forClass(List.class);
    ArgumentCaptor<CfRequestConfig> cfRequestCaptor = ArgumentCaptor.forClass(CfRequestConfig.class);

    verify(cfDeploymentManager, times(1))
        .mapRouteMapForApplication(cfRequestCaptor.capture(), routesCaptor.capture(), any());
    List routesCaptorValue = routesCaptor.getValue();
    assertThat(routesCaptorValue.size()).isEqualTo(2);
    assertThat(routesCaptorValue.get(0)).isEqualTo(prodRoutes.get(0));
    assertThat(routesCaptorValue.get(1)).isEqualTo(prodRoutes.get(1));

    verify(cfDeploymentManager, times(1)).resizeApplication(any(), any());

    ArgumentCaptor<List> unMapRoutesCaptor = ArgumentCaptor.forClass(List.class);
    ArgumentCaptor<CfRequestConfig> unMapRequestCaptor = ArgumentCaptor.forClass(CfRequestConfig.class);
    verify(cfDeploymentManager, times(1))
        .unmapRouteMapForApplication(unMapRequestCaptor.capture(), unMapRoutesCaptor.capture(), any());
    routesCaptorValue = routesCaptor.getValue();
    assertThat(routesCaptorValue.size()).isEqualTo(2);
    assertThat(routesCaptorValue.get(0)).isEqualTo(prodRoutes.get(0));
    assertThat(routesCaptorValue.get(1)).isEqualTo(prodRoutes.get(1));

    ArgumentCaptor<CfRequestConfig> deleteRequestCaptor = ArgumentCaptor.forClass(CfRequestConfig.class);
    verify(cfDeploymentManager, times(1)).deleteApplication(deleteRequestCaptor.capture());
    assertThat(deleteRequestCaptor.getValue().getApplicationName()).isEqualTo(newApp.getApplicationName());

    ArgumentCaptor<CfRenameRequest> renamedRequestCaptor = ArgumentCaptor.forClass(CfRenameRequest.class);
    verify(cfDeploymentManager, times(4)).renameApplication(renamedRequestCaptor.capture(), any());
    List<CfRenameRequest> requestCaptorAllValues = renamedRequestCaptor.getAllValues();
    assertThat(requestCaptorAllValues.size()).isEqualTo(4);

    // first renaming due to non-version mode
    assertThat(requestCaptorAllValues.get(0).getGuid())
        .isEqualTo(cfCommandRequest.getAppsToBeDownSized().get(0).getApplicationGuid());
    assertThat(requestCaptorAllValues.get(0).getNewName()).isEqualTo(cfAppNamePrefix);
    assertThat(requestCaptorAllValues.get(1).getGuid())
        .isEqualTo(cfCommandRequest.getExistingInActiveApplicationDetails().getApplicationGuid());
    assertThat(requestCaptorAllValues.get(1).getNewName()).isEqualTo(cfAppNamePrefix + "__INACTIVE");

    // second renaming due to non-version to version mode
    assertThat(requestCaptorAllValues.get(2).getGuid())
        .isEqualTo(cfCommandRequest.getAppsToBeDownSized().get(0).getApplicationGuid());
    assertThat(requestCaptorAllValues.get(2).getNewName()).isEqualTo(cfAppNamePrefix + "__5");
    assertThat(requestCaptorAllValues.get(3).getGuid())
        .isEqualTo(cfCommandRequest.getExistingInActiveApplicationDetails().getApplicationGuid());
    assertThat(requestCaptorAllValues.get(3).getNewName()).isEqualTo(cfAppNamePrefix + "__4");
  }

  @Test()
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  // Before the start of the deployment following apps were present
  //{PaymentApp,          Guid = 5, Active}
  //{PaymentApp_INACTIVE, Guid = 4, InActive}
  //{PaymentApp_3,        Guid = 3}
  //
  // After App Setup step the apps would have been renamed to
  //{PaymentApp,          Guid = 6, Active} --> New App
  //{PaymentApp_INACTIVE, Guid = 5, InActive}
  //{PaymentApp_4,        Guid = 4}
  //{PaymentApp_3,        Guid = 3}
  public void testNonVersionToNonVersionBasicCanaryRollback() throws PivotalClientApiException {
    List<String> prodRoutes = Arrays.asList("harness-prod1-pcf.com", "harness-prod2-pcf.com");
    String cfAppNamePrefix = "PaymentApp";
    String prevActiveAppName = cfAppNamePrefix + "__INACTIVE";
    String preInActiveAppName = cfAppNamePrefix + "__4";

    CfAppSetupTimeDetails newApp = CfAppSetupTimeDetails.builder()
                                       .applicationName(cfAppNamePrefix)
                                       .applicationGuid("6")
                                       .initialInstanceCount(0)
                                       .urls(prodRoutes)
                                       .build();

    CfAppSetupTimeDetails prevActiveApp = CfAppSetupTimeDetails.builder()
                                              .applicationName(prevActiveAppName)
                                              .applicationGuid("5")
                                              .initialInstanceCount(2)
                                              .urls(prodRoutes)
                                              .build();

    CfAppSetupTimeDetails existingInActiveApp = CfAppSetupTimeDetails.builder()
                                                    .applicationName(preInActiveAppName)
                                                    .applicationGuid("4")
                                                    .initialInstanceCount(2)
                                                    .urls(Collections.emptyList())
                                                    .build();

    CfCommandRollbackRequest cfCommandRequest =
        getRollbackRequest(newApp, prevActiveApp, existingInActiveApp, cfAppNamePrefix, true, false, -1);

    mockGetApplicationByName(prodRoutes, cfAppNamePrefix, prevActiveAppName, newApp, prevActiveApp);

    ApplicationDetail prevActiveDetailsAfterUpSize = ApplicationDetail.builder()
                                                         .id(prevActiveApp.getApplicationGuid())
                                                         .instances(2)
                                                         .diskQuota(1)
                                                         .memoryLimit(1)
                                                         .stack("Java")
                                                         .memoryLimit(1)
                                                         .name(prevActiveAppName)
                                                         .requestedState(RUNNING)
                                                         .runningInstances(2)
                                                         .build();

    doReturn(Collections.singletonList(newApp.getApplicationName()))
        .when(pcfCommandTaskHelper)
        .getAppNameBasedOnGuid(any(), eq(cfAppNamePrefix), eq(newApp.getApplicationGuid()));
    doReturn(prevActiveDetailsAfterUpSize)
        .when(cfDeploymentManager)
        .upsizeApplicationWithSteadyStateCheck(any(), any());

    doReturn(prevActiveDetailsAfterUpSize).when(cfDeploymentManager).resizeApplication(any(), any());

    CfCommandExecutionResponse cfCommandExecutionResponse =
        pcfRollbackCommandTaskHandler.executeTaskInternal(cfCommandRequest, null, logStreamingTaskClient, false);

    assertThat(cfCommandExecutionResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    CfDeployCommandResponse pcfDeployCommandResponse =
        (CfDeployCommandResponse) cfCommandExecutionResponse.getPcfCommandResponse();

    assertThat(pcfDeployCommandResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);

    verify(cfDeploymentManager, times(1)).upsizeApplicationWithSteadyStateCheck(any(), any());

    ArgumentCaptor<List> routesCaptor = ArgumentCaptor.forClass(List.class);
    ArgumentCaptor<CfRequestConfig> cfRequestCaptor = ArgumentCaptor.forClass(CfRequestConfig.class);

    verify(cfDeploymentManager, times(1))
        .mapRouteMapForApplication(cfRequestCaptor.capture(), routesCaptor.capture(), any());
    List routesCaptorValue = routesCaptor.getValue();
    assertThat(routesCaptorValue.size()).isEqualTo(2);
    assertThat(routesCaptorValue.get(0)).isEqualTo(prodRoutes.get(0));
    assertThat(routesCaptorValue.get(1)).isEqualTo(prodRoutes.get(1));

    verify(cfDeploymentManager, times(1)).resizeApplication(any(), any());

    ArgumentCaptor<List> unMapRoutesCaptor = ArgumentCaptor.forClass(List.class);
    ArgumentCaptor<CfRequestConfig> unMapRequestCaptor = ArgumentCaptor.forClass(CfRequestConfig.class);
    verify(cfDeploymentManager, times(1))
        .unmapRouteMapForApplication(unMapRequestCaptor.capture(), unMapRoutesCaptor.capture(), any());
    routesCaptorValue = routesCaptor.getValue();
    assertThat(routesCaptorValue.size()).isEqualTo(2);
    assertThat(routesCaptorValue.get(0)).isEqualTo(prodRoutes.get(0));
    assertThat(routesCaptorValue.get(1)).isEqualTo(prodRoutes.get(1));

    ArgumentCaptor<CfRequestConfig> deleteRequestCaptor = ArgumentCaptor.forClass(CfRequestConfig.class);
    verify(cfDeploymentManager, times(1)).deleteApplication(deleteRequestCaptor.capture());
    assertThat(deleteRequestCaptor.getValue().getApplicationName()).isEqualTo(newApp.getApplicationName());

    ArgumentCaptor<CfRenameRequest> renamedRequestCaptor = ArgumentCaptor.forClass(CfRenameRequest.class);
    verify(cfDeploymentManager, times(2)).renameApplication(renamedRequestCaptor.capture(), any());
    List<CfRenameRequest> requestCaptorAllValues = renamedRequestCaptor.getAllValues();
    assertThat(requestCaptorAllValues.size()).isEqualTo(2);

    // first renaming due to non-version mode
    assertThat(requestCaptorAllValues.get(0).getGuid())
        .isEqualTo(cfCommandRequest.getAppsToBeDownSized().get(0).getApplicationGuid());
    assertThat(requestCaptorAllValues.get(0).getNewName()).isEqualTo(cfAppNamePrefix);
    assertThat(requestCaptorAllValues.get(1).getGuid())
        .isEqualTo(cfCommandRequest.getExistingInActiveApplicationDetails().getApplicationGuid());
    assertThat(requestCaptorAllValues.get(1).getNewName()).isEqualTo(cfAppNamePrefix + "__INACTIVE");
  }

  @Test()
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  // Before the start of the deployment following apps were present
  //{PaymentApp,          Guid = 5, Active}
  //{PaymentApp_INACTIVE, Guid = 4, InActive}
  //{PaymentApp_3,        Guid = 3}
  //
  // After App Setup step the apps would have been renamed to
  //{PaymentApp_6,        Guid = 6, Active} --> New App
  //{PaymentApp_5,        Guid = 5, InActive}
  //{PaymentApp_4,        Guid = 4}
  //{PaymentApp_3,        Guid = 3}
  public void testNonVersionToVersionBasicCanaryRollback() throws PivotalClientApiException {
    List<String> prodRoutes = Arrays.asList("harness-prod1-pcf.com", "harness-prod2-pcf.com");
    String cfAppNamePrefix = "PaymentApp";
    String newAppName = cfAppNamePrefix + "__6";
    String prevActiveAppName = cfAppNamePrefix + "__5";
    String preInActiveAppName = cfAppNamePrefix + "__4";

    CfAppSetupTimeDetails newApp = CfAppSetupTimeDetails.builder()
                                       .applicationName(newAppName)
                                       .applicationGuid("6")
                                       .initialInstanceCount(0)
                                       .urls(prodRoutes)
                                       .build();

    CfAppSetupTimeDetails prevActiveApp = CfAppSetupTimeDetails.builder()
                                              .applicationName(prevActiveAppName)
                                              .applicationGuid("5")
                                              .initialInstanceCount(2)
                                              .urls(prodRoutes)
                                              .build();

    CfAppSetupTimeDetails existingInActiveApp = CfAppSetupTimeDetails.builder()
                                                    .applicationName(preInActiveAppName)
                                                    .applicationGuid("4")
                                                    .initialInstanceCount(2)
                                                    .urls(Collections.emptyList())
                                                    .build();

    CfCommandRollbackRequest cfCommandRequest =
        getRollbackRequest(newApp, prevActiveApp, existingInActiveApp, cfAppNamePrefix, false, true, -1);

    mockGetApplicationByName(prodRoutes, cfAppNamePrefix, prevActiveAppName, newApp, prevActiveApp);

    ApplicationDetail prevActiveDetailsAfterUpSize = ApplicationDetail.builder()
                                                         .id(prevActiveApp.getApplicationGuid())
                                                         .instances(2)
                                                         .diskQuota(1)
                                                         .memoryLimit(1)
                                                         .stack("Java")
                                                         .memoryLimit(1)
                                                         .name(prevActiveAppName)
                                                         .requestedState(RUNNING)
                                                         .runningInstances(2)
                                                         .build();

    doReturn(Collections.singletonList(newApp.getApplicationName()))
        .when(pcfCommandTaskHelper)
        .getAppNameBasedOnGuid(any(), eq(cfAppNamePrefix), eq(newApp.getApplicationGuid()));
    doReturn(prevActiveDetailsAfterUpSize)
        .when(cfDeploymentManager)
        .upsizeApplicationWithSteadyStateCheck(any(), any());

    doReturn(prevActiveDetailsAfterUpSize).when(cfDeploymentManager).resizeApplication(any(), any());
    doReturn(getPreviousReleasesDuringNonVersionToVersionRollback(cfCommandRequest))
        .when(cfDeploymentManager)
        .getPreviousReleases(any(), eq(cfAppNamePrefix));

    CfCommandExecutionResponse cfCommandExecutionResponse =
        pcfRollbackCommandTaskHandler.executeTaskInternal(cfCommandRequest, null, logStreamingTaskClient, false);

    assertThat(cfCommandExecutionResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    CfDeployCommandResponse pcfDeployCommandResponse =
        (CfDeployCommandResponse) cfCommandExecutionResponse.getPcfCommandResponse();

    assertThat(pcfDeployCommandResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);

    verify(cfDeploymentManager, times(1)).upsizeApplicationWithSteadyStateCheck(any(), any());

    ArgumentCaptor<List> routesCaptor = ArgumentCaptor.forClass(List.class);
    ArgumentCaptor<CfRequestConfig> cfRequestCaptor = ArgumentCaptor.forClass(CfRequestConfig.class);

    verify(cfDeploymentManager, times(1))
        .mapRouteMapForApplication(cfRequestCaptor.capture(), routesCaptor.capture(), any());
    List routesCaptorValue = routesCaptor.getValue();
    assertThat(routesCaptorValue.size()).isEqualTo(2);
    assertThat(routesCaptorValue.get(0)).isEqualTo(prodRoutes.get(0));
    assertThat(routesCaptorValue.get(1)).isEqualTo(prodRoutes.get(1));

    verify(cfDeploymentManager, times(1)).resizeApplication(any(), any());

    ArgumentCaptor<List> unMapRoutesCaptor = ArgumentCaptor.forClass(List.class);
    ArgumentCaptor<CfRequestConfig> unMapRequestCaptor = ArgumentCaptor.forClass(CfRequestConfig.class);
    verify(cfDeploymentManager, times(1))
        .unmapRouteMapForApplication(unMapRequestCaptor.capture(), unMapRoutesCaptor.capture(), any());
    routesCaptorValue = routesCaptor.getValue();
    assertThat(routesCaptorValue.size()).isEqualTo(2);
    assertThat(routesCaptorValue.get(0)).isEqualTo(prodRoutes.get(0));
    assertThat(routesCaptorValue.get(1)).isEqualTo(prodRoutes.get(1));

    ArgumentCaptor<CfRequestConfig> deleteRequestCaptor = ArgumentCaptor.forClass(CfRequestConfig.class);
    verify(cfDeploymentManager, times(1)).deleteApplication(deleteRequestCaptor.capture());
    assertThat(deleteRequestCaptor.getValue().getApplicationName()).isEqualTo(newApp.getApplicationName());

    ArgumentCaptor<CfRenameRequest> renamedRequestCaptor = ArgumentCaptor.forClass(CfRenameRequest.class);
    verify(cfDeploymentManager, times(2)).renameApplication(renamedRequestCaptor.capture(), any());
    List<CfRenameRequest> requestCaptorAllValues = renamedRequestCaptor.getAllValues();
    assertThat(requestCaptorAllValues.size()).isEqualTo(2);

    // renaming due to non-version to version mode
    assertThat(requestCaptorAllValues.get(0).getGuid())
        .isEqualTo(cfCommandRequest.getAppsToBeDownSized().get(0).getApplicationGuid());
    assertThat(requestCaptorAllValues.get(0).getNewName()).isEqualTo(cfAppNamePrefix);
    assertThat(requestCaptorAllValues.get(1).getGuid())
        .isEqualTo(cfCommandRequest.getExistingInActiveApplicationDetails().getApplicationGuid());
    assertThat(requestCaptorAllValues.get(1).getNewName()).isEqualTo(cfAppNamePrefix + "__INACTIVE");
  }

  @Test()
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testAppRenamingBlueGreenRollback() throws PivotalClientApiException {
    List<String> prodRoutes = Arrays.asList("harness-prod1-pcf.com", "harness-prod2-pcf.com");
    String cfAppNamePrefix = "PaymentApp";
    String newAppName = cfAppNamePrefix + "__6";
    String prevActiveAppName = cfAppNamePrefix + "__5";
    String inActiveAppName = cfAppNamePrefix + "__4";

    String interimAppName = PcfConstants.generateInterimAppName(cfAppNamePrefix);

    String inActiveAppGuid = "ca289f74-fdb6-486e-8679-2f91d8ce566e";
    String activeAppGuid = "806c5057-10d4-44c1-ba1b-9e56bd5a997f";
    String newAppGuid = "ca289f74-fdb6-486e-8679-2f91d8ce897";

    CfAppSetupTimeDetails newApp = CfAppSetupTimeDetails.builder()
                                       .applicationName(newAppName)
                                       .applicationGuid(newAppGuid)
                                       .initialInstanceCount(0)
                                       .urls(prodRoutes)
                                       .build();

    CfAppSetupTimeDetails activeApp = CfAppSetupTimeDetails.builder()
                                          .applicationName(prevActiveAppName)
                                          .applicationGuid(activeAppGuid)
                                          .initialInstanceCount(2)
                                          .urls(prodRoutes)
                                          .build();

    CfAppSetupTimeDetails inActiveApp = CfAppSetupTimeDetails.builder()
                                            .applicationName(inActiveAppName)
                                            .applicationGuid(inActiveAppGuid)
                                            .initialInstanceCount(2)
                                            .urls(Collections.emptyList())
                                            .build();

    CfCommandRollbackRequest cfCommandRequest =
        getRollbackRequest(newApp, activeApp, inActiveApp, cfAppNamePrefix, true, false, -1);
    cfCommandRequest.setStandardBlueGreenWorkflow(true);
    cfCommandRequest.getInstanceData().get(0).setId(newAppGuid);
    // In BG, pre active app would have been scaled up during Swap route rollback handler
    cfCommandRequest.getInstanceData().get(1).setDesiredCount(0);
    cfCommandRequest.getInstanceData().get(1).setId(activeAppGuid);

    mockGetApplicationByName(prodRoutes, newAppName, prevActiveAppName, newApp, activeApp);

    ApplicationDetail prevActiveDetailsAfterUpSize = ApplicationDetail.builder()
                                                         .id(activeApp.getApplicationGuid())
                                                         .instances(2)
                                                         .diskQuota(1)
                                                         .memoryLimit(1)
                                                         .stack("Java")
                                                         .memoryLimit(1)
                                                         .name(activeApp.getApplicationName())
                                                         .requestedState(RUNNING)
                                                         .runningInstances(2)
                                                         .build();

    doReturn(Collections.singletonList(interimAppName))
        .when(pcfCommandTaskHelper)
        .getAppNameBasedOnGuid(any(), eq(cfAppNamePrefix), eq(newApp.getApplicationGuid()));
    doReturn(prevActiveDetailsAfterUpSize)
        .when(cfDeploymentManager)
        .upsizeApplicationWithSteadyStateCheck(any(), any());
    doReturn(prevActiveDetailsAfterUpSize).when(cfDeploymentManager).resizeApplication(any(), any());

    CfCommandExecutionResponse cfCommandExecutionResponse =
        pcfRollbackCommandTaskHandler.executeTaskInternal(cfCommandRequest, null, logStreamingTaskClient, false);

    assertThat(cfCommandExecutionResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    CfDeployCommandResponse pcfDeployCommandResponse =
        (CfDeployCommandResponse) cfCommandExecutionResponse.getPcfCommandResponse();

    assertThat(pcfDeployCommandResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);

    ArgumentCaptor<List<CfServiceData>> upSizeListCaptor = ArgumentCaptor.forClass((Class) List.class);
    verify(pcfCommandTaskHelper).upsizeListOfInstances(any(), any(), any(), any(), upSizeListCaptor.capture(), any());
    List<CfServiceData> upSizeListCaptorValue = upSizeListCaptor.getValue();
    assertThat(upSizeListCaptorValue)
        .isEqualTo(Arrays.asList(CfServiceData.builder()
                                     .name(activeApp.getApplicationName())
                                     .previousCount(0)
                                     .desiredCount(0)
                                     .id(activeAppGuid)
                                     .build()));

    verify(cfDeploymentManager, times(1)).upsizeApplicationWithSteadyStateCheck(any(), any());
    verify(cfDeploymentManager, times(1)).resizeApplication(any(), any());
    verify(cfDeploymentManager, times(0)).renameApplication(any(), any());

    ArgumentCaptor<CfRequestConfig> cfRequestConfigCaptor = ArgumentCaptor.forClass(CfRequestConfig.class);
    verify(cfDeploymentManager, times(1)).deleteApplication(cfRequestConfigCaptor.capture());
    CfRequestConfig cfRequestConfig = cfRequestConfigCaptor.getValue();
    assertThat(cfRequestConfig.getApplicationName()).isEqualTo(interimAppName);
  }

  private List<ApplicationSummary> getPreviousReleasesDuringNonVersionToVersionRollback(
      CfCommandRollbackRequest rollbackRequest) {
    String cfAppNamePrefix = rollbackRequest.getCfAppNamePrefix();
    CfAppSetupTimeDetails inActiveApplicationDetails = rollbackRequest.getExistingInActiveApplicationDetails();
    CfAppSetupTimeDetails prevActiveAppDetails = rollbackRequest.getAppsToBeDownSized().get(0);

    List<ApplicationSummary> previousReleases = new ArrayList<>();
    previousReleases.add(ApplicationSummary.builder()
                             .name(cfAppNamePrefix + "__3")
                             .diskQuota(1)
                             .requestedState(STOPPED)
                             .id("3")
                             .instances(0)
                             .memoryLimit(1)
                             .runningInstances(0)
                             .build());
    previousReleases.add(ApplicationSummary.builder()
                             .name(inActiveApplicationDetails.getApplicationName())
                             .diskQuota(1)
                             .requestedState(STOPPED)
                             .id(inActiveApplicationDetails.getApplicationGuid())
                             .instances(0)
                             .memoryLimit(1)
                             .runningInstances(0)
                             .build());
    previousReleases.add(ApplicationSummary.builder()
                             .name(prevActiveAppDetails.getApplicationName())
                             .diskQuota(1)
                             .requestedState(RUNNING)
                             .id(prevActiveAppDetails.getApplicationGuid())
                             .instances(2)
                             .memoryLimit(1)
                             .runningInstances(2)
                             .build());
    return previousReleases;
  }

  private List<ApplicationSummary> getPreviousReleasesAfterNonVersioningRenaming(
      CfCommandRollbackRequest rollbackRequest) {
    String cfAppNamePrefix = rollbackRequest.getCfAppNamePrefix();
    CfAppSetupTimeDetails inActiveApplicationDetails = rollbackRequest.getExistingInActiveApplicationDetails();
    CfAppSetupTimeDetails prevActiveAppDetails = rollbackRequest.getAppsToBeDownSized().get(0);

    List<ApplicationSummary> previousReleases = new ArrayList<>();
    previousReleases.add(ApplicationSummary.builder()
                             .name(cfAppNamePrefix + "__INACTIVE")
                             .diskQuota(1)
                             .requestedState(STOPPED)
                             .id(inActiveApplicationDetails.getApplicationGuid())
                             .instances(0)
                             .memoryLimit(1)
                             .runningInstances(0)
                             .build());
    previousReleases.add(ApplicationSummary.builder()
                             .name(cfAppNamePrefix)
                             .diskQuota(1)
                             .requestedState(RUNNING)
                             .id(prevActiveAppDetails.getApplicationGuid())
                             .instances(2)
                             .memoryLimit(1)
                             .runningInstances(2)
                             .build());
    previousReleases.add(ApplicationSummary.builder()
                             .name(cfAppNamePrefix + "__3")
                             .diskQuota(1)
                             .requestedState(STOPPED)
                             .id("3")
                             .instances(0)
                             .memoryLimit(1)
                             .runningInstances(0)
                             .build());
    return previousReleases;
  }

  private void mockGetApplicationByName(List<String> prodRoutes, String newAppName, String prevActiveAppName,
      CfAppSetupTimeDetails newApp, CfAppSetupTimeDetails prevActiveApp) throws PivotalClientApiException {
    when(cfDeploymentManager.getApplicationByName(any())).thenAnswer(new Answer<ApplicationDetail>() {
      private int count = 0;

      @Override
      public ApplicationDetail answer(InvocationOnMock invocationOnMock) {
        count++;
        if (count == 1) {
          return ApplicationDetail.builder()
              .id(prevActiveApp.getApplicationGuid())
              .instances(0)
              .diskQuota(1)
              .stack("Java")
              .memoryLimit(1)
              .name(prevActiveAppName)
              .requestedState(STOPPED)
              .runningInstances(0)
              .urls()
              .build();
        } else if (count == 2) {
          return ApplicationDetail.builder()
              .id(prevActiveApp.getApplicationGuid())
              .instances(2)
              .diskQuota(1)
              .stack("Java")
              .memoryLimit(1)
              .name(prevActiveAppName)
              .requestedState(RUNNING)
              .runningInstances(2)
              .build();
        } else if (count == 3) {
          return ApplicationDetail.builder()
              .id(prevActiveApp.getApplicationGuid())
              .instances(0)
              .diskQuota(1)
              .stack("Java")
              .memoryLimit(1)
              .name(newAppName)
              .requestedState(STOPPED)
              .urls(prodRoutes)
              .runningInstances(0)
              .build();
        } else if (count == 4) {
          return ApplicationDetail.builder()
              .id(newApp.getApplicationGuid())
              .instances(0)
              .diskQuota(1)
              .stack("Java")
              .memoryLimit(1)
              .name(newAppName)
              .requestedState(STOPPED)
              .runningInstances(0)
              .build();
        }
        return ApplicationDetail.builder()
            .id(prevActiveApp.getApplicationGuid())
            .instances(2)
            .diskQuota(1)
            .stack("Java")
            .memoryLimit(1)
            .name(prevActiveAppName)
            .requestedState(RUNNING)
            .runningInstances(2)
            .build();
      }
    });
  }

  private CfCommandRollbackRequest getRollbackRequest(CfAppSetupTimeDetails newApp, CfAppSetupTimeDetails prevActiveApp,
      CfAppSetupTimeDetails existingInActiveApp, String cfAppNamePrefix, boolean isNonVersion, boolean isVersionChanged,
      int activeAppVersion) {
    return CfCommandRollbackRequest.builder()
        .pcfCommandType(CfCommandRequest.PcfCommandType.ROLLBACK)
        .pcfConfig(getPcfConfig())
        .accountId(ACCOUNT_ID)
        .instanceData(Arrays.asList(
            CfServiceData.builder().name(newApp.getApplicationName()).previousCount(2).desiredCount(0).build(),
            CfServiceData.builder().name(prevActiveApp.getApplicationName()).previousCount(0).desiredCount(2).build()))
        .resizeStrategy(ResizeStrategy.DOWNSIZE_OLD_FIRST)
        .organization(ORG)
        .space(SPACE)
        .timeoutIntervalInMin(5)
        .nonVersioning(isNonVersion)
        .versioningChanged(isVersionChanged)
        .cfAppNamePrefix(cfAppNamePrefix)
        .activeAppRevision(activeAppVersion)
        .isStandardBlueGreenWorkflow(false)
        .existingInActiveApplicationDetails(existingInActiveApp)
        .appsToBeDownSized(Collections.singletonList(prevActiveApp))
        .newApplicationDetails(newApp)
        .build();
  }
}
