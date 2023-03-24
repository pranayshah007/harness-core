/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.pcf;

import static io.harness.pcf.model.PcfConstants.PCF_ARTIFACT_DOWNLOAD_DIR_PATH;
import static io.harness.rule.OwnerRule.SOURABH;

import static org.assertj.core.api.Assertions.assertThat;
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
import io.harness.delegate.cf.PcfCommandTaskBaseHelper;
import io.harness.delegate.task.cf.CfCommandTaskHelperNG;
import io.harness.delegate.task.cf.TasArtifactDownloadResponse;
import io.harness.delegate.task.pcf.CfCommandTypeNG;
import io.harness.delegate.task.pcf.TasTaskHelperBase;
import io.harness.delegate.task.pcf.artifact.TasContainerArtifactConfig;
import io.harness.delegate.task.pcf.request.CfBasicSetupRequestNG;
import io.harness.delegate.task.pcf.response.CfBasicSetupResponseNG;
import io.harness.delegate.task.pcf.response.TasInfraConfig;
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
  public static final String APP_INACTIVE_OLD = "app_inactive__0";

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
