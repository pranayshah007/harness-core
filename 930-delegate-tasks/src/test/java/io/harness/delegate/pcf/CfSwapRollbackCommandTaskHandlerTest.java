/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.pcf;

import static io.harness.delegate.cf.CfTestConstants.ACCOUNT_ID;
import static io.harness.delegate.cf.CfTestConstants.STOPPED;
import static io.harness.delegate.cf.apprenaming.AppRenamingOperator.NamingTransition.ROLLBACK_OPERATOR;
import static io.harness.rule.OwnerRule.PIYUSH_BHUWALKA;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.connector.task.tas.TasNgConfigMapper;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.beans.pcf.CfInBuiltVariablesUpdateValues;
import io.harness.delegate.beans.pcf.CfRouteUpdateRequestConfigData;
import io.harness.delegate.beans.pcf.CfServiceData;
import io.harness.delegate.beans.pcf.TasApplicationInfo;
import io.harness.delegate.task.cf.CfCommandTaskHelperNG;
import io.harness.delegate.task.pcf.CfCommandTypeNG;
import io.harness.delegate.task.pcf.TasTaskHelperBase;
import io.harness.delegate.task.pcf.request.CfSwapRollbackCommandRequestNG;
import io.harness.delegate.task.pcf.request.CfSwapRoutesRequestNG;
import io.harness.delegate.task.pcf.response.CfCommandResponseNG;
import io.harness.delegate.task.pcf.response.TasInfraConfig;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.pcf.CfCommandUnitConstants;
import io.harness.pcf.CfDeploymentManager;
import io.harness.pcf.PivotalClientApiException;
import io.harness.pcf.model.CfAppAutoscalarRequestData;
import io.harness.pcf.model.CfRequestConfig;
import io.harness.pcf.model.CloudFoundryConfig;
import io.harness.rule.Owner;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.SecretDecryptionService;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.cloudfoundry.operations.applications.ApplicationDetail;
import org.cloudfoundry.operations.applications.ApplicationSummary;
import org.cloudfoundry.operations.applications.InstanceDetail;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.runners.MockitoJUnitRunner;

@OwnedBy(HarnessTeam.CDP)
@RunWith(MockitoJUnitRunner.class)
public class CfSwapRollbackCommandTaskHandlerTest extends CategoryTest {
  @Mock LogCallback executionLogCallback;
  @Mock ILogStreamingTaskClient logStreamingTaskClient;
  @Mock CfDeploymentManager cfDeploymentManager;
  @Mock SecretDecryptionService encryptionService;
  @Mock EncryptedDataDetail encryptedDataDetail;

