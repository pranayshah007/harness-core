/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.cf;

import static io.harness.delegate.cf.CfTestConstants.ACCOUNT_ID;
import static io.harness.delegate.cf.CfTestConstants.ORG;
import static io.harness.delegate.cf.CfTestConstants.RUNNING;
import static io.harness.delegate.cf.CfTestConstants.SPACE;
import static io.harness.delegate.cf.CfTestConstants.STOPPED;
import static io.harness.delegate.cf.CfTestConstants.getPcfConfig;
import static io.harness.delegate.cf.CfTestConstants.getRouteUpdateRequest;
import static io.harness.pcf.model.PcfConstants.DELIMITER;
import static io.harness.pcf.model.PcfConstants.INACTIVE_APP_NAME_SUFFIX;
import static io.harness.rule.OwnerRule.ADWAIT;
import static io.harness.rule.OwnerRule.ANIL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.beans.pcf.CfAppSetupTimeDetails;
import io.harness.delegate.beans.pcf.CfRouteUpdateRequestConfigData;
import io.harness.delegate.cf.apprenaming.AppNamingStrategy;
import io.harness.delegate.task.pcf.CfCommandRequest;
import io.harness.delegate.task.pcf.request.CfCommandRouteUpdateRequest;
import io.harness.delegate.task.pcf.response.CfCommandExecutionResponse;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.pcf.CfDeploymentManager;
import io.harness.pcf.PivotalClientApiException;
import io.harness.pcf.model.CfAppAutoscalarRequestData;
import io.harness.pcf.model.CfRequestConfig;
import io.harness.pcf.model.PcfConstants;
import io.harness.rule.Owner;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.SecretDecryptionService;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.cloudfoundry.operations.applications.ApplicationDetail;
import org.cloudfoundry.operations.applications.ApplicationSummary;
import org.cloudfoundry.operations.applications.InstanceDetail;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;

@OwnedBy(HarnessTeam.CDP)
@RunWith(MockitoJUnitRunner.class)
public class PcfRouteUpdateCommandTaskHandlerTest extends CategoryTest {
  @Mock CfDeploymentManager cfDeploymentManager;
  @Mock SecretDecryptionService encryptionService;
  @Mock EncryptedDataDetail encryptedDataDetail;
  @Mock LogCallback executionLogCallback;
  @Mock ILogStreamingTaskClient logStreamingTaskClient;

  @InjectMocks @Spy PcfCommandTaskBaseHelper pcfCommandTaskHelper;
  @InjectMocks @Spy PcfRouteUpdateCommandTaskHandler pcfRouteUpdateCommandTaskHandler;

