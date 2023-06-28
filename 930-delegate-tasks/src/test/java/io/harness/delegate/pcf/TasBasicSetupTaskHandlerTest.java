/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.pcf;

import static io.harness.delegate.cf.CfTestConstants.STOPPED;
import static io.harness.delegate.pcf.TasTestConstants.MANIFEST_YAML;
import static io.harness.delegate.pcf.TasTestConstants.MANIFEST_YAML_PROCESS;
import static io.harness.delegate.pcf.TasTestConstants.VARS_YAML;
import static io.harness.pcf.model.PcfConstants.PCF_ARTIFACT_DOWNLOAD_DIR_PATH;
import static io.harness.rule.OwnerRule.SOURABH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.doThrow;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.connector.task.tas.TasNgConfigMapper;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.beans.logstreaming.NGDelegateLogCallback;
import io.harness.delegate.beans.pcf.artifact.TasArtifactRegistryType;
import io.harness.delegate.cf.PcfCommandTaskBaseHelper;
import io.harness.delegate.task.cf.CfCommandTaskHelperNG;
import io.harness.delegate.task.cf.TasArtifactDownloadResponse;
import io.harness.delegate.task.cf.artifact.TasArtifactCreds;
import io.harness.delegate.task.cf.artifact.TasRegistrySettingsAdapter;
import io.harness.delegate.task.pcf.CfCommandTypeNG;
import io.harness.delegate.task.pcf.TasTaskHelperBase;
import io.harness.delegate.task.pcf.artifact.TasContainerArtifactConfig;
import io.harness.delegate.task.pcf.artifact.TasPackageArtifactConfig;
import io.harness.delegate.task.pcf.request.CfBasicSetupRequestNG;
import io.harness.delegate.task.pcf.request.CfBlueGreenSetupRequestNG;
import io.harness.delegate.task.pcf.request.TasManifestsPackage;
import io.harness.delegate.task.pcf.response.CfBasicSetupResponseNG;
import io.harness.delegate.task.pcf.response.TasInfraConfig;
import io.harness.exception.InvalidArgumentsException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.pcf.CfDeploymentManager;
import io.harness.pcf.PivotalClientApiException;
import io.harness.pcf.model.CfCliVersion;
import io.harness.pcf.model.CfRenameRequest;
import io.harness.pcf.model.CfRequestConfig;
import io.harness.pcf.model.CloudFoundryConfig;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import org.cloudfoundry.operations.applications.ApplicationDetail;
import org.cloudfoundry.operations.applications.ApplicationSummary;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

public class TasBasicSetupTaskHandlerTest extends CategoryTest {
  public static final String ENDPOINT_URL = "endpointUrl";
  public static final String USERNAME = "username";
  public static final String PASSWORD = "password";
  public static final String ORGANIZATION = "organization";
  public static final String SPACE = "space";
  public static final String APP_NAME = "appName";
  public static final String APP_ID = "appName";
  public static final String APP_NAME_OLD = "appName__0";
  public static final String ACCOUNT = "account_Id";
  public static final String APP_NAME_OLD_1 = "appName__1";

  private final CloudFoundryConfig cloudFoundryConfig = CloudFoundryConfig.builder()
                                                            .endpointUrl(ENDPOINT_URL)
                                                            .userName(USERNAME.toCharArray())
                                                            .password(PASSWORD.toCharArray())
                                                            .build();
  private final TasInfraConfig tasInfraConfig =
      TasInfraConfig.builder().organization(ORGANIZATION).space(SPACE).build();

  @Mock TasNgConfigMapper tasNgConfigMapper;
  @Mock TasTaskHelperBase tasTaskHelperBase;
  @Mock ILogStreamingTaskClient logStreamingTaskClient;
  @Mock CfDeploymentManager cfDeploymentManager;
  @Mock TasRegistrySettingsAdapter tasRegistrySettingsAdapter;
  @InjectMocks @Spy PcfCommandTaskBaseHelper pcfCommandTaskBaseHelper;
  @InjectMocks @Spy CfCommandTaskHelperNG cfCommandTaskHelperNG;