  @Mock TasTaskHelperBase tasTaskHelperBase;
  @Mock TasNgConfigMapper tasNgConfigMapper;
  @Mock protected CfCommandTaskHelperNG pcfCommandTaskHelper;
  @InjectMocks @Spy CfSwapRollbackCommandTaskHandlerNG pcfRollbackCommandTaskHandler;

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void executeTaskInternalTest() throws PivotalClientApiException, IOException {
    TasInfraConfig tasInfraConfig = TasInfraConfig.builder().build();
    char[] password = {'a'};
    char[] username = {'b'};
    CloudFoundryConfig cfConfig = CloudFoundryConfig.builder().userName(username).password(password).build();
    CommandUnitsProgress commandUnitsProgress = CommandUnitsProgress.builder().build();

    File file = new File("/gs");
    doReturn(file).when(pcfCommandTaskHelper).generateWorkingDirectoryForDeployment();

    doReturn(executionLogCallback)
        .when(tasTaskHelperBase)
        .getLogCallback(logStreamingTaskClient, CfCommandUnitConstants.SwapRollback, true, commandUnitsProgress);

    doReturn(executionLogCallback)
        .when(tasTaskHelperBase)
        .getLogCallback(logStreamingTaskClient, CfCommandUnitConstants.Upsize, true, commandUnitsProgress);

    doReturn(executionLogCallback)
        .when(tasTaskHelperBase)
        .getLogCallback(logStreamingTaskClient, CfCommandUnitConstants.Downsize, true, commandUnitsProgress);

    doReturn(executionLogCallback)
        .when(tasTaskHelperBase)
        .getLogCallback(logStreamingTaskClient, CfCommandUnitConstants.Wrapup, true, commandUnitsProgress);

    doReturn(cfConfig)
        .when(tasNgConfigMapper)
        .mapTasConfigWithDecryption(tasInfraConfig.getTasConnectorDTO(), tasInfraConfig.getEncryptionDataDetails());

    String inactiveApplicationGuid = "id";
    String inactiveRoute = "route";
    int activeInstanceCount = 1;
    int inactiveInstanceCount = 1;
    TasApplicationInfo activeApplicationDetails =
        TasApplicationInfo.builder().applicationName("a_s_e__5").runningCount(activeInstanceCount).build();
    TasApplicationInfo newApplicationDetails =
        TasApplicationInfo.builder().applicationName("a_s_e__6").attachedRoutes(emptyList()).build();
    TasApplicationInfo inactiveApplicationDetails = TasApplicationInfo.builder()
                                                        .applicationName("a_s_e__3")
                                                        .runningCount(inactiveInstanceCount)
                                                        .applicationGuid(inactiveApplicationGuid)
                                                        .attachedRoutes(Arrays.asList(inactiveRoute))
                                                        .build();

    CfSwapRollbackCommandRequestNG cfRollbackCommandRequestNG =
        CfSwapRollbackCommandRequestNG.builder()
            .tasInfraConfig(tasInfraConfig)
            .cfCommandTypeNG(CfCommandTypeNG.SWAP_ROLLBACK)
            .accountId(ACCOUNT_ID)
            .swapRouteOccurred(true)
            .instanceData(
                Arrays.asList(CfServiceData.builder().name("a_s_e__6").previousCount(2).desiredCount(0).build(),
                    CfServiceData.builder().name("a_s_e__4").previousCount(0).desiredCount(2).build()))
            .timeoutIntervalInMin(5)
            .newApplicationDetails(newApplicationDetails)
            .inActiveApplicationDetails(inactiveApplicationDetails)
            .activeApplicationDetails(activeApplicationDetails)
            .downsizeOldApplication(true)
            .upsizeInActiveApp(true)
            .useAppAutoScalar(true)
            .build();

    String cfCliPath = "cfCliPath";
    doReturn(cfCliPath)
        .when(pcfCommandTaskHelper)
        .getCfCliPathOnDelegate(true, cfRollbackCommandRequestNG.getCfCliVersion());

    CfRequestConfig cfRequestConfig =
        CfRequestConfig.builder()
            .userName(String.valueOf(cfConfig.getUserName()))
            .endpointUrl(cfConfig.getEndpointUrl())
            .password(String.valueOf(cfConfig.getPassword()))
            .orgName(tasInfraConfig.getOrganization())
            .spaceName(tasInfraConfig.getSpace())
            .timeOutIntervalInMins(cfRollbackCommandRequestNG.getTimeoutIntervalInMin())
            .cfHomeDirPath(file.getAbsolutePath())
            .cfCliPath(pcfCommandTaskHelper.getCfCliPathOnDelegate(true, cfRollbackCommandRequestNG.getCfCliVersion()))
            .cfCliVersion(cfRollbackCommandRequestNG.getCfCliVersion())
            .useCFCLI(true)
            .build();

    CfRouteUpdateRequestConfigData cfRouteUpdateConfigData =
        CfRouteUpdateRequestConfigData.builder()
            .isRollback(true)
            .existingApplicationDetails(activeApplicationDetails != null
                    ? Collections.singletonList(activeApplicationDetails.toCfAppSetupTimeDetails())
                    : null)
            .cfAppNamePrefix(cfRollbackCommandRequestNG.getCfAppNamePrefix())
            .downsizeOldApplication(cfRollbackCommandRequestNG.isDownsizeOldApplication())
            .existingApplicationNames(Arrays.asList("a_s_e__5"))
            .existingInActiveApplicationDetails(cfRollbackCommandRequestNG.getInActiveApplicationDetails() != null
                    ? cfRollbackCommandRequestNG.getInActiveApplicationDetails().toCfAppSetupTimeDetails()
                    : null)
            .tempRoutes(cfRollbackCommandRequestNG.getTempRoutes())
            .skipRollback(false)
            .isStandardBlueGreen(true)
            .newApplicationDetails(cfRollbackCommandRequestNG.getNewApplicationDetails().toCfAppSetupTimeDetails())
            .upSizeInActiveApp(cfRollbackCommandRequestNG.isUpsizeInActiveApp())
            .versioningChanged(false)
            .nonVersioning(true)
            .newApplicationName(cfRollbackCommandRequestNG.getNewApplicationDetails().getApplicationName())
            .finalRoutes(cfRollbackCommandRequestNG.getRouteMaps())
            .isMapRoutesOperation(false)
            .build();

    CfInBuiltVariablesUpdateValues cfInBuiltVariablesUpdateValues = CfInBuiltVariablesUpdateValues.builder().build();
    doReturn(cfInBuiltVariablesUpdateValues)
        .when(pcfCommandTaskHelper)
        .performAppRenaming(ROLLBACK_OPERATOR, cfRouteUpdateConfigData, cfRequestConfig, executionLogCallback);

    String newAppName = "app";
    List<String> newApps = Arrays.asList(newAppName);
    doReturn(newApps)
        .when(pcfCommandTaskHelper)
        .getAppNameBasedOnGuidForBlueGreenDeployment(
            cfRequestConfig, cfRouteUpdateConfigData.getCfAppNamePrefix(), newApplicationDetails.getApplicationGuid());

    ApplicationDetail applicationDetail = ApplicationDetail.builder()
                                              .id("Guid:a_s_e__6")
                                              .diskQuota(1)
                                              .instances(0)
                                              .memoryLimit(1)
                                              .name("a_s_e__")
                                              .requestedState(STOPPED)
                                              .stack("")
                                              .runningInstances(0)
                                              .build();
    doReturn(applicationDetail).when(cfDeploymentManager).getApplicationByName(any());
    CfAppAutoscalarRequestData appAutoscalarRequestData = CfAppAutoscalarRequestData.builder()
                                                              .applicationGuid(applicationDetail.getId())
                                                              .applicationName(applicationDetail.getName())
                                                              .cfRequestConfig(cfRequestConfig)
                                                              .configPathVar(file.getAbsolutePath())
                                                              .timeoutInMins(5)
                                                              .build();

    appAutoscalarRequestData.setExpectedEnabled(false);

    String appName = "app";
    ApplicationSummary previousRelease = ApplicationSummary.builder()
                                             .id(inactiveApplicationGuid)
                                             .diskQuota(1)
                                             .instances(0)
                                             .memoryLimit(1)
                                             .name(appName)
                                             .requestedState(STOPPED)
                                             .runningInstances(0)
                                             .build();
    List<ApplicationSummary> previousReleases = Arrays.asList(previousRelease);
    doReturn(previousReleases).when(cfDeploymentManager).getPreviousReleases(any(), any());

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
        .resizeApplication(any());

    CfCommandResponseNG cfCommandExecutionResponse = pcfRollbackCommandTaskHandler.executeTaskInternal(
        cfRollbackCommandRequestNG, logStreamingTaskClient, commandUnitsProgress);

    assertThat(cfCommandExecutionResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    // Test Exception flow
    doThrow(new IOException("")).when(pcfCommandTaskHelper).generateWorkingDirectoryForDeployment();
    cfCommandExecutionResponse = pcfRollbackCommandTaskHandler.executeTaskInternal(
        cfRollbackCommandRequestNG, logStreamingTaskClient, commandUnitsProgress);
    assertThat(cfCommandExecutionResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.FAILURE);
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void executeTaskInternalTestWhenExistingAppNameIsEmpty() throws PivotalClientApiException, IOException {
    TasInfraConfig tasInfraConfig = TasInfraConfig.builder().build();
    char[] password = {'a'};
    char[] username = {'b'};
    CloudFoundryConfig cfConfig = CloudFoundryConfig.builder().userName(username).password(password).build();
    CommandUnitsProgress commandUnitsProgress = CommandUnitsProgress.builder().build();

    File file = new File("/gs");
    doReturn(file).when(pcfCommandTaskHelper).generateWorkingDirectoryForDeployment();

    doReturn(executionLogCallback)
        .when(tasTaskHelperBase)
        .getLogCallback(logStreamingTaskClient, CfCommandUnitConstants.SwapRollback, true, commandUnitsProgress);

    doReturn(executionLogCallback)
        .when(tasTaskHelperBase)
        .getLogCallback(logStreamingTaskClient, CfCommandUnitConstants.Upsize, true, commandUnitsProgress);

    doReturn(executionLogCallback)
        .when(tasTaskHelperBase)
        .getLogCallback(logStreamingTaskClient, CfCommandUnitConstants.Downsize, true, commandUnitsProgress);

    doReturn(executionLogCallback)
        .when(tasTaskHelperBase)
        .getLogCallback(logStreamingTaskClient, CfCommandUnitConstants.Wrapup, true, commandUnitsProgress);

    doReturn(cfConfig)
        .when(tasNgConfigMapper)
        .mapTasConfigWithDecryption(tasInfraConfig.getTasConnectorDTO(), tasInfraConfig.getEncryptionDataDetails());

    String inactiveApplicationGuid = "id";
    String inactiveRoute = "route";
    int activeInstanceCount = 1;
    int inactiveInstanceCount = 1;
    TasApplicationInfo activeApplicationDetails =
        TasApplicationInfo.builder().applicationName("a_s_e__5").runningCount(activeInstanceCount).build();
    TasApplicationInfo newApplicationDetails =
        TasApplicationInfo.builder().applicationName("a_s_e__6").attachedRoutes(emptyList()).build();
    TasApplicationInfo inactiveApplicationDetails = TasApplicationInfo.builder()
                                                        .applicationName("a_s_e__3")
                                                        .runningCount(inactiveInstanceCount)
                                                        .applicationGuid(inactiveApplicationGuid)
                                                        .attachedRoutes(Arrays.asList(inactiveRoute))
                                                        .build();

    CfSwapRollbackCommandRequestNG cfRollbackCommandRequestNG =
        CfSwapRollbackCommandRequestNG.builder()
            .tasInfraConfig(tasInfraConfig)
            .cfCommandTypeNG(CfCommandTypeNG.SWAP_ROLLBACK)
            .accountId(ACCOUNT_ID)
            .swapRouteOccurred(true)
            .instanceData(
                Arrays.asList(CfServiceData.builder().name("a_s_e__6").previousCount(2).desiredCount(0).build(),
                    CfServiceData.builder().name("a_s_e__4").previousCount(0).desiredCount(2).build()))
            .timeoutIntervalInMin(5)
            .newApplicationDetails(newApplicationDetails)
            .inActiveApplicationDetails(inactiveApplicationDetails)
            .activeApplicationDetails(null)
            .downsizeOldApplication(true)
            .upsizeInActiveApp(true)
            .useAppAutoScalar(true)
            .build();

    String cfCliPath = "cfCliPath";
    doReturn(cfCliPath)
        .when(pcfCommandTaskHelper)
        .getCfCliPathOnDelegate(true, cfRollbackCommandRequestNG.getCfCliVersion());

    CfRequestConfig cfRequestConfig =
        CfRequestConfig.builder()
            .userName(String.valueOf(cfConfig.getUserName()))
            .endpointUrl(cfConfig.getEndpointUrl())
            .password(String.valueOf(cfConfig.getPassword()))
            .orgName(tasInfraConfig.getOrganization())
            .spaceName(tasInfraConfig.getSpace())
            .timeOutIntervalInMins(cfRollbackCommandRequestNG.getTimeoutIntervalInMin())
            .cfHomeDirPath(file.getAbsolutePath())
            .cfCliPath(pcfCommandTaskHelper.getCfCliPathOnDelegate(true, cfRollbackCommandRequestNG.getCfCliVersion()))
            .cfCliVersion(cfRollbackCommandRequestNG.getCfCliVersion())
            .useCFCLI(true)
            .build();

    CfRouteUpdateRequestConfigData cfRouteUpdateConfigData =
        CfRouteUpdateRequestConfigData.builder()
            .isRollback(true)
            .existingApplicationDetails(activeApplicationDetails != null
                    ? Collections.singletonList(activeApplicationDetails.toCfAppSetupTimeDetails())
                    : null)
            .cfAppNamePrefix(cfRollbackCommandRequestNG.getCfAppNamePrefix())
            .downsizeOldApplication(cfRollbackCommandRequestNG.isDownsizeOldApplication())
            .existingApplicationNames(Arrays.asList("a_s_e__5"))
            .existingInActiveApplicationDetails(cfRollbackCommandRequestNG.getInActiveApplicationDetails() != null
                    ? cfRollbackCommandRequestNG.getInActiveApplicationDetails().toCfAppSetupTimeDetails()
                    : null)
            .tempRoutes(cfRollbackCommandRequestNG.getTempRoutes())
            .skipRollback(false)
            .isStandardBlueGreen(true)
            .newApplicationDetails(cfRollbackCommandRequestNG.getNewApplicationDetails().toCfAppSetupTimeDetails())
            .upSizeInActiveApp(cfRollbackCommandRequestNG.isUpsizeInActiveApp())
            .versioningChanged(false)
            .nonVersioning(true)
            .newApplicationName(cfRollbackCommandRequestNG.getNewApplicationDetails().getApplicationName())
            .finalRoutes(cfRollbackCommandRequestNG.getRouteMaps())
            .isMapRoutesOperation(false)
            .build();

    CfInBuiltVariablesUpdateValues cfInBuiltVariablesUpdateValues = CfInBuiltVariablesUpdateValues.builder().build();
    doReturn(cfInBuiltVariablesUpdateValues)
        .when(pcfCommandTaskHelper)
        .performAppRenaming(ROLLBACK_OPERATOR, cfRouteUpdateConfigData, cfRequestConfig, executionLogCallback);

    String newAppName = "app";
    List<String> newApps = Arrays.asList(newAppName);
    doReturn(newApps)
        .when(pcfCommandTaskHelper)
        .getAppNameBasedOnGuidForBlueGreenDeployment(
            cfRequestConfig, cfRouteUpdateConfigData.getCfAppNamePrefix(), newApplicationDetails.getApplicationGuid());

    ApplicationDetail applicationDetail = ApplicationDetail.builder()
                                              .id("Guid:a_s_e__6")
                                              .diskQuota(1)
                                              .instances(0)
                                              .memoryLimit(1)
                                              .name("a_s_e__")
                                              .requestedState(STOPPED)
                                              .stack("")
                                              .runningInstances(0)
                                              .build();
    doReturn(applicationDetail).when(cfDeploymentManager).getApplicationByName(any());
    CfAppAutoscalarRequestData appAutoscalarRequestData = CfAppAutoscalarRequestData.builder()
                                                              .applicationGuid(applicationDetail.getId())
                                                              .applicationName(applicationDetail.getName())
                                                              .cfRequestConfig(cfRequestConfig)
                                                              .configPathVar(file.getAbsolutePath())
                                                              .timeoutInMins(5)
                                                              .build();

    appAutoscalarRequestData.setExpectedEnabled(false);

    String appName = "app";
    ApplicationSummary previousRelease = ApplicationSummary.builder()
                                             .id(inactiveApplicationGuid)
                                             .diskQuota(1)
                                             .instances(0)
                                             .memoryLimit(1)
                                             .name(appName)
                                             .requestedState(STOPPED)
                                             .runningInstances(0)
                                             .build();
    List<ApplicationSummary> previousReleases = Arrays.asList(previousRelease);
    doReturn(previousReleases).when(cfDeploymentManager).getPreviousReleases(any(), any());

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
        .resizeApplication(any());

    CfCommandResponseNG cfCommandExecutionResponse = pcfRollbackCommandTaskHandler.executeTaskInternal(
        cfRollbackCommandRequestNG, logStreamingTaskClient, commandUnitsProgress);

    assertThat(cfCommandExecutionResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    // Test Exception flow
    doThrow(new IOException("")).when(pcfCommandTaskHelper).generateWorkingDirectoryForDeployment();
    cfCommandExecutionResponse = pcfRollbackCommandTaskHandler.executeTaskInternal(
        cfRollbackCommandRequestNG, logStreamingTaskClient, commandUnitsProgress);
    assertThat(cfCommandExecutionResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.FAILURE);
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void executeTaskInternalTestWhenUrlsAreNotProvidedForInactiveApp()
      throws PivotalClientApiException, IOException {
    TasInfraConfig tasInfraConfig = TasInfraConfig.builder().build();
    char[] password = {'a'};
    char[] username = {'b'};
    CloudFoundryConfig cfConfig = CloudFoundryConfig.builder().userName(username).password(password).build();
    CommandUnitsProgress commandUnitsProgress = CommandUnitsProgress.builder().build();

    File file = new File("/gs");
    doReturn(file).when(pcfCommandTaskHelper).generateWorkingDirectoryForDeployment();

    doReturn(executionLogCallback)
        .when(tasTaskHelperBase)
        .getLogCallback(logStreamingTaskClient, CfCommandUnitConstants.SwapRollback, true, commandUnitsProgress);

    doReturn(executionLogCallback)
        .when(tasTaskHelperBase)
        .getLogCallback(logStreamingTaskClient, CfCommandUnitConstants.Upsize, true, commandUnitsProgress);

    doReturn(executionLogCallback)
        .when(tasTaskHelperBase)
        .getLogCallback(logStreamingTaskClient, CfCommandUnitConstants.Downsize, true, commandUnitsProgress);

    doReturn(executionLogCallback)
        .when(tasTaskHelperBase)
        .getLogCallback(logStreamingTaskClient, CfCommandUnitConstants.Wrapup, true, commandUnitsProgress);

    doReturn(cfConfig)
        .when(tasNgConfigMapper)
        .mapTasConfigWithDecryption(tasInfraConfig.getTasConnectorDTO(), tasInfraConfig.getEncryptionDataDetails());

    String inactiveApplicationGuid = "id";
    String inactiveRoute = "route";
    int activeInstanceCount = 1;
    int inactiveInstanceCount = 1;
    TasApplicationInfo activeApplicationDetails =
        TasApplicationInfo.builder().applicationName("a_s_e__5").runningCount(activeInstanceCount).build();
    TasApplicationInfo newApplicationDetails =
        TasApplicationInfo.builder().applicationName("a_s_e__6").attachedRoutes(emptyList()).build();
    TasApplicationInfo inactiveApplicationDetails = TasApplicationInfo.builder()
                                                        .applicationName("a_s_e__3")
                                                        .runningCount(inactiveInstanceCount)
                                                        .applicationGuid(inactiveApplicationGuid)
                                                        .attachedRoutes(emptyList())
                                                        .build();

    CfSwapRollbackCommandRequestNG cfRollbackCommandRequestNG =
        CfSwapRollbackCommandRequestNG.builder()
            .tasInfraConfig(tasInfraConfig)
            .cfCommandTypeNG(CfCommandTypeNG.SWAP_ROLLBACK)
            .accountId(ACCOUNT_ID)
            .swapRouteOccurred(true)
            .instanceData(
                Arrays.asList(CfServiceData.builder().name("a_s_e__6").previousCount(2).desiredCount(0).build(),
                    CfServiceData.builder().name("a_s_e__4").previousCount(0).desiredCount(2).build()))
            .timeoutIntervalInMin(5)
            .newApplicationDetails(newApplicationDetails)
            .inActiveApplicationDetails(inactiveApplicationDetails)
            .activeApplicationDetails(activeApplicationDetails)
            .downsizeOldApplication(true)
            .upsizeInActiveApp(true)
            .useAppAutoScalar(true)
            .build();

    String cfCliPath = "cfCliPath";
    doReturn(cfCliPath)
        .when(pcfCommandTaskHelper)
        .getCfCliPathOnDelegate(true, cfRollbackCommandRequestNG.getCfCliVersion());

    CfRequestConfig cfRequestConfig =
        CfRequestConfig.builder()
            .userName(String.valueOf(cfConfig.getUserName()))
            .endpointUrl(cfConfig.getEndpointUrl())
            .password(String.valueOf(cfConfig.getPassword()))
            .orgName(tasInfraConfig.getOrganization())
            .spaceName(tasInfraConfig.getSpace())
            .timeOutIntervalInMins(cfRollbackCommandRequestNG.getTimeoutIntervalInMin())
            .cfHomeDirPath(file.getAbsolutePath())
            .cfCliPath(pcfCommandTaskHelper.getCfCliPathOnDelegate(true, cfRollbackCommandRequestNG.getCfCliVersion()))
            .cfCliVersion(cfRollbackCommandRequestNG.getCfCliVersion())
            .useCFCLI(true)
            .build();

    CfRouteUpdateRequestConfigData cfRouteUpdateConfigData =
        CfRouteUpdateRequestConfigData.builder()
            .isRollback(true)
            .existingApplicationDetails(activeApplicationDetails != null
                    ? Collections.singletonList(activeApplicationDetails.toCfAppSetupTimeDetails())
                    : null)
            .cfAppNamePrefix(cfRollbackCommandRequestNG.getCfAppNamePrefix())
            .downsizeOldApplication(cfRollbackCommandRequestNG.isDownsizeOldApplication())
            .existingApplicationNames(Arrays.asList("a_s_e__5"))
            .existingInActiveApplicationDetails(cfRollbackCommandRequestNG.getInActiveApplicationDetails() != null
                    ? cfRollbackCommandRequestNG.getInActiveApplicationDetails().toCfAppSetupTimeDetails()
                    : null)
            .tempRoutes(cfRollbackCommandRequestNG.getTempRoutes())
            .skipRollback(false)
            .isStandardBlueGreen(true)
            .newApplicationDetails(cfRollbackCommandRequestNG.getNewApplicationDetails().toCfAppSetupTimeDetails())
            .upSizeInActiveApp(cfRollbackCommandRequestNG.isUpsizeInActiveApp())
            .versioningChanged(false)
            .nonVersioning(true)
            .newApplicationName(cfRollbackCommandRequestNG.getNewApplicationDetails().getApplicationName())
            .finalRoutes(cfRollbackCommandRequestNG.getRouteMaps())
            .isMapRoutesOperation(false)
            .build();

    CfInBuiltVariablesUpdateValues cfInBuiltVariablesUpdateValues = CfInBuiltVariablesUpdateValues.builder().build();
    doReturn(cfInBuiltVariablesUpdateValues)
        .when(pcfCommandTaskHelper)
        .performAppRenaming(ROLLBACK_OPERATOR, cfRouteUpdateConfigData, cfRequestConfig, executionLogCallback);

    String newAppName = "app";
    List<String> newApps = Arrays.asList(newAppName);
    doReturn(newApps)
        .when(pcfCommandTaskHelper)
        .getAppNameBasedOnGuidForBlueGreenDeployment(
            cfRequestConfig, cfRouteUpdateConfigData.getCfAppNamePrefix(), newApplicationDetails.getApplicationGuid());

    ApplicationDetail applicationDetail = ApplicationDetail.builder()
                                              .id("Guid:a_s_e__6")
                                              .diskQuota(1)
                                              .instances(0)
                                              .memoryLimit(1)
                                              .name("a_s_e__")
                                              .requestedState(STOPPED)
                                              .stack("")
                                              .runningInstances(0)
                                              .build();
    doReturn(applicationDetail).when(cfDeploymentManager).getApplicationByName(any());
    CfAppAutoscalarRequestData appAutoscalarRequestData = CfAppAutoscalarRequestData.builder()
                                                              .applicationGuid(applicationDetail.getId())
                                                              .applicationName(applicationDetail.getName())
                                                              .cfRequestConfig(cfRequestConfig)
                                                              .configPathVar(file.getAbsolutePath())
                                                              .timeoutInMins(5)
                                                              .build();

    appAutoscalarRequestData.setExpectedEnabled(false);

    String appName = "app";
    ApplicationSummary previousRelease = ApplicationSummary.builder()
                                             .id(inactiveApplicationGuid)
                                             .diskQuota(1)
                                             .instances(0)
                                             .memoryLimit(1)
                                             .name(appName)
                                             .requestedState(STOPPED)
                                             .runningInstances(0)
                                             .build();
    List<ApplicationSummary> previousReleases = Arrays.asList(previousRelease);
    doReturn(previousReleases).when(cfDeploymentManager).getPreviousReleases(any(), any());

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
        .resizeApplication(any());

    CfCommandResponseNG cfCommandExecutionResponse = pcfRollbackCommandTaskHandler.executeTaskInternal(
        cfRollbackCommandRequestNG, logStreamingTaskClient, commandUnitsProgress);

    assertThat(cfCommandExecutionResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    // Test Exception flow
    doThrow(new IOException("")).when(pcfCommandTaskHelper).generateWorkingDirectoryForDeployment();
    cfCommandExecutionResponse = pcfRollbackCommandTaskHandler.executeTaskInternal(
        cfRollbackCommandRequestNG, logStreamingTaskClient, commandUnitsProgress);
    assertThat(cfCommandExecutionResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.FAILURE);
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void executeTaskInternalTestWhenPreviousReleasesSizeIsNotOne() throws PivotalClientApiException, IOException {
    TasInfraConfig tasInfraConfig = TasInfraConfig.builder().build();
    char[] password = {'a'};
    char[] username = {'b'};
    CloudFoundryConfig cfConfig = CloudFoundryConfig.builder().userName(username).password(password).build();
    CommandUnitsProgress commandUnitsProgress = CommandUnitsProgress.builder().build();

    File file = new File("/gs");
    doReturn(file).when(pcfCommandTaskHelper).generateWorkingDirectoryForDeployment();

    doReturn(executionLogCallback)
        .when(tasTaskHelperBase)
        .getLogCallback(logStreamingTaskClient, CfCommandUnitConstants.SwapRollback, true, commandUnitsProgress);

    doReturn(executionLogCallback)
        .when(tasTaskHelperBase)
        .getLogCallback(logStreamingTaskClient, CfCommandUnitConstants.Upsize, true, commandUnitsProgress);

    doReturn(executionLogCallback)
        .when(tasTaskHelperBase)
        .getLogCallback(logStreamingTaskClient, CfCommandUnitConstants.Downsize, true, commandUnitsProgress);

    doReturn(executionLogCallback)
        .when(tasTaskHelperBase)
        .getLogCallback(logStreamingTaskClient, CfCommandUnitConstants.Wrapup, true, commandUnitsProgress);

    doReturn(cfConfig)
        .when(tasNgConfigMapper)
        .mapTasConfigWithDecryption(tasInfraConfig.getTasConnectorDTO(), tasInfraConfig.getEncryptionDataDetails());

    String inactiveApplicationGuid = "id";
    String inactiveRoute = "route";
    int activeInstanceCount = 1;
    int inactiveInstanceCount = 1;
    TasApplicationInfo activeApplicationDetails =
        TasApplicationInfo.builder().applicationName("a_s_e__5").runningCount(activeInstanceCount).build();
    TasApplicationInfo newApplicationDetails =
        TasApplicationInfo.builder().applicationName("a_s_e__6").attachedRoutes(emptyList()).build();
    TasApplicationInfo inactiveApplicationDetails = TasApplicationInfo.builder()
                                                        .applicationName("a_s_e__3")
                                                        .runningCount(inactiveInstanceCount)
                                                        .applicationGuid(inactiveApplicationGuid)
                                                        .attachedRoutes(Arrays.asList(inactiveRoute))
                                                        .build();

    CfSwapRollbackCommandRequestNG cfRollbackCommandRequestNG =
        CfSwapRollbackCommandRequestNG.builder()
            .tasInfraConfig(tasInfraConfig)
            .cfCommandTypeNG(CfCommandTypeNG.SWAP_ROLLBACK)
            .accountId(ACCOUNT_ID)
            .swapRouteOccurred(true)
            .instanceData(
                Arrays.asList(CfServiceData.builder().name("a_s_e__6").previousCount(2).desiredCount(0).build(),
                    CfServiceData.builder().name("a_s_e__4").previousCount(0).desiredCount(2).build()))
            .timeoutIntervalInMin(5)
            .newApplicationDetails(newApplicationDetails)
            .inActiveApplicationDetails(inactiveApplicationDetails)
            .activeApplicationDetails(activeApplicationDetails)
            .downsizeOldApplication(true)
            .upsizeInActiveApp(true)
            .useAppAutoScalar(true)
            .build();

    String cfCliPath = "cfCliPath";
    doReturn(cfCliPath)
        .when(pcfCommandTaskHelper)
        .getCfCliPathOnDelegate(true, cfRollbackCommandRequestNG.getCfCliVersion());

    CfRequestConfig cfRequestConfig =
        CfRequestConfig.builder()
            .userName(String.valueOf(cfConfig.getUserName()))
            .endpointUrl(cfConfig.getEndpointUrl())
            .password(String.valueOf(cfConfig.getPassword()))
            .orgName(tasInfraConfig.getOrganization())
            .spaceName(tasInfraConfig.getSpace())
            .timeOutIntervalInMins(cfRollbackCommandRequestNG.getTimeoutIntervalInMin())
            .cfHomeDirPath(file.getAbsolutePath())
            .cfCliPath(pcfCommandTaskHelper.getCfCliPathOnDelegate(true, cfRollbackCommandRequestNG.getCfCliVersion()))
            .cfCliVersion(cfRollbackCommandRequestNG.getCfCliVersion())
            .useCFCLI(true)
            .build();

    CfRouteUpdateRequestConfigData cfRouteUpdateConfigData =
        CfRouteUpdateRequestConfigData.builder()
            .isRollback(true)
            .existingApplicationDetails(activeApplicationDetails != null
                    ? Collections.singletonList(activeApplicationDetails.toCfAppSetupTimeDetails())
                    : null)
            .cfAppNamePrefix(cfRollbackCommandRequestNG.getCfAppNamePrefix())
            .downsizeOldApplication(cfRollbackCommandRequestNG.isDownsizeOldApplication())
            .existingApplicationNames(Arrays.asList("a_s_e__5"))
            .existingInActiveApplicationDetails(cfRollbackCommandRequestNG.getInActiveApplicationDetails() != null
                    ? cfRollbackCommandRequestNG.getInActiveApplicationDetails().toCfAppSetupTimeDetails()
                    : null)
            .tempRoutes(cfRollbackCommandRequestNG.getTempRoutes())
            .skipRollback(false)
            .isStandardBlueGreen(true)
            .newApplicationDetails(cfRollbackCommandRequestNG.getNewApplicationDetails().toCfAppSetupTimeDetails())
            .upSizeInActiveApp(cfRollbackCommandRequestNG.isUpsizeInActiveApp())
            .versioningChanged(false)
            .nonVersioning(true)
            .newApplicationName(cfRollbackCommandRequestNG.getNewApplicationDetails().getApplicationName())
            .finalRoutes(cfRollbackCommandRequestNG.getRouteMaps())
            .isMapRoutesOperation(false)
            .build();

    CfInBuiltVariablesUpdateValues cfInBuiltVariablesUpdateValues = CfInBuiltVariablesUpdateValues.builder().build();
    doReturn(cfInBuiltVariablesUpdateValues)
        .when(pcfCommandTaskHelper)
        .performAppRenaming(ROLLBACK_OPERATOR, cfRouteUpdateConfigData, cfRequestConfig, executionLogCallback);

    String newAppName = "app";
    List<String> newApps = Arrays.asList(newAppName);
    doReturn(newApps)
        .when(pcfCommandTaskHelper)
        .getAppNameBasedOnGuidForBlueGreenDeployment(
            cfRequestConfig, cfRouteUpdateConfigData.getCfAppNamePrefix(), newApplicationDetails.getApplicationGuid());

    ApplicationDetail applicationDetail = ApplicationDetail.builder()
                                              .id("Guid:a_s_e__6")
                                              .diskQuota(1)
                                              .instances(0)
                                              .memoryLimit(1)
                                              .name("a_s_e__")
                                              .requestedState(STOPPED)
                                              .stack("")
                                              .runningInstances(0)
                                              .build();
    doReturn(applicationDetail).when(cfDeploymentManager).getApplicationByName(any());
    CfAppAutoscalarRequestData appAutoscalarRequestData = CfAppAutoscalarRequestData.builder()
                                                              .applicationGuid(applicationDetail.getId())
                                                              .applicationName(applicationDetail.getName())
                                                              .cfRequestConfig(cfRequestConfig)
                                                              .configPathVar(file.getAbsolutePath())
                                                              .timeoutInMins(5)
                                                              .build();

    appAutoscalarRequestData.setExpectedEnabled(false);

    String appName = "app";
    ApplicationSummary previousRelease = ApplicationSummary.builder()
                                             .id(inactiveApplicationGuid)
                                             .diskQuota(1)
                                             .instances(0)
                                             .memoryLimit(1)
                                             .name(appName)
                                             .requestedState(STOPPED)
                                             .runningInstances(0)
                                             .build();
    List<ApplicationSummary> previousReleases = Arrays.asList(previousRelease, previousRelease);
    doReturn(previousReleases).when(cfDeploymentManager).getPreviousReleases(any(), any());

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
        .resizeApplication(any());

    CfCommandResponseNG cfCommandExecutionResponse = pcfRollbackCommandTaskHandler.executeTaskInternal(
        cfRollbackCommandRequestNG, logStreamingTaskClient, commandUnitsProgress);

    assertThat(cfCommandExecutionResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    // Test Exception flow
    doThrow(new IOException("")).when(pcfCommandTaskHelper).generateWorkingDirectoryForDeployment();
    cfCommandExecutionResponse = pcfRollbackCommandTaskHandler.executeTaskInternal(
        cfRollbackCommandRequestNG, logStreamingTaskClient, commandUnitsProgress);
    assertThat(cfCommandExecutionResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.FAILURE);
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void executeTaskInternalTestWhenInstanceCountOfInactiveAppIsZero()
      throws PivotalClientApiException, IOException {
    TasInfraConfig tasInfraConfig = TasInfraConfig.builder().build();
    char[] password = {'a'};
    char[] username = {'b'};
    CloudFoundryConfig cfConfig = CloudFoundryConfig.builder().userName(username).password(password).build();
    CommandUnitsProgress commandUnitsProgress = CommandUnitsProgress.builder().build();

    File file = new File("/gs");
    doReturn(file).when(pcfCommandTaskHelper).generateWorkingDirectoryForDeployment();

    doReturn(executionLogCallback)
        .when(tasTaskHelperBase)
        .getLogCallback(logStreamingTaskClient, CfCommandUnitConstants.SwapRollback, true, commandUnitsProgress);

    doReturn(executionLogCallback)
        .when(tasTaskHelperBase)
        .getLogCallback(logStreamingTaskClient, CfCommandUnitConstants.Upsize, true, commandUnitsProgress);

    doReturn(executionLogCallback)
        .when(tasTaskHelperBase)
        .getLogCallback(logStreamingTaskClient, CfCommandUnitConstants.Downsize, true, commandUnitsProgress);

    doReturn(executionLogCallback)
        .when(tasTaskHelperBase)
        .getLogCallback(logStreamingTaskClient, CfCommandUnitConstants.Wrapup, true, commandUnitsProgress);

    doReturn(cfConfig)
        .when(tasNgConfigMapper)
        .mapTasConfigWithDecryption(tasInfraConfig.getTasConnectorDTO(), tasInfraConfig.getEncryptionDataDetails());

    String inactiveApplicationGuid = "id";
    String inactiveRoute = "route";
    int activeInstanceCount = 1;
    int inactiveInstanceCount = 0;
    TasApplicationInfo activeApplicationDetails =
        TasApplicationInfo.builder().applicationName("a_s_e__5").runningCount(activeInstanceCount).build();
    TasApplicationInfo newApplicationDetails =
        TasApplicationInfo.builder().applicationName("a_s_e__6").attachedRoutes(emptyList()).build();
    TasApplicationInfo inactiveApplicationDetails = TasApplicationInfo.builder()
                                                        .applicationName("a_s_e__3")
                                                        .runningCount(inactiveInstanceCount)
                                                        .applicationGuid(inactiveApplicationGuid)
                                                        .attachedRoutes(Arrays.asList(inactiveRoute))
                                                        .build();

    CfSwapRollbackCommandRequestNG cfRollbackCommandRequestNG =
        CfSwapRollbackCommandRequestNG.builder()
            .tasInfraConfig(tasInfraConfig)
            .cfCommandTypeNG(CfCommandTypeNG.SWAP_ROLLBACK)
            .accountId(ACCOUNT_ID)
            .swapRouteOccurred(true)
            .instanceData(
                Arrays.asList(CfServiceData.builder().name("a_s_e__6").previousCount(2).desiredCount(0).build(),
                    CfServiceData.builder().name("a_s_e__4").previousCount(0).desiredCount(2).build()))
            .timeoutIntervalInMin(5)
            .newApplicationDetails(newApplicationDetails)
            .inActiveApplicationDetails(inactiveApplicationDetails)
            .activeApplicationDetails(activeApplicationDetails)
            .downsizeOldApplication(true)
            .upsizeInActiveApp(true)
            .useAppAutoScalar(true)
            .build();

    String cfCliPath = "cfCliPath";
    doReturn(cfCliPath)
        .when(pcfCommandTaskHelper)
        .getCfCliPathOnDelegate(true, cfRollbackCommandRequestNG.getCfCliVersion());

    CfRequestConfig cfRequestConfig =
        CfRequestConfig.builder()
            .userName(String.valueOf(cfConfig.getUserName()))
            .endpointUrl(cfConfig.getEndpointUrl())
            .password(String.valueOf(cfConfig.getPassword()))
            .orgName(tasInfraConfig.getOrganization())
            .spaceName(tasInfraConfig.getSpace())
            .timeOutIntervalInMins(cfRollbackCommandRequestNG.getTimeoutIntervalInMin())
            .cfHomeDirPath(file.getAbsolutePath())
            .cfCliPath(pcfCommandTaskHelper.getCfCliPathOnDelegate(true, cfRollbackCommandRequestNG.getCfCliVersion()))
            .cfCliVersion(cfRollbackCommandRequestNG.getCfCliVersion())
            .useCFCLI(true)
            .build();

    CfRouteUpdateRequestConfigData cfRouteUpdateConfigData =
        CfRouteUpdateRequestConfigData.builder()
            .isRollback(true)
            .existingApplicationDetails(activeApplicationDetails != null
                    ? Collections.singletonList(activeApplicationDetails.toCfAppSetupTimeDetails())
                    : null)
            .cfAppNamePrefix(cfRollbackCommandRequestNG.getCfAppNamePrefix())
            .downsizeOldApplication(cfRollbackCommandRequestNG.isDownsizeOldApplication())
            .existingApplicationNames(Arrays.asList("a_s_e__5"))
            .existingInActiveApplicationDetails(cfRollbackCommandRequestNG.getInActiveApplicationDetails() != null
                    ? cfRollbackCommandRequestNG.getInActiveApplicationDetails().toCfAppSetupTimeDetails()
                    : null)
            .tempRoutes(cfRollbackCommandRequestNG.getTempRoutes())
            .skipRollback(false)
            .isStandardBlueGreen(true)
            .newApplicationDetails(cfRollbackCommandRequestNG.getNewApplicationDetails().toCfAppSetupTimeDetails())
            .upSizeInActiveApp(cfRollbackCommandRequestNG.isUpsizeInActiveApp())
            .versioningChanged(false)
            .nonVersioning(true)
            .newApplicationName(cfRollbackCommandRequestNG.getNewApplicationDetails().getApplicationName())
            .finalRoutes(cfRollbackCommandRequestNG.getRouteMaps())
            .isMapRoutesOperation(false)
            .build();

    CfInBuiltVariablesUpdateValues cfInBuiltVariablesUpdateValues = CfInBuiltVariablesUpdateValues.builder().build();
    doReturn(cfInBuiltVariablesUpdateValues)
        .when(pcfCommandTaskHelper)
        .performAppRenaming(ROLLBACK_OPERATOR, cfRouteUpdateConfigData, cfRequestConfig, executionLogCallback);

    String newAppName = "app";
    List<String> newApps = Arrays.asList(newAppName);
    doReturn(newApps)
        .when(pcfCommandTaskHelper)
        .getAppNameBasedOnGuidForBlueGreenDeployment(
            cfRequestConfig, cfRouteUpdateConfigData.getCfAppNamePrefix(), newApplicationDetails.getApplicationGuid());

    ApplicationDetail applicationDetail = ApplicationDetail.builder()
                                              .id("Guid:a_s_e__6")
                                              .diskQuota(1)
                                              .instances(0)
                                              .memoryLimit(1)
                                              .name("a_s_e__")
                                              .requestedState(STOPPED)
                                              .stack("")
                                              .runningInstances(0)
                                              .build();
    doReturn(applicationDetail).when(cfDeploymentManager).getApplicationByName(any());
    CfAppAutoscalarRequestData appAutoscalarRequestData = CfAppAutoscalarRequestData.builder()
                                                              .applicationGuid(applicationDetail.getId())
                                                              .applicationName(applicationDetail.getName())
                                                              .cfRequestConfig(cfRequestConfig)
                                                              .configPathVar(file.getAbsolutePath())
                                                              .timeoutInMins(5)
                                                              .build();

    appAutoscalarRequestData.setExpectedEnabled(false);

    String appName = "app";
    ApplicationSummary previousRelease = ApplicationSummary.builder()
                                             .id(inactiveApplicationGuid)
                                             .diskQuota(1)
                                             .instances(0)
                                             .memoryLimit(1)
                                             .name(appName)
                                             .requestedState(STOPPED)
                                             .runningInstances(0)
                                             .build();
    List<ApplicationSummary> previousReleases = Arrays.asList(previousRelease);
    doReturn(previousReleases)
        .when(cfDeploymentManager)
        .getPreviousReleases(cfRequestConfig, cfRouteUpdateConfigData.getCfAppNamePrefix());

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
        .resizeApplication(any());

    CfCommandResponseNG cfCommandExecutionResponse = pcfRollbackCommandTaskHandler.executeTaskInternal(
        cfRollbackCommandRequestNG, logStreamingTaskClient, commandUnitsProgress);

    assertThat(cfCommandExecutionResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    // Test Exception flow
    doThrow(new IOException("")).when(pcfCommandTaskHelper).generateWorkingDirectoryForDeployment();
    cfCommandExecutionResponse = pcfRollbackCommandTaskHandler.executeTaskInternal(
        cfRollbackCommandRequestNG, logStreamingTaskClient, commandUnitsProgress);
    assertThat(cfCommandExecutionResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.FAILURE);
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void executeTaskInternalTestWhenAppIdIsNullForInactiveApp() throws PivotalClientApiException, IOException {
    TasInfraConfig tasInfraConfig = TasInfraConfig.builder().build();
    char[] password = {'a'};
    char[] username = {'b'};
    CloudFoundryConfig cfConfig = CloudFoundryConfig.builder().userName(username).password(password).build();
    CommandUnitsProgress commandUnitsProgress = CommandUnitsProgress.builder().build();

    File file = new File("/gs");
    doReturn(file).when(pcfCommandTaskHelper).generateWorkingDirectoryForDeployment();

    doReturn(executionLogCallback)
        .when(tasTaskHelperBase)
        .getLogCallback(logStreamingTaskClient, CfCommandUnitConstants.SwapRollback, true, commandUnitsProgress);

    doReturn(executionLogCallback)
        .when(tasTaskHelperBase)
        .getLogCallback(logStreamingTaskClient, CfCommandUnitConstants.Upsize, true, commandUnitsProgress);

    doReturn(executionLogCallback)
        .when(tasTaskHelperBase)
        .getLogCallback(logStreamingTaskClient, CfCommandUnitConstants.Downsize, true, commandUnitsProgress);

    doReturn(executionLogCallback)
        .when(tasTaskHelperBase)
        .getLogCallback(logStreamingTaskClient, CfCommandUnitConstants.Wrapup, true, commandUnitsProgress);

    doReturn(cfConfig)
        .when(tasNgConfigMapper)
        .mapTasConfigWithDecryption(tasInfraConfig.getTasConnectorDTO(), tasInfraConfig.getEncryptionDataDetails());

    String inactiveApplicationGuid = "id";
    String inactiveRoute = "route";
    int activeInstanceCount = 1;
    int inactiveInstanceCount = 1;
    TasApplicationInfo activeApplicationDetails =
        TasApplicationInfo.builder().applicationName("a_s_e__5").runningCount(activeInstanceCount).build();
    TasApplicationInfo newApplicationDetails =
        TasApplicationInfo.builder().applicationName("a_s_e__6").attachedRoutes(emptyList()).build();
    TasApplicationInfo inactiveApplicationDetails = TasApplicationInfo.builder()
                                                        .applicationName("a_s_e__3")
                                                        .runningCount(inactiveInstanceCount)
                                                        .applicationGuid("")
                                                        .attachedRoutes(Arrays.asList(inactiveRoute))
                                                        .build();

    CfSwapRollbackCommandRequestNG cfRollbackCommandRequestNG =
        CfSwapRollbackCommandRequestNG.builder()
            .tasInfraConfig(tasInfraConfig)
            .cfCommandTypeNG(CfCommandTypeNG.SWAP_ROLLBACK)
            .accountId(ACCOUNT_ID)
            .swapRouteOccurred(true)
            .instanceData(
                Arrays.asList(CfServiceData.builder().name("a_s_e__6").previousCount(2).desiredCount(0).build(),
                    CfServiceData.builder().name("a_s_e__4").previousCount(0).desiredCount(2).build()))
            .timeoutIntervalInMin(5)
            .newApplicationDetails(newApplicationDetails)
            .inActiveApplicationDetails(inactiveApplicationDetails)
            .activeApplicationDetails(activeApplicationDetails)
            .downsizeOldApplication(true)
            .upsizeInActiveApp(true)
            .useAppAutoScalar(true)
            .build();

    String cfCliPath = "cfCliPath";
    doReturn(cfCliPath)
        .when(pcfCommandTaskHelper)
        .getCfCliPathOnDelegate(true, cfRollbackCommandRequestNG.getCfCliVersion());

    CfRequestConfig cfRequestConfig =
        CfRequestConfig.builder()
            .userName(String.valueOf(cfConfig.getUserName()))
            .endpointUrl(cfConfig.getEndpointUrl())
            .password(String.valueOf(cfConfig.getPassword()))
            .orgName(tasInfraConfig.getOrganization())
            .spaceName(tasInfraConfig.getSpace())
            .timeOutIntervalInMins(cfRollbackCommandRequestNG.getTimeoutIntervalInMin())
            .cfHomeDirPath(file.getAbsolutePath())
            .cfCliPath(pcfCommandTaskHelper.getCfCliPathOnDelegate(true, cfRollbackCommandRequestNG.getCfCliVersion()))
            .cfCliVersion(cfRollbackCommandRequestNG.getCfCliVersion())
            .useCFCLI(true)
            .build();

    CfRouteUpdateRequestConfigData cfRouteUpdateConfigData =
        CfRouteUpdateRequestConfigData.builder()
            .isRollback(true)
            .existingApplicationDetails(activeApplicationDetails != null
                    ? Collections.singletonList(activeApplicationDetails.toCfAppSetupTimeDetails())
                    : null)
            .cfAppNamePrefix(cfRollbackCommandRequestNG.getCfAppNamePrefix())
            .downsizeOldApplication(cfRollbackCommandRequestNG.isDownsizeOldApplication())
            .existingApplicationNames(Arrays.asList("a_s_e__5"))
            .existingInActiveApplicationDetails(cfRollbackCommandRequestNG.getInActiveApplicationDetails() != null
                    ? cfRollbackCommandRequestNG.getInActiveApplicationDetails().toCfAppSetupTimeDetails()
                    : null)
            .tempRoutes(cfRollbackCommandRequestNG.getTempRoutes())
            .skipRollback(false)
            .isStandardBlueGreen(true)
            .newApplicationDetails(cfRollbackCommandRequestNG.getNewApplicationDetails().toCfAppSetupTimeDetails())
            .upSizeInActiveApp(cfRollbackCommandRequestNG.isUpsizeInActiveApp())
            .versioningChanged(false)
            .nonVersioning(true)
            .newApplicationName(cfRollbackCommandRequestNG.getNewApplicationDetails().getApplicationName())
            .finalRoutes(cfRollbackCommandRequestNG.getRouteMaps())
            .isMapRoutesOperation(false)
            .build();

    CfInBuiltVariablesUpdateValues cfInBuiltVariablesUpdateValues = CfInBuiltVariablesUpdateValues.builder().build();
    doReturn(cfInBuiltVariablesUpdateValues)
        .when(pcfCommandTaskHelper)
        .performAppRenaming(ROLLBACK_OPERATOR, cfRouteUpdateConfigData, cfRequestConfig, executionLogCallback);

    String newAppName = "app";
    List<String> newApps = Arrays.asList(newAppName);
    doReturn(newApps)
        .when(pcfCommandTaskHelper)
        .getAppNameBasedOnGuidForBlueGreenDeployment(
            cfRequestConfig, cfRouteUpdateConfigData.getCfAppNamePrefix(), newApplicationDetails.getApplicationGuid());

    ApplicationDetail applicationDetail = ApplicationDetail.builder()
                                              .id("Guid:a_s_e__6")
                                              .diskQuota(1)
                                              .instances(0)
                                              .memoryLimit(1)
                                              .name("a_s_e__")
                                              .requestedState(STOPPED)
                                              .stack("")
                                              .runningInstances(0)
                                              .build();
    doReturn(applicationDetail).when(cfDeploymentManager).getApplicationByName(any());
    CfAppAutoscalarRequestData appAutoscalarRequestData = CfAppAutoscalarRequestData.builder()
                                                              .applicationGuid(applicationDetail.getId())
                                                              .applicationName(applicationDetail.getName())
                                                              .cfRequestConfig(cfRequestConfig)
                                                              .configPathVar(file.getAbsolutePath())
                                                              .timeoutInMins(5)
                                                              .build();

    appAutoscalarRequestData.setExpectedEnabled(false);

    String appName = "app";
    ApplicationSummary previousRelease = ApplicationSummary.builder()
                                             .id(inactiveApplicationGuid)
                                             .diskQuota(1)
                                             .instances(0)
                                             .memoryLimit(1)
                                             .name(appName)
                                             .requestedState(STOPPED)
                                             .runningInstances(0)
                                             .build();
    List<ApplicationSummary> previousReleases = Arrays.asList(previousRelease);
    doReturn(previousReleases)
        .when(cfDeploymentManager)
        .getPreviousReleases(cfRequestConfig, cfRouteUpdateConfigData.getCfAppNamePrefix());

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
        .resizeApplication(any());

    CfCommandResponseNG cfCommandExecutionResponse = pcfRollbackCommandTaskHandler.executeTaskInternal(
        cfRollbackCommandRequestNG, logStreamingTaskClient, commandUnitsProgress);

    assertThat(cfCommandExecutionResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    // Test Exception flow
    doThrow(new IOException("")).when(pcfCommandTaskHelper).generateWorkingDirectoryForDeployment();
    cfCommandExecutionResponse = pcfRollbackCommandTaskHandler.executeTaskInternal(
        cfRollbackCommandRequestNG, logStreamingTaskClient, commandUnitsProgress);
    assertThat(cfCommandExecutionResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.FAILURE);
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void executeTaskInternalTestWhenInactiveAppDetailsIsNull() throws PivotalClientApiException, IOException {
    TasInfraConfig tasInfraConfig = TasInfraConfig.builder().build();
    char[] password = {'a'};
    char[] username = {'b'};
    CloudFoundryConfig cfConfig = CloudFoundryConfig.builder().userName(username).password(password).build();
    CommandUnitsProgress commandUnitsProgress = CommandUnitsProgress.builder().build();

    File file = new File("/gs");
    doReturn(file).when(pcfCommandTaskHelper).generateWorkingDirectoryForDeployment();

    doReturn(executionLogCallback)
        .when(tasTaskHelperBase)
        .getLogCallback(logStreamingTaskClient, CfCommandUnitConstants.SwapRollback, true, commandUnitsProgress);

    doReturn(executionLogCallback)
        .when(tasTaskHelperBase)
        .getLogCallback(logStreamingTaskClient, CfCommandUnitConstants.Upsize, true, commandUnitsProgress);

    doReturn(executionLogCallback)
        .when(tasTaskHelperBase)
        .getLogCallback(logStreamingTaskClient, CfCommandUnitConstants.Downsize, true, commandUnitsProgress);

    doReturn(executionLogCallback)
        .when(tasTaskHelperBase)
        .getLogCallback(logStreamingTaskClient, CfCommandUnitConstants.Wrapup, true, commandUnitsProgress);

    doReturn(cfConfig)
        .when(tasNgConfigMapper)
        .mapTasConfigWithDecryption(tasInfraConfig.getTasConnectorDTO(), tasInfraConfig.getEncryptionDataDetails());

    String inactiveApplicationGuid = "id";
    String inactiveRoute = "route";
    int activeInstanceCount = 1;
    int inactiveInstanceCount = 1;
    TasApplicationInfo activeApplicationDetails =
        TasApplicationInfo.builder().applicationName("a_s_e__5").runningCount(activeInstanceCount).build();
    TasApplicationInfo newApplicationDetails =
        TasApplicationInfo.builder().applicationName("a_s_e__6").attachedRoutes(emptyList()).build();
    TasApplicationInfo inactiveApplicationDetails = TasApplicationInfo.builder()
                                                        .applicationName("a_s_e__3")
                                                        .runningCount(inactiveInstanceCount)
                                                        .applicationGuid(inactiveApplicationGuid)
                                                        .attachedRoutes(Arrays.asList(inactiveRoute))
                                                        .build();

    CfSwapRollbackCommandRequestNG cfRollbackCommandRequestNG =
        CfSwapRollbackCommandRequestNG.builder()
            .tasInfraConfig(tasInfraConfig)
            .cfCommandTypeNG(CfCommandTypeNG.SWAP_ROLLBACK)
            .accountId(ACCOUNT_ID)
            .swapRouteOccurred(true)
            .instanceData(
                Arrays.asList(CfServiceData.builder().name("a_s_e__6").previousCount(2).desiredCount(0).build(),
                    CfServiceData.builder().name("a_s_e__4").previousCount(0).desiredCount(2).build()))
            .timeoutIntervalInMin(5)
            .newApplicationDetails(newApplicationDetails)
            .inActiveApplicationDetails(null)
            .activeApplicationDetails(activeApplicationDetails)
            .downsizeOldApplication(true)
            .upsizeInActiveApp(true)
            .useAppAutoScalar(true)
            .build();

    String cfCliPath = "cfCliPath";
    doReturn(cfCliPath)
        .when(pcfCommandTaskHelper)
        .getCfCliPathOnDelegate(true, cfRollbackCommandRequestNG.getCfCliVersion());

    CfRequestConfig cfRequestConfig =
        CfRequestConfig.builder()
            .userName(String.valueOf(cfConfig.getUserName()))
            .endpointUrl(cfConfig.getEndpointUrl())
            .password(String.valueOf(cfConfig.getPassword()))
            .orgName(tasInfraConfig.getOrganization())
            .spaceName(tasInfraConfig.getSpace())
            .timeOutIntervalInMins(cfRollbackCommandRequestNG.getTimeoutIntervalInMin())
            .cfHomeDirPath(file.getAbsolutePath())
            .cfCliPath(pcfCommandTaskHelper.getCfCliPathOnDelegate(true, cfRollbackCommandRequestNG.getCfCliVersion()))
            .cfCliVersion(cfRollbackCommandRequestNG.getCfCliVersion())
            .useCFCLI(true)
            .build();

    CfRouteUpdateRequestConfigData cfRouteUpdateConfigData =
        CfRouteUpdateRequestConfigData.builder()
            .isRollback(true)
            .existingApplicationDetails(activeApplicationDetails != null
                    ? Collections.singletonList(activeApplicationDetails.toCfAppSetupTimeDetails())
                    : null)
            .cfAppNamePrefix(cfRollbackCommandRequestNG.getCfAppNamePrefix())
            .downsizeOldApplication(cfRollbackCommandRequestNG.isDownsizeOldApplication())
            .existingApplicationNames(Arrays.asList("a_s_e__5"))
            .existingInActiveApplicationDetails(cfRollbackCommandRequestNG.getInActiveApplicationDetails() != null
                    ? cfRollbackCommandRequestNG.getInActiveApplicationDetails().toCfAppSetupTimeDetails()
                    : null)
            .tempRoutes(cfRollbackCommandRequestNG.getTempRoutes())
            .skipRollback(false)
            .isStandardBlueGreen(true)
            .newApplicationDetails(cfRollbackCommandRequestNG.getNewApplicationDetails().toCfAppSetupTimeDetails())
            .upSizeInActiveApp(cfRollbackCommandRequestNG.isUpsizeInActiveApp())
            .versioningChanged(false)
            .nonVersioning(true)
            .newApplicationName(cfRollbackCommandRequestNG.getNewApplicationDetails().getApplicationName())
            .finalRoutes(cfRollbackCommandRequestNG.getRouteMaps())
            .isMapRoutesOperation(false)
            .build();

    CfInBuiltVariablesUpdateValues cfInBuiltVariablesUpdateValues = CfInBuiltVariablesUpdateValues.builder().build();
    doReturn(cfInBuiltVariablesUpdateValues)
        .when(pcfCommandTaskHelper)
        .performAppRenaming(ROLLBACK_OPERATOR, cfRouteUpdateConfigData, cfRequestConfig, executionLogCallback);

    String newAppName = "app";
    List<String> newApps = Arrays.asList(newAppName);
    doReturn(newApps)
        .when(pcfCommandTaskHelper)
        .getAppNameBasedOnGuidForBlueGreenDeployment(
            cfRequestConfig, cfRouteUpdateConfigData.getCfAppNamePrefix(), newApplicationDetails.getApplicationGuid());

    ApplicationDetail applicationDetail = ApplicationDetail.builder()
                                              .id("Guid:a_s_e__6")
                                              .diskQuota(1)
                                              .instances(0)
                                              .memoryLimit(1)
                                              .name("a_s_e__")
                                              .requestedState(STOPPED)
                                              .stack("")
                                              .runningInstances(0)
                                              .build();
    doReturn(applicationDetail).when(cfDeploymentManager).getApplicationByName(any());
    CfAppAutoscalarRequestData appAutoscalarRequestData = CfAppAutoscalarRequestData.builder()
                                                              .applicationGuid(applicationDetail.getId())
                                                              .applicationName(applicationDetail.getName())
                                                              .cfRequestConfig(cfRequestConfig)
                                                              .configPathVar(file.getAbsolutePath())
                                                              .timeoutInMins(5)
                                                              .build();

    appAutoscalarRequestData.setExpectedEnabled(false);

    String appName = "app";
    ApplicationSummary previousRelease = ApplicationSummary.builder()
                                             .id(inactiveApplicationGuid)
                                             .diskQuota(1)
                                             .instances(0)
                                             .memoryLimit(1)
                                             .name(appName)
                                             .requestedState(STOPPED)
                                             .runningInstances(0)
                                             .build();
    List<ApplicationSummary> previousReleases = Arrays.asList(previousRelease);
    doReturn(previousReleases)
        .when(cfDeploymentManager)
        .getPreviousReleases(cfRequestConfig, cfRouteUpdateConfigData.getCfAppNamePrefix());

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
        .resizeApplication(any());

    CfCommandResponseNG cfCommandExecutionResponse = pcfRollbackCommandTaskHandler.executeTaskInternal(
        cfRollbackCommandRequestNG, logStreamingTaskClient, commandUnitsProgress);

    assertThat(cfCommandExecutionResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    // Test Exception flow
    doThrow(new IOException("")).when(pcfCommandTaskHelper).generateWorkingDirectoryForDeployment();
    cfCommandExecutionResponse = pcfRollbackCommandTaskHandler.executeTaskInternal(
        cfRollbackCommandRequestNG, logStreamingTaskClient, commandUnitsProgress);
    assertThat(cfCommandExecutionResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.FAILURE);
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void executeTaskInternalTestWhenUpsizeInactiveAppIsFalse() throws PivotalClientApiException, IOException {
    TasInfraConfig tasInfraConfig = TasInfraConfig.builder().build();
    char[] password = {'a'};
    char[] username = {'b'};
    CloudFoundryConfig cfConfig = CloudFoundryConfig.builder().userName(username).password(password).build();
    CommandUnitsProgress commandUnitsProgress = CommandUnitsProgress.builder().build();

    File file = new File("/gs");
    doReturn(file).when(pcfCommandTaskHelper).generateWorkingDirectoryForDeployment();

    doReturn(executionLogCallback)
        .when(tasTaskHelperBase)
        .getLogCallback(logStreamingTaskClient, CfCommandUnitConstants.SwapRollback, true, commandUnitsProgress);

    doReturn(executionLogCallback)
        .when(tasTaskHelperBase)
        .getLogCallback(logStreamingTaskClient, CfCommandUnitConstants.Upsize, true, commandUnitsProgress);

    doReturn(executionLogCallback)
        .when(tasTaskHelperBase)
        .getLogCallback(logStreamingTaskClient, CfCommandUnitConstants.Downsize, true, commandUnitsProgress);

    doReturn(executionLogCallback)
        .when(tasTaskHelperBase)
        .getLogCallback(logStreamingTaskClient, CfCommandUnitConstants.Wrapup, true, commandUnitsProgress);

    doReturn(cfConfig)
        .when(tasNgConfigMapper)
        .mapTasConfigWithDecryption(tasInfraConfig.getTasConnectorDTO(), tasInfraConfig.getEncryptionDataDetails());

    String inactiveApplicationGuid = "id";
    String inactiveRoute = "route";
    int activeInstanceCount = 1;
    int inactiveInstanceCount = 1;
    TasApplicationInfo activeApplicationDetails =
        TasApplicationInfo.builder().applicationName("a_s_e__5").runningCount(activeInstanceCount).build();
    TasApplicationInfo newApplicationDetails =
        TasApplicationInfo.builder().applicationName("a_s_e__6").attachedRoutes(emptyList()).build();
    TasApplicationInfo inactiveApplicationDetails = TasApplicationInfo.builder()
                                                        .applicationName("a_s_e__3")
                                                        .runningCount(inactiveInstanceCount)
                                                        .applicationGuid(inactiveApplicationGuid)
                                                        .attachedRoutes(Arrays.asList(inactiveRoute))
                                                        .build();

    CfSwapRollbackCommandRequestNG cfRollbackCommandRequestNG =
        CfSwapRollbackCommandRequestNG.builder()
            .tasInfraConfig(tasInfraConfig)
            .cfCommandTypeNG(CfCommandTypeNG.SWAP_ROLLBACK)
            .accountId(ACCOUNT_ID)
            .swapRouteOccurred(true)
            .instanceData(
                Arrays.asList(CfServiceData.builder().name("a_s_e__6").previousCount(2).desiredCount(0).build(),
                    CfServiceData.builder().name("a_s_e__4").previousCount(0).desiredCount(2).build()))
            .timeoutIntervalInMin(5)
            .newApplicationDetails(newApplicationDetails)
            .inActiveApplicationDetails(inactiveApplicationDetails)
            .activeApplicationDetails(activeApplicationDetails)
            .downsizeOldApplication(true)
            .upsizeInActiveApp(false)
            .useAppAutoScalar(true)
            .build();

    String cfCliPath = "cfCliPath";
    doReturn(cfCliPath)
        .when(pcfCommandTaskHelper)
        .getCfCliPathOnDelegate(true, cfRollbackCommandRequestNG.getCfCliVersion());

    CfRequestConfig cfRequestConfig =
        CfRequestConfig.builder()
            .userName(String.valueOf(cfConfig.getUserName()))
            .endpointUrl(cfConfig.getEndpointUrl())
            .password(String.valueOf(cfConfig.getPassword()))
            .orgName(tasInfraConfig.getOrganization())
            .spaceName(tasInfraConfig.getSpace())
            .timeOutIntervalInMins(cfRollbackCommandRequestNG.getTimeoutIntervalInMin())
            .cfHomeDirPath(file.getAbsolutePath())
            .cfCliPath(pcfCommandTaskHelper.getCfCliPathOnDelegate(true, cfRollbackCommandRequestNG.getCfCliVersion()))
            .cfCliVersion(cfRollbackCommandRequestNG.getCfCliVersion())
            .useCFCLI(true)
            .build();

    CfRouteUpdateRequestConfigData cfRouteUpdateConfigData =
        CfRouteUpdateRequestConfigData.builder()
            .isRollback(true)
            .existingApplicationDetails(activeApplicationDetails != null
                    ? Collections.singletonList(activeApplicationDetails.toCfAppSetupTimeDetails())
                    : null)
            .cfAppNamePrefix(cfRollbackCommandRequestNG.getCfAppNamePrefix())
            .downsizeOldApplication(cfRollbackCommandRequestNG.isDownsizeOldApplication())
            .existingApplicationNames(Arrays.asList("a_s_e__5"))
            .existingInActiveApplicationDetails(cfRollbackCommandRequestNG.getInActiveApplicationDetails() != null
                    ? cfRollbackCommandRequestNG.getInActiveApplicationDetails().toCfAppSetupTimeDetails()
                    : null)
            .tempRoutes(cfRollbackCommandRequestNG.getTempRoutes())
            .skipRollback(false)
            .isStandardBlueGreen(true)
            .newApplicationDetails(cfRollbackCommandRequestNG.getNewApplicationDetails().toCfAppSetupTimeDetails())
            .upSizeInActiveApp(cfRollbackCommandRequestNG.isUpsizeInActiveApp())
            .versioningChanged(false)
            .nonVersioning(true)
            .newApplicationName(cfRollbackCommandRequestNG.getNewApplicationDetails().getApplicationName())
            .finalRoutes(cfRollbackCommandRequestNG.getRouteMaps())
            .isMapRoutesOperation(false)
            .build();

    CfInBuiltVariablesUpdateValues cfInBuiltVariablesUpdateValues = CfInBuiltVariablesUpdateValues.builder().build();
    doReturn(cfInBuiltVariablesUpdateValues)
        .when(pcfCommandTaskHelper)
        .performAppRenaming(ROLLBACK_OPERATOR, cfRouteUpdateConfigData, cfRequestConfig, executionLogCallback);

    String newAppName = "app";
    List<String> newApps = Arrays.asList(newAppName);
    doReturn(newApps)
        .when(pcfCommandTaskHelper)
        .getAppNameBasedOnGuidForBlueGreenDeployment(
            cfRequestConfig, cfRouteUpdateConfigData.getCfAppNamePrefix(), newApplicationDetails.getApplicationGuid());

    ApplicationDetail applicationDetail = ApplicationDetail.builder()
                                              .id("Guid:a_s_e__6")
                                              .diskQuota(1)
                                              .instances(0)
                                              .memoryLimit(1)
                                              .name("a_s_e__")
                                              .requestedState(STOPPED)
                                              .stack("")
                                              .runningInstances(0)
                                              .build();
    doReturn(applicationDetail).when(cfDeploymentManager).getApplicationByName(any());
    CfAppAutoscalarRequestData appAutoscalarRequestData = CfAppAutoscalarRequestData.builder()
                                                              .applicationGuid(applicationDetail.getId())
                                                              .applicationName(applicationDetail.getName())
                                                              .cfRequestConfig(cfRequestConfig)
                                                              .configPathVar(file.getAbsolutePath())
                                                              .timeoutInMins(5)
                                                              .build();

    appAutoscalarRequestData.setExpectedEnabled(false);

    String appName = "app";
    ApplicationSummary previousRelease = ApplicationSummary.builder()
                                             .id(inactiveApplicationGuid)
                                             .diskQuota(1)
                                             .instances(0)
                                             .memoryLimit(1)
                                             .name(appName)
                                             .requestedState(STOPPED)
                                             .runningInstances(0)
                                             .build();
    List<ApplicationSummary> previousReleases = Arrays.asList(previousRelease);
    doReturn(previousReleases)
        .when(cfDeploymentManager)
        .getPreviousReleases(cfRequestConfig, cfRouteUpdateConfigData.getCfAppNamePrefix());

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
        .resizeApplication(any());

    CfCommandResponseNG cfCommandExecutionResponse = pcfRollbackCommandTaskHandler.executeTaskInternal(
        cfRollbackCommandRequestNG, logStreamingTaskClient, commandUnitsProgress);

    assertThat(cfCommandExecutionResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    // Test Exception flow
    doThrow(new IOException("")).when(pcfCommandTaskHelper).generateWorkingDirectoryForDeployment();
    cfCommandExecutionResponse = pcfRollbackCommandTaskHandler.executeTaskInternal(
        cfRollbackCommandRequestNG, logStreamingTaskClient, commandUnitsProgress);
    assertThat(cfCommandExecutionResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.FAILURE);
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void executeTaskInternalTestWhenDownsizeOldAppIsFalse() throws PivotalClientApiException, IOException {
    TasInfraConfig tasInfraConfig = TasInfraConfig.builder().build();
    char[] password = {'a'};
    char[] username = {'b'};
    CloudFoundryConfig cfConfig = CloudFoundryConfig.builder().userName(username).password(password).build();
    CommandUnitsProgress commandUnitsProgress = CommandUnitsProgress.builder().build();

    File file = new File("/gs");
    doReturn(file).when(pcfCommandTaskHelper).generateWorkingDirectoryForDeployment();

    doReturn(executionLogCallback)
        .when(tasTaskHelperBase)
        .getLogCallback(logStreamingTaskClient, CfCommandUnitConstants.SwapRollback, true, commandUnitsProgress);

    doReturn(executionLogCallback)
        .when(tasTaskHelperBase)
        .getLogCallback(logStreamingTaskClient, CfCommandUnitConstants.Upsize, true, commandUnitsProgress);

    doReturn(executionLogCallback)
        .when(tasTaskHelperBase)
        .getLogCallback(logStreamingTaskClient, CfCommandUnitConstants.Downsize, true, commandUnitsProgress);

    doReturn(executionLogCallback)
        .when(tasTaskHelperBase)
        .getLogCallback(logStreamingTaskClient, CfCommandUnitConstants.Wrapup, true, commandUnitsProgress);

    doReturn(cfConfig)
        .when(tasNgConfigMapper)
        .mapTasConfigWithDecryption(tasInfraConfig.getTasConnectorDTO(), tasInfraConfig.getEncryptionDataDetails());

    String inactiveApplicationGuid = "id";
    String inactiveRoute = "route";
    int activeInstanceCount = 1;
    int inactiveInstanceCount = 1;
    TasApplicationInfo activeApplicationDetails =
        TasApplicationInfo.builder().applicationName("a_s_e__5").runningCount(activeInstanceCount).build();
    TasApplicationInfo newApplicationDetails =
        TasApplicationInfo.builder().applicationName("a_s_e__6").attachedRoutes(emptyList()).build();
    TasApplicationInfo inactiveApplicationDetails = TasApplicationInfo.builder()
                                                        .applicationName("a_s_e__3")
                                                        .runningCount(inactiveInstanceCount)
                                                        .applicationGuid(inactiveApplicationGuid)
                                                        .attachedRoutes(Arrays.asList(inactiveRoute))
                                                        .build();

    CfSwapRollbackCommandRequestNG cfRollbackCommandRequestNG =
        CfSwapRollbackCommandRequestNG.builder()
            .tasInfraConfig(tasInfraConfig)
            .cfCommandTypeNG(CfCommandTypeNG.SWAP_ROLLBACK)
            .accountId(ACCOUNT_ID)
            .swapRouteOccurred(true)
            .instanceData(
                Arrays.asList(CfServiceData.builder().name("a_s_e__6").previousCount(2).desiredCount(0).build(),
                    CfServiceData.builder().name("a_s_e__4").previousCount(0).desiredCount(2).build()))
            .timeoutIntervalInMin(5)
            .newApplicationDetails(newApplicationDetails)
            .inActiveApplicationDetails(inactiveApplicationDetails)
            .activeApplicationDetails(activeApplicationDetails)
            .downsizeOldApplication(false)
            .upsizeInActiveApp(false)
            .useAppAutoScalar(true)
            .build();

    String cfCliPath = "cfCliPath";
    doReturn(cfCliPath)
        .when(pcfCommandTaskHelper)
        .getCfCliPathOnDelegate(true, cfRollbackCommandRequestNG.getCfCliVersion());

    CfRequestConfig cfRequestConfig =
        CfRequestConfig.builder()
            .userName(String.valueOf(cfConfig.getUserName()))
            .endpointUrl(cfConfig.getEndpointUrl())
            .password(String.valueOf(cfConfig.getPassword()))
            .orgName(tasInfraConfig.getOrganization())
            .spaceName(tasInfraConfig.getSpace())
            .timeOutIntervalInMins(cfRollbackCommandRequestNG.getTimeoutIntervalInMin())
            .cfHomeDirPath(file.getAbsolutePath())
            .cfCliPath(pcfCommandTaskHelper.getCfCliPathOnDelegate(true, cfRollbackCommandRequestNG.getCfCliVersion()))
            .cfCliVersion(cfRollbackCommandRequestNG.getCfCliVersion())
            .useCFCLI(true)
            .build();

    CfRouteUpdateRequestConfigData cfRouteUpdateConfigData =
        CfRouteUpdateRequestConfigData.builder()
            .isRollback(true)
            .existingApplicationDetails(activeApplicationDetails != null
                    ? Collections.singletonList(activeApplicationDetails.toCfAppSetupTimeDetails())
                    : null)
            .cfAppNamePrefix(cfRollbackCommandRequestNG.getCfAppNamePrefix())
            .downsizeOldApplication(cfRollbackCommandRequestNG.isDownsizeOldApplication())
            .existingApplicationNames(Arrays.asList("a_s_e__5"))
            .existingInActiveApplicationDetails(cfRollbackCommandRequestNG.getInActiveApplicationDetails() != null
                    ? cfRollbackCommandRequestNG.getInActiveApplicationDetails().toCfAppSetupTimeDetails()
                    : null)
            .tempRoutes(cfRollbackCommandRequestNG.getTempRoutes())
            .skipRollback(false)
            .isStandardBlueGreen(true)
            .newApplicationDetails(cfRollbackCommandRequestNG.getNewApplicationDetails().toCfAppSetupTimeDetails())
            .upSizeInActiveApp(cfRollbackCommandRequestNG.isUpsizeInActiveApp())
            .versioningChanged(false)
            .nonVersioning(true)
            .newApplicationName(cfRollbackCommandRequestNG.getNewApplicationDetails().getApplicationName())
            .finalRoutes(cfRollbackCommandRequestNG.getRouteMaps())
            .isMapRoutesOperation(false)
            .build();

    CfInBuiltVariablesUpdateValues cfInBuiltVariablesUpdateValues = CfInBuiltVariablesUpdateValues.builder().build();
    doReturn(cfInBuiltVariablesUpdateValues)
        .when(pcfCommandTaskHelper)
        .performAppRenaming(ROLLBACK_OPERATOR, cfRouteUpdateConfigData, cfRequestConfig, executionLogCallback);

    String newAppName = "app";
    List<String> newApps = Arrays.asList(newAppName);
    doReturn(newApps)
        .when(pcfCommandTaskHelper)
        .getAppNameBasedOnGuidForBlueGreenDeployment(
            cfRequestConfig, cfRouteUpdateConfigData.getCfAppNamePrefix(), newApplicationDetails.getApplicationGuid());

    ApplicationDetail applicationDetail = ApplicationDetail.builder()
                                              .id("Guid:a_s_e__6")
                                              .diskQuota(1)
                                              .instances(0)
                                              .memoryLimit(1)
                                              .name("a_s_e__")
                                              .requestedState(STOPPED)
                                              .stack("")
                                              .runningInstances(0)
                                              .build();
    doReturn(applicationDetail).when(cfDeploymentManager).getApplicationByName(any());
    CfAppAutoscalarRequestData appAutoscalarRequestData = CfAppAutoscalarRequestData.builder()
                                                              .applicationGuid(applicationDetail.getId())
                                                              .applicationName(applicationDetail.getName())
                                                              .cfRequestConfig(cfRequestConfig)
                                                              .configPathVar(file.getAbsolutePath())
                                                              .timeoutInMins(5)
                                                              .build();

    appAutoscalarRequestData.setExpectedEnabled(false);

    String appName = "app";
    ApplicationSummary previousRelease = ApplicationSummary.builder()
                                             .id(inactiveApplicationGuid)
                                             .diskQuota(1)
                                             .instances(0)
                                             .memoryLimit(1)
                                             .name(appName)
                                             .requestedState(STOPPED)
                                             .runningInstances(0)
                                             .build();
    List<ApplicationSummary> previousReleases = Arrays.asList(previousRelease);
    doReturn(previousReleases)
        .when(cfDeploymentManager)
        .getPreviousReleases(cfRequestConfig, cfRouteUpdateConfigData.getCfAppNamePrefix());

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
        .resizeApplication(any());

    CfCommandResponseNG cfCommandExecutionResponse = pcfRollbackCommandTaskHandler.executeTaskInternal(
        cfRollbackCommandRequestNG, logStreamingTaskClient, commandUnitsProgress);

    assertThat(cfCommandExecutionResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    // Test Exception flow
    doThrow(new IOException("")).when(pcfCommandTaskHelper).generateWorkingDirectoryForDeployment();
    cfCommandExecutionResponse = pcfRollbackCommandTaskHandler.executeTaskInternal(
        cfRollbackCommandRequestNG, logStreamingTaskClient, commandUnitsProgress);
    assertThat(cfCommandExecutionResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.FAILURE);
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void executeTaskInternalTestWhenNewAppListIsNull() throws PivotalClientApiException, IOException {
    TasInfraConfig tasInfraConfig = TasInfraConfig.builder().build();
    char[] password = {'a'};
    char[] username = {'b'};
    CloudFoundryConfig cfConfig = CloudFoundryConfig.builder().userName(username).password(password).build();
    CommandUnitsProgress commandUnitsProgress = CommandUnitsProgress.builder().build();

    File file = new File("/gs");
    doReturn(file).when(pcfCommandTaskHelper).generateWorkingDirectoryForDeployment();

    doReturn(executionLogCallback)
        .when(tasTaskHelperBase)
        .getLogCallback(logStreamingTaskClient, CfCommandUnitConstants.SwapRollback, true, commandUnitsProgress);

    doReturn(executionLogCallback)
        .when(tasTaskHelperBase)
        .getLogCallback(logStreamingTaskClient, CfCommandUnitConstants.Upsize, true, commandUnitsProgress);

    doReturn(executionLogCallback)
        .when(tasTaskHelperBase)
        .getLogCallback(logStreamingTaskClient, CfCommandUnitConstants.Downsize, true, commandUnitsProgress);

    doReturn(executionLogCallback)
        .when(tasTaskHelperBase)
        .getLogCallback(logStreamingTaskClient, CfCommandUnitConstants.Wrapup, true, commandUnitsProgress);

    doReturn(cfConfig)
        .when(tasNgConfigMapper)
        .mapTasConfigWithDecryption(tasInfraConfig.getTasConnectorDTO(), tasInfraConfig.getEncryptionDataDetails());

    String inactiveApplicationGuid = "id";
    String inactiveRoute = "route";
    int activeInstanceCount = 1;
    int inactiveInstanceCount = 1;
    TasApplicationInfo activeApplicationDetails =
        TasApplicationInfo.builder().applicationName("a_s_e__5").runningCount(activeInstanceCount).build();
    TasApplicationInfo newApplicationDetails =
        TasApplicationInfo.builder().applicationName("a_s_e__6").attachedRoutes(emptyList()).build();
    TasApplicationInfo inactiveApplicationDetails = TasApplicationInfo.builder()
                                                        .applicationName("a_s_e__3")
                                                        .runningCount(inactiveInstanceCount)
                                                        .applicationGuid(inactiveApplicationGuid)
                                                        .attachedRoutes(Arrays.asList(inactiveRoute))
                                                        .build();

    CfSwapRollbackCommandRequestNG cfRollbackCommandRequestNG =
        CfSwapRollbackCommandRequestNG.builder()
            .tasInfraConfig(tasInfraConfig)
            .cfCommandTypeNG(CfCommandTypeNG.SWAP_ROLLBACK)
            .accountId(ACCOUNT_ID)
            .swapRouteOccurred(true)
            .instanceData(
                Arrays.asList(CfServiceData.builder().name("a_s_e__6").previousCount(2).desiredCount(0).build(),
                    CfServiceData.builder().name("a_s_e__4").previousCount(0).desiredCount(2).build()))
            .timeoutIntervalInMin(5)
            .newApplicationDetails(newApplicationDetails)
            .inActiveApplicationDetails(inactiveApplicationDetails)
            .activeApplicationDetails(activeApplicationDetails)
            .downsizeOldApplication(true)
            .upsizeInActiveApp(true)
            .useAppAutoScalar(true)
            .build();

    String cfCliPath = "cfCliPath";
    doReturn(cfCliPath)
        .when(pcfCommandTaskHelper)
        .getCfCliPathOnDelegate(true, cfRollbackCommandRequestNG.getCfCliVersion());

    CfRequestConfig cfRequestConfig =
        CfRequestConfig.builder()
            .userName(String.valueOf(cfConfig.getUserName()))
            .endpointUrl(cfConfig.getEndpointUrl())
            .password(String.valueOf(cfConfig.getPassword()))
            .orgName(tasInfraConfig.getOrganization())
            .spaceName(tasInfraConfig.getSpace())
            .timeOutIntervalInMins(cfRollbackCommandRequestNG.getTimeoutIntervalInMin())
            .cfHomeDirPath(file.getAbsolutePath())
            .cfCliPath(pcfCommandTaskHelper.getCfCliPathOnDelegate(true, cfRollbackCommandRequestNG.getCfCliVersion()))
            .cfCliVersion(cfRollbackCommandRequestNG.getCfCliVersion())
            .useCFCLI(true)
            .build();

    CfRouteUpdateRequestConfigData cfRouteUpdateConfigData =
        CfRouteUpdateRequestConfigData.builder()
            .isRollback(true)
            .existingApplicationDetails(activeApplicationDetails != null
                    ? Collections.singletonList(activeApplicationDetails.toCfAppSetupTimeDetails())
                    : null)
            .cfAppNamePrefix(cfRollbackCommandRequestNG.getCfAppNamePrefix())
            .downsizeOldApplication(cfRollbackCommandRequestNG.isDownsizeOldApplication())
            .existingApplicationNames(Arrays.asList("a_s_e__5"))
            .existingInActiveApplicationDetails(cfRollbackCommandRequestNG.getInActiveApplicationDetails() != null
                    ? cfRollbackCommandRequestNG.getInActiveApplicationDetails().toCfAppSetupTimeDetails()
                    : null)
            .tempRoutes(cfRollbackCommandRequestNG.getTempRoutes())
            .skipRollback(false)
            .isStandardBlueGreen(true)
            .newApplicationDetails(cfRollbackCommandRequestNG.getNewApplicationDetails().toCfAppSetupTimeDetails())
            .upSizeInActiveApp(cfRollbackCommandRequestNG.isUpsizeInActiveApp())
            .versioningChanged(false)
            .nonVersioning(true)
            .newApplicationName(cfRollbackCommandRequestNG.getNewApplicationDetails().getApplicationName())
            .finalRoutes(cfRollbackCommandRequestNG.getRouteMaps())
            .isMapRoutesOperation(false)
            .build();

    CfInBuiltVariablesUpdateValues cfInBuiltVariablesUpdateValues = CfInBuiltVariablesUpdateValues.builder().build();
    doReturn(cfInBuiltVariablesUpdateValues)
        .when(pcfCommandTaskHelper)
        .performAppRenaming(ROLLBACK_OPERATOR, cfRouteUpdateConfigData, cfRequestConfig, executionLogCallback);

    String newAppName = "app";
    List<String> newApps = Arrays.asList(newAppName);
    doReturn(emptyList())
        .when(pcfCommandTaskHelper)
        .getAppNameBasedOnGuidForBlueGreenDeployment(
            cfRequestConfig, cfRouteUpdateConfigData.getCfAppNamePrefix(), newApplicationDetails.getApplicationGuid());

    ApplicationDetail applicationDetail = ApplicationDetail.builder()
                                              .id("Guid:a_s_e__6")
                                              .diskQuota(1)
                                              .instances(0)
                                              .memoryLimit(1)
                                              .name("a_s_e__")
                                              .requestedState(STOPPED)
                                              .stack("")
                                              .runningInstances(0)
                                              .build();
    doReturn(applicationDetail).when(cfDeploymentManager).getApplicationByName(any());
    CfAppAutoscalarRequestData appAutoscalarRequestData = CfAppAutoscalarRequestData.builder()
                                                              .applicationGuid(applicationDetail.getId())
                                                              .applicationName(applicationDetail.getName())
                                                              .cfRequestConfig(cfRequestConfig)
                                                              .configPathVar(file.getAbsolutePath())
                                                              .timeoutInMins(5)
                                                              .build();

    appAutoscalarRequestData.setExpectedEnabled(false);

    String appName = "app";
    ApplicationSummary previousRelease = ApplicationSummary.builder()
                                             .id(inactiveApplicationGuid)
                                             .diskQuota(1)
                                             .instances(0)
                                             .memoryLimit(1)
                                             .name(appName)
                                             .requestedState(STOPPED)
                                             .runningInstances(0)
                                             .build();
    List<ApplicationSummary> previousReleases = Arrays.asList(previousRelease);
    doReturn(previousReleases)
        .when(cfDeploymentManager)
        .getPreviousReleases(cfRequestConfig, cfRouteUpdateConfigData.getCfAppNamePrefix());

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
        .resizeApplication(any());

    CfCommandResponseNG cfCommandExecutionResponse = pcfRollbackCommandTaskHandler.executeTaskInternal(
        cfRollbackCommandRequestNG, logStreamingTaskClient, commandUnitsProgress);

    assertThat(cfCommandExecutionResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    // Test Exception flow
    doThrow(new IOException("")).when(pcfCommandTaskHelper).generateWorkingDirectoryForDeployment();
    cfCommandExecutionResponse = pcfRollbackCommandTaskHandler.executeTaskInternal(
        cfRollbackCommandRequestNG, logStreamingTaskClient, commandUnitsProgress);
    assertThat(cfCommandExecutionResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.FAILURE);
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void executeTaskInternalTestWhenAutoscalerinRequestIsFalse() throws PivotalClientApiException, IOException {
    TasInfraConfig tasInfraConfig = TasInfraConfig.builder().build();
    char[] password = {'a'};
    char[] username = {'b'};
    CloudFoundryConfig cfConfig = CloudFoundryConfig.builder().userName(username).password(password).build();
    CommandUnitsProgress commandUnitsProgress = CommandUnitsProgress.builder().build();

    File file = new File("/gs");
    doReturn(file).when(pcfCommandTaskHelper).generateWorkingDirectoryForDeployment();

    doReturn(executionLogCallback)
        .when(tasTaskHelperBase)
        .getLogCallback(logStreamingTaskClient, CfCommandUnitConstants.SwapRollback, true, commandUnitsProgress);

    doReturn(executionLogCallback)
        .when(tasTaskHelperBase)
        .getLogCallback(logStreamingTaskClient, CfCommandUnitConstants.Upsize, true, commandUnitsProgress);

    doReturn(executionLogCallback)
        .when(tasTaskHelperBase)
        .getLogCallback(logStreamingTaskClient, CfCommandUnitConstants.Downsize, true, commandUnitsProgress);

    doReturn(executionLogCallback)
        .when(tasTaskHelperBase)
        .getLogCallback(logStreamingTaskClient, CfCommandUnitConstants.Wrapup, true, commandUnitsProgress);

    doReturn(cfConfig)
        .when(tasNgConfigMapper)
        .mapTasConfigWithDecryption(tasInfraConfig.getTasConnectorDTO(), tasInfraConfig.getEncryptionDataDetails());

    String inactiveApplicationGuid = "id";
    String inactiveRoute = "route";
    int activeInstanceCount = 1;
    int inactiveInstanceCount = 1;
    TasApplicationInfo activeApplicationDetails =
        TasApplicationInfo.builder().applicationName("a_s_e__5").runningCount(activeInstanceCount).build();
    TasApplicationInfo newApplicationDetails =
        TasApplicationInfo.builder().applicationName("a_s_e__6").attachedRoutes(emptyList()).build();
    TasApplicationInfo inactiveApplicationDetails = TasApplicationInfo.builder()
                                                        .applicationName("a_s_e__3")
                                                        .runningCount(inactiveInstanceCount)
                                                        .applicationGuid(inactiveApplicationGuid)
                                                        .attachedRoutes(Arrays.asList(inactiveRoute))
                                                        .build();

    CfSwapRollbackCommandRequestNG cfRollbackCommandRequestNG =
        CfSwapRollbackCommandRequestNG.builder()
            .tasInfraConfig(tasInfraConfig)
            .cfCommandTypeNG(CfCommandTypeNG.SWAP_ROLLBACK)
            .accountId(ACCOUNT_ID)
            .swapRouteOccurred(true)
            .instanceData(
                Arrays.asList(CfServiceData.builder().name("a_s_e__6").previousCount(2).desiredCount(0).build(),
                    CfServiceData.builder().name("a_s_e__4").previousCount(0).desiredCount(2).build()))
            .timeoutIntervalInMin(5)
            .newApplicationDetails(newApplicationDetails)
            .inActiveApplicationDetails(inactiveApplicationDetails)
            .activeApplicationDetails(activeApplicationDetails)
            .downsizeOldApplication(true)
            .upsizeInActiveApp(true)
            .useAppAutoScalar(false)
            .build();

    String cfCliPath = "cfCliPath";
    doReturn(cfCliPath)
        .when(pcfCommandTaskHelper)
        .getCfCliPathOnDelegate(true, cfRollbackCommandRequestNG.getCfCliVersion());

    CfRequestConfig cfRequestConfig =
        CfRequestConfig.builder()
            .userName(String.valueOf(cfConfig.getUserName()))
            .endpointUrl(cfConfig.getEndpointUrl())
            .password(String.valueOf(cfConfig.getPassword()))
            .orgName(tasInfraConfig.getOrganization())
            .spaceName(tasInfraConfig.getSpace())
            .timeOutIntervalInMins(cfRollbackCommandRequestNG.getTimeoutIntervalInMin())
            .cfHomeDirPath(file.getAbsolutePath())
            .cfCliPath(pcfCommandTaskHelper.getCfCliPathOnDelegate(true, cfRollbackCommandRequestNG.getCfCliVersion()))
            .cfCliVersion(cfRollbackCommandRequestNG.getCfCliVersion())
            .useCFCLI(true)
            .build();

    CfRouteUpdateRequestConfigData cfRouteUpdateConfigData =
        CfRouteUpdateRequestConfigData.builder()
            .isRollback(true)
            .existingApplicationDetails(activeApplicationDetails != null
                    ? Collections.singletonList(activeApplicationDetails.toCfAppSetupTimeDetails())
                    : null)
            .cfAppNamePrefix(cfRollbackCommandRequestNG.getCfAppNamePrefix())
            .downsizeOldApplication(cfRollbackCommandRequestNG.isDownsizeOldApplication())
            .existingApplicationNames(Arrays.asList("a_s_e__5"))
            .existingInActiveApplicationDetails(cfRollbackCommandRequestNG.getInActiveApplicationDetails() != null
                    ? cfRollbackCommandRequestNG.getInActiveApplicationDetails().toCfAppSetupTimeDetails()
                    : null)
            .tempRoutes(cfRollbackCommandRequestNG.getTempRoutes())
            .skipRollback(false)
            .isStandardBlueGreen(true)
            .newApplicationDetails(cfRollbackCommandRequestNG.getNewApplicationDetails().toCfAppSetupTimeDetails())
            .upSizeInActiveApp(cfRollbackCommandRequestNG.isUpsizeInActiveApp())
            .versioningChanged(false)
            .nonVersioning(true)
            .newApplicationName(cfRollbackCommandRequestNG.getNewApplicationDetails().getApplicationName())
            .finalRoutes(cfRollbackCommandRequestNG.getRouteMaps())
            .isMapRoutesOperation(false)
            .build();

    CfInBuiltVariablesUpdateValues cfInBuiltVariablesUpdateValues = CfInBuiltVariablesUpdateValues.builder().build();
    doReturn(cfInBuiltVariablesUpdateValues)
        .when(pcfCommandTaskHelper)
        .performAppRenaming(ROLLBACK_OPERATOR, cfRouteUpdateConfigData, cfRequestConfig, executionLogCallback);

    String newAppName = "app";
    List<String> newApps = Arrays.asList(newAppName);
    doReturn(newApps)
        .when(pcfCommandTaskHelper)
        .getAppNameBasedOnGuidForBlueGreenDeployment(
            cfRequestConfig, cfRouteUpdateConfigData.getCfAppNamePrefix(), newApplicationDetails.getApplicationGuid());

    ApplicationDetail applicationDetail = ApplicationDetail.builder()
                                              .id("Guid:a_s_e__6")
                                              .diskQuota(1)
                                              .instances(0)
                                              .memoryLimit(1)
                                              .name("a_s_e__")
                                              .requestedState(STOPPED)
                                              .stack("")
                                              .runningInstances(0)
                                              .build();
    doReturn(applicationDetail).when(cfDeploymentManager).getApplicationByName(any());
    CfAppAutoscalarRequestData appAutoscalarRequestData = CfAppAutoscalarRequestData.builder()
                                                              .applicationGuid(applicationDetail.getId())
                                                              .applicationName(applicationDetail.getName())
                                                              .cfRequestConfig(cfRequestConfig)
                                                              .configPathVar(file.getAbsolutePath())
                                                              .timeoutInMins(5)
                                                              .build();

    appAutoscalarRequestData.setExpectedEnabled(false);

    String appName = "app";
    ApplicationSummary previousRelease = ApplicationSummary.builder()
                                             .id(inactiveApplicationGuid)
                                             .diskQuota(1)
                                             .instances(0)
                                             .memoryLimit(1)
                                             .name(appName)
                                             .requestedState(STOPPED)
                                             .runningInstances(0)
                                             .build();
    List<ApplicationSummary> previousReleases = Arrays.asList(previousRelease);
    doReturn(previousReleases)
        .when(cfDeploymentManager)
        .getPreviousReleases(cfRequestConfig, cfRouteUpdateConfigData.getCfAppNamePrefix());

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
        .resizeApplication(any());

    CfCommandResponseNG cfCommandExecutionResponse = pcfRollbackCommandTaskHandler.executeTaskInternal(
        cfRollbackCommandRequestNG, logStreamingTaskClient, commandUnitsProgress);

    assertThat(cfCommandExecutionResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    // Test Exception flow
    doThrow(new IOException("")).when(pcfCommandTaskHelper).generateWorkingDirectoryForDeployment();
    cfCommandExecutionResponse = pcfRollbackCommandTaskHandler.executeTaskInternal(
        cfRollbackCommandRequestNG, logStreamingTaskClient, commandUnitsProgress);
    assertThat(cfCommandExecutionResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.FAILURE);
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void executeTaskInternalTestWhenSwapRouteOccurredWhenInstanceDataIsEmpty()
      throws PivotalClientApiException, IOException {
    TasInfraConfig tasInfraConfig = TasInfraConfig.builder().build();
    char[] password = {'a'};
    char[] username = {'b'};
    CloudFoundryConfig cfConfig = CloudFoundryConfig.builder().userName(username).password(password).build();
    CommandUnitsProgress commandUnitsProgress = CommandUnitsProgress.builder().build();

    File file = new File("/gs");
    doReturn(file).when(pcfCommandTaskHelper).generateWorkingDirectoryForDeployment();

    doReturn(executionLogCallback)
        .when(tasTaskHelperBase)
        .getLogCallback(logStreamingTaskClient, CfCommandUnitConstants.SwapRollback, true, commandUnitsProgress);

    doReturn(executionLogCallback)
        .when(tasTaskHelperBase)
        .getLogCallback(logStreamingTaskClient, CfCommandUnitConstants.Upsize, true, commandUnitsProgress);

    doReturn(executionLogCallback)
        .when(tasTaskHelperBase)
        .getLogCallback(logStreamingTaskClient, CfCommandUnitConstants.Downsize, true, commandUnitsProgress);

    doReturn(executionLogCallback)
        .when(tasTaskHelperBase)
        .getLogCallback(logStreamingTaskClient, CfCommandUnitConstants.Wrapup, true, commandUnitsProgress);

    doReturn(cfConfig)
        .when(tasNgConfigMapper)
        .mapTasConfigWithDecryption(tasInfraConfig.getTasConnectorDTO(), tasInfraConfig.getEncryptionDataDetails());

    String inactiveApplicationGuid = "id";
    String inactiveRoute = "route";
    int activeInstanceCount = 1;
    int inactiveInstanceCount = 1;
    TasApplicationInfo activeApplicationDetails =
        TasApplicationInfo.builder().applicationName("a_s_e__5").runningCount(activeInstanceCount).build();
    TasApplicationInfo newApplicationDetails =
        TasApplicationInfo.builder().applicationName("a_s_e__6").attachedRoutes(emptyList()).build();
    TasApplicationInfo inactiveApplicationDetails = TasApplicationInfo.builder()
                                                        .applicationName("a_s_e__3")
                                                        .runningCount(inactiveInstanceCount)
                                                        .applicationGuid(inactiveApplicationGuid)
                                                        .attachedRoutes(Arrays.asList(inactiveRoute))
                                                        .build();

    CfSwapRollbackCommandRequestNG cfRollbackCommandRequestNG =
        CfSwapRollbackCommandRequestNG.builder()
            .tasInfraConfig(tasInfraConfig)
            .cfCommandTypeNG(CfCommandTypeNG.SWAP_ROLLBACK)
            .accountId(ACCOUNT_ID)
            .swapRouteOccurred(true)
            .instanceData(emptyList())
            .timeoutIntervalInMin(5)
            .newApplicationDetails(newApplicationDetails)
            .inActiveApplicationDetails(inactiveApplicationDetails)
            .activeApplicationDetails(activeApplicationDetails)
            .downsizeOldApplication(true)
            .upsizeInActiveApp(true)
            .useAppAutoScalar(true)
            .build();

    String cfCliPath = "cfCliPath";
    doReturn(cfCliPath)
        .when(pcfCommandTaskHelper)
        .getCfCliPathOnDelegate(true, cfRollbackCommandRequestNG.getCfCliVersion());

    CfRequestConfig cfRequestConfig =
        CfRequestConfig.builder()
            .userName(String.valueOf(cfConfig.getUserName()))
            .endpointUrl(cfConfig.getEndpointUrl())
            .password(String.valueOf(cfConfig.getPassword()))
            .orgName(tasInfraConfig.getOrganization())
            .spaceName(tasInfraConfig.getSpace())
            .timeOutIntervalInMins(cfRollbackCommandRequestNG.getTimeoutIntervalInMin())
            .cfHomeDirPath(file.getAbsolutePath())
            .cfCliPath(pcfCommandTaskHelper.getCfCliPathOnDelegate(true, cfRollbackCommandRequestNG.getCfCliVersion()))
            .cfCliVersion(cfRollbackCommandRequestNG.getCfCliVersion())
            .useCFCLI(true)
            .build();

    CfRouteUpdateRequestConfigData cfRouteUpdateConfigData =
        CfRouteUpdateRequestConfigData.builder()
            .isRollback(true)
            .existingApplicationDetails(activeApplicationDetails != null
                    ? Collections.singletonList(activeApplicationDetails.toCfAppSetupTimeDetails())
                    : null)
            .cfAppNamePrefix(cfRollbackCommandRequestNG.getCfAppNamePrefix())
            .downsizeOldApplication(cfRollbackCommandRequestNG.isDownsizeOldApplication())
            .existingApplicationNames(Arrays.asList("a_s_e__5"))
            .existingInActiveApplicationDetails(cfRollbackCommandRequestNG.getInActiveApplicationDetails() != null
                    ? cfRollbackCommandRequestNG.getInActiveApplicationDetails().toCfAppSetupTimeDetails()
                    : null)
            .tempRoutes(cfRollbackCommandRequestNG.getTempRoutes())
            .skipRollback(false)
            .isStandardBlueGreen(true)
            .newApplicationDetails(cfRollbackCommandRequestNG.getNewApplicationDetails().toCfAppSetupTimeDetails())
            .upSizeInActiveApp(cfRollbackCommandRequestNG.isUpsizeInActiveApp())
            .versioningChanged(false)
            .nonVersioning(true)
            .newApplicationName(cfRollbackCommandRequestNG.getNewApplicationDetails().getApplicationName())
            .finalRoutes(cfRollbackCommandRequestNG.getRouteMaps())
            .isMapRoutesOperation(false)
            .build();

    CfInBuiltVariablesUpdateValues cfInBuiltVariablesUpdateValues = CfInBuiltVariablesUpdateValues.builder().build();
    doReturn(cfInBuiltVariablesUpdateValues)
        .when(pcfCommandTaskHelper)
        .performAppRenaming(ROLLBACK_OPERATOR, cfRouteUpdateConfigData, cfRequestConfig, executionLogCallback);

    String newAppName = "app";
    List<String> newApps = Arrays.asList(newAppName);
    doReturn(newApps)
        .when(pcfCommandTaskHelper)
        .getAppNameBasedOnGuidForBlueGreenDeployment(
            cfRequestConfig, cfRouteUpdateConfigData.getCfAppNamePrefix(), newApplicationDetails.getApplicationGuid());

    ApplicationDetail applicationDetail = ApplicationDetail.builder()
                                              .id("Guid:a_s_e__6")
                                              .diskQuota(1)
                                              .instances(0)
                                              .memoryLimit(1)
                                              .name("a_s_e__")
                                              .requestedState(STOPPED)
                                              .stack("")
                                              .runningInstances(0)
                                              .build();
    doReturn(applicationDetail).when(cfDeploymentManager).getApplicationByName(any());
    CfAppAutoscalarRequestData appAutoscalarRequestData = CfAppAutoscalarRequestData.builder()
                                                              .applicationGuid(applicationDetail.getId())
                                                              .applicationName(applicationDetail.getName())
                                                              .cfRequestConfig(cfRequestConfig)
                                                              .configPathVar(file.getAbsolutePath())
                                                              .timeoutInMins(5)
                                                              .build();

    appAutoscalarRequestData.setExpectedEnabled(false);

    String appName = "app";
    ApplicationSummary previousRelease = ApplicationSummary.builder()
                                             .id(inactiveApplicationGuid)
                                             .diskQuota(1)
                                             .instances(0)
                                             .memoryLimit(1)
                                             .name(appName)
                                             .requestedState(STOPPED)
                                             .runningInstances(0)
                                             .build();
    List<ApplicationSummary> previousReleases = Arrays.asList(previousRelease);
    doReturn(previousReleases)
        .when(cfDeploymentManager)
        .getPreviousReleases(cfRequestConfig, cfRouteUpdateConfigData.getCfAppNamePrefix());

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
        .resizeApplication(any());

    CfCommandResponseNG cfCommandExecutionResponse = pcfRollbackCommandTaskHandler.executeTaskInternal(
        cfRollbackCommandRequestNG, logStreamingTaskClient, commandUnitsProgress);

    assertThat(cfCommandExecutionResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    // Test Exception flow
    doThrow(new IOException("")).when(pcfCommandTaskHelper).generateWorkingDirectoryForDeployment();
    cfCommandExecutionResponse = pcfRollbackCommandTaskHandler.executeTaskInternal(
        cfRollbackCommandRequestNG, logStreamingTaskClient, commandUnitsProgress);
    assertThat(cfCommandExecutionResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.FAILURE);
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void executeTaskInternalTestWhenSwapRouteOccurredButActiveApplicationDetailsAreNull()
      throws PivotalClientApiException, IOException {
    TasInfraConfig tasInfraConfig = TasInfraConfig.builder().build();
    char[] password = {'a'};
    char[] username = {'b'};
    CloudFoundryConfig cfConfig = CloudFoundryConfig.builder().userName(username).password(password).build();
    CommandUnitsProgress commandUnitsProgress = CommandUnitsProgress.builder().build();

    File file = new File("/gs");
    doReturn(file).when(pcfCommandTaskHelper).generateWorkingDirectoryForDeployment();

    doReturn(executionLogCallback)
        .when(tasTaskHelperBase)
        .getLogCallback(logStreamingTaskClient, CfCommandUnitConstants.SwapRollback, true, commandUnitsProgress);

    doReturn(executionLogCallback)
        .when(tasTaskHelperBase)
        .getLogCallback(logStreamingTaskClient, CfCommandUnitConstants.Upsize, true, commandUnitsProgress);

    doReturn(executionLogCallback)
        .when(tasTaskHelperBase)
        .getLogCallback(logStreamingTaskClient, CfCommandUnitConstants.Downsize, true, commandUnitsProgress);

    doReturn(executionLogCallback)
        .when(tasTaskHelperBase)
        .getLogCallback(logStreamingTaskClient, CfCommandUnitConstants.Wrapup, true, commandUnitsProgress);

    doReturn(cfConfig)
        .when(tasNgConfigMapper)
        .mapTasConfigWithDecryption(tasInfraConfig.getTasConnectorDTO(), tasInfraConfig.getEncryptionDataDetails());

    String inactiveApplicationGuid = "id";
    String inactiveRoute = "route";
    int activeInstanceCount = 1;
    int inactiveInstanceCount = 1;
    TasApplicationInfo activeApplicationDetails =
        TasApplicationInfo.builder().applicationName("a_s_e__5").runningCount(activeInstanceCount).build();
    TasApplicationInfo newApplicationDetails =
        TasApplicationInfo.builder().applicationName("a_s_e__6").attachedRoutes(emptyList()).build();
    TasApplicationInfo inactiveApplicationDetails = TasApplicationInfo.builder()
                                                        .applicationName("a_s_e__3")
                                                        .runningCount(inactiveInstanceCount)
                                                        .applicationGuid(inactiveApplicationGuid)
                                                        .attachedRoutes(Arrays.asList(inactiveRoute))
                                                        .build();

    CfSwapRollbackCommandRequestNG cfRollbackCommandRequestNG =
        CfSwapRollbackCommandRequestNG.builder()
            .tasInfraConfig(tasInfraConfig)
            .cfCommandTypeNG(CfCommandTypeNG.SWAP_ROLLBACK)
            .accountId(ACCOUNT_ID)
            .swapRouteOccurred(true)
            .instanceData(
                Arrays.asList(CfServiceData.builder().name("a_s_e__6").previousCount(2).desiredCount(0).build(),
                    CfServiceData.builder().name("a_s_e__4").previousCount(0).desiredCount(2).build()))
            .timeoutIntervalInMin(5)
            .newApplicationDetails(newApplicationDetails)
            .inActiveApplicationDetails(inactiveApplicationDetails)
            .activeApplicationDetails(activeApplicationDetails)
            .downsizeOldApplication(true)
            .upsizeInActiveApp(true)
            .useAppAutoScalar(true)
            .build();

    String cfCliPath = "cfCliPath";
    doReturn(cfCliPath)
        .when(pcfCommandTaskHelper)
        .getCfCliPathOnDelegate(true, cfRollbackCommandRequestNG.getCfCliVersion());

    CfRequestConfig cfRequestConfig =
        CfRequestConfig.builder()
            .userName(String.valueOf(cfConfig.getUserName()))
            .endpointUrl(cfConfig.getEndpointUrl())
            .password(String.valueOf(cfConfig.getPassword()))
            .orgName(tasInfraConfig.getOrganization())
            .spaceName(tasInfraConfig.getSpace())
            .timeOutIntervalInMins(cfRollbackCommandRequestNG.getTimeoutIntervalInMin())
            .cfHomeDirPath(file.getAbsolutePath())
            .cfCliPath(pcfCommandTaskHelper.getCfCliPathOnDelegate(true, cfRollbackCommandRequestNG.getCfCliVersion()))
            .cfCliVersion(cfRollbackCommandRequestNG.getCfCliVersion())
            .useCFCLI(true)
            .build();

    CfRouteUpdateRequestConfigData cfRouteUpdateConfigData =
        CfRouteUpdateRequestConfigData.builder()
            .isRollback(true)
            .existingApplicationDetails(activeApplicationDetails != null
                    ? Collections.singletonList(activeApplicationDetails.toCfAppSetupTimeDetails())
                    : null)
            .cfAppNamePrefix(cfRollbackCommandRequestNG.getCfAppNamePrefix())
            .downsizeOldApplication(cfRollbackCommandRequestNG.isDownsizeOldApplication())
            .existingApplicationNames(Arrays.asList("a_s_e__5"))
            .existingInActiveApplicationDetails(cfRollbackCommandRequestNG.getInActiveApplicationDetails() != null
                    ? cfRollbackCommandRequestNG.getInActiveApplicationDetails().toCfAppSetupTimeDetails()
                    : null)
            .tempRoutes(cfRollbackCommandRequestNG.getTempRoutes())
            .skipRollback(false)
            .isStandardBlueGreen(true)
            .newApplicationDetails(cfRollbackCommandRequestNG.getNewApplicationDetails().toCfAppSetupTimeDetails())
            .upSizeInActiveApp(cfRollbackCommandRequestNG.isUpsizeInActiveApp())
            .versioningChanged(false)
            .nonVersioning(true)
            .newApplicationName(cfRollbackCommandRequestNG.getNewApplicationDetails().getApplicationName())
            .finalRoutes(cfRollbackCommandRequestNG.getRouteMaps())
            .isMapRoutesOperation(false)
            .build();

    CfInBuiltVariablesUpdateValues cfInBuiltVariablesUpdateValues = CfInBuiltVariablesUpdateValues.builder().build();
    doReturn(cfInBuiltVariablesUpdateValues)
        .when(pcfCommandTaskHelper)
        .performAppRenaming(ROLLBACK_OPERATOR, cfRouteUpdateConfigData, cfRequestConfig, executionLogCallback);

    String newAppName = "app";
    List<String> newApps = Arrays.asList(newAppName);
    doReturn(newApps)
        .when(pcfCommandTaskHelper)
        .getAppNameBasedOnGuidForBlueGreenDeployment(
            cfRequestConfig, cfRouteUpdateConfigData.getCfAppNamePrefix(), newApplicationDetails.getApplicationGuid());

    ApplicationDetail applicationDetail = ApplicationDetail.builder()
                                              .id("Guid:a_s_e__6")
                                              .diskQuota(1)
                                              .instances(0)
                                              .memoryLimit(1)
                                              .name("a_s_e__")
                                              .requestedState(STOPPED)
                                              .stack("")
                                              .runningInstances(0)
                                              .build();
    doReturn(applicationDetail).when(cfDeploymentManager).getApplicationByName(any());
    CfAppAutoscalarRequestData appAutoscalarRequestData = CfAppAutoscalarRequestData.builder()
                                                              .applicationGuid(applicationDetail.getId())
                                                              .applicationName(applicationDetail.getName())
                                                              .cfRequestConfig(cfRequestConfig)
                                                              .configPathVar(file.getAbsolutePath())
                                                              .timeoutInMins(5)
                                                              .build();

    appAutoscalarRequestData.setExpectedEnabled(false);

    String appName = "app";
    ApplicationSummary previousRelease = ApplicationSummary.builder()
                                             .id(inactiveApplicationGuid)
                                             .diskQuota(1)
                                             .instances(0)
                                             .memoryLimit(1)
                                             .name(appName)
                                             .requestedState(STOPPED)
                                             .runningInstances(0)
                                             .build();
    List<ApplicationSummary> previousReleases = Arrays.asList(previousRelease);
    doReturn(previousReleases)
        .when(cfDeploymentManager)
        .getPreviousReleases(cfRequestConfig, cfRouteUpdateConfigData.getCfAppNamePrefix());

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
        .resizeApplication(any());

    CfCommandResponseNG cfCommandExecutionResponse = pcfRollbackCommandTaskHandler.executeTaskInternal(
        cfRollbackCommandRequestNG, logStreamingTaskClient, commandUnitsProgress);

    assertThat(cfCommandExecutionResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    // Test Exception flow
    doThrow(new IOException("")).when(pcfCommandTaskHelper).generateWorkingDirectoryForDeployment();
    cfCommandExecutionResponse = pcfRollbackCommandTaskHandler.executeTaskInternal(
        cfRollbackCommandRequestNG, logStreamingTaskClient, commandUnitsProgress);
    assertThat(cfCommandExecutionResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.FAILURE);
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void executeTaskInternalTestWithAutoscalerEnabledForActiveApp() throws PivotalClientApiException, IOException {
    TasInfraConfig tasInfraConfig = TasInfraConfig.builder().build();
    char[] password = {'a'};
    char[] username = {'b'};
    CloudFoundryConfig cfConfig = CloudFoundryConfig.builder().userName(username).password(password).build();
    CommandUnitsProgress commandUnitsProgress = CommandUnitsProgress.builder().build();

    File file = new File("/gs");
    doReturn(file).when(pcfCommandTaskHelper).generateWorkingDirectoryForDeployment();

    doReturn(executionLogCallback)
        .when(tasTaskHelperBase)
        .getLogCallback(logStreamingTaskClient, CfCommandUnitConstants.SwapRollback, true, commandUnitsProgress);

    doReturn(executionLogCallback)
        .when(tasTaskHelperBase)
        .getLogCallback(logStreamingTaskClient, CfCommandUnitConstants.Upsize, true, commandUnitsProgress);

    doReturn(executionLogCallback)
        .when(tasTaskHelperBase)
        .getLogCallback(logStreamingTaskClient, CfCommandUnitConstants.Downsize, true, commandUnitsProgress);

    doReturn(executionLogCallback)
        .when(tasTaskHelperBase)
        .getLogCallback(logStreamingTaskClient, CfCommandUnitConstants.Wrapup, true, commandUnitsProgress);

    doReturn(cfConfig)
        .when(tasNgConfigMapper)
        .mapTasConfigWithDecryption(tasInfraConfig.getTasConnectorDTO(), tasInfraConfig.getEncryptionDataDetails());

    String inactiveApplicationGuid = "id";
    String inactiveRoute = "route";
    int activeInstanceCount = 1;
    int inactiveInstanceCount = 1;
    TasApplicationInfo activeApplicationDetails = TasApplicationInfo.builder()
                                                      .applicationName("a_s_e__5")
                                                      .runningCount(activeInstanceCount)
                                                      .isAutoScalarEnabled(true)
                                                      .build();
    TasApplicationInfo newApplicationDetails =
        TasApplicationInfo.builder().applicationName("a_s_e__6").attachedRoutes(emptyList()).build();
    TasApplicationInfo inactiveApplicationDetails = TasApplicationInfo.builder()
                                                        .applicationName("a_s_e__3")
                                                        .runningCount(inactiveInstanceCount)
                                                        .applicationGuid(inactiveApplicationGuid)
                                                        .attachedRoutes(Arrays.asList(inactiveRoute))
                                                        .build();

    CfSwapRollbackCommandRequestNG cfRollbackCommandRequestNG =
        CfSwapRollbackCommandRequestNG.builder()
            .tasInfraConfig(tasInfraConfig)
            .cfCommandTypeNG(CfCommandTypeNG.SWAP_ROLLBACK)
            .accountId(ACCOUNT_ID)
            .swapRouteOccurred(true)
            .instanceData(
                Arrays.asList(CfServiceData.builder().name("a_s_e__6").previousCount(2).desiredCount(0).build(),
                    CfServiceData.builder().name("a_s_e__4").previousCount(0).desiredCount(2).build()))
            .timeoutIntervalInMin(5)
            .newApplicationDetails(newApplicationDetails)
            .inActiveApplicationDetails(inactiveApplicationDetails)
            .activeApplicationDetails(activeApplicationDetails)
            .downsizeOldApplication(true)
            .upsizeInActiveApp(true)
            .useAppAutoScalar(true)
            .build();

    String cfCliPath = "cfCliPath";
    doReturn(cfCliPath)
        .when(pcfCommandTaskHelper)
        .getCfCliPathOnDelegate(true, cfRollbackCommandRequestNG.getCfCliVersion());

    CfRequestConfig cfRequestConfig =
        CfRequestConfig.builder()
            .userName(String.valueOf(cfConfig.getUserName()))
            .endpointUrl(cfConfig.getEndpointUrl())
            .password(String.valueOf(cfConfig.getPassword()))
            .orgName(tasInfraConfig.getOrganization())
            .spaceName(tasInfraConfig.getSpace())
            .timeOutIntervalInMins(cfRollbackCommandRequestNG.getTimeoutIntervalInMin())
            .cfHomeDirPath(file.getAbsolutePath())
            .cfCliPath(pcfCommandTaskHelper.getCfCliPathOnDelegate(true, cfRollbackCommandRequestNG.getCfCliVersion()))
            .cfCliVersion(cfRollbackCommandRequestNG.getCfCliVersion())
            .useCFCLI(true)
            .build();

    CfRouteUpdateRequestConfigData cfRouteUpdateConfigData =
        CfRouteUpdateRequestConfigData.builder()
            .isRollback(true)
            .existingApplicationDetails(activeApplicationDetails != null
                    ? Collections.singletonList(activeApplicationDetails.toCfAppSetupTimeDetails())
                    : null)
            .cfAppNamePrefix(cfRollbackCommandRequestNG.getCfAppNamePrefix())
            .downsizeOldApplication(cfRollbackCommandRequestNG.isDownsizeOldApplication())
            .existingApplicationNames(Arrays.asList("a_s_e__5"))
            .existingInActiveApplicationDetails(cfRollbackCommandRequestNG.getInActiveApplicationDetails() != null
                    ? cfRollbackCommandRequestNG.getInActiveApplicationDetails().toCfAppSetupTimeDetails()
                    : null)
            .tempRoutes(cfRollbackCommandRequestNG.getTempRoutes())
            .skipRollback(false)
            .isStandardBlueGreen(true)
            .newApplicationDetails(cfRollbackCommandRequestNG.getNewApplicationDetails().toCfAppSetupTimeDetails())
            .upSizeInActiveApp(cfRollbackCommandRequestNG.isUpsizeInActiveApp())
            .versioningChanged(false)
            .nonVersioning(true)
            .newApplicationName(cfRollbackCommandRequestNG.getNewApplicationDetails().getApplicationName())
            .finalRoutes(cfRollbackCommandRequestNG.getRouteMaps())
            .isMapRoutesOperation(false)
            .build();

    CfInBuiltVariablesUpdateValues cfInBuiltVariablesUpdateValues = CfInBuiltVariablesUpdateValues.builder().build();
    doReturn(cfInBuiltVariablesUpdateValues)
        .when(pcfCommandTaskHelper)
        .performAppRenaming(ROLLBACK_OPERATOR, cfRouteUpdateConfigData, cfRequestConfig, executionLogCallback);

    String newAppName = "app";
    List<String> newApps = Arrays.asList(newAppName);
    doReturn(newApps)
        .when(pcfCommandTaskHelper)
        .getAppNameBasedOnGuidForBlueGreenDeployment(
            cfRequestConfig, cfRouteUpdateConfigData.getCfAppNamePrefix(), newApplicationDetails.getApplicationGuid());

    ApplicationDetail applicationDetail = ApplicationDetail.builder()
                                              .id("Guid:a_s_e__6")
                                              .diskQuota(1)
                                              .instances(0)
                                              .memoryLimit(1)
                                              .name("a_s_e__")
                                              .requestedState(STOPPED)
                                              .stack("")
                                              .runningInstances(0)
                                              .build();
    doReturn(applicationDetail).when(cfDeploymentManager).getApplicationByName(any());
    CfAppAutoscalarRequestData appAutoscalarRequestData = CfAppAutoscalarRequestData.builder()
                                                              .applicationGuid(applicationDetail.getId())
                                                              .applicationName(applicationDetail.getName())
                                                              .cfRequestConfig(cfRequestConfig)
                                                              .configPathVar(file.getAbsolutePath())
                                                              .timeoutInMins(5)
                                                              .build();

    appAutoscalarRequestData.setExpectedEnabled(false);

    String appName = "app";
    ApplicationSummary previousRelease = ApplicationSummary.builder()
                                             .id(inactiveApplicationGuid)
                                             .diskQuota(1)
                                             .instances(0)
                                             .memoryLimit(1)
                                             .name(appName)
                                             .requestedState(STOPPED)
                                             .runningInstances(0)
                                             .build();
    List<ApplicationSummary> previousReleases = Arrays.asList(previousRelease);
    doReturn(previousReleases)
        .when(cfDeploymentManager)
        .getPreviousReleases(cfRequestConfig, cfRouteUpdateConfigData.getCfAppNamePrefix());

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
        .resizeApplication(any());

    CfCommandResponseNG cfCommandExecutionResponse = pcfRollbackCommandTaskHandler.executeTaskInternal(
        cfRollbackCommandRequestNG, logStreamingTaskClient, commandUnitsProgress);

    assertThat(cfCommandExecutionResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    // Test Exception flow
    doThrow(new IOException("")).when(pcfCommandTaskHelper).generateWorkingDirectoryForDeployment();
    cfCommandExecutionResponse = pcfRollbackCommandTaskHandler.executeTaskInternal(
        cfRollbackCommandRequestNG, logStreamingTaskClient, commandUnitsProgress);
    assertThat(cfCommandExecutionResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.FAILURE);
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void executeTaskInternalTestWithAutoscalerEnabledForInactiveApp()
      throws PivotalClientApiException, IOException {
    TasInfraConfig tasInfraConfig = TasInfraConfig.builder().build();
    char[] password = {'a'};
    char[] username = {'b'};
    CloudFoundryConfig cfConfig = CloudFoundryConfig.builder().userName(username).password(password).build();
    CommandUnitsProgress commandUnitsProgress = CommandUnitsProgress.builder().build();

    File file = new File("/gs");
    doReturn(file).when(pcfCommandTaskHelper).generateWorkingDirectoryForDeployment();

    doReturn(executionLogCallback)
        .when(tasTaskHelperBase)
        .getLogCallback(logStreamingTaskClient, CfCommandUnitConstants.SwapRollback, true, commandUnitsProgress);

    doReturn(executionLogCallback)
        .when(tasTaskHelperBase)
        .getLogCallback(logStreamingTaskClient, CfCommandUnitConstants.Upsize, true, commandUnitsProgress);

    doReturn(executionLogCallback)
        .when(tasTaskHelperBase)
        .getLogCallback(logStreamingTaskClient, CfCommandUnitConstants.Downsize, true, commandUnitsProgress);

    doReturn(executionLogCallback)
        .when(tasTaskHelperBase)
        .getLogCallback(logStreamingTaskClient, CfCommandUnitConstants.Wrapup, true, commandUnitsProgress);

    doReturn(cfConfig)
        .when(tasNgConfigMapper)
        .mapTasConfigWithDecryption(tasInfraConfig.getTasConnectorDTO(), tasInfraConfig.getEncryptionDataDetails());

    String inactiveApplicationGuid = "id";
    String inactiveRoute = "route";
    int activeInstanceCount = 1;
    int inactiveInstanceCount = 1;
    TasApplicationInfo activeApplicationDetails = TasApplicationInfo.builder()
                                                      .applicationName("a_s_e__5")
                                                      .runningCount(activeInstanceCount)
                                                      .isAutoScalarEnabled(true)
                                                      .build();
    TasApplicationInfo newApplicationDetails =
        TasApplicationInfo.builder().applicationName("a_s_e__6").attachedRoutes(emptyList()).build();
    TasApplicationInfo inactiveApplicationDetails = TasApplicationInfo.builder()
                                                        .applicationName("a_s_e__3")
                                                        .runningCount(inactiveInstanceCount)
                                                        .applicationGuid(inactiveApplicationGuid)
                                                        .attachedRoutes(Arrays.asList(inactiveRoute))
                                                        .isAutoScalarEnabled(true)
                                                        .build();

    CfSwapRollbackCommandRequestNG cfRollbackCommandRequestNG =
        CfSwapRollbackCommandRequestNG.builder()
            .tasInfraConfig(tasInfraConfig)
            .cfCommandTypeNG(CfCommandTypeNG.SWAP_ROLLBACK)
            .accountId(ACCOUNT_ID)
            .swapRouteOccurred(true)
            .instanceData(
                Arrays.asList(CfServiceData.builder().name("a_s_e__6").previousCount(2).desiredCount(0).build(),
                    CfServiceData.builder().name("a_s_e__4").previousCount(0).desiredCount(2).build()))
            .timeoutIntervalInMin(5)
            .newApplicationDetails(newApplicationDetails)
            .inActiveApplicationDetails(inactiveApplicationDetails)
            .activeApplicationDetails(activeApplicationDetails)
            .downsizeOldApplication(true)
            .upsizeInActiveApp(true)
            .useAppAutoScalar(true)
            .build();

    String cfCliPath = "cfCliPath";
    doReturn(cfCliPath)
        .when(pcfCommandTaskHelper)
        .getCfCliPathOnDelegate(true, cfRollbackCommandRequestNG.getCfCliVersion());

    CfRequestConfig cfRequestConfig =
        CfRequestConfig.builder()
            .userName(String.valueOf(cfConfig.getUserName()))
            .endpointUrl(cfConfig.getEndpointUrl())
            .password(String.valueOf(cfConfig.getPassword()))
            .orgName(tasInfraConfig.getOrganization())
            .spaceName(tasInfraConfig.getSpace())
            .timeOutIntervalInMins(cfRollbackCommandRequestNG.getTimeoutIntervalInMin())
            .cfHomeDirPath(file.getAbsolutePath())
            .cfCliPath(pcfCommandTaskHelper.getCfCliPathOnDelegate(true, cfRollbackCommandRequestNG.getCfCliVersion()))
            .cfCliVersion(cfRollbackCommandRequestNG.getCfCliVersion())
            .useCFCLI(true)
            .build();

    CfRouteUpdateRequestConfigData cfRouteUpdateConfigData =
        CfRouteUpdateRequestConfigData.builder()
            .isRollback(true)
            .existingApplicationDetails(activeApplicationDetails != null
                    ? Collections.singletonList(activeApplicationDetails.toCfAppSetupTimeDetails())
                    : null)
            .cfAppNamePrefix(cfRollbackCommandRequestNG.getCfAppNamePrefix())
            .downsizeOldApplication(cfRollbackCommandRequestNG.isDownsizeOldApplication())
            .existingApplicationNames(Arrays.asList("a_s_e__5"))
            .existingInActiveApplicationDetails(cfRollbackCommandRequestNG.getInActiveApplicationDetails() != null
                    ? cfRollbackCommandRequestNG.getInActiveApplicationDetails().toCfAppSetupTimeDetails()
                    : null)
            .tempRoutes(cfRollbackCommandRequestNG.getTempRoutes())
            .skipRollback(false)
            .isStandardBlueGreen(true)
            .newApplicationDetails(cfRollbackCommandRequestNG.getNewApplicationDetails().toCfAppSetupTimeDetails())
            .upSizeInActiveApp(cfRollbackCommandRequestNG.isUpsizeInActiveApp())
            .versioningChanged(false)
            .nonVersioning(true)
            .newApplicationName(cfRollbackCommandRequestNG.getNewApplicationDetails().getApplicationName())
            .finalRoutes(cfRollbackCommandRequestNG.getRouteMaps())
            .isMapRoutesOperation(false)
            .build();

    CfInBuiltVariablesUpdateValues cfInBuiltVariablesUpdateValues = CfInBuiltVariablesUpdateValues.builder().build();
    doReturn(cfInBuiltVariablesUpdateValues)
        .when(pcfCommandTaskHelper)
        .performAppRenaming(ROLLBACK_OPERATOR, cfRouteUpdateConfigData, cfRequestConfig, executionLogCallback);

    String newAppName = "app";
    List<String> newApps = Arrays.asList(newAppName);
    doReturn(newApps)
        .when(pcfCommandTaskHelper)
        .getAppNameBasedOnGuidForBlueGreenDeployment(
            cfRequestConfig, cfRouteUpdateConfigData.getCfAppNamePrefix(), newApplicationDetails.getApplicationGuid());

    ApplicationDetail applicationDetail = ApplicationDetail.builder()
                                              .id("Guid:a_s_e__6")
                                              .diskQuota(1)
                                              .instances(0)
                                              .memoryLimit(1)
                                              .name("a_s_e__")
                                              .requestedState(STOPPED)
                                              .stack("")
                                              .runningInstances(0)
                                              .build();
    doReturn(applicationDetail).when(cfDeploymentManager).getApplicationByName(any());
    CfAppAutoscalarRequestData appAutoscalarRequestData = CfAppAutoscalarRequestData.builder()
                                                              .applicationGuid(applicationDetail.getId())
                                                              .applicationName(applicationDetail.getName())
                                                              .cfRequestConfig(cfRequestConfig)
                                                              .configPathVar(file.getAbsolutePath())
                                                              .timeoutInMins(5)
                                                              .build();

    appAutoscalarRequestData.setExpectedEnabled(false);

    String appName = "app";
    ApplicationSummary previousRelease = ApplicationSummary.builder()
                                             .id(inactiveApplicationGuid)
                                             .diskQuota(1)
                                             .instances(0)
                                             .memoryLimit(1)
                                             .name(appName)
                                             .requestedState(STOPPED)
                                             .runningInstances(0)
                                             .build();
    List<ApplicationSummary> previousReleases = Arrays.asList(previousRelease);
    doReturn(previousReleases)
        .when(cfDeploymentManager)
        .getPreviousReleases(cfRequestConfig, cfRouteUpdateConfigData.getCfAppNamePrefix());

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
        .resizeApplication(any());

    CfCommandResponseNG cfCommandExecutionResponse = pcfRollbackCommandTaskHandler.executeTaskInternal(
        cfRollbackCommandRequestNG, logStreamingTaskClient, commandUnitsProgress);

    assertThat(cfCommandExecutionResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    // Test Exception flow
    doThrow(new IOException("")).when(pcfCommandTaskHelper).generateWorkingDirectoryForDeployment();
    cfCommandExecutionResponse = pcfRollbackCommandTaskHandler.executeTaskInternal(
        cfRollbackCommandRequestNG, logStreamingTaskClient, commandUnitsProgress);
    assertThat(cfCommandExecutionResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.FAILURE);
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void executeTaskInternalTestWithAutoscalerEnabledWhenExceptionOccurred()
      throws PivotalClientApiException, IOException {
    TasInfraConfig tasInfraConfig = TasInfraConfig.builder().build();
    char[] password = {'a'};
    char[] username = {'b'};
    CloudFoundryConfig cfConfig = CloudFoundryConfig.builder().userName(username).password(password).build();
    CommandUnitsProgress commandUnitsProgress = CommandUnitsProgress.builder().build();

    File file = new File("/gs");
    doReturn(file).when(pcfCommandTaskHelper).generateWorkingDirectoryForDeployment();

    doReturn(executionLogCallback)
        .when(tasTaskHelperBase)
        .getLogCallback(logStreamingTaskClient, CfCommandUnitConstants.SwapRollback, true, commandUnitsProgress);

    doReturn(executionLogCallback)
        .when(tasTaskHelperBase)
        .getLogCallback(logStreamingTaskClient, CfCommandUnitConstants.Upsize, true, commandUnitsProgress);

    doReturn(executionLogCallback)
        .when(tasTaskHelperBase)
        .getLogCallback(logStreamingTaskClient, CfCommandUnitConstants.Downsize, true, commandUnitsProgress);

    doReturn(executionLogCallback)
        .when(tasTaskHelperBase)
        .getLogCallback(logStreamingTaskClient, CfCommandUnitConstants.Wrapup, true, commandUnitsProgress);

    doReturn(cfConfig)
        .when(tasNgConfigMapper)
        .mapTasConfigWithDecryption(tasInfraConfig.getTasConnectorDTO(), tasInfraConfig.getEncryptionDataDetails());

    String inactiveApplicationGuid = "id";
    String inactiveRoute = "route";
    int activeInstanceCount = 1;
    int inactiveInstanceCount = 1;
    TasApplicationInfo activeApplicationDetails = TasApplicationInfo.builder()
                                                      .applicationName("a_s_e__5")
                                                      .runningCount(activeInstanceCount)
                                                      .isAutoScalarEnabled(true)
                                                      .build();
    TasApplicationInfo newApplicationDetails =
        TasApplicationInfo.builder().applicationName("a_s_e__6").attachedRoutes(emptyList()).build();
    TasApplicationInfo inactiveApplicationDetails = TasApplicationInfo.builder()
                                                        .applicationName("a_s_e__3")
                                                        .runningCount(inactiveInstanceCount)
                                                        .applicationGuid(inactiveApplicationGuid)
                                                        .attachedRoutes(Arrays.asList(inactiveRoute))
                                                        .isAutoScalarEnabled(true)
                                                        .build();

    CfSwapRollbackCommandRequestNG cfRollbackCommandRequestNG =
        CfSwapRollbackCommandRequestNG.builder()
            .tasInfraConfig(tasInfraConfig)
            .cfCommandTypeNG(CfCommandTypeNG.SWAP_ROLLBACK)
            .accountId(ACCOUNT_ID)
            .swapRouteOccurred(true)
            .instanceData(
                Arrays.asList(CfServiceData.builder().name("a_s_e__6").previousCount(2).desiredCount(0).build(),
                    CfServiceData.builder().name("a_s_e__4").previousCount(0).desiredCount(2).build()))
            .timeoutIntervalInMin(5)
            .newApplicationDetails(newApplicationDetails)
            .inActiveApplicationDetails(inactiveApplicationDetails)
            .activeApplicationDetails(activeApplicationDetails)
            .downsizeOldApplication(true)
            .upsizeInActiveApp(true)
            .useAppAutoScalar(true)
            .build();

    String cfCliPath = "cfCliPath";
    doReturn(cfCliPath)
        .when(pcfCommandTaskHelper)
        .getCfCliPathOnDelegate(true, cfRollbackCommandRequestNG.getCfCliVersion());

    CfRequestConfig cfRequestConfig =
        CfRequestConfig.builder()
            .userName(String.valueOf(cfConfig.getUserName()))
            .endpointUrl(cfConfig.getEndpointUrl())
            .password(String.valueOf(cfConfig.getPassword()))
            .orgName(tasInfraConfig.getOrganization())
            .spaceName(tasInfraConfig.getSpace())
            .timeOutIntervalInMins(cfRollbackCommandRequestNG.getTimeoutIntervalInMin())
            .cfHomeDirPath(file.getAbsolutePath())
            .cfCliPath(pcfCommandTaskHelper.getCfCliPathOnDelegate(true, cfRollbackCommandRequestNG.getCfCliVersion()))
            .cfCliVersion(cfRollbackCommandRequestNG.getCfCliVersion())
            .useCFCLI(true)
            .build();

    CfRouteUpdateRequestConfigData cfRouteUpdateConfigData =
        CfRouteUpdateRequestConfigData.builder()
            .isRollback(true)
            .existingApplicationDetails(activeApplicationDetails != null
                    ? Collections.singletonList(activeApplicationDetails.toCfAppSetupTimeDetails())
                    : null)
            .cfAppNamePrefix(cfRollbackCommandRequestNG.getCfAppNamePrefix())
            .downsizeOldApplication(cfRollbackCommandRequestNG.isDownsizeOldApplication())
            .existingApplicationNames(Arrays.asList("a_s_e__5"))
            .existingInActiveApplicationDetails(cfRollbackCommandRequestNG.getInActiveApplicationDetails() != null
                    ? cfRollbackCommandRequestNG.getInActiveApplicationDetails().toCfAppSetupTimeDetails()
                    : null)
            .tempRoutes(cfRollbackCommandRequestNG.getTempRoutes())
            .skipRollback(false)
            .isStandardBlueGreen(true)
            .newApplicationDetails(cfRollbackCommandRequestNG.getNewApplicationDetails().toCfAppSetupTimeDetails())
            .upSizeInActiveApp(cfRollbackCommandRequestNG.isUpsizeInActiveApp())
            .versioningChanged(false)
            .nonVersioning(true)
            .newApplicationName(cfRollbackCommandRequestNG.getNewApplicationDetails().getApplicationName())
            .finalRoutes(cfRollbackCommandRequestNG.getRouteMaps())
            .isMapRoutesOperation(false)
            .build();

    CfInBuiltVariablesUpdateValues cfInBuiltVariablesUpdateValues = CfInBuiltVariablesUpdateValues.builder().build();
    doReturn(cfInBuiltVariablesUpdateValues)
        .when(pcfCommandTaskHelper)
        .performAppRenaming(ROLLBACK_OPERATOR, cfRouteUpdateConfigData, cfRequestConfig, executionLogCallback);

    String newAppName = "app";
    List<String> newApps = Arrays.asList(newAppName);
    doReturn(newApps)
        .when(pcfCommandTaskHelper)
        .getAppNameBasedOnGuidForBlueGreenDeployment(
            cfRequestConfig, cfRouteUpdateConfigData.getCfAppNamePrefix(), newApplicationDetails.getApplicationGuid());

    ApplicationDetail applicationDetail = ApplicationDetail.builder()
                                              .id("Guid:a_s_e__6")
                                              .diskQuota(1)
                                              .instances(0)
                                              .memoryLimit(1)
                                              .name("a_s_e__")
                                              .requestedState(STOPPED)
                                              .stack("")
                                              .runningInstances(0)
                                              .build();
    doThrow(new PivotalClientApiException("exception")).when(cfDeploymentManager).getApplicationByName(any());
    CfAppAutoscalarRequestData appAutoscalarRequestData = CfAppAutoscalarRequestData.builder()
                                                              .applicationGuid(applicationDetail.getId())
                                                              .applicationName(applicationDetail.getName())
                                                              .cfRequestConfig(cfRequestConfig)
                                                              .configPathVar(file.getAbsolutePath())
                                                              .timeoutInMins(5)
                                                              .build();

    appAutoscalarRequestData.setExpectedEnabled(false);

    String appName = "app";
    ApplicationSummary previousRelease = ApplicationSummary.builder()
                                             .id(inactiveApplicationGuid)
                                             .diskQuota(1)
                                             .instances(0)
                                             .memoryLimit(1)
                                             .name(appName)
                                             .requestedState(STOPPED)
                                             .runningInstances(0)
                                             .build();
    List<ApplicationSummary> previousReleases = Arrays.asList(previousRelease);
    doReturn(previousReleases)
        .when(cfDeploymentManager)
        .getPreviousReleases(cfRequestConfig, cfRouteUpdateConfigData.getCfAppNamePrefix());

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
        .resizeApplication(any());

    CfCommandResponseNG cfCommandExecutionResponse = pcfRollbackCommandTaskHandler.executeTaskInternal(
        cfRollbackCommandRequestNG, logStreamingTaskClient, commandUnitsProgress);

    assertThat(cfCommandExecutionResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.FAILURE);
  }

  @Test
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void executeTaskInternalTestBeforeSwapRoute() throws PivotalClientApiException, IOException {
    TasInfraConfig tasInfraConfig = TasInfraConfig.builder().build();
    char[] password = {'a'};
    char[] username = {'b'};
    CloudFoundryConfig cfConfig = CloudFoundryConfig.builder().userName(username).password(password).build();
    CommandUnitsProgress commandUnitsProgress = CommandUnitsProgress.builder().build();

    File file = new File("/gs");
    doReturn(file).when(pcfCommandTaskHelper).generateWorkingDirectoryForDeployment();

    doReturn(executionLogCallback)
        .when(tasTaskHelperBase)
        .getLogCallback(logStreamingTaskClient, CfCommandUnitConstants.SwapRollback, true, commandUnitsProgress);

    doReturn(executionLogCallback)
        .when(tasTaskHelperBase)
        .getLogCallback(logStreamingTaskClient, CfCommandUnitConstants.Upsize, true, commandUnitsProgress);

    doReturn(executionLogCallback)
        .when(tasTaskHelperBase)
        .getLogCallback(logStreamingTaskClient, CfCommandUnitConstants.Downsize, true, commandUnitsProgress);

    doReturn(executionLogCallback)
        .when(tasTaskHelperBase)
        .getLogCallback(logStreamingTaskClient, CfCommandUnitConstants.Wrapup, true, commandUnitsProgress);

    doReturn(cfConfig)
        .when(tasNgConfigMapper)
        .mapTasConfigWithDecryption(tasInfraConfig.getTasConnectorDTO(), tasInfraConfig.getEncryptionDataDetails());

    String inactiveApplicationGuid = "id";
    String inactiveRoute = "route";
    int activeInstanceCount = 1;
    int inactiveInstanceCount = 1;
    TasApplicationInfo activeApplicationDetails =
        TasApplicationInfo.builder().applicationName("a_s_e__5").runningCount(activeInstanceCount).build();
    TasApplicationInfo newApplicationDetails =
        TasApplicationInfo.builder().applicationName("a_s_e__6").attachedRoutes(emptyList()).build();
    TasApplicationInfo inactiveApplicationDetails = TasApplicationInfo.builder()
                                                        .applicationName("a_s_e__3")
                                                        .runningCount(inactiveInstanceCount)
                                                        .applicationGuid(inactiveApplicationGuid)
                                                        .attachedRoutes(Arrays.asList(inactiveRoute))
                                                        .build();

    CfSwapRollbackCommandRequestNG cfRollbackCommandRequestNG =
        CfSwapRollbackCommandRequestNG.builder()
            .tasInfraConfig(tasInfraConfig)
            .cfCommandTypeNG(CfCommandTypeNG.SWAP_ROLLBACK)
            .accountId(ACCOUNT_ID)
            .swapRouteOccurred(false)
            .instanceData(
                Arrays.asList(CfServiceData.builder().name("a_s_e__6").previousCount(2).desiredCount(0).build(),
                    CfServiceData.builder().name("a_s_e__4").previousCount(0).desiredCount(2).build()))
            .timeoutIntervalInMin(5)
            .newApplicationDetails(newApplicationDetails)
            .inActiveApplicationDetails(inactiveApplicationDetails)
            .activeApplicationDetails(activeApplicationDetails)
            .downsizeOldApplication(true)
            .upsizeInActiveApp(true)
            .useAppAutoScalar(true)
            .build();

    String cfCliPath = "cfCliPath";
    doReturn(cfCliPath)
        .when(pcfCommandTaskHelper)
        .getCfCliPathOnDelegate(true, cfRollbackCommandRequestNG.getCfCliVersion());

    CfRequestConfig cfRequestConfig =
        CfRequestConfig.builder()
            .userName(String.valueOf(cfConfig.getUserName()))
            .endpointUrl(cfConfig.getEndpointUrl())
            .password(String.valueOf(cfConfig.getPassword()))
            .orgName(tasInfraConfig.getOrganization())
            .spaceName(tasInfraConfig.getSpace())
            .timeOutIntervalInMins(cfRollbackCommandRequestNG.getTimeoutIntervalInMin())
            .cfHomeDirPath(file.getAbsolutePath())
            .cfCliPath(pcfCommandTaskHelper.getCfCliPathOnDelegate(true, cfRollbackCommandRequestNG.getCfCliVersion()))
            .cfCliVersion(cfRollbackCommandRequestNG.getCfCliVersion())
            .useCFCLI(true)
            .build();

    CfRouteUpdateRequestConfigData cfRouteUpdateConfigData =
        CfRouteUpdateRequestConfigData.builder()
            .isRollback(true)
            .existingApplicationDetails(activeApplicationDetails != null
                    ? Collections.singletonList(activeApplicationDetails.toCfAppSetupTimeDetails())
                    : null)
            .cfAppNamePrefix(cfRollbackCommandRequestNG.getCfAppNamePrefix())
            .downsizeOldApplication(cfRollbackCommandRequestNG.isDownsizeOldApplication())
            .existingApplicationNames(Arrays.asList("a_s_e__5"))
            .existingInActiveApplicationDetails(cfRollbackCommandRequestNG.getInActiveApplicationDetails() != null
                    ? cfRollbackCommandRequestNG.getInActiveApplicationDetails().toCfAppSetupTimeDetails()
                    : null)
            .tempRoutes(cfRollbackCommandRequestNG.getTempRoutes())
            .skipRollback(false)
            .isStandardBlueGreen(true)
            .newApplicationDetails(cfRollbackCommandRequestNG.getNewApplicationDetails().toCfAppSetupTimeDetails())
            .upSizeInActiveApp(cfRollbackCommandRequestNG.isUpsizeInActiveApp())
            .versioningChanged(false)
            .nonVersioning(true)
            .newApplicationName(cfRollbackCommandRequestNG.getNewApplicationDetails().getApplicationName())
            .finalRoutes(cfRollbackCommandRequestNG.getRouteMaps())
            .isMapRoutesOperation(false)
            .build();

    CfInBuiltVariablesUpdateValues cfInBuiltVariablesUpdateValues = CfInBuiltVariablesUpdateValues.builder().build();
    doReturn(cfInBuiltVariablesUpdateValues)
        .when(pcfCommandTaskHelper)
        .performAppRenaming(ROLLBACK_OPERATOR, cfRouteUpdateConfigData, cfRequestConfig, executionLogCallback);

    String newAppName = "app";
    List<String> newApps = Arrays.asList(newAppName);
    doReturn(newApps)
        .when(pcfCommandTaskHelper)
        .getAppNameBasedOnGuidForBlueGreenDeployment(
            cfRequestConfig, cfRouteUpdateConfigData.getCfAppNamePrefix(), newApplicationDetails.getApplicationGuid());

    ApplicationDetail applicationDetail = ApplicationDetail.builder()
                                              .id("Guid:a_s_e__6")
                                              .diskQuota(1)
                                              .instances(0)
                                              .memoryLimit(1)
                                              .name("a_s_e__")
                                              .requestedState(STOPPED)
                                              .stack("")
                                              .runningInstances(0)
                                              .build();
    doReturn(applicationDetail).when(cfDeploymentManager).getApplicationByName(any());
    CfAppAutoscalarRequestData appAutoscalarRequestData = CfAppAutoscalarRequestData.builder()
                                                              .applicationGuid(applicationDetail.getId())
                                                              .applicationName(applicationDetail.getName())
                                                              .cfRequestConfig(cfRequestConfig)
                                                              .configPathVar(file.getAbsolutePath())
                                                              .timeoutInMins(5)
                                                              .build();

    appAutoscalarRequestData.setExpectedEnabled(false);

    String appName = "app";
    ApplicationSummary previousRelease = ApplicationSummary.builder()
                                             .id(inactiveApplicationGuid)
                                             .diskQuota(1)
                                             .instances(0)
                                             .memoryLimit(1)
                                             .name(appName)
                                             .requestedState(STOPPED)
                                             .runningInstances(0)
                                             .build();
    List<ApplicationSummary> previousReleases = Arrays.asList(previousRelease);
    doReturn(previousReleases)
        .when(cfDeploymentManager)
        .getPreviousReleases(cfRequestConfig, cfRouteUpdateConfigData.getCfAppNamePrefix());

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
        .resizeApplication(any());

    CfCommandResponseNG cfCommandExecutionResponse = pcfRollbackCommandTaskHandler.executeTaskInternal(
        cfRollbackCommandRequestNG, logStreamingTaskClient, commandUnitsProgress);

    assertThat(cfCommandExecutionResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);

    // Test Exception flow
    doThrow(new IOException("")).when(pcfCommandTaskHelper).generateWorkingDirectoryForDeployment();
    cfCommandExecutionResponse = pcfRollbackCommandTaskHandler.executeTaskInternal(
        cfRollbackCommandRequestNG, logStreamingTaskClient, commandUnitsProgress);
    assertThat(cfCommandExecutionResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.FAILURE);
  }

  @Test(expected = Exception.class)
  @Owner(developers = PIYUSH_BHUWALKA)
  @Category(UnitTests.class)
  public void executeTaskInternalTestWhenNotSwapRollbackRequest() throws PivotalClientApiException, IOException {
    TasInfraConfig tasInfraConfig = TasInfraConfig.builder().build();
    char[] password = {'a'};
    char[] username = {'b'};
    CloudFoundryConfig cfConfig = CloudFoundryConfig.builder().userName(username).password(password).build();
    CommandUnitsProgress commandUnitsProgress = CommandUnitsProgress.builder().build();

    File file = new File("/gs");
    doReturn(file).when(pcfCommandTaskHelper).generateWorkingDirectoryForDeployment();

    doReturn(executionLogCallback)
        .when(tasTaskHelperBase)
        .getLogCallback(logStreamingTaskClient, CfCommandUnitConstants.SwapRollback, true, commandUnitsProgress);

    doReturn(executionLogCallback)
        .when(tasTaskHelperBase)
        .getLogCallback(logStreamingTaskClient, CfCommandUnitConstants.Upsize, true, commandUnitsProgress);

    doReturn(executionLogCallback)
        .when(tasTaskHelperBase)
        .getLogCallback(logStreamingTaskClient, CfCommandUnitConstants.Downsize, true, commandUnitsProgress);

    doReturn(executionLogCallback)
        .when(tasTaskHelperBase)
        .getLogCallback(logStreamingTaskClient, CfCommandUnitConstants.Wrapup, true, commandUnitsProgress);

    doReturn(cfConfig)
        .when(tasNgConfigMapper)
        .mapTasConfigWithDecryption(tasInfraConfig.getTasConnectorDTO(), tasInfraConfig.getEncryptionDataDetails());

    String inactiveApplicationGuid = "id";
    String inactiveRoute = "route";
    int activeInstanceCount = 1;
    int inactiveInstanceCount = 1;
    TasApplicationInfo activeApplicationDetails =
        TasApplicationInfo.builder().applicationName("a_s_e__5").runningCount(activeInstanceCount).build();
    TasApplicationInfo newApplicationDetails =
        TasApplicationInfo.builder().applicationName("a_s_e__6").attachedRoutes(emptyList()).build();
    TasApplicationInfo inactiveApplicationDetails = TasApplicationInfo.builder()
                                                        .applicationName("a_s_e__3")
                                                        .runningCount(inactiveInstanceCount)
                                                        .applicationGuid(inactiveApplicationGuid)
                                                        .attachedRoutes(Arrays.asList(inactiveRoute))
                                                        .build();

    CfSwapRollbackCommandRequestNG cfRollbackCommandRequestNG =
        CfSwapRollbackCommandRequestNG.builder()
            .tasInfraConfig(tasInfraConfig)
            .cfCommandTypeNG(CfCommandTypeNG.SWAP_ROLLBACK)
            .accountId(ACCOUNT_ID)
            .swapRouteOccurred(false)
            .instanceData(
                Arrays.asList(CfServiceData.builder().name("a_s_e__6").previousCount(2).desiredCount(0).build(),
                    CfServiceData.builder().name("a_s_e__4").previousCount(0).desiredCount(2).build()))
            .timeoutIntervalInMin(5)
            .newApplicationDetails(newApplicationDetails)
            .inActiveApplicationDetails(inactiveApplicationDetails)
            .activeApplicationDetails(activeApplicationDetails)
            .downsizeOldApplication(true)
            .upsizeInActiveApp(true)
            .useAppAutoScalar(true)
            .build();

    String cfCliPath = "cfCliPath";
    doReturn(cfCliPath)
        .when(pcfCommandTaskHelper)
        .getCfCliPathOnDelegate(true, cfRollbackCommandRequestNG.getCfCliVersion());

    CfRequestConfig cfRequestConfig =
        CfRequestConfig.builder()
            .userName(String.valueOf(cfConfig.getUserName()))
            .endpointUrl(cfConfig.getEndpointUrl())
            .password(String.valueOf(cfConfig.getPassword()))
            .orgName(tasInfraConfig.getOrganization())
            .spaceName(tasInfraConfig.getSpace())
            .timeOutIntervalInMins(cfRollbackCommandRequestNG.getTimeoutIntervalInMin())
            .cfHomeDirPath(file.getAbsolutePath())
            .cfCliPath(pcfCommandTaskHelper.getCfCliPathOnDelegate(true, cfRollbackCommandRequestNG.getCfCliVersion()))
            .cfCliVersion(cfRollbackCommandRequestNG.getCfCliVersion())
            .useCFCLI(true)
            .build();

    CfRouteUpdateRequestConfigData cfRouteUpdateConfigData =
        CfRouteUpdateRequestConfigData.builder()
            .isRollback(true)
            .existingApplicationDetails(activeApplicationDetails != null
                    ? Collections.singletonList(activeApplicationDetails.toCfAppSetupTimeDetails())
                    : null)
            .cfAppNamePrefix(cfRollbackCommandRequestNG.getCfAppNamePrefix())
            .downsizeOldApplication(cfRollbackCommandRequestNG.isDownsizeOldApplication())
            .existingApplicationNames(Arrays.asList("a_s_e__5"))
            .existingInActiveApplicationDetails(cfRollbackCommandRequestNG.getInActiveApplicationDetails() != null
                    ? cfRollbackCommandRequestNG.getInActiveApplicationDetails().toCfAppSetupTimeDetails()
                    : null)
            .tempRoutes(cfRollbackCommandRequestNG.getTempRoutes())
            .skipRollback(false)
            .isStandardBlueGreen(true)
            .newApplicationDetails(cfRollbackCommandRequestNG.getNewApplicationDetails().toCfAppSetupTimeDetails())
            .upSizeInActiveApp(cfRollbackCommandRequestNG.isUpsizeInActiveApp())
            .versioningChanged(false)
            .nonVersioning(true)
            .newApplicationName(cfRollbackCommandRequestNG.getNewApplicationDetails().getApplicationName())
            .finalRoutes(cfRollbackCommandRequestNG.getRouteMaps())
            .isMapRoutesOperation(false)
            .build();

    CfInBuiltVariablesUpdateValues cfInBuiltVariablesUpdateValues = CfInBuiltVariablesUpdateValues.builder().build();
    doReturn(cfInBuiltVariablesUpdateValues)
        .when(pcfCommandTaskHelper)
        .performAppRenaming(ROLLBACK_OPERATOR, cfRouteUpdateConfigData, cfRequestConfig, executionLogCallback);

    String newAppName = "app";
    List<String> newApps = Arrays.asList(newAppName);
    doReturn(newApps)
        .when(pcfCommandTaskHelper)
        .getAppNameBasedOnGuidForBlueGreenDeployment(
            cfRequestConfig, cfRouteUpdateConfigData.getCfAppNamePrefix(), newApplicationDetails.getApplicationGuid());

    ApplicationDetail applicationDetail = ApplicationDetail.builder()
                                              .id("Guid:a_s_e__6")
                                              .diskQuota(1)
                                              .instances(0)
                                              .memoryLimit(1)
                                              .name("a_s_e__")
                                              .requestedState(STOPPED)
                                              .stack("")
                                              .runningInstances(0)
                                              .build();
    doReturn(applicationDetail).when(cfDeploymentManager).getApplicationByName(any());
    CfAppAutoscalarRequestData appAutoscalarRequestData = CfAppAutoscalarRequestData.builder()
                                                              .applicationGuid(applicationDetail.getId())
                                                              .applicationName(applicationDetail.getName())
                                                              .cfRequestConfig(cfRequestConfig)
                                                              .configPathVar(file.getAbsolutePath())
                                                              .timeoutInMins(5)
                                                              .build();

    appAutoscalarRequestData.setExpectedEnabled(false);

    String appName = "app";
    ApplicationSummary previousRelease = ApplicationSummary.builder()
                                             .id(inactiveApplicationGuid)
                                             .diskQuota(1)
                                             .instances(0)
                                             .memoryLimit(1)
                                             .name(appName)
                                             .requestedState(STOPPED)
                                             .runningInstances(0)
                                             .build();
    List<ApplicationSummary> previousReleases = Arrays.asList(previousRelease);
    doReturn(previousReleases)
        .when(cfDeploymentManager)
        .getPreviousReleases(cfRequestConfig, cfRouteUpdateConfigData.getCfAppNamePrefix());

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
        .resizeApplication(any());

    CfCommandResponseNG cfCommandExecutionResponse = pcfRollbackCommandTaskHandler.executeTaskInternal(
        CfSwapRoutesRequestNG.builder().build(), logStreamingTaskClient, commandUnitsProgress);
  }
}