  @Before
  public void setUp() {
    doReturn(executionLogCallback).when(logStreamingTaskClient).obtainLogCallback(any());
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testPerformSwapRouteExecute() throws PivotalClientApiException {
    CfRouteUpdateRequestConfigData routeUpdateRequestConfigData =
        CfRouteUpdateRequestConfigData.builder()
            .downsizeOldApplication(false)
            .finalRoutes(Collections.singletonList("a.b.c"))
            .isRollback(true)
            .isStandardBlueGreen(true)
            .existingApplicationDetails(Collections.singletonList(
                CfAppSetupTimeDetails.builder().applicationName("app1").initialInstanceCount(1).build()))
            .newApplicationDetails(CfAppSetupTimeDetails.builder().applicationName("newApp").build())
            .build();
    CfCommandRequest cfCommandRequest = getRouteUpdateRequest(routeUpdateRequestConfigData);

    doNothing().when(pcfCommandTaskHelper).mapRouteMaps(anyString(), anyList(), any(), any());
    doNothing().when(pcfCommandTaskHelper).unmapRouteMaps(anyString(), anyList(), any(), any());
    doReturn(null).when(cfDeploymentManager).upsizeApplicationWithSteadyStateCheck(any(), any());
    doReturn(null).when(cfDeploymentManager).resizeApplication(any(), any());

    // 2 Rollback True, existingApplication : available
    reset(cfDeploymentManager);
    routeUpdateRequestConfigData.setDownsizeOldApplication(true);
    CfCommandExecutionResponse cfCommandExecutionResponse =
        pcfRouteUpdateCommandTaskHandler.executeTaskInternal(cfCommandRequest, null, logStreamingTaskClient, false);
    verify(cfDeploymentManager, times(1)).upsizeApplicationWithSteadyStateCheck(any(), any());
    assertThat(cfCommandExecutionResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);

    // 3 Rollback True, existingApplication : unavailable
    reset(cfDeploymentManager);
    routeUpdateRequestConfigData.setDownsizeOldApplication(true);
    routeUpdateRequestConfigData.setExistingApplicationDetails(null);
    cfCommandExecutionResponse =
        pcfRouteUpdateCommandTaskHandler.executeTaskInternal(cfCommandRequest, null, logStreamingTaskClient, false);
    verify(cfDeploymentManager, never()).upsizeApplicationWithSteadyStateCheck(any(), any());
    assertThat(cfCommandExecutionResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);

    // 4 Rollback False, existingApplication : unavailable
    reset(cfDeploymentManager);
    routeUpdateRequestConfigData.setDownsizeOldApplication(true);
    routeUpdateRequestConfigData.setRollback(false);
    routeUpdateRequestConfigData.setExistingApplicationDetails(null);
    cfCommandExecutionResponse =
        pcfRouteUpdateCommandTaskHandler.executeTaskInternal(cfCommandRequest, null, logStreamingTaskClient, false);
    verify(cfDeploymentManager, never()).upsizeApplicationWithSteadyStateCheck(any(), any());
    assertThat(cfCommandExecutionResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);

    // 5  Rollback False, existingApplication : available
    reset(cfDeploymentManager);
    routeUpdateRequestConfigData.setDownsizeOldApplication(true);
    routeUpdateRequestConfigData.setExistingApplicationDetails(Collections.singletonList(
        CfAppSetupTimeDetails.builder().applicationName("app1").initialInstanceCount(1).build()));
    cfCommandExecutionResponse =
        pcfRouteUpdateCommandTaskHandler.executeTaskInternal(cfCommandRequest, null, logStreamingTaskClient, false);
    verify(cfDeploymentManager, times(1)).resizeApplication(any(), any());
    verify(cfDeploymentManager, never()).upsizeApplicationWithSteadyStateCheck(any(), any());
    assertThat(cfCommandExecutionResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testResizeOldApplications() throws PivotalClientApiException {
    List<CfAppSetupTimeDetails> appSetupTimeDetailsList = Collections.singletonList(
        CfAppSetupTimeDetails.builder().applicationName("app1").initialInstanceCount(1).build());
    CfRouteUpdateRequestConfigData routeUpdateRequestConfigData = CfRouteUpdateRequestConfigData.builder()
                                                                      .downsizeOldApplication(false)
                                                                      .finalRoutes(Collections.singletonList("a.b.c"))
                                                                      .isRollback(false)
                                                                      .isStandardBlueGreen(true)
                                                                      .build();

    CfCommandRouteUpdateRequest pcfCommandRequest = CfCommandRouteUpdateRequest.builder()
                                                        .pcfCommandType(CfCommandRequest.PcfCommandType.RESIZE)
                                                        .pcfConfig(getPcfConfig())
                                                        .accountId(ACCOUNT_ID)
                                                        .organization(ORG)
                                                        .space(SPACE)
                                                        .timeoutIntervalInMin(2)
                                                        .pcfCommandType(CfCommandRequest.PcfCommandType.UPDATE_ROUTE)
                                                        .pcfRouteUpdateConfigData(routeUpdateRequestConfigData)
                                                        .useAppAutoscalar(false)
                                                        .build();

    reset(cfDeploymentManager);

    // Existing applications are empty. No op expected
    pcfRouteUpdateCommandTaskHandler.resizeOldApplications(
        pcfCommandRequest, CfRequestConfig.builder().build(), executionLogCallback, false, "");
    verify(cfDeploymentManager, never()).resizeApplication(any(), any());

    // Autoscalar is false, existing app is present
    routeUpdateRequestConfigData.setExistingApplicationDetails(appSetupTimeDetailsList);
    ApplicationDetail applicationDetail = ApplicationDetail.builder()
                                              .id("10")
                                              .diskQuota(1)
                                              .instances(1)
                                              .memoryLimit(1)
                                              .name("app1")
                                              .requestedState("STOPPED")
                                              .stack("")
                                              .runningInstances(1)
                                              .instanceDetails(Collections.singletonList(InstanceDetail.builder()
                                                                                             .cpu(1.0)
                                                                                             .diskQuota((long) 1.23)
                                                                                             .diskUsage((long) 1.23)
                                                                                             .index("2")
                                                                                             .memoryQuota((long) 1)
                                                                                             .memoryUsage((long) 1)
                                                                                             .build()))
                                              .diskQuota(1)
                                              .instances(1)
                                              .memoryLimit(1)
                                              .name("app1")
                                              .requestedState(RUNNING)
                                              .stack("")
                                              .runningInstances(1)
                                              .build();
    doReturn(applicationDetail).when(cfDeploymentManager).getApplicationByName(any());
    doReturn(applicationDetail).when(cfDeploymentManager).resizeApplication(any(), any());

    doReturn(true).when(pcfCommandTaskHelper).disableAutoscalar(any(), any());
    pcfRouteUpdateCommandTaskHandler.resizeOldApplications(
        pcfCommandRequest, CfRequestConfig.builder().build(), executionLogCallback, false, "");
    verify(cfDeploymentManager, times(1)).resizeApplication(any(), any());
    verify(pcfCommandTaskHelper, never()).disableAutoscalar(any(), any());

    // Autoscalar is true, existing app present
    pcfCommandRequest.setUseAppAutoscalar(true);
    ArgumentCaptor<CfAppAutoscalarRequestData> argumentCaptor =
        ArgumentCaptor.forClass(CfAppAutoscalarRequestData.class);
    pcfRouteUpdateCommandTaskHandler.resizeOldApplications(
        pcfCommandRequest, CfRequestConfig.builder().build(), executionLogCallback, false, "");
    verify(cfDeploymentManager, times(2)).resizeApplication(any(), any());
    verify(pcfCommandTaskHelper, times(1)).disableAutoscalar(argumentCaptor.capture(), any());
    CfAppAutoscalarRequestData pcfAppAutoscalarRequestData = argumentCaptor.getValue();
    assertThat(pcfAppAutoscalarRequestData.getApplicationName()).isEqualTo("app1");
    assertThat(pcfAppAutoscalarRequestData.getApplicationGuid()).isEqualTo("10");
    assertThat(pcfAppAutoscalarRequestData.getTimeoutInMins()).isEqualTo(2);
    assertThat(pcfAppAutoscalarRequestData.isExpectedEnabled()).isTrue();

    routeUpdateRequestConfigData.setRollback(true);
    doReturn(true).when(cfDeploymentManager).changeAutoscalarState(any(), any(), anyBoolean());
    pcfRouteUpdateCommandTaskHandler.resizeOldApplications(
        pcfCommandRequest, CfRequestConfig.builder().build(), executionLogCallback, true, "");
    verify(cfDeploymentManager, times(1)).changeAutoscalarState(argumentCaptor.capture(), any(), anyBoolean());
    pcfAppAutoscalarRequestData = argumentCaptor.getValue();
    assertThat(pcfAppAutoscalarRequestData.getApplicationName()).isEqualTo("app1");
    assertThat(pcfAppAutoscalarRequestData.getApplicationGuid()).isEqualTo("10");
    assertThat(pcfAppAutoscalarRequestData.getTimeoutInMins()).isEqualTo(2);
    assertThat(pcfAppAutoscalarRequestData.isExpectedEnabled()).isFalse();
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testSwapRouteExecutionNeeded() {
    assertThat(pcfRouteUpdateCommandTaskHandler.swapRouteExecutionNeeded(null)).isFalse();
    assertThat(pcfRouteUpdateCommandTaskHandler.swapRouteExecutionNeeded(
                   CfRouteUpdateRequestConfigData.builder().isRollback(false).build()))
        .isTrue();
    assertThat(pcfRouteUpdateCommandTaskHandler.swapRouteExecutionNeeded(
                   CfRouteUpdateRequestConfigData.builder().isRollback(true).skipRollback(false).build()))
        .isTrue();
    assertThat(pcfRouteUpdateCommandTaskHandler.swapRouteExecutionNeeded(
                   CfRouteUpdateRequestConfigData.builder().isRollback(true).skipRollback(true).build()))
        .isFalse();
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testSwapRouteRollbackForInActiveApp() throws PivotalClientApiException {
    reset(cfDeploymentManager);
    String appPrefix = "cf_app";
    String inActiveAppName = appPrefix + "_4";
    String activeAppName = appPrefix + "_5";
    String newAppName = appPrefix + "_6";
    List<String> finalRoutes = Arrays.asList("basicRoute.apps.pcf-harness.com", "shiny-jackal-pa.apps.pcf-harness.com");
    List<String> tempRoutes = Collections.singletonList("tempBg.apps.pcf-harness.com");

    CfAppSetupTimeDetails inActiveApp = CfAppSetupTimeDetails.builder()
                                            .activeApp(false)
                                            .applicationGuid("ca289f74-fdb6-486e-8679-2f91d8ce566e")
                                            .initialInstanceCount(5)
                                            .urls(tempRoutes)
                                            .applicationName(inActiveAppName)
                                            .build();
    CfAppSetupTimeDetails activeApp = CfAppSetupTimeDetails.builder()
                                          .activeApp(false)
                                          .applicationGuid("806c5057-10d4-44c1-ba1b-9e56bd5a997f")
                                          .initialInstanceCount(5)
                                          .urls(finalRoutes)
                                          .applicationName(activeAppName)
                                          .build();
    CfAppSetupTimeDetails newApplication = CfAppSetupTimeDetails.builder()
                                               .activeApp(false)
                                               .applicationGuid("ca289f74-fdb6-486e-8679-2f91d8ce897")
                                               .initialInstanceCount(5)
                                               .urls(tempRoutes)
                                               .applicationName(newAppName)
                                               .build();
    List<ApplicationSummary> existingApplicationSummaries =
        getExistingApplicationSummaries(inActiveApp, activeApp, newApplication);
    doReturn(existingApplicationSummaries).when(cfDeploymentManager).getPreviousReleases(any(), eq(appPrefix));

    CfRouteUpdateRequestConfigData routeUpdateRequestConfigData = getRouteUpdateConfigData(appPrefix, activeApp,
        finalRoutes, tempRoutes, newApplication, inActiveApp, true, false, AppNamingStrategy.VERSIONING.name(), true);

    CfCommandRequest cfCommandRequest = getRouteUpdateRequest(routeUpdateRequestConfigData);

    CfCommandExecutionResponse cfCommandExecutionResponse =
        pcfRouteUpdateCommandTaskHandler.executeTaskInternal(cfCommandRequest, null, logStreamingTaskClient, false);

    verifyRollbackBehaviour(
        inActiveAppName, activeAppName, newAppName, finalRoutes, tempRoutes, cfCommandExecutionResponse);
  }

  private void verifyRollbackBehaviour(String inActiveAppName, String activeAppName, String newAppName,
      List<String> finalRoutes, List<String> tempRoutes, CfCommandExecutionResponse cfCommandExecutionResponse)
      throws PivotalClientApiException {
    assertThat(cfCommandExecutionResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);

    // verify In Active app was up sized
    verify(cfDeploymentManager).upsizeApplicationWithSteadyStateCheck(any(), any());

    // verify correct routes are mapped to Active & InActive application
    ArgumentCaptor<String> appNameMapRouteCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<List<String>> mapRoutesCaptor = ArgumentCaptor.forClass((Class) List.class);
    verify(pcfCommandTaskHelper, times(2))
        .mapRouteMaps(appNameMapRouteCaptor.capture(), mapRoutesCaptor.capture(), any(), any());
    List<String> appWhoseRoutesWasModified = appNameMapRouteCaptor.getAllValues();
    List<List<String>> routesLists = mapRoutesCaptor.getAllValues();
    assertThat(appWhoseRoutesWasModified).isNotNull();
    assertThat(appWhoseRoutesWasModified.size()).isEqualTo(2);
    assertThat(routesLists).isNotNull();
    assertThat(appWhoseRoutesWasModified.get(0).equalsIgnoreCase(activeAppName)).isTrue();
    assertThat(routesLists.get(0).containsAll(finalRoutes)).isTrue();
    assertThat(appWhoseRoutesWasModified.get(1).equalsIgnoreCase(inActiveAppName)).isTrue();
    assertThat(routesLists.get(1).containsAll(tempRoutes)).isTrue();

    // verify correct routes are unmapped from Active & New application
    ArgumentCaptor<String> appNameUnMapRouteCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<List<String>> unMapRoutesCaptor = ArgumentCaptor.forClass((Class) List.class);
    verify(pcfCommandTaskHelper, times(2))
        .unmapRouteMaps(appNameUnMapRouteCaptor.capture(), unMapRoutesCaptor.capture(), any(), any());
    List<String> appWhoseRoutesWereRemoved = appNameUnMapRouteCaptor.getAllValues();
    List<List<String>> removedRouteList = unMapRoutesCaptor.getAllValues();
    assertThat(appWhoseRoutesWereRemoved).isNotNull();
    assertThat(appWhoseRoutesWereRemoved.size()).isEqualTo(2);
    assertThat(removedRouteList).isNotNull();
    assertThat(appWhoseRoutesWereRemoved.get(0).equalsIgnoreCase(activeAppName)).isTrue();
    assertThat(removedRouteList.get(0).containsAll(tempRoutes)).isTrue();
    assertThat(appWhoseRoutesWereRemoved.get(1).equalsIgnoreCase(newAppName)).isTrue();
    assertThat(removedRouteList.get(1).containsAll(finalRoutes)).isTrue();

    // verify setting of ENV variables for Active & InActive app
    ArgumentCaptor<Boolean> isActiveAppCaptor = ArgumentCaptor.forClass(Boolean.class);
    verify(cfDeploymentManager, times(2)).setEnvironmentVariableForAppStatus(any(), isActiveAppCaptor.capture(), any());
    List<Boolean> isActiveAppValues = isActiveAppCaptor.getAllValues();
    assertThat(isActiveAppValues).isNotNull();
    assertThat(isActiveAppValues.get(0)).isTrue(); // for active app
    assertThat(isActiveAppValues.get(1)).isFalse(); // for inactive app

    // verify un-setting of ENV variables for New app
    ArgumentCaptor<CfRequestConfig> cfRequestConfigArgumentCaptor = ArgumentCaptor.forClass(CfRequestConfig.class);
    verify(cfDeploymentManager).unsetEnvironmentVariableForAppStatus(cfRequestConfigArgumentCaptor.capture(), any());
    CfRequestConfig cfRequestConfig = cfRequestConfigArgumentCaptor.getValue();
    assertThat(cfRequestConfig).isNotNull();
    assertThat(cfRequestConfig.getApplicationName()).isEqualTo(newAppName);
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testSwapRouteRollbackForInActiveAppNotPresent() throws PivotalClientApiException {
    reset(cfDeploymentManager);
    String appPrefix = "cf_app";
    String inActiveAppName = appPrefix + "_4";
    String activeAppName = appPrefix + "_5";
    String newAppName = appPrefix + "_6";
    List<String> finalRoutes = Arrays.asList("basicRoute.apps.pcf-harness.com", "shiny-jackal-pa.apps.pcf-harness.com");
    List<String> tempRoutes = Collections.singletonList("tempBg.apps.pcf-harness.com");

    CfAppSetupTimeDetails inActiveApp = CfAppSetupTimeDetails.builder()
                                            .activeApp(false)
                                            .applicationGuid("ca289f74-fdb6-486e-8679-2f91d8ce566e")
                                            .initialInstanceCount(5)
                                            .urls(tempRoutes)
                                            .applicationName(inActiveAppName)
                                            .build();
    CfAppSetupTimeDetails activeApp = CfAppSetupTimeDetails.builder()
                                          .activeApp(false)
                                          .applicationGuid("806c5057-10d4-44c1-ba1b-9e56bd5a997f")
                                          .initialInstanceCount(5)
                                          .urls(finalRoutes)
                                          .applicationName(activeAppName)
                                          .build();
    CfAppSetupTimeDetails newApplication = CfAppSetupTimeDetails.builder()
                                               .activeApp(false)
                                               .applicationGuid("ca289f74-fdb6-486e-8679-2f91d8ce897")
                                               .initialInstanceCount(5)
                                               .urls(tempRoutes)
                                               .applicationName(newAppName)
                                               .build();

    List<ApplicationSummary> existingApplicationSummaries =
        getExistingApplicationSummaries(inActiveApp, activeApp, newApplication);
    doReturn(existingApplicationSummaries).when(cfDeploymentManager).getPreviousReleases(any(), eq(appPrefix));

    CfRouteUpdateRequestConfigData routeUpdateRequestConfigData = getRouteUpdateConfigData(appPrefix, activeApp,
        finalRoutes, tempRoutes, newApplication, null, true, false, AppNamingStrategy.VERSIONING.name(), true);

    CfCommandRequest cfCommandRequest = getRouteUpdateRequest(routeUpdateRequestConfigData);

    CfCommandExecutionResponse cfCommandExecutionResponse =
        pcfRouteUpdateCommandTaskHandler.executeTaskInternal(cfCommandRequest, null, logStreamingTaskClient, false);
    verifyRollbackBehaviourInActiveAppNotPresent(
        activeAppName, newAppName, finalRoutes, tempRoutes, cfCommandExecutionResponse);
  }

  private void verifyRollbackBehaviourInActiveAppNotPresent(String activeAppName, String newAppName,
      List<String> finalRoutes, List<String> tempRoutes, CfCommandExecutionResponse cfCommandExecutionResponse)
      throws PivotalClientApiException {
    assertThat(cfCommandExecutionResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);

    // verify In Active app was not up sized as there is no In Active app
    verify(cfDeploymentManager, never()).upsizeApplicationWithSteadyStateCheck(any(), any());

    // verify correct routes are mapped to Active app only
    ArgumentCaptor<String> appNameMapRouteCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<List<String>> mapRoutesCaptor = ArgumentCaptor.forClass((Class) List.class);
    verify(pcfCommandTaskHelper).mapRouteMaps(appNameMapRouteCaptor.capture(), mapRoutesCaptor.capture(), any(), any());
    List<String> appWhoseRoutesWasModified = appNameMapRouteCaptor.getAllValues();
    List<List<String>> routesLists = mapRoutesCaptor.getAllValues();
    assertThat(appWhoseRoutesWasModified).isNotNull();
    assertThat(appWhoseRoutesWasModified.size()).isEqualTo(1);
    assertThat(routesLists).isNotNull();
    assertThat(appWhoseRoutesWasModified.get(0).equalsIgnoreCase(activeAppName)).isTrue();
    assertThat(routesLists.get(0).containsAll(finalRoutes)).isTrue();

    // verify correct routes are unmapped to Active & New application
    ArgumentCaptor<String> appNameUnMapRouteCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<List<String>> unMapRoutesCaptor = ArgumentCaptor.forClass((Class) List.class);
    verify(pcfCommandTaskHelper, times(2))
        .unmapRouteMaps(appNameUnMapRouteCaptor.capture(), unMapRoutesCaptor.capture(), any(), any());
    List<String> appWhoseRoutesWereRemoved = appNameUnMapRouteCaptor.getAllValues();
    List<List<String>> removedRouteList = unMapRoutesCaptor.getAllValues();
    assertThat(appWhoseRoutesWereRemoved).isNotNull();
    assertThat(appWhoseRoutesWereRemoved.size()).isEqualTo(2);
    assertThat(removedRouteList).isNotNull();
    assertThat(appWhoseRoutesWereRemoved.get(0).equalsIgnoreCase(activeAppName)).isTrue();
    assertThat(removedRouteList.get(0).containsAll(tempRoutes)).isTrue();
    assertThat(appWhoseRoutesWereRemoved.get(1).equalsIgnoreCase(newAppName)).isTrue();
    assertThat(removedRouteList.get(1).containsAll(finalRoutes)).isTrue();

    // verify setting of ENV variables for Active
    ArgumentCaptor<Boolean> isActiveAppCaptor = ArgumentCaptor.forClass(Boolean.class);
    verify(cfDeploymentManager).setEnvironmentVariableForAppStatus(any(), isActiveAppCaptor.capture(), any());
    List<Boolean> isActiveAppValues = isActiveAppCaptor.getAllValues();
    assertThat(isActiveAppValues).isNotNull();
    assertThat(isActiveAppValues.size()).isEqualTo(1);
    assertThat(isActiveAppValues.get(0)).isTrue(); // for active app

    // verify un-setting of ENV variables for New app
    ArgumentCaptor<CfRequestConfig> cfRequestConfigArgumentCaptor = ArgumentCaptor.forClass(CfRequestConfig.class);
    verify(cfDeploymentManager).unsetEnvironmentVariableForAppStatus(cfRequestConfigArgumentCaptor.capture(), any());
    CfRequestConfig cfRequestConfig = cfRequestConfigArgumentCaptor.getValue();
    assertThat(cfRequestConfig).isNotNull();
    assertThat(cfRequestConfig.getApplicationName()).isEqualTo(newAppName);
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testSwapRouteRollbackForInActiveAppFeatureFlagDisabled() throws PivotalClientApiException {
    reset(cfDeploymentManager);
    String appPrefix = "cf_app";
    String inActiveAppName = appPrefix + "_4";
    String activeAppName = appPrefix + "_5";
    String newAppName = appPrefix + "_6";
    List<String> finalRoutes = Arrays.asList("basicRoute.apps.pcf-harness.com", "shiny-jackal-pa.apps.pcf-harness.com");
    List<String> tempRoutes = Collections.singletonList("tempBg.apps.pcf-harness.com");

    CfAppSetupTimeDetails inActiveApp = CfAppSetupTimeDetails.builder()
                                            .activeApp(false)
                                            .applicationGuid("ca289f74-fdb6-486e-8679-2f91d8ce566e")
                                            .initialInstanceCount(5)
                                            .urls(tempRoutes)
                                            .applicationName(inActiveAppName)
                                            .build();
    CfAppSetupTimeDetails activeApp = CfAppSetupTimeDetails.builder()
                                          .activeApp(false)
                                          .applicationGuid("806c5057-10d4-44c1-ba1b-9e56bd5a997f")
                                          .initialInstanceCount(5)
                                          .urls(finalRoutes)
                                          .applicationName(activeAppName)
                                          .build();
    CfAppSetupTimeDetails newApplication = CfAppSetupTimeDetails.builder()
                                               .activeApp(false)
                                               .applicationGuid("ca289f74-fdb6-486e-8679-2f91d8ce897")
                                               .initialInstanceCount(5)
                                               .urls(tempRoutes)
                                               .applicationName(newAppName)
                                               .build();
    List<ApplicationSummary> existingApplicationSummaries =
        getExistingApplicationSummaries(inActiveApp, activeApp, newApplication);
    doReturn(existingApplicationSummaries).when(cfDeploymentManager).getPreviousReleases(any(), eq(appPrefix));

    CfRouteUpdateRequestConfigData routeUpdateRequestConfigData = getRouteUpdateConfigData(appPrefix, activeApp,
        finalRoutes, tempRoutes, newApplication, inActiveApp, true, false, AppNamingStrategy.VERSIONING.name(), true);
    routeUpdateRequestConfigData.setUpSizeInActiveApp(false);

    CfCommandRequest cfCommandRequest = getRouteUpdateRequest(routeUpdateRequestConfigData);

    CfCommandExecutionResponse cfCommandExecutionResponse =
        pcfRouteUpdateCommandTaskHandler.executeTaskInternal(cfCommandRequest, null, logStreamingTaskClient, false);

    assertThat(cfCommandExecutionResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    verifyRollbackBehaviourFFDisabled(activeAppName, newAppName, finalRoutes, tempRoutes);
  }

  private void verifyRollbackBehaviourFFDisabled(String activeAppName, String newAppName, List<String> finalRoutes,
      List<String> tempRoutes) throws PivotalClientApiException {
    // verify In Active app was not up sized as upSizeInActiveApp is disabled
    verify(cfDeploymentManager, never()).upsizeApplicationWithSteadyStateCheck(any(), any());

    // verify correct routes are mapped to Active & New app
    ArgumentCaptor<String> appNameMapRouteCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<List<String>> mapRoutesCaptor = ArgumentCaptor.forClass((Class) List.class);
    verify(pcfCommandTaskHelper, times(1))
        .mapRouteMaps(appNameMapRouteCaptor.capture(), mapRoutesCaptor.capture(), any(), any());
    List<String> appWhoseRoutesWasModified = appNameMapRouteCaptor.getAllValues();
    List<List<String>> routesLists = mapRoutesCaptor.getAllValues();
    assertThat(appWhoseRoutesWasModified).isNotNull();
    assertThat(appWhoseRoutesWasModified.size()).isEqualTo(1);
    assertThat(routesLists).isNotNull();
    assertThat(appWhoseRoutesWasModified.get(0).equalsIgnoreCase(activeAppName)).isTrue();
    assertThat(routesLists.get(0).containsAll(finalRoutes)).isTrue();

    // verify correct routes are unmapped from Active & New application
    ArgumentCaptor<String> appNameUnMapRouteCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<List<String>> unMapRoutesCaptor = ArgumentCaptor.forClass((Class) List.class);
    verify(pcfCommandTaskHelper, times(2))
        .unmapRouteMaps(appNameUnMapRouteCaptor.capture(), unMapRoutesCaptor.capture(), any(), any());
    List<String> appWhoseRoutesWereRemoved = appNameUnMapRouteCaptor.getAllValues();
    List<List<String>> removedRouteList = unMapRoutesCaptor.getAllValues();
    assertThat(appWhoseRoutesWereRemoved).isNotNull();
    assertThat(appWhoseRoutesWereRemoved.size()).isEqualTo(2);
    assertThat(removedRouteList).isNotNull();
    assertThat(appWhoseRoutesWereRemoved.get(0).equalsIgnoreCase(activeAppName)).isTrue();
    assertThat(removedRouteList.get(0).containsAll(tempRoutes)).isTrue();
    assertThat(appWhoseRoutesWereRemoved.get(1).equalsIgnoreCase(newAppName)).isTrue();
    assertThat(removedRouteList.get(1).containsAll(finalRoutes)).isTrue();

    // verify setting of ENV variables for Active & New app
    ArgumentCaptor<Boolean> isActiveAppCaptor = ArgumentCaptor.forClass(Boolean.class);
    verify(cfDeploymentManager, times(1)).setEnvironmentVariableForAppStatus(any(), isActiveAppCaptor.capture(), any());
    List<Boolean> isActiveAppValues = isActiveAppCaptor.getAllValues();
    assertThat(isActiveAppValues).isNotNull();
    assertThat(isActiveAppValues.get(0)).isTrue(); // for active app
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testVersionToVersionAppRenamingBlueGreen() throws PivotalClientApiException {
    reset(cfDeploymentManager);
    reset(pcfCommandTaskHelper);
    String appPrefix = "PaymentService";
    String inActiveAppName = appPrefix + DELIMITER + "2";
    String activeAppName = appPrefix + DELIMITER + "3";
    String newAppName = appPrefix + DELIMITER + "4";
    List<String> finalRoutes = Arrays.asList("prod1.apps.pcf-harness.com", "prod2.apps.pcf-harness.com");
    List<String> tempRoutes = Collections.singletonList("tempBg.apps.pcf-harness.com");

    CfAppSetupTimeDetails inActiveApp = CfAppSetupTimeDetails.builder()
                                            .activeApp(false)
                                            .applicationGuid("ca289f74-fdb6-486e-8679-2f91d8ce566e")
                                            .initialInstanceCount(5)
                                            .urls(tempRoutes)
                                            .applicationName(inActiveAppName)
                                            .build();
    CfAppSetupTimeDetails activeApp = CfAppSetupTimeDetails.builder()
                                          .activeApp(false)
                                          .applicationGuid("806c5057-10d4-44c1-ba1b-9e56bd5a997f")
                                          .initialInstanceCount(5)
                                          .urls(finalRoutes)
                                          .applicationName(activeAppName)
                                          .build();
    CfAppSetupTimeDetails newApplication = CfAppSetupTimeDetails.builder()
                                               .activeApp(false)
                                               .applicationGuid("ca289f74-fdb6-486e-8679-2f91d8ce897")
                                               .initialInstanceCount(5)
                                               .urls(tempRoutes)
                                               .applicationName(newAppName)
                                               .build();
    List<ApplicationSummary> existingApplicationSummaries =
        getExistingApplicationSummaries(inActiveApp, activeApp, newApplication);
    doReturn(existingApplicationSummaries).when(cfDeploymentManager).getPreviousReleases(any(), eq(appPrefix));

    CfRouteUpdateRequestConfigData routeUpdateRequestConfigData = getRouteUpdateConfigData(appPrefix, activeApp,
        finalRoutes, tempRoutes, newApplication, inActiveApp, false, false, AppNamingStrategy.VERSIONING.name(), false);

    CfCommandRequest cfCommandRequest = getRouteUpdateRequest(routeUpdateRequestConfigData);

    CfCommandExecutionResponse cfCommandExecutionResponse =
        pcfRouteUpdateCommandTaskHandler.executeTaskInternal(cfCommandRequest, null, logStreamingTaskClient, false);

    assertThat(cfCommandExecutionResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);

    // verify routes are mapped correctly to current Active & New application
    ArgumentCaptor<String> appNameMapRouteCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<List<String>> mapRoutesCaptor = ArgumentCaptor.forClass((Class) List.class);
    verify(pcfCommandTaskHelper, times(2))
        .mapRouteMaps(appNameMapRouteCaptor.capture(), mapRoutesCaptor.capture(), any(), any());
    List<String> appWhoseRoutesWasModified = appNameMapRouteCaptor.getAllValues();
    List<List<String>> routesLists = mapRoutesCaptor.getAllValues();
    assertThat(appWhoseRoutesWasModified).isNotNull();
    assertThat(appWhoseRoutesWasModified.size()).isEqualTo(2);
    assertThat(routesLists).isNotNull();
    assertThat(appWhoseRoutesWasModified.get(0).equalsIgnoreCase(newAppName)).isTrue();
    assertThat(routesLists.get(0).containsAll(finalRoutes)).isTrue();
    assertThat(appWhoseRoutesWasModified.get(1).equalsIgnoreCase(activeAppName)).isTrue();
    assertThat(routesLists.get(1).containsAll(tempRoutes)).isTrue();

    // verify correct routes are unmapped from Active & New application
    ArgumentCaptor<String> appNameUnMapRouteCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<List<String>> unMapRoutesCaptor = ArgumentCaptor.forClass((Class) List.class);
    verify(pcfCommandTaskHelper, times(2))
        .unmapRouteMaps(appNameUnMapRouteCaptor.capture(), unMapRoutesCaptor.capture(), any(), any());
    List<String> appWhoseRoutesWereRemoved = appNameUnMapRouteCaptor.getAllValues();
    List<List<String>> removedRouteList = unMapRoutesCaptor.getAllValues();
    assertThat(appWhoseRoutesWereRemoved).isNotNull();
    assertThat(appWhoseRoutesWereRemoved.size()).isEqualTo(2);
    assertThat(removedRouteList).isNotNull();
    assertThat(appWhoseRoutesWereRemoved.get(0).equalsIgnoreCase(newAppName)).isTrue();
    assertThat(removedRouteList.get(0).containsAll(tempRoutes)).isTrue();
    assertThat(appWhoseRoutesWereRemoved.get(1).equalsIgnoreCase(activeAppName)).isTrue();
    assertThat(removedRouteList.get(1).containsAll(finalRoutes)).isTrue();

    ArgumentCaptor<Boolean> appEnvVariableCaptor = ArgumentCaptor.forClass(Boolean.class);
    verify(cfDeploymentManager, times(2))
        .setEnvironmentVariableForAppStatus(any(), appEnvVariableCaptor.capture(), any());
    List<Boolean> allValues = appEnvVariableCaptor.getAllValues();
    assertThat(allValues).isNotNull();
    assertThat(allValues.size()).isEqualTo(2);
    assertThat(allValues.get(0)).isTrue(); // for new app True means ACTIVE will be set
    assertThat(allValues.get(1)).isFalse(); // for current active app False means STAGE will be set

    ArgumentCaptor<String> executionLogs = ArgumentCaptor.forClass(String.class);
    verify(executionLogCallback, atLeastOnce()).saveExecutionLog(executionLogs.capture());
    List<String> logMessages = executionLogs.getAllValues();
    List<String> renamingMsg =
        logMessages.stream().filter(msg -> msg.contains("# Starting Renaming apps")).collect(Collectors.toList());
    assertThat(renamingMsg.isEmpty()).isTrue();

    // in version to version deployment renameApp should not be called
    verify(pcfCommandTaskHelper, times(0)).renameApp(any(), any(), any(), any());
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testVersionToNonVersionAppRenamingBlueGreen() throws PivotalClientApiException {
    reset(cfDeploymentManager);
    reset(pcfCommandTaskHelper);
    String appPrefix = "PaymentService";
    String inActiveAppName = appPrefix + "_4";
    String activeAppName = appPrefix + "_5";
    String newAppName = appPrefix + INACTIVE_APP_NAME_SUFFIX;
    List<String> finalRoutes = Arrays.asList("prod1.apps.pcf-harness.com", "prod2.apps.pcf-harness.com");
    List<String> tempRoutes = Collections.singletonList("tempBg.apps.pcf-harness.com");

    CfAppSetupTimeDetails inActiveApp = CfAppSetupTimeDetails.builder()
                                            .activeApp(false)
                                            .applicationGuid("ca289f74-fdb6-486e-8679-2f91d8ce566e")
                                            .initialInstanceCount(5)
                                            .urls(tempRoutes)
                                            .applicationName(inActiveAppName)
                                            .build();
    CfAppSetupTimeDetails activeApp = CfAppSetupTimeDetails.builder()
                                          .activeApp(false)
                                          .applicationGuid("806c5057-10d4-44c1-ba1b-9e56bd5a997f")
                                          .initialInstanceCount(5)
                                          .urls(finalRoutes)
                                          .applicationName(activeAppName)
                                          .build();
    CfAppSetupTimeDetails newApplication = CfAppSetupTimeDetails.builder()
                                               .activeApp(false)
                                               .applicationGuid("ca289f74-fdb6-486e-8679-2f91d8ce897")
                                               .initialInstanceCount(5)
                                               .urls(tempRoutes)
                                               .applicationName(newAppName)
                                               .build();
    List<ApplicationSummary> existingApplicationSummaries =
        getExistingApplicationSummaries(inActiveApp, activeApp, newApplication);
    doReturn(existingApplicationSummaries).when(cfDeploymentManager).getPreviousReleases(any(), eq(appPrefix));

    CfRouteUpdateRequestConfigData routeUpdateRequestConfigData = getRouteUpdateConfigData(appPrefix, activeApp,
        finalRoutes, tempRoutes, newApplication, inActiveApp, false, true, AppNamingStrategy.VERSIONING.name(), false);

    CfCommandRequest cfCommandRequest = getRouteUpdateRequest(routeUpdateRequestConfigData);

    CfCommandExecutionResponse cfCommandExecutionResponse =
        pcfRouteUpdateCommandTaskHandler.executeTaskInternal(cfCommandRequest, null, logStreamingTaskClient, false);

    assertThat(cfCommandExecutionResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);

    // verify routes are mapped correctly to current Active & New application
    ArgumentCaptor<String> appNameMapRouteCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<List<String>> mapRoutesCaptor = ArgumentCaptor.forClass((Class) List.class);
    verify(pcfCommandTaskHelper, times(2))
        .mapRouteMaps(appNameMapRouteCaptor.capture(), mapRoutesCaptor.capture(), any(), any());
    List<String> appWhoseRoutesWasModified = appNameMapRouteCaptor.getAllValues();
    List<List<String>> routesLists = mapRoutesCaptor.getAllValues();
    assertThat(appWhoseRoutesWasModified).isNotNull();
    assertThat(appWhoseRoutesWasModified.size()).isEqualTo(2);
    assertThat(routesLists).isNotNull();
    assertThat(appWhoseRoutesWasModified.get(0).equalsIgnoreCase(newAppName)).isTrue();
    assertThat(routesLists.get(0).containsAll(finalRoutes)).isTrue();
    assertThat(appWhoseRoutesWasModified.get(1).equalsIgnoreCase(activeAppName)).isTrue();
    assertThat(routesLists.get(1).containsAll(tempRoutes)).isTrue();

    // verify correct routes are unmapped from Active & New application
    ArgumentCaptor<String> appNameUnMapRouteCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<List<String>> unMapRoutesCaptor = ArgumentCaptor.forClass((Class) List.class);
    verify(pcfCommandTaskHelper, times(2))
        .unmapRouteMaps(appNameUnMapRouteCaptor.capture(), unMapRoutesCaptor.capture(), any(), any());
    List<String> appWhoseRoutesWereRemoved = appNameUnMapRouteCaptor.getAllValues();
    List<List<String>> removedRouteList = unMapRoutesCaptor.getAllValues();
    assertThat(appWhoseRoutesWereRemoved).isNotNull();
    assertThat(appWhoseRoutesWereRemoved.size()).isEqualTo(2);
    assertThat(removedRouteList).isNotNull();
    assertThat(appWhoseRoutesWereRemoved.get(0).equalsIgnoreCase(newAppName)).isTrue();
    assertThat(removedRouteList.get(0).containsAll(tempRoutes)).isTrue();
    assertThat(appWhoseRoutesWereRemoved.get(1).equalsIgnoreCase(activeAppName)).isTrue();
    assertThat(removedRouteList.get(1).containsAll(finalRoutes)).isTrue();

    ArgumentCaptor<Boolean> appEnvVariableCaptor = ArgumentCaptor.forClass(Boolean.class);
    verify(cfDeploymentManager, times(2))
        .setEnvironmentVariableForAppStatus(any(), appEnvVariableCaptor.capture(), any());
    List<Boolean> allValues = appEnvVariableCaptor.getAllValues();
    assertThat(allValues).isNotNull();
    assertThat(allValues.size()).isEqualTo(2);
    assertThat(allValues.get(0)).isTrue(); // for new app True means ACTIVE will be set
    assertThat(allValues.get(1)).isFalse(); // for current active app False means STAGE will be set

    ArgumentCaptor<String> executionLogs = ArgumentCaptor.forClass(String.class);
    verify(executionLogCallback, atLeastOnce()).saveExecutionLog(executionLogs.capture());
    List<String> logMessages = executionLogs.getAllValues();
    List<String> renamingMsg =
        logMessages.stream().filter(msg -> msg.contains("# Starting Renaming apps")).collect(Collectors.toList());
    assertThat(renamingMsg.isEmpty()).isFalse();

    ArgumentCaptor<ApplicationSummary> applicationCaptor = ArgumentCaptor.forClass(ApplicationSummary.class);
    ArgumentCaptor<String> newNameCaptor = ArgumentCaptor.forClass(String.class);
    verify(pcfCommandTaskHelper, times(2))
        .renameApp(applicationCaptor.capture(), any(), any(), newNameCaptor.capture());
    List<ApplicationSummary> applicationSummaries = applicationCaptor.getAllValues();
    List<String> newNames = newNameCaptor.getAllValues();
    assertThat(applicationSummaries).isNotEmpty();
    assertThat(newNames).isNotEmpty();

    assertThat(applicationSummaries.get(0).getId()).isEqualTo(newApplication.getApplicationGuid());
    assertThat(newNames.get(0)).isEqualTo(appPrefix);

    assertThat(applicationSummaries.get(1).getId()).isEqualTo(activeApp.getApplicationGuid());
    assertThat(newNames.get(1)).isEqualTo(appPrefix + INACTIVE_APP_NAME_SUFFIX);
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testNonVersionToNonVersionAppRenamingBlueGreen() throws PivotalClientApiException {
    reset(cfDeploymentManager);
    reset(pcfCommandTaskHelper);
    String appPrefix = "PaymentService";
    String inActiveAppName = appPrefix + "2";
    String activeAppName = appPrefix;
    String newAppName = appPrefix + INACTIVE_APP_NAME_SUFFIX;
    List<String> finalRoutes = Arrays.asList("prod1.apps.pcf-harness.com", "prod2.apps.pcf-harness.com");
    List<String> tempRoutes = Collections.singletonList("tempBg.apps.pcf-harness.com");

    CfAppSetupTimeDetails inActiveApp = CfAppSetupTimeDetails.builder()
                                            .activeApp(false)
                                            .applicationGuid("ca289f74-fdb6-486e-8679-2f91d8ce566e")
                                            .initialInstanceCount(5)
                                            .urls(tempRoutes)
                                            .applicationName(inActiveAppName)
                                            .build();
    CfAppSetupTimeDetails activeApp = CfAppSetupTimeDetails.builder()
                                          .activeApp(false)
                                          .applicationGuid("806c5057-10d4-44c1-ba1b-9e56bd5a997f")
                                          .initialInstanceCount(5)
                                          .urls(finalRoutes)
                                          .applicationName(activeAppName)
                                          .build();
    CfAppSetupTimeDetails newApplication = CfAppSetupTimeDetails.builder()
                                               .activeApp(false)
                                               .applicationGuid("ca289f74-fdb6-486e-8679-2f91d8ce897")
                                               .initialInstanceCount(5)
                                               .urls(tempRoutes)
                                               .applicationName(newAppName)
                                               .build();
    List<ApplicationSummary> existingApplicationSummaries =
        getExistingApplicationSummaries(inActiveApp, activeApp, newApplication);
    doReturn(existingApplicationSummaries).when(cfDeploymentManager).getPreviousReleases(any(), eq(appPrefix));

    CfRouteUpdateRequestConfigData routeUpdateRequestConfigData =
        getRouteUpdateConfigData(appPrefix, activeApp, finalRoutes, tempRoutes, newApplication, inActiveApp, false,
            true, AppNamingStrategy.APP_NAME_WITH_VERSIONING.name(), false);

    CfCommandRequest cfCommandRequest = getRouteUpdateRequest(routeUpdateRequestConfigData);

    CfCommandExecutionResponse cfCommandExecutionResponse =
        pcfRouteUpdateCommandTaskHandler.executeTaskInternal(cfCommandRequest, null, logStreamingTaskClient, false);

    assertThat(cfCommandExecutionResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);

    // verify routes are mapped correctly to current Active & New application
    ArgumentCaptor<String> appNameMapRouteCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<List<String>> mapRoutesCaptor = ArgumentCaptor.forClass((Class) List.class);
    verify(pcfCommandTaskHelper, times(2))
        .mapRouteMaps(appNameMapRouteCaptor.capture(), mapRoutesCaptor.capture(), any(), any());
    List<String> appWhoseRoutesWasModified = appNameMapRouteCaptor.getAllValues();
    List<List<String>> routesLists = mapRoutesCaptor.getAllValues();
    assertThat(appWhoseRoutesWasModified).isNotNull();
    assertThat(appWhoseRoutesWasModified.size()).isEqualTo(2);
    assertThat(routesLists).isNotNull();
    assertThat(appWhoseRoutesWasModified.get(0).equalsIgnoreCase(newAppName)).isTrue();
    assertThat(routesLists.get(0).containsAll(finalRoutes)).isTrue();
    assertThat(appWhoseRoutesWasModified.get(1).equalsIgnoreCase(activeAppName)).isTrue();
    assertThat(routesLists.get(1).containsAll(tempRoutes)).isTrue();

    // verify correct routes are unmapped from Active & New application
    ArgumentCaptor<String> appNameUnMapRouteCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<List<String>> unMapRoutesCaptor = ArgumentCaptor.forClass((Class) List.class);
    verify(pcfCommandTaskHelper, times(2))
        .unmapRouteMaps(appNameUnMapRouteCaptor.capture(), unMapRoutesCaptor.capture(), any(), any());
    List<String> appWhoseRoutesWereRemoved = appNameUnMapRouteCaptor.getAllValues();
    List<List<String>> removedRouteList = unMapRoutesCaptor.getAllValues();
    assertThat(appWhoseRoutesWereRemoved).isNotNull();
    assertThat(appWhoseRoutesWereRemoved.size()).isEqualTo(2);
    assertThat(removedRouteList).isNotNull();
    assertThat(appWhoseRoutesWereRemoved.get(0).equalsIgnoreCase(newAppName)).isTrue();
    assertThat(removedRouteList.get(0).containsAll(tempRoutes)).isTrue();
    assertThat(appWhoseRoutesWereRemoved.get(1).equalsIgnoreCase(activeAppName)).isTrue();
    assertThat(removedRouteList.get(1).containsAll(finalRoutes)).isTrue();

    ArgumentCaptor<Boolean> appEnvVariableCaptor = ArgumentCaptor.forClass(Boolean.class);
    verify(cfDeploymentManager, times(2))
        .setEnvironmentVariableForAppStatus(any(), appEnvVariableCaptor.capture(), any());
    List<Boolean> allValues = appEnvVariableCaptor.getAllValues();
    assertThat(allValues).isNotNull();
    assertThat(allValues.size()).isEqualTo(2);
    assertThat(allValues.get(0)).isTrue(); // for new app True means ACTIVE will be set
    assertThat(allValues.get(1)).isFalse(); // for current active app False means STAGE will be set

    // verify renaming happened correctly
    ArgumentCaptor<String> executionLogs = ArgumentCaptor.forClass(String.class);
    verify(executionLogCallback, atLeastOnce()).saveExecutionLog(executionLogs.capture());
    List<String> logMessages = executionLogs.getAllValues();
    List<String> renamingMsg =
        logMessages.stream().filter(msg -> msg.contains("# Starting Renaming apps")).collect(Collectors.toList());
    assertThat(renamingMsg.isEmpty()).isFalse();

    ArgumentCaptor<ApplicationSummary> applicationCaptor = ArgumentCaptor.forClass(ApplicationSummary.class);
    ArgumentCaptor<String> newNameCaptor = ArgumentCaptor.forClass(String.class);
    verify(pcfCommandTaskHelper, times(2))
        .renameApp(applicationCaptor.capture(), any(), any(), newNameCaptor.capture());
    List<ApplicationSummary> applicationSummaries = applicationCaptor.getAllValues();
    List<String> newNames = newNameCaptor.getAllValues();
    assertThat(applicationSummaries).isNotEmpty();
    assertThat(newNames).isNotEmpty();

    assertThat(applicationSummaries.get(0).getId()).isEqualTo(activeApp.getApplicationGuid());
    assertThat(newNames.get(0)).isEqualTo(PcfConstants.generateInterimAppName(appPrefix));

    assertThat(applicationSummaries.get(1).getId()).isEqualTo(newApplication.getApplicationGuid());
    assertThat(newNames.get(1)).isEqualTo(appPrefix);

    ArgumentCaptor<ApplicationSummary> activeAppCaptor = ArgumentCaptor.forClass(ApplicationSummary.class);
    ArgumentCaptor<String> activeAppNewNameCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<String> activeAppNewIntermediateNameCaptor = ArgumentCaptor.forClass(String.class);
    verify(pcfCommandTaskHelper, times(1))
        .renameApp(activeAppCaptor.capture(), any(), any(), activeAppNewNameCaptor.capture(),
            activeAppNewIntermediateNameCaptor.capture());
    assertThat(activeAppCaptor.getValue().getId()).isEqualTo(activeApp.getApplicationGuid());
    assertThat(activeAppNewNameCaptor.getValue()).isEqualTo(appPrefix + INACTIVE_APP_NAME_SUFFIX);
    assertThat(activeAppNewIntermediateNameCaptor.getValue()).isEqualTo(PcfConstants.generateInterimAppName(appPrefix));
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testNonVersionToVersionAppRenamingBlueGreen() throws PivotalClientApiException {
    reset(cfDeploymentManager);
    reset(pcfCommandTaskHelper);
    String appPrefix = "PaymentService";
    String inActiveAppName = appPrefix + DELIMITER + "2";
    String activeAppName = appPrefix;
    String newAppName = appPrefix + INACTIVE_APP_NAME_SUFFIX;
    List<String> finalRoutes = Arrays.asList("prod1.apps.pcf-harness.com", "prod2.apps.pcf-harness.com");
    List<String> tempRoutes = Collections.singletonList("tempBg.apps.pcf-harness.com");

    CfAppSetupTimeDetails inActiveApp = CfAppSetupTimeDetails.builder()
                                            .activeApp(false)
                                            .applicationGuid("ca289f74-fdb6-486e-8679-2f91d8ce566e")
                                            .initialInstanceCount(5)
                                            .urls(tempRoutes)
                                            .applicationName(inActiveAppName)
                                            .build();
    CfAppSetupTimeDetails activeApp = CfAppSetupTimeDetails.builder()
                                          .activeApp(false)
                                          .applicationGuid("806c5057-10d4-44c1-ba1b-9e56bd5a997f")
                                          .initialInstanceCount(5)
                                          .urls(finalRoutes)
                                          .applicationName(activeAppName)
                                          .build();
    CfAppSetupTimeDetails newApplication = CfAppSetupTimeDetails.builder()
                                               .activeApp(false)
                                               .applicationGuid("ca289f74-fdb6-486e-8679-2f91d8ce897")
                                               .initialInstanceCount(5)
                                               .urls(tempRoutes)
                                               .applicationName(newAppName)
                                               .build();
    List<ApplicationSummary> existingApplicationSummaries =
        getExistingApplicationSummaries(inActiveApp, activeApp, newApplication);
    doReturn(existingApplicationSummaries).when(cfDeploymentManager).getPreviousReleases(any(), eq(appPrefix));

    CfRouteUpdateRequestConfigData routeUpdateRequestConfigData =
        getRouteUpdateConfigData(appPrefix, activeApp, finalRoutes, tempRoutes, newApplication, inActiveApp, false,
            false, AppNamingStrategy.APP_NAME_WITH_VERSIONING.name(), false);

    CfCommandRequest cfCommandRequest = getRouteUpdateRequest(routeUpdateRequestConfigData);

    CfCommandExecutionResponse cfCommandExecutionResponse =
        pcfRouteUpdateCommandTaskHandler.executeTaskInternal(cfCommandRequest, null, logStreamingTaskClient, false);

    assertThat(cfCommandExecutionResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);

    // verify routes are mapped correctly to current Active & New application
    ArgumentCaptor<String> appNameMapRouteCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<List<String>> mapRoutesCaptor = ArgumentCaptor.forClass((Class) List.class);
    verify(pcfCommandTaskHelper, times(2))
        .mapRouteMaps(appNameMapRouteCaptor.capture(), mapRoutesCaptor.capture(), any(), any());
    List<String> appWhoseRoutesWasModified = appNameMapRouteCaptor.getAllValues();
    List<List<String>> routesLists = mapRoutesCaptor.getAllValues();
    assertThat(appWhoseRoutesWasModified).isNotNull();
    assertThat(appWhoseRoutesWasModified.size()).isEqualTo(2);
    assertThat(routesLists).isNotNull();
    assertThat(appWhoseRoutesWasModified.get(0).equalsIgnoreCase(newAppName)).isTrue();
    assertThat(routesLists.get(0).containsAll(finalRoutes)).isTrue();
    assertThat(appWhoseRoutesWasModified.get(1).equalsIgnoreCase(activeAppName)).isTrue();
    assertThat(routesLists.get(1).containsAll(tempRoutes)).isTrue();

    // verify correct routes are unmapped from Active & New application
    ArgumentCaptor<String> appNameUnMapRouteCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<List<String>> unMapRoutesCaptor = ArgumentCaptor.forClass((Class) List.class);
    verify(pcfCommandTaskHelper, times(2))
        .unmapRouteMaps(appNameUnMapRouteCaptor.capture(), unMapRoutesCaptor.capture(), any(), any());
    List<String> appWhoseRoutesWereRemoved = appNameUnMapRouteCaptor.getAllValues();
    List<List<String>> removedRouteList = unMapRoutesCaptor.getAllValues();
    assertThat(appWhoseRoutesWereRemoved).isNotNull();
    assertThat(appWhoseRoutesWereRemoved.size()).isEqualTo(2);
    assertThat(removedRouteList).isNotNull();
    assertThat(appWhoseRoutesWereRemoved.get(0).equalsIgnoreCase(newAppName)).isTrue();
    assertThat(removedRouteList.get(0).containsAll(tempRoutes)).isTrue();
    assertThat(appWhoseRoutesWereRemoved.get(1).equalsIgnoreCase(activeAppName)).isTrue();
    assertThat(removedRouteList.get(1).containsAll(finalRoutes)).isTrue();

    ArgumentCaptor<Boolean> appEnvVariableCaptor = ArgumentCaptor.forClass(Boolean.class);
    verify(cfDeploymentManager, times(2))
        .setEnvironmentVariableForAppStatus(any(), appEnvVariableCaptor.capture(), any());
    List<Boolean> allValues = appEnvVariableCaptor.getAllValues();
    assertThat(allValues).isNotNull();
    assertThat(allValues.size()).isEqualTo(2);
    assertThat(allValues.get(0)).isTrue(); // for new app True means ACTIVE will be set
    assertThat(allValues.get(1)).isFalse(); // for current active app False means STAGE will be set

    // verify renaming happened correctly
    ArgumentCaptor<String> executionLogs = ArgumentCaptor.forClass(String.class);
    verify(executionLogCallback, atLeastOnce()).saveExecutionLog(executionLogs.capture());
    List<String> logMessages = executionLogs.getAllValues();
    List<String> renamingMsg =
        logMessages.stream().filter(msg -> msg.contains("# Starting Renaming apps")).collect(Collectors.toList());
    assertThat(renamingMsg.isEmpty()).isFalse();

    ArgumentCaptor<ApplicationSummary> applicationCaptor = ArgumentCaptor.forClass(ApplicationSummary.class);
    ArgumentCaptor<String> newNameCaptor = ArgumentCaptor.forClass(String.class);
    verify(pcfCommandTaskHelper, times(2))
        .renameApp(applicationCaptor.capture(), any(), any(), newNameCaptor.capture());
    List<ApplicationSummary> applicationSummaries = applicationCaptor.getAllValues();
    List<String> newNames = newNameCaptor.getAllValues();
    assertThat(applicationSummaries).isNotEmpty();
    assertThat(newNames).isNotEmpty();

    assertThat(applicationSummaries.get(0).getId()).isEqualTo(activeApp.getApplicationGuid());
    assertThat(newNames.get(0)).isEqualTo(appPrefix + DELIMITER + "3");

    assertThat(applicationSummaries.get(1).getId()).isEqualTo(newApplication.getApplicationGuid());
    assertThat(newNames.get(1)).isEqualTo(appPrefix + DELIMITER + "4");
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testVersionToVersionRollbackAppRenamingBlueGreen() throws PivotalClientApiException {
    reset(cfDeploymentManager);
    reset(pcfCommandTaskHelper);

    String appPrefix = "PaymentService";
    String inActiveAppName = appPrefix + DELIMITER + "2";
    String activeAppName = appPrefix + DELIMITER + "3";
    String newAppName = appPrefix + DELIMITER + "4";
    List<String> finalRoutes = Arrays.asList("prod1.apps.pcf-harness.com", "prod2.apps.pcf-harness.com");
    List<String> tempRoutes = Collections.singletonList("tempBg.apps.pcf-harness.com");

    CfAppSetupTimeDetails inActiveApp = CfAppSetupTimeDetails.builder()
                                            .activeApp(false)
                                            .applicationGuid("ca289f74-fdb6-486e-8679-2f91d8ce566e")
                                            .initialInstanceCount(5)
                                            .urls(tempRoutes)
                                            .applicationName(inActiveAppName)
                                            .build();
    CfAppSetupTimeDetails activeApp = CfAppSetupTimeDetails.builder()
                                          .activeApp(false)
                                          .applicationGuid("806c5057-10d4-44c1-ba1b-9e56bd5a997f")
                                          .initialInstanceCount(5)
                                          .urls(finalRoutes)
                                          .applicationName(activeAppName)
                                          .build();
    CfAppSetupTimeDetails newApplication = CfAppSetupTimeDetails.builder()
                                               .activeApp(false)
                                               .applicationGuid("ca289f74-fdb6-486e-8679-2f91d8ce897")
                                               .initialInstanceCount(0)
                                               .urls(tempRoutes)
                                               .applicationName(newAppName)
                                               .build();
    List<ApplicationSummary> existingApplicationSummaries =
        getExistingApplicationSummaries(inActiveApp, activeApp, newApplication);
    doReturn(existingApplicationSummaries).when(cfDeploymentManager).getPreviousReleases(any(), eq(appPrefix));

    CfRouteUpdateRequestConfigData routeUpdateRequestConfigData = getRouteUpdateConfigData(appPrefix, activeApp,
        finalRoutes, tempRoutes, newApplication, inActiveApp, true, false, AppNamingStrategy.VERSIONING.name(), false);

    CfCommandRequest cfCommandRequest = getRouteUpdateRequest(routeUpdateRequestConfigData);

    CfCommandExecutionResponse cfCommandExecutionResponse =
        pcfRouteUpdateCommandTaskHandler.executeTaskInternal(cfCommandRequest, null, logStreamingTaskClient, false);

    assertThat(cfCommandExecutionResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);

    // verify routes are mapped correctly to current Active & New application
    ArgumentCaptor<String> appNameMapRouteCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<List<String>> mapRoutesCaptor = ArgumentCaptor.forClass((Class) List.class);
    verify(pcfCommandTaskHelper, times(1))
        .mapRouteMaps(appNameMapRouteCaptor.capture(), mapRoutesCaptor.capture(), any(), any());
    List<String> appWhoseRoutesWasModified = appNameMapRouteCaptor.getAllValues();
    List<List<String>> routesLists = mapRoutesCaptor.getAllValues();
    assertThat(appWhoseRoutesWasModified).isNotNull();
    assertThat(appWhoseRoutesWasModified.size()).isEqualTo(1);
    assertThat(routesLists).isNotNull();
    assertThat(appWhoseRoutesWasModified.get(0).equalsIgnoreCase(activeAppName)).isTrue();
    assertThat(routesLists.get(0).containsAll(finalRoutes)).isTrue();

    // verify correct routes are unmapped from Active & New application
    ArgumentCaptor<String> appNameUnMapRouteCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<List<String>> unMapRoutesCaptor = ArgumentCaptor.forClass((Class) List.class);
    verify(pcfCommandTaskHelper, times(2))
        .unmapRouteMaps(appNameUnMapRouteCaptor.capture(), unMapRoutesCaptor.capture(), any(), any());
    List<String> appWhoseRoutesWereRemoved = appNameUnMapRouteCaptor.getAllValues();
    List<List<String>> removedRouteList = unMapRoutesCaptor.getAllValues();
    assertThat(appWhoseRoutesWereRemoved).isNotNull();
    assertThat(appWhoseRoutesWereRemoved.size()).isEqualTo(2);
    assertThat(removedRouteList).isNotNull();
    assertThat(appWhoseRoutesWereRemoved.get(0).equalsIgnoreCase(activeAppName)).isTrue();
    assertThat(removedRouteList.get(0).containsAll(tempRoutes)).isTrue();
    assertThat(appWhoseRoutesWereRemoved.get(1).equalsIgnoreCase(newAppName)).isTrue();
    assertThat(removedRouteList.get(1).containsAll(finalRoutes)).isTrue();

    ArgumentCaptor<Boolean> appEnvVariableCaptor = ArgumentCaptor.forClass(Boolean.class);
    verify(cfDeploymentManager, times(1))
        .setEnvironmentVariableForAppStatus(any(), appEnvVariableCaptor.capture(), any());
    List<Boolean> allValues = appEnvVariableCaptor.getAllValues();
    assertThat(allValues).isNotNull();
    assertThat(allValues.size()).isEqualTo(1);
    assertThat(allValues.get(0)).isTrue(); // for new app True means ACTIVE will be set

    // verify renaming did not happen
    ArgumentCaptor<String> executionLogs = ArgumentCaptor.forClass(String.class);
    verify(executionLogCallback, atLeastOnce()).saveExecutionLog(executionLogs.capture());
    List<String> logMessages = executionLogs.getAllValues();
    List<String> renamingMsg =
        logMessages.stream().filter(msg -> msg.contains("# Starting Renaming apps")).collect(Collectors.toList());
    assertThat(renamingMsg.isEmpty()).isTrue();

    verify(pcfCommandTaskHelper, times(0)).renameApp(any(), any(), any(), any());
  }

  /**
   * Before Deployment      After App Setup               After Swap Route            After rollback
   * -----------------      -----------------             -----------------           -----------------
   * PaymentService__2      PaymentService__2             PaymentService__2           PaymentService__2
   * PaymentService__3      PaymentService__2             PaymentService__INACTIVE    PaymentService__3
   *                        PaymentService__INACTIVE      PaymentService              PaymentService__interim
   *
   * Following test verify the rollback renaming worked correctly when something failed after swap route step
   *
   *  @throws PivotalClientApiException throw error
   */
  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testVersionToNonVersionRollbackAppRenamingBlueGreen() throws PivotalClientApiException {
    reset(cfDeploymentManager);
    reset(pcfCommandTaskHelper);

    String appPrefix = "PaymentService";
    String interimAppName = PcfConstants.generateInterimAppName(appPrefix);

    String inActiveAppNameAfterAppSetup = appPrefix + DELIMITER + "2";
    String activeAppNameAfterAppSetup = appPrefix + DELIMITER + "3";
    String newAppNameAfterAppSetup = appPrefix + INACTIVE_APP_NAME_SUFFIX;

    String activeAppNameAfterSwap = appPrefix + INACTIVE_APP_NAME_SUFFIX;
    String newAppNameAfterSwap = appPrefix;

    String inActiveAppGuid = "ca289f74-fdb6-486e-8679-2f91d8ce566e";
    String activeAppGuid = "806c5057-10d4-44c1-ba1b-9e56bd5a997f";
    String newAppGuid = "ca289f74-fdb6-486e-8679-2f91d8ce897";

    List<String> finalRoutes = Arrays.asList("prod1.apps.pcf-harness.com", "prod2.apps.pcf-harness.com");
    List<String> tempRoutes = Collections.singletonList("tempBg.apps.pcf-harness.com");

    CfAppSetupTimeDetails inActiveApp = CfAppSetupTimeDetails.builder()
                                            .activeApp(false)
                                            .applicationGuid(inActiveAppGuid)
                                            .initialInstanceCount(5)
                                            .urls(tempRoutes)
                                            .applicationName(inActiveAppNameAfterAppSetup)
                                            .oldName("")
                                            .build();
    CfAppSetupTimeDetails activeApp = CfAppSetupTimeDetails.builder()
                                          .activeApp(false)
                                          .applicationGuid(activeAppGuid)
                                          .initialInstanceCount(5)
                                          .urls(finalRoutes)
                                          .applicationName(activeAppNameAfterAppSetup)
                                          .oldName(activeAppNameAfterAppSetup)
                                          .build();
    CfAppSetupTimeDetails newApplication = CfAppSetupTimeDetails.builder()
                                               .activeApp(false)
                                               .applicationGuid(newAppGuid)
                                               .initialInstanceCount(0)
                                               .urls(tempRoutes)
                                               .applicationName(newAppNameAfterAppSetup)
                                               .oldName(newAppNameAfterAppSetup)
                                               .build();

    // this result the details of existing system after swap route
    List<ApplicationSummary> existingApplicationSummaries =
        getExistingApplicationSummaries(cloneAppDetails(inActiveApp, inActiveAppNameAfterAppSetup),
            cloneAppDetails(activeApp, activeAppNameAfterSwap), cloneAppDetails(newApplication, newAppNameAfterSwap));
    doReturn(existingApplicationSummaries).when(cfDeploymentManager).getPreviousReleases(any(), eq(appPrefix));

    CfRouteUpdateRequestConfigData routeUpdateRequestConfigData = getRouteUpdateConfigData(appPrefix, activeApp,
        finalRoutes, tempRoutes, newApplication, inActiveApp, true, true, AppNamingStrategy.VERSIONING.name(), false);

    CfCommandRequest cfCommandRequest = getRouteUpdateRequest(routeUpdateRequestConfigData);

    // this is required as renaming during rollback would have renamed the inactive app name
    // from PaymentService -> PaymentService__interim
    doReturn(Collections.singletonList(interimAppName))
        .when(pcfCommandTaskHelper)
        .getAppNameBasedOnGuid(any(CfRequestConfig.class), eq(appPrefix), eq(newAppGuid));

    CfCommandExecutionResponse cfCommandExecutionResponse =
        pcfRouteUpdateCommandTaskHandler.executeTaskInternal(cfCommandRequest, null, logStreamingTaskClient, false);

    assertThat(cfCommandExecutionResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);

    // verify routes are mapped correctly to current Active & New application
    ArgumentCaptor<String> appNameMapRouteCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<List<String>> mapRoutesCaptor = ArgumentCaptor.forClass((Class) List.class);
    verify(pcfCommandTaskHelper, times(1))
        .mapRouteMaps(appNameMapRouteCaptor.capture(), mapRoutesCaptor.capture(), any(), any());
    List<String> appWhoseRoutesWasModified = appNameMapRouteCaptor.getAllValues();
    List<List<String>> routesLists = mapRoutesCaptor.getAllValues();
    assertThat(appWhoseRoutesWasModified).isNotNull();
    assertThat(appWhoseRoutesWasModified.size()).isEqualTo(1);
    assertThat(routesLists).isNotNull();
    assertThat(appWhoseRoutesWasModified.get(0).equalsIgnoreCase(activeAppNameAfterAppSetup)).isTrue();
    assertThat(routesLists.get(0).containsAll(finalRoutes)).isTrue();

    // verify correct routes are unmapped from Active & New application
    ArgumentCaptor<String> appNameUnMapRouteCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<List<String>> unMapRoutesCaptor = ArgumentCaptor.forClass((Class) List.class);
    verify(pcfCommandTaskHelper, times(2))
        .unmapRouteMaps(appNameUnMapRouteCaptor.capture(), unMapRoutesCaptor.capture(), any(), any());
    List<String> appWhoseRoutesWereRemoved = appNameUnMapRouteCaptor.getAllValues();
    List<List<String>> removedRouteList = unMapRoutesCaptor.getAllValues();
    assertThat(appWhoseRoutesWereRemoved).isNotNull();
    assertThat(appWhoseRoutesWereRemoved.size()).isEqualTo(2);
    assertThat(removedRouteList).isNotNull();
    assertThat(appWhoseRoutesWereRemoved.get(0).equalsIgnoreCase(activeAppNameAfterAppSetup)).isTrue();
    assertThat(removedRouteList.get(0).containsAll(tempRoutes)).isTrue();
    assertThat(appWhoseRoutesWereRemoved.get(1).equalsIgnoreCase(interimAppName)).isTrue();
    assertThat(removedRouteList.get(1).containsAll(finalRoutes)).isTrue();

    ArgumentCaptor<Boolean> appEnvVariableCaptor = ArgumentCaptor.forClass(Boolean.class);
    verify(cfDeploymentManager, times(1))
        .setEnvironmentVariableForAppStatus(any(), appEnvVariableCaptor.capture(), any());
    List<Boolean> allValues = appEnvVariableCaptor.getAllValues();
    assertThat(allValues).isNotNull();
    assertThat(allValues.size()).isEqualTo(1);
    assertThat(allValues.get(0)).isTrue();

    // verify renaming happened
    ArgumentCaptor<String> executionLogs = ArgumentCaptor.forClass(String.class);
    verify(executionLogCallback, atLeastOnce()).saveExecutionLog(executionLogs.capture());
    List<String> logMessages = executionLogs.getAllValues();
    List<String> renamingMsg =
        logMessages.stream().filter(msg -> msg.contains("# Starting Renaming apps")).collect(Collectors.toList());
    assertThat(renamingMsg.isEmpty()).isTrue();

    ArgumentCaptor<ApplicationSummary> applicationCaptor = ArgumentCaptor.forClass(ApplicationSummary.class);
    ArgumentCaptor<String> newNameCaptor = ArgumentCaptor.forClass(String.class);
    verify(pcfCommandTaskHelper, times(2))
        .renameApp(applicationCaptor.capture(), any(), any(), newNameCaptor.capture());
    List<ApplicationSummary> applicationSummaries = applicationCaptor.getAllValues();
    List<String> newNames = newNameCaptor.getAllValues();
    assertThat(applicationSummaries).isNotEmpty();
    assertThat(newNames).isNotEmpty();

    assertThat(applicationSummaries.get(0).getId()).isEqualTo(newApplication.getApplicationGuid());
    assertThat(newNames.get(0)).isEqualTo(interimAppName);

    assertThat(applicationSummaries.get(1).getId()).isEqualTo(activeApp.getApplicationGuid());
    assertThat(newNames.get(1)).isEqualTo(activeAppNameAfterAppSetup);
  }

  /**
   * Before Deployment            After App Setup               After Swap Route            After rollback
   * -----------------            -----------------             -----------------           -----------------
   * PaymentService__INACTIVE     PaymentService__2             PaymentService__2           PaymentService__INACTIVE
   * PaymentService               PaymentService                PaymentService__INACTIVE    PaymentService
   *                              PaymentService__INACTIVE      PaymentService              PaymentService__interim
   *
   * Following test verify the rollback renaming worked correctly when something failed after swap route step
   *
   *  @throws PivotalClientApiException throw error
   */
  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testNonVersionToNonVersionRollbackAppRenamingBlueGreen() throws PivotalClientApiException {
    reset(cfDeploymentManager);
    reset(pcfCommandTaskHelper);

    String appPrefix = "PaymentService";
    String interimAppName = PcfConstants.generateInterimAppName(appPrefix);

    String inActiveAppNameAfterAppSetup = appPrefix + DELIMITER + "2";
    String activeAppNameAfterAppSetup = appPrefix;
    String newAppNameAfterAppSetup = appPrefix + INACTIVE_APP_NAME_SUFFIX;

    String activeAppNameAfterSwap = appPrefix + INACTIVE_APP_NAME_SUFFIX;
    String newAppNameAfterSwap = appPrefix;

    String inActiveAppGuid = "ca289f74-fdb6-486e-8679-2f91d8ce566e";
    String activeAppGuid = "806c5057-10d4-44c1-ba1b-9e56bd5a997f";
    String newAppGuid = "ca289f74-fdb6-486e-8679-2f91d8ce897";

    List<String> finalRoutes = Arrays.asList("prod1.apps.pcf-harness.com", "prod2.apps.pcf-harness.com");
    List<String> tempRoutes = Collections.singletonList("tempBg.apps.pcf-harness.com");

    CfAppSetupTimeDetails inActiveApp = CfAppSetupTimeDetails.builder()
                                            .activeApp(false)
                                            .applicationGuid(inActiveAppGuid)
                                            .initialInstanceCount(5)
                                            .urls(tempRoutes)
                                            .applicationName(inActiveAppNameAfterAppSetup)
                                            .oldName(appPrefix + INACTIVE_APP_NAME_SUFFIX)
                                            .build();
    CfAppSetupTimeDetails activeApp = CfAppSetupTimeDetails.builder()
                                          .activeApp(false)
                                          .applicationGuid(activeAppGuid)
                                          .initialInstanceCount(5)
                                          .urls(finalRoutes)
                                          .applicationName(activeAppNameAfterAppSetup)
                                          .oldName(activeAppNameAfterAppSetup)
                                          .build();
    CfAppSetupTimeDetails newApplication = CfAppSetupTimeDetails.builder()
                                               .activeApp(false)
                                               .applicationGuid(newAppGuid)
                                               .initialInstanceCount(0)
                                               .urls(tempRoutes)
                                               .applicationName(newAppNameAfterAppSetup)
                                               .oldName(newAppNameAfterAppSetup)
                                               .build();

    // this result the details of existing system after swap route
    List<ApplicationSummary> existingApplicationSummaries =
        getExistingApplicationSummaries(cloneAppDetails(inActiveApp, inActiveAppNameAfterAppSetup),
            cloneAppDetails(activeApp, activeAppNameAfterSwap), cloneAppDetails(newApplication, newAppNameAfterSwap));
    doReturn(existingApplicationSummaries).when(cfDeploymentManager).getPreviousReleases(any(), eq(appPrefix));

    CfRouteUpdateRequestConfigData routeUpdateRequestConfigData =
        getRouteUpdateConfigData(appPrefix, activeApp, finalRoutes, tempRoutes, newApplication, inActiveApp, true, true,
            AppNamingStrategy.APP_NAME_WITH_VERSIONING.name(), false);

    CfCommandRequest cfCommandRequest = getRouteUpdateRequest(routeUpdateRequestConfigData);

    // this is required as renaming during rollback would have renamed the inactive app name
    // from PaymentService -> PaymentService__interim
    doReturn(Collections.singletonList(interimAppName))
        .when(pcfCommandTaskHelper)
        .getAppNameBasedOnGuid(any(CfRequestConfig.class), eq(appPrefix), eq(newAppGuid));

    CfCommandExecutionResponse cfCommandExecutionResponse =
        pcfRouteUpdateCommandTaskHandler.executeTaskInternal(cfCommandRequest, null, logStreamingTaskClient, false);

    assertThat(cfCommandExecutionResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);

    // verify routes are mapped correctly to current Active & New application
    ArgumentCaptor<String> appNameMapRouteCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<List<String>> mapRoutesCaptor = ArgumentCaptor.forClass((Class) List.class);
    verify(pcfCommandTaskHelper, times(1))
        .mapRouteMaps(appNameMapRouteCaptor.capture(), mapRoutesCaptor.capture(), any(), any());
    List<String> appWhoseRoutesWasModified = appNameMapRouteCaptor.getAllValues();
    List<List<String>> routesLists = mapRoutesCaptor.getAllValues();
    assertThat(appWhoseRoutesWasModified).isNotNull();
    assertThat(appWhoseRoutesWasModified.size()).isEqualTo(1);
    assertThat(routesLists).isNotNull();
    assertThat(appWhoseRoutesWasModified.get(0).equalsIgnoreCase(activeAppNameAfterAppSetup)).isTrue();
    assertThat(routesLists.get(0).containsAll(finalRoutes)).isTrue();

    // verify correct routes are unmapped from Active & New application
    ArgumentCaptor<String> appNameUnMapRouteCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<List<String>> unMapRoutesCaptor = ArgumentCaptor.forClass((Class) List.class);
    verify(pcfCommandTaskHelper, times(2))
        .unmapRouteMaps(appNameUnMapRouteCaptor.capture(), unMapRoutesCaptor.capture(), any(), any());
    List<String> appWhoseRoutesWereRemoved = appNameUnMapRouteCaptor.getAllValues();
    List<List<String>> removedRouteList = unMapRoutesCaptor.getAllValues();
    assertThat(appWhoseRoutesWereRemoved).isNotNull();
    assertThat(appWhoseRoutesWereRemoved.size()).isEqualTo(2);
    assertThat(removedRouteList).isNotNull();
    assertThat(appWhoseRoutesWereRemoved.get(0).equalsIgnoreCase(activeAppNameAfterAppSetup)).isTrue();
    assertThat(removedRouteList.get(0).containsAll(tempRoutes)).isTrue();
    assertThat(appWhoseRoutesWereRemoved.get(1).equalsIgnoreCase(interimAppName)).isTrue();
    assertThat(removedRouteList.get(1).containsAll(finalRoutes)).isTrue();

    ArgumentCaptor<Boolean> appEnvVariableCaptor = ArgumentCaptor.forClass(Boolean.class);
    verify(cfDeploymentManager, times(1))
        .setEnvironmentVariableForAppStatus(any(), appEnvVariableCaptor.capture(), any());
    List<Boolean> allValues = appEnvVariableCaptor.getAllValues();
    assertThat(allValues).isNotNull();
    assertThat(allValues.size()).isEqualTo(1);
    assertThat(allValues.get(0)).isTrue(); // for new app True means ACTIVE will be set

    // verify renaming happened
    ArgumentCaptor<String> executionLogs = ArgumentCaptor.forClass(String.class);
    verify(executionLogCallback, atLeastOnce()).saveExecutionLog(executionLogs.capture());
    List<String> logMessages = executionLogs.getAllValues();
    List<String> renamingMsg =
        logMessages.stream().filter(msg -> msg.contains("# Starting Renaming apps")).collect(Collectors.toList());
    assertThat(renamingMsg.isEmpty()).isTrue();

    ArgumentCaptor<ApplicationSummary> applicationCaptor = ArgumentCaptor.forClass(ApplicationSummary.class);
    ArgumentCaptor<String> newNameCaptor = ArgumentCaptor.forClass(String.class);
    verify(pcfCommandTaskHelper, times(3))
        .renameApp(applicationCaptor.capture(), any(), any(), newNameCaptor.capture());
    List<ApplicationSummary> applicationSummaries = applicationCaptor.getAllValues();
    List<String> newNames = newNameCaptor.getAllValues();
    assertThat(applicationSummaries.size()).isEqualTo(3);
    assertThat(newNames.size()).isEqualTo(3);

    assertThat(applicationSummaries.get(0).getId()).isEqualTo(newApplication.getApplicationGuid());
    assertThat(newNames.get(0)).isEqualTo(interimAppName);

    assertThat(applicationSummaries.get(1).getId()).isEqualTo(activeApp.getApplicationGuid());
    assertThat(newNames.get(1)).isEqualTo(activeAppNameAfterAppSetup);

    assertThat(applicationSummaries.get(2).getId()).isEqualTo(inActiveApp.getApplicationGuid());
    assertThat(newNames.get(2)).isEqualTo(inActiveApp.getOldName());
  }

  /**
   * Before Deployment            After App Setup               After Swap Route            After rollback
   * -----------------            -----------------             -----------------           -----------------
   * PaymentService__INACTIVE     PaymentService__2             PaymentService__2           PaymentService__INACTIVE
   * PaymentService               PaymentService                PaymentService__3           PaymentService
   *                              PaymentService__INACTIVE      PaymentService__4           PaymentService__interim
   *
   * Following test verify the rollback renaming worked correctly when something failed after swap route step
   *
   *  @throws PivotalClientApiException throw error
   */
  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testNonVersionToVersionRollbackAppRenamingBlueGreen() throws PivotalClientApiException {
    reset(cfDeploymentManager);
    reset(pcfCommandTaskHelper);

    String appPrefix = "PaymentService";
    String interimAppName = PcfConstants.generateInterimAppName(appPrefix);

    String inActiveAppNameAfterAppSetup = appPrefix + DELIMITER + "2";
    String activeAppNameAfterAppSetup = appPrefix;
    String newAppNameAfterAppSetup = appPrefix + INACTIVE_APP_NAME_SUFFIX;

    String activeAppNameAfterSwap = appPrefix + DELIMITER + "3";
    String newAppNameAfterSwap = appPrefix + DELIMITER + "4";

    String inActiveAppGuid = "ca289f74-fdb6-486e-8679-2f91d8ce566e";
    String activeAppGuid = "806c5057-10d4-44c1-ba1b-9e56bd5a997f";
    String newAppGuid = "ca289f74-fdb6-486e-8679-2f91d8ce897";

    List<String> finalRoutes = Arrays.asList("prod1.apps.pcf-harness.com", "prod2.apps.pcf-harness.com");
    List<String> tempRoutes = Collections.singletonList("tempBg.apps.pcf-harness.com");

    CfAppSetupTimeDetails inActiveApp = CfAppSetupTimeDetails.builder()
                                            .activeApp(false)
                                            .applicationGuid(inActiveAppGuid)
                                            .initialInstanceCount(5)
                                            .urls(tempRoutes)
                                            .applicationName(inActiveAppNameAfterAppSetup)
                                            .oldName(appPrefix + INACTIVE_APP_NAME_SUFFIX)
                                            .build();
    CfAppSetupTimeDetails activeApp = CfAppSetupTimeDetails.builder()
                                          .activeApp(false)
                                          .applicationGuid(activeAppGuid)
                                          .initialInstanceCount(5)
                                          .urls(finalRoutes)
                                          .applicationName(activeAppNameAfterAppSetup)
                                          .oldName(activeAppNameAfterAppSetup)
                                          .build();
    CfAppSetupTimeDetails newApplication = CfAppSetupTimeDetails.builder()
                                               .activeApp(false)
                                               .applicationGuid(newAppGuid)
                                               .initialInstanceCount(0)
                                               .urls(tempRoutes)
                                               .applicationName(newAppNameAfterAppSetup)
                                               .oldName(newAppNameAfterAppSetup)
                                               .build();

    // this result the details of existing system after swap route
    List<ApplicationSummary> existingApplicationSummaries =
        getExistingApplicationSummaries(cloneAppDetails(inActiveApp, inActiveAppNameAfterAppSetup),
            cloneAppDetails(activeApp, activeAppNameAfterSwap), cloneAppDetails(newApplication, newAppNameAfterSwap));
    doReturn(existingApplicationSummaries).when(cfDeploymentManager).getPreviousReleases(any(), eq(appPrefix));

    CfRouteUpdateRequestConfigData routeUpdateRequestConfigData =
        getRouteUpdateConfigData(appPrefix, activeApp, finalRoutes, tempRoutes, newApplication, inActiveApp, true, true,
            AppNamingStrategy.APP_NAME_WITH_VERSIONING.name(), false);

    CfCommandRequest cfCommandRequest = getRouteUpdateRequest(routeUpdateRequestConfigData);

    // this is required as renaming during rollback would have renamed the inactive app name
    // from PaymentService -> PaymentService__interim
    doReturn(Collections.singletonList(interimAppName))
        .when(pcfCommandTaskHelper)
        .getAppNameBasedOnGuid(any(CfRequestConfig.class), eq(appPrefix), eq(newAppGuid));

    CfCommandExecutionResponse cfCommandExecutionResponse =
        pcfRouteUpdateCommandTaskHandler.executeTaskInternal(cfCommandRequest, null, logStreamingTaskClient, false);

    assertThat(cfCommandExecutionResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);

    // verify routes are mapped correctly to current Active & New application
    ArgumentCaptor<String> appNameMapRouteCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<List<String>> mapRoutesCaptor = ArgumentCaptor.forClass((Class) List.class);
    verify(pcfCommandTaskHelper, times(1))
        .mapRouteMaps(appNameMapRouteCaptor.capture(), mapRoutesCaptor.capture(), any(), any());
    List<String> appWhoseRoutesWasModified = appNameMapRouteCaptor.getAllValues();
    List<List<String>> routesLists = mapRoutesCaptor.getAllValues();
    assertThat(appWhoseRoutesWasModified).isNotNull();
    assertThat(appWhoseRoutesWasModified.size()).isEqualTo(1);
    assertThat(routesLists).isNotNull();
    assertThat(appWhoseRoutesWasModified.get(0).equalsIgnoreCase(activeAppNameAfterAppSetup)).isTrue();
    assertThat(routesLists.get(0).containsAll(finalRoutes)).isTrue();

    // verify correct routes are unmapped from Active & New application
    ArgumentCaptor<String> appNameUnMapRouteCaptor = ArgumentCaptor.forClass(String.class);
    ArgumentCaptor<List<String>> unMapRoutesCaptor = ArgumentCaptor.forClass((Class) List.class);
    verify(pcfCommandTaskHelper, times(2))
        .unmapRouteMaps(appNameUnMapRouteCaptor.capture(), unMapRoutesCaptor.capture(), any(), any());
    List<String> appWhoseRoutesWereRemoved = appNameUnMapRouteCaptor.getAllValues();
    List<List<String>> removedRouteList = unMapRoutesCaptor.getAllValues();
    assertThat(appWhoseRoutesWereRemoved).isNotNull();
    assertThat(appWhoseRoutesWereRemoved.size()).isEqualTo(2);
    assertThat(removedRouteList).isNotNull();
    assertThat(appWhoseRoutesWereRemoved.get(0).equalsIgnoreCase(activeAppNameAfterAppSetup)).isTrue();
    assertThat(removedRouteList.get(0).containsAll(tempRoutes)).isTrue();
    assertThat(appWhoseRoutesWereRemoved.get(1).equalsIgnoreCase(interimAppName)).isTrue();
    assertThat(removedRouteList.get(1).containsAll(finalRoutes)).isTrue();

    ArgumentCaptor<Boolean> appEnvVariableCaptor = ArgumentCaptor.forClass(Boolean.class);
    verify(cfDeploymentManager, times(1))
        .setEnvironmentVariableForAppStatus(any(), appEnvVariableCaptor.capture(), any());
    List<Boolean> allValues = appEnvVariableCaptor.getAllValues();
    assertThat(allValues).isNotNull();
    assertThat(allValues.size()).isEqualTo(1);
    assertThat(allValues.get(0)).isTrue(); // for new app True means ACTIVE will be set

    // verify renaming happened
    ArgumentCaptor<String> executionLogs = ArgumentCaptor.forClass(String.class);
    verify(executionLogCallback, atLeastOnce()).saveExecutionLog(executionLogs.capture());
    List<String> logMessages = executionLogs.getAllValues();
    List<String> renamingMsg =
        logMessages.stream().filter(msg -> msg.contains("# Starting Renaming apps")).collect(Collectors.toList());
    assertThat(renamingMsg.isEmpty()).isTrue();

    ArgumentCaptor<ApplicationSummary> applicationCaptor = ArgumentCaptor.forClass(ApplicationSummary.class);
    ArgumentCaptor<String> newNameCaptor = ArgumentCaptor.forClass(String.class);
    verify(pcfCommandTaskHelper, times(3))
        .renameApp(applicationCaptor.capture(), any(), any(), newNameCaptor.capture());
    List<ApplicationSummary> applicationSummaries = applicationCaptor.getAllValues();
    List<String> newNames = newNameCaptor.getAllValues();
    assertThat(applicationSummaries.size()).isEqualTo(3);
    assertThat(newNames.size()).isEqualTo(3);

    assertThat(applicationSummaries.get(0).getId()).isEqualTo(newApplication.getApplicationGuid());
    assertThat(newNames.get(0)).isEqualTo(interimAppName);

    assertThat(applicationSummaries.get(1).getId()).isEqualTo(activeApp.getApplicationGuid());
    assertThat(newNames.get(1)).isEqualTo(activeAppNameAfterAppSetup);

    assertThat(applicationSummaries.get(2).getId()).isEqualTo(inActiveApp.getApplicationGuid());
    assertThat(newNames.get(2)).isEqualTo(inActiveApp.getOldName());
  }

  private CfAppSetupTimeDetails cloneAppDetails(CfAppSetupTimeDetails source, String latestName) {
    return CfAppSetupTimeDetails.builder()
        .activeApp(source.isActiveApp())
        .applicationGuid(source.getApplicationGuid())
        .initialInstanceCount(source.getInitialInstanceCount())
        .urls(source.getUrls())
        .applicationName(latestName)
        .build();
  }

  @NotNull
  private List<ApplicationSummary> getExistingApplicationSummaries(
      CfAppSetupTimeDetails inActiveApp, CfAppSetupTimeDetails activeApp, CfAppSetupTimeDetails newApplication) {
    return Arrays.asList(ApplicationSummary.builder()
                             .id(inActiveApp.getApplicationGuid())
                             .name(inActiveApp.getApplicationName())
                             .diskQuota(1)
                             .instances(0)
                             .memoryLimit(250)
                             .requestedState(STOPPED)
                             .runningInstances(inActiveApp.getInitialInstanceCount())
                             .build(),
        ApplicationSummary.builder()
            .id(activeApp.getApplicationGuid())
            .name(activeApp.getApplicationName())
            .diskQuota(1)
            .instances(2)
            .memoryLimit(250)
            .requestedState(RUNNING)
            .runningInstances(activeApp.getInitialInstanceCount())
            .build(),
        ApplicationSummary.builder()
            .id(newApplication.getApplicationGuid())
            .name(newApplication.getApplicationName())
            .diskQuota(1)
            .instances(2)
            .memoryLimit(250)
            .requestedState(RUNNING)
            .runningInstances(newApplication.getInitialInstanceCount())
            .build());
  }

  private CfRouteUpdateRequestConfigData getRouteUpdateConfigData(String appPrefix, CfAppSetupTimeDetails activeApp,
      List<String> finalRoutes, List<String> tempRoutes, CfAppSetupTimeDetails newApp,
      CfAppSetupTimeDetails inActiveApp, boolean isRollback, boolean isNonVersioning, String existingAppNamingStrategy,
      boolean upSizeInActiveApp) {
    return CfRouteUpdateRequestConfigData.builder()
        .downsizeOldApplication(false)
        .isRollback(isRollback)
        .isStandardBlueGreen(true)
        .existingApplicationDetails(Collections.singletonList(activeApp))
        .existingApplicationNames(Collections.singletonList(activeApp.getApplicationName()))
        .newApplicationDetails(newApp)
        .newApplicationName(newApp.getApplicationName())
        .upSizeInActiveApp(upSizeInActiveApp)
        .existingInActiveApplicationDetails(inActiveApp)
        .cfAppNamePrefix(appPrefix)
        .finalRoutes(finalRoutes)
        .tempRoutes(tempRoutes)
        .existingAppNamingStrategy(existingAppNamingStrategy)
        .nonVersioning(isNonVersioning)
        .build();
  }
}