  @InjectMocks @Inject private TasBasicSetupTaskHandler tasBasicSetupTaskHandler;
  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    LogCallback logCallback = mock(NGDelegateLogCallback.class);
    doReturn(logCallback).when(tasTaskHelperBase).getLogCallback(any(), any(), anyBoolean(), any());
    doReturn(cloudFoundryConfig).when(tasNgConfigMapper).mapTasConfigWithDecryption(any(), any());
    doReturn("cfCliPath").when(cfCommandTaskHelperNG).getCfCliPathOnDelegate(anyBoolean(), any());
    doReturn(false).when(cfDeploymentManager).checkIfAppHasAutoscalarEnabled(any(), any());
    doReturn(false).when(pcfCommandTaskBaseHelper).disableAutoscalarSafe(any(), any());
    doNothing().when(cfDeploymentManager).unmapRouteMapForApplication(any(), any(), any());
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void testExecuteTaskInternalWithIncorrectRequest() {
    CfBlueGreenSetupRequestNG cfBlueGreenSetupRequestNG =
        CfBlueGreenSetupRequestNG.builder()
            .tasInfraConfig(tasInfraConfig)
            .cfCommandTypeNG(CfCommandTypeNG.APP_RESIZE)
            .cfCliVersion(CfCliVersion.V7)
            .olderActiveVersionCountToKeep(1)
            .releaseNamePrefix(APP_NAME)
            .tasManifestsPackage(
                TasManifestsPackage.builder().manifestYml(MANIFEST_YAML).variableYmls(List.of()).build())
            .tasArtifactConfig(TasContainerArtifactConfig.builder()
                                   .registryType(TasArtifactRegistryType.GITHUB_PACKAGE_REGISTRY)
                                   .build())
            .commandUnitsProgress(CommandUnitsProgress.builder().build())
            .accountId(ACCOUNT)
            .timeoutIntervalInMin(10)
            .useAppAutoScalar(false)
            .build();
    assertThatThrownBy(()
                           -> tasBasicSetupTaskHandler.executeTaskInternal(cfBlueGreenSetupRequestNG,
                               logStreamingTaskClient, CommandUnitsProgress.builder().build()))
        .isInstanceOf(InvalidArgumentsException.class);
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void testExecuteTaskInternalWithFirstDeployment() throws Exception {
    CfBasicSetupRequestNG cfBasicSetupRequestNG =
        CfBasicSetupRequestNG.builder()
            .tasInfraConfig(tasInfraConfig)
            .cfCommandTypeNG(CfCommandTypeNG.APP_RESIZE)
            .cfCliVersion(CfCliVersion.V7)
            .olderActiveVersionCountToKeep(1)
            .releaseNamePrefix(APP_NAME)
            .tasManifestsPackage(
                TasManifestsPackage.builder().manifestYml(MANIFEST_YAML).variableYmls(List.of()).build())
            .tasArtifactConfig(TasContainerArtifactConfig.builder()
                                   .registryType(TasArtifactRegistryType.GITHUB_PACKAGE_REGISTRY)
                                   .build())
            .commandUnitsProgress(CommandUnitsProgress.builder().build())
            .accountId(ACCOUNT)
            .timeoutIntervalInMin(10)
            .useAppAutoScalar(false)
            .build();

    ApplicationDetail applicationDetail = ApplicationDetail.builder()
                                              .id(APP_ID)
                                              .diskQuota(1)
                                              .url("url")
                                              .instances(0)
                                              .memoryLimit(1)
                                              .name(APP_NAME)
                                              .requestedState(STOPPED)
                                              .stack("")
                                              .runningInstances(0)
                                              .build();
    doReturn(false).when(cfDeploymentManager).checkIfAppHasAutoscalarEnabled(any(), any());
    doNothing().when(cfDeploymentManager).renameApplication(any(), any());
    doNothing().when(cfDeploymentManager).deleteApplication(any());
    doReturn(applicationDetail).when(cfDeploymentManager).createApplication(any(), any());
    doReturn(TasArtifactDownloadResponse.builder().build())
        .when(cfCommandTaskHelperNG)
        .downloadPackageArtifact(any(), any());

    doReturn(TasArtifactCreds.builder().username("user").password("pass").build())
        .when(tasRegistrySettingsAdapter)
        .getContainerSettings(any());

    when(cfDeploymentManager.getPreviousReleases(any(), any()))
        .thenReturn(List.of(getApplicationSummary(APP_NAME, APP_ID)));

    CfBasicSetupResponseNG cfBasicSetupResponseNG =
        (CfBasicSetupResponseNG) tasBasicSetupTaskHandler.executeTaskInternal(
            cfBasicSetupRequestNG, logStreamingTaskClient, CommandUnitsProgress.builder().build());

    assertThat(cfBasicSetupResponseNG.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    verify(cfDeploymentManager, times(0)).deleteApplication(any());
    verify(cfDeploymentManager, times(1)).renameApplication(any(), any());
    verify(cfDeploymentManager, times(1)).createApplication(any(), any());

    assertThat(cfBasicSetupResponseNG.getNewApplicationInfo().getApplicationGuid()).isEqualTo(APP_ID);
    assertThat(cfBasicSetupResponseNG.getNewApplicationInfo().getApplicationName()).isEqualTo(APP_NAME);
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void testExecuteTaskInternalWithSecondDeployment() throws Exception {
    CfBasicSetupRequestNG cfBasicSetupRequestNG =
        CfBasicSetupRequestNG.builder()
            .tasInfraConfig(tasInfraConfig)
            .cfCommandTypeNG(CfCommandTypeNG.APP_RESIZE)
            .cfCliVersion(CfCliVersion.V7)
            .releaseNamePrefix(APP_NAME)
            .tasManifestsPackage(
                TasManifestsPackage.builder().manifestYml(MANIFEST_YAML).variableYmls(List.of(VARS_YAML)).build())
            .tasArtifactConfig(TasPackageArtifactConfig.builder().build())
            .commandUnitsProgress(CommandUnitsProgress.builder().build())
            .accountId(ACCOUNT)
            .timeoutIntervalInMin(10)
            .useAppAutoScalar(false)
            .build();

    ApplicationDetail applicationDetail = ApplicationDetail.builder()
                                              .id(APP_ID)
                                              .diskQuota(1)
                                              .instances(0)
                                              .memoryLimit(1)
                                              .name(APP_NAME)
                                              .requestedState(STOPPED)
                                              .stack("")
                                              .runningInstances(0)
                                              .build();

    doReturn(false).when(cfDeploymentManager).checkIfAppHasAutoscalarEnabled(any(), any());
    doNothing().when(cfDeploymentManager).renameApplication(any(), any());
    doNothing().when(cfDeploymentManager).deleteApplication(any());
    doReturn(applicationDetail).when(cfDeploymentManager).createApplication(any(), any());
    doReturn(TasArtifactDownloadResponse.builder().build())
        .when(cfCommandTaskHelperNG)
        .downloadPackageArtifact(any(), any());
    doReturn(TasArtifactCreds.builder().username("user").password("pass").build())
        .when(tasRegistrySettingsAdapter)
        .getContainerSettings(any());
    when(cfDeploymentManager.getPreviousReleases(any(), any()))
        .thenReturn(List.of(getApplicationSummary(APP_NAME, APP_ID)));

    ArgumentCaptor<CfRenameRequest> cfRenameRequestArgumentCaptor = ArgumentCaptor.forClass(CfRenameRequest.class);
    CfBasicSetupResponseNG cfBasicSetupResponseNG =
        (CfBasicSetupResponseNG) tasBasicSetupTaskHandler.executeTaskInternal(
            cfBasicSetupRequestNG, logStreamingTaskClient, CommandUnitsProgress.builder().build());

    assertThat(cfBasicSetupResponseNG.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    verify(cfDeploymentManager, times(0)).deleteApplication(any());
    verify(cfDeploymentManager, times(1)).renameApplication(cfRenameRequestArgumentCaptor.capture(), any());
    verify(cfDeploymentManager, times(1)).createApplication(any(), any());

    assertThat(cfBasicSetupResponseNG.getNewApplicationInfo().getApplicationGuid()).isEqualTo(APP_ID);
    assertThat(cfBasicSetupResponseNG.getNewApplicationInfo().getApplicationName()).isEqualTo(APP_NAME);
    CfRenameRequest cfRenameRequest = cfRenameRequestArgumentCaptor.getValue();
    assertForRenameConfig(cfRenameRequest, APP_NAME, APP_NAME_OLD);
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void testExecuteTaskInternalWithThirdDeployment() throws Exception {
    CfBasicSetupRequestNG cfBasicSetupRequestNG =
        CfBasicSetupRequestNG.builder()
            .tasInfraConfig(tasInfraConfig)
            .cfCommandTypeNG(CfCommandTypeNG.APP_RESIZE)
            .cfCliVersion(CfCliVersion.V7)
            .releaseNamePrefix(APP_NAME)
            .tasManifestsPackage(
                TasManifestsPackage.builder().manifestYml(MANIFEST_YAML).variableYmls(List.of(VARS_YAML)).build())
            .tasArtifactConfig(TasPackageArtifactConfig.builder().build())
            .commandUnitsProgress(CommandUnitsProgress.builder().build())
            .olderActiveVersionCountToKeep(2)
            .accountId(ACCOUNT)
            .timeoutIntervalInMin(10)
            .useAppAutoScalar(false)
            .build();

    ApplicationDetail applicationDetail = ApplicationDetail.builder()
                                              .id(APP_ID)
                                              .diskQuota(1)
                                              .instances(0)
                                              .url("url")
                                              .memoryLimit(1)
                                              .name(APP_NAME)
                                              .requestedState(STOPPED)
                                              .stack("")
                                              .runningInstances(0)
                                              .build();

    doReturn(false).when(cfDeploymentManager).checkIfAppHasAutoscalarEnabled(any(), any());
    doNothing().when(cfDeploymentManager).renameApplication(any(), any());
    doReturn(null).when(cfDeploymentManager).resizeApplication(any(), any());
    doReturn(applicationDetail).when(cfDeploymentManager).createApplication(any(), any());
    doReturn(applicationDetail).when(cfDeploymentManager).getApplicationByName(any());
    doReturn(TasArtifactDownloadResponse.builder().build())
        .when(cfCommandTaskHelperNG)
        .downloadPackageArtifact(any(), any());
    doReturn(TasArtifactCreds.builder().username("user").password("pass").build())
        .when(tasRegistrySettingsAdapter)
        .getContainerSettings(any());
    when(cfDeploymentManager.getPreviousReleases(any(), any()))
        .thenReturn(List.of(getApplicationSummary(APP_NAME, APP_ID), getApplicationSummary(APP_NAME_OLD, APP_ID)));

    ArgumentCaptor<CfRenameRequest> cfRenameRequestArgumentCaptor = ArgumentCaptor.forClass(CfRenameRequest.class);
    CfBasicSetupResponseNG cfBasicSetupResponseNG =
        (CfBasicSetupResponseNG) tasBasicSetupTaskHandler.executeTaskInternal(
            cfBasicSetupRequestNG, logStreamingTaskClient, CommandUnitsProgress.builder().build());

    assertThat(cfBasicSetupResponseNG.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    assertThat(cfBasicSetupResponseNG.getNewApplicationInfo().getApplicationGuid()).isEqualTo(APP_ID);
    assertThat(cfBasicSetupResponseNG.getNewApplicationInfo().getApplicationName()).isEqualTo(APP_NAME);
    verify(cfDeploymentManager, times(0)).deleteApplication(any());
    verify(cfDeploymentManager, times(1)).renameApplication(cfRenameRequestArgumentCaptor.capture(), any());
    verify(cfDeploymentManager, times(1)).createApplication(any(), any());

    CfRenameRequest cfRenameRequest = cfRenameRequestArgumentCaptor.getValue();
    assertForRenameConfig(cfRenameRequest, APP_NAME_OLD, APP_NAME_OLD_1);
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void testExecuteTaskInternalWithDeploymentFailure() throws Exception {
    CfBasicSetupRequestNG cfBasicSetupRequestNG =
        CfBasicSetupRequestNG.builder()
            .tasInfraConfig(tasInfraConfig)
            .cfCommandTypeNG(CfCommandTypeNG.APP_RESIZE)
            .cfCliVersion(CfCliVersion.V7)
            .releaseNamePrefix(APP_NAME)
            .tasManifestsPackage(
                TasManifestsPackage.builder().manifestYml(MANIFEST_YAML).variableYmls(List.of(VARS_YAML)).build())
            .tasArtifactConfig(TasPackageArtifactConfig.builder().build())
            .commandUnitsProgress(CommandUnitsProgress.builder().build())
            .olderActiveVersionCountToKeep(1)
            .accountId(ACCOUNT)
            .timeoutIntervalInMin(10)
            .useAppAutoScalar(false)
            .build();

    ApplicationDetail applicationDetail = ApplicationDetail.builder()
                                              .id(APP_ID)
                                              .diskQuota(1)
                                              .instances(0)
                                              .url("url")
                                              .memoryLimit(1)
                                              .name(APP_NAME)
                                              .requestedState(STOPPED)
                                              .stack("")
                                              .runningInstances(0)
                                              .build();

    doReturn(false).when(cfDeploymentManager).checkIfAppHasAutoscalarEnabled(any(), any());
    doNothing().when(cfDeploymentManager).renameApplication(any(), any());
    doThrow(new PivotalClientApiException("error")).when(cfDeploymentManager).deleteApplication(any());
    doReturn(null).when(cfDeploymentManager).resizeApplication(any(), any());
    doReturn(applicationDetail).when(cfDeploymentManager).createApplication(any(), any());
    doReturn(applicationDetail).when(cfDeploymentManager).getApplicationByName(any());
    doReturn(TasArtifactDownloadResponse.builder().build())
        .when(cfCommandTaskHelperNG)
        .downloadPackageArtifact(any(), any());
    doReturn(TasArtifactCreds.builder().username("user").password("pass").build())
        .when(tasRegistrySettingsAdapter)
        .getContainerSettings(any());
    when(cfDeploymentManager.getPreviousReleases(any(), any()))
        .thenReturn(List.of(getApplicationSummary(APP_NAME, APP_ID), getApplicationSummary(APP_NAME_OLD, APP_ID)));

    CfBasicSetupResponseNG cfBasicSetupResponseNG =
        (CfBasicSetupResponseNG) tasBasicSetupTaskHandler.executeTaskInternal(
            cfBasicSetupRequestNG, logStreamingTaskClient, CommandUnitsProgress.builder().build());

    assertThat(cfBasicSetupResponseNG.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.FAILURE);
    verify(cfDeploymentManager, times(1)).deleteApplication(any());
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void testExecuteTaskInternalWithProcessManifest() throws Exception {
    CfBasicSetupRequestNG cfBasicSetupRequestNG = CfBasicSetupRequestNG.builder()
                                                      .tasInfraConfig(tasInfraConfig)
                                                      .cfCommandTypeNG(CfCommandTypeNG.APP_RESIZE)
                                                      .cfCliVersion(CfCliVersion.V7)
                                                      .releaseNamePrefix(APP_NAME)
                                                      .tasManifestsPackage(TasManifestsPackage.builder()
                                                                               .manifestYml(MANIFEST_YAML_PROCESS)
                                                                               .variableYmls(List.of(VARS_YAML))
                                                                               .build())
                                                      .tasArtifactConfig(TasPackageArtifactConfig.builder().build())
                                                      .commandUnitsProgress(CommandUnitsProgress.builder().build())
                                                      .olderActiveVersionCountToKeep(2)
                                                      .routeMaps(List.of("route1"))
                                                      .accountId(ACCOUNT)
                                                      .timeoutIntervalInMin(10)
                                                      .useAppAutoScalar(false)
                                                      .build();

    ApplicationDetail applicationDetail = ApplicationDetail.builder()
                                              .id(APP_ID)
                                              .diskQuota(1)
                                              .instances(0)
                                              .url("url")
                                              .memoryLimit(1)
                                              .name(APP_NAME)
                                              .requestedState(STOPPED)
                                              .stack("")
                                              .runningInstances(0)
                                              .build();

    doReturn(false).when(cfDeploymentManager).checkIfAppHasAutoscalarEnabled(any(), any());
    doNothing().when(cfDeploymentManager).renameApplication(any(), any());
    doThrow(new PivotalClientApiException("error")).when(cfDeploymentManager).deleteApplication(any());
    doReturn(null).when(cfDeploymentManager).resizeApplication(any(), any());
    doReturn(applicationDetail).when(cfDeploymentManager).createApplication(any(), any());
    doReturn(applicationDetail).when(cfDeploymentManager).getApplicationByName(any());
    doReturn(TasArtifactDownloadResponse.builder().build())
        .when(cfCommandTaskHelperNG)
        .downloadPackageArtifact(any(), any());
    doReturn(TasArtifactCreds.builder().username("user").password("pass").build())
        .when(tasRegistrySettingsAdapter)
        .getContainerSettings(any());
    when(cfDeploymentManager.getPreviousReleases(any(), any()))
        .thenReturn(List.of(getApplicationSummary(APP_NAME, APP_ID), getApplicationSummary(APP_NAME_OLD, APP_ID)));
    ArgumentCaptor<CfRenameRequest> cfRenameRequestArgumentCaptor = ArgumentCaptor.forClass(CfRenameRequest.class);

    CfBasicSetupResponseNG cfBasicSetupResponseNG =
        (CfBasicSetupResponseNG) tasBasicSetupTaskHandler.executeTaskInternal(
            cfBasicSetupRequestNG, logStreamingTaskClient, CommandUnitsProgress.builder().build());

    assertThat(cfBasicSetupResponseNG.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    assertThat(cfBasicSetupResponseNG.getNewApplicationInfo().getApplicationGuid()).isEqualTo(APP_ID);
    assertThat(cfBasicSetupResponseNG.getNewApplicationInfo().getApplicationName()).isEqualTo(APP_NAME);
    verify(cfDeploymentManager, times(0)).deleteApplication(any());
    verify(cfDeploymentManager, times(1)).renameApplication(cfRenameRequestArgumentCaptor.capture(), any());
    verify(cfDeploymentManager, times(1)).createApplication(any(), any());

    CfRenameRequest cfRenameRequest = cfRenameRequestArgumentCaptor.getValue();
    assertForRenameConfig(cfRenameRequest, APP_NAME_OLD, APP_NAME_OLD_1);
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void testExecuteTaskInternalFailureWithNoRevert() throws Exception {
    CfBasicSetupRequestNG cfBasicSetupRequestNG = CfBasicSetupRequestNG.builder()
                                                      .tasInfraConfig(tasInfraConfig)
                                                      .cfCommandTypeNG(CfCommandTypeNG.APP_RESIZE)
                                                      .cfCliVersion(CfCliVersion.V7)
                                                      .releaseNamePrefix(APP_NAME)
                                                      .commandUnitsProgress(CommandUnitsProgress.builder().build())
                                                      .accountId(ACCOUNT)
                                                      .timeoutIntervalInMin(10)
                                                      .useAppAutoScalar(false)
                                                      .build();

    doThrow(new IOException()).when(pcfCommandTaskBaseHelper).generateWorkingDirectoryForDeployment();

    CfBasicSetupResponseNG cfBasicSetupResponseNG =
        (CfBasicSetupResponseNG) tasBasicSetupTaskHandler.executeTaskInternal(
            cfBasicSetupRequestNG, logStreamingTaskClient, CommandUnitsProgress.builder().build());
    assertThat(cfBasicSetupResponseNG.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.FAILURE);
    verify(cfDeploymentManager, times(0)).deleteApplication(any());
    verify(cfDeploymentManager, times(0)).renameApplication(any(), any());
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void testExecuteTaskInternalFailureWithFirstDeploymentRevert() throws Exception {
    CfBasicSetupRequestNG cfBasicSetupRequestNG = CfBasicSetupRequestNG.builder()
                                                      .tasInfraConfig(tasInfraConfig)
                                                      .cfCommandTypeNG(CfCommandTypeNG.APP_RESIZE)
                                                      .cfCliVersion(CfCliVersion.V7)
                                                      .releaseNamePrefix(APP_NAME)
                                                      .commandUnitsProgress(CommandUnitsProgress.builder().build())
                                                      .accountId(ACCOUNT)
                                                      .tasArtifactConfig(TasContainerArtifactConfig.builder().build())
                                                      .timeoutIntervalInMin(10)
                                                      .useAppAutoScalar(false)
                                                      .build();

    doReturn(false).when(cfDeploymentManager).checkIfAppHasAutoscalarEnabled(any(), any());
    doNothing().when(cfDeploymentManager).renameApplication(any(), any());
    doNothing().when(cfDeploymentManager).deleteApplication(any());
    Mockito.doThrow(PivotalClientApiException.class).when(cfDeploymentManager).createApplication(any(), any());
    when(cfDeploymentManager.getPreviousReleases(any(), any()))
        .thenReturn(Collections.emptyList())
        .thenReturn(List.of(getApplicationSummary(APP_NAME, APP_ID)));
    doReturn(TasArtifactDownloadResponse.builder().build())
        .when(cfCommandTaskHelperNG)
        .downloadPackageArtifact(any(), any());

    ArgumentCaptor<CfRequestConfig> cfRequestConfigArgumentCaptor = ArgumentCaptor.forClass(CfRequestConfig.class);
    CfBasicSetupResponseNG cfBasicSetupResponseNG =
        (CfBasicSetupResponseNG) tasBasicSetupTaskHandler.executeTaskInternal(
            cfBasicSetupRequestNG, logStreamingTaskClient, CommandUnitsProgress.builder().build());
    assertThat(cfBasicSetupResponseNG.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.FAILURE);
    verify(cfDeploymentManager, times(1)).deleteApplication(cfRequestConfigArgumentCaptor.capture());
    verify(cfDeploymentManager, times(0)).renameApplication(any(), any());

    CfRequestConfig cfRequestConfig = cfRequestConfigArgumentCaptor.getValue();
    assertForRequestConfig(cfRequestConfig, APP_NAME);
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void testExecuteTaskInternalFailureWithSecondDeploymentRevert() throws Exception {
    CfBasicSetupRequestNG cfBasicSetupRequestNG = CfBasicSetupRequestNG.builder()
                                                      .tasInfraConfig(tasInfraConfig)
                                                      .cfCommandTypeNG(CfCommandTypeNG.APP_RESIZE)
                                                      .cfCliVersion(CfCliVersion.V7)
                                                      .releaseNamePrefix(APP_NAME)
                                                      .commandUnitsProgress(CommandUnitsProgress.builder().build())
                                                      .accountId(ACCOUNT)
                                                      .timeoutIntervalInMin(10)
                                                      .tasArtifactConfig(TasContainerArtifactConfig.builder().build())
                                                      .useAppAutoScalar(false)
                                                      .build();

    doReturn(false).when(cfDeploymentManager).checkIfAppHasAutoscalarEnabled(any(), any());
    doNothing().when(cfDeploymentManager).renameApplication(any(), any());
    doNothing().when(cfDeploymentManager).deleteApplication(any());
    Mockito.doThrow(PivotalClientApiException.class).when(cfDeploymentManager).createApplication(any(), any());
    when(cfDeploymentManager.getPreviousReleases(any(), any()))
        .thenReturn(List.of(getApplicationSummary(APP_NAME, APP_ID)));
    doReturn(TasArtifactDownloadResponse.builder().build())
        .when(cfCommandTaskHelperNG)
        .downloadPackageArtifact(any(), any());

    ArgumentCaptor<CfRequestConfig> cfRequestConfigArgumentCaptor = ArgumentCaptor.forClass(CfRequestConfig.class);
    ArgumentCaptor<CfRenameRequest> cfRenameRequestArgumentCaptor = ArgumentCaptor.forClass(CfRenameRequest.class);
    CfBasicSetupResponseNG cfBasicSetupResponseNG =
        (CfBasicSetupResponseNG) tasBasicSetupTaskHandler.executeTaskInternal(
            cfBasicSetupRequestNG, logStreamingTaskClient, CommandUnitsProgress.builder().build());
    assertThat(cfBasicSetupResponseNG.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.FAILURE);
    verify(cfDeploymentManager, times(1)).deleteApplication(cfRequestConfigArgumentCaptor.capture());
    verify(cfDeploymentManager, times(2)).renameApplication(cfRenameRequestArgumentCaptor.capture(), any());

    CfRequestConfig cfRequestConfig = cfRequestConfigArgumentCaptor.getValue();
    CfRenameRequest cfRenameRequest = cfRenameRequestArgumentCaptor.getAllValues().get(1);

    assertForRequestConfig(cfRequestConfig, APP_NAME);
    assertForRenameConfig(cfRenameRequest, APP_NAME_OLD, APP_NAME);
  }

  private void assertForRenameConfig(CfRenameRequest cfRenameRequest, String appName, String appNameOld) {
    assertThat(cfRenameRequest.getNewName()).isEqualTo(appNameOld);
    assertThat(cfRenameRequest.getName()).isEqualTo(appName);
    assertThat(cfRenameRequest.getEndpointUrl()).isEqualTo(ENDPOINT_URL);
    assertThat(cfRenameRequest.getPassword()).isEqualTo(PASSWORD);
    assertThat(cfRenameRequest.getSpaceName()).isEqualTo(SPACE);
    assertThat(cfRenameRequest.getOrgName()).isEqualTo(ORGANIZATION);
  }

  private void assertForRequestConfig(CfRequestConfig cfRequestConfig, String appName) {
    assertThat(cfRequestConfig.getApplicationName()).isEqualTo(appName);
    assertThat(cfRequestConfig.getEndpointUrl()).isEqualTo(ENDPOINT_URL);
    assertThat(cfRequestConfig.getCfHomeDirPath()).contains(PCF_ARTIFACT_DOWNLOAD_DIR_PATH);
    assertThat(cfRequestConfig.getPassword()).isEqualTo(PASSWORD);
    assertThat(cfRequestConfig.getSpaceName()).isEqualTo(SPACE);
    assertThat(cfRequestConfig.getOrgName()).isEqualTo(ORGANIZATION);
  }

  private ApplicationSummary getApplicationSummary(String appName, String appId) {
    return ApplicationSummary.builder()
        .diskQuota(1)
        .id(appId)
        .name(appName)
        .memoryLimit(1)
        .runningInstances(1)
        .requestedState("RUNNING")
        .instances(2)
        .build();
  }
}
