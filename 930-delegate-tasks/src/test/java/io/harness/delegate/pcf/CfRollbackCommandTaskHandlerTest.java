
/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.pcf;

import static io.harness.pcf.model.CfConstants.CF_ARTIFACT_DOWNLOAD_DIR_PATH;
import static io.harness.rule.OwnerRule.SOURABH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.connector.task.tas.TasNgConfigMapper;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.beans.logstreaming.NGDelegateLogCallback;
import io.harness.delegate.beans.pcf.TasApplicationInfo;
import io.harness.delegate.task.cf.CfCommandTaskHelperNG;
import io.harness.delegate.task.pcf.CfCommandTypeNG;
import io.harness.delegate.task.pcf.TasTaskHelperBase;
import io.harness.delegate.task.pcf.request.CfInfraMappingDataRequestNG;
import io.harness.delegate.task.pcf.request.CfRollbackCommandRequestNG;
import io.harness.delegate.task.pcf.response.CfRollbackCommandResponseNG;
import io.harness.delegate.task.pcf.response.TasInfraConfig;
import io.harness.exception.InvalidArgumentsException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.pcf.CfDeploymentManagerImpl;
import io.harness.pcf.model.CfAppAutoscalarRequestData;
import io.harness.pcf.model.CfCliVersion;
import io.harness.pcf.model.CfRequestConfig;
import io.harness.pcf.model.CloudFoundryConfig;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import org.cloudfoundry.operations.applications.ApplicationDetail;
import org.cloudfoundry.operations.applications.InstanceDetail;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

public class CfRollbackCommandTaskHandlerTest extends CategoryTest {
  public static final String ENDPOINT_URL = "endpointUrl";
  public static final String USERNAME = "username";
  public static final String PASSWORD = "password";
  public static final String ORGANIZATION = "organization";
  public static final String SPACE = "space";
  public static final String APP_NAME = "appName";
  public static final String APP_ID = "appName";
  public static final String APP_NAME_INACTIVE = "appName_Inactive";
  public static final String ACCOUNT = "account_Id";
  public static final String APP_INACTIVE_ID = "app_inactive_Id";

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
  @Mock CfDeploymentManagerImpl cfDeploymentManager;
  @InjectMocks @Spy CfCommandTaskHelperNG cfCommandTaskHelperNG;

  @InjectMocks @Inject private CfRollbackCommandTaskHandlerNG cfRollbackCommandTaskHandlerNG;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    LogCallback logCallback = mock(NGDelegateLogCallback.class);
    doReturn(cloudFoundryConfig).when(tasNgConfigMapper).mapTasConfigWithDecryption(any(), any());
    doReturn(logCallback).when(tasTaskHelperBase).getLogCallback(any(), any(), anyBoolean(), any());
    doNothing().when(cfCommandTaskHelperNG).downSizeListOfInstancesAndUnmapRoutes(any(), any(), any(), any(), any());
    doNothing()
        .when(cfCommandTaskHelperNG)
        .upsizeListOfInstancesAndRestoreRoutes(any(), any(), any(), any(), any(), any(), any());
    doNothing().when(cfDeploymentManager).renameApplication(any(), any());
    doReturn("path").when(cfCommandTaskHelperNG).getCfCliPathOnDelegate(anyBoolean(), any());
    doNothing().when(cfDeploymentManager).deleteApplication(any());
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void testExecuteTaskInternalInvalidArgumentsException() {
    CfInfraMappingDataRequestNG cfInfraMappingDataRequestNG = CfInfraMappingDataRequestNG.builder()
                                                                  .tasInfraConfig(tasInfraConfig)
                                                                  .applicationNamePrefix(APP_NAME)
                                                                  .timeoutIntervalInMin(1)
                                                                  .build();
    assertThatThrownBy(()
                           -> cfRollbackCommandTaskHandlerNG.executeTaskInternal(
                               cfInfraMappingDataRequestNG, null, CommandUnitsProgress.builder().build()))
        .isInstanceOf(InvalidArgumentsException.class);
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void testExecuteTaskInternal() throws Exception {
    TasApplicationInfo oldAppInfo = TasApplicationInfo.builder()
                                        .applicationName(APP_NAME_INACTIVE)
                                        .oldName(APP_NAME)
                                        .applicationGuid(APP_INACTIVE_ID)
                                        .runningCount(0)
                                        .build();
    TasApplicationInfo newAppInfo =
        TasApplicationInfo.builder().applicationName(APP_NAME).applicationGuid(APP_ID).runningCount(2).build();
    CfRollbackCommandRequestNG cfRollbackCommandRequestNG =
        CfRollbackCommandRequestNG.builder()
            .tasInfraConfig(tasInfraConfig)
            .cfCommandTypeNG(CfCommandTypeNG.APP_RESIZE)
            .cfCliVersion(CfCliVersion.V7)
            .commandUnitsProgress(CommandUnitsProgress.builder().build())
            .accountId(ACCOUNT)
            .timeoutIntervalInMin(10)
            .useAppAutoScalar(false)
            .cfAppNamePrefix(APP_NAME)
            .activeApplicationDetails(oldAppInfo)
            .newApplicationDetails(newAppInfo)
            .build();

    ApplicationDetail applicationDetail = getApplicationDetail(Collections.emptyList());
    doReturn(applicationDetail).when(cfDeploymentManager).getApplicationByName(any());
    ArgumentCaptor<CfRequestConfig> cfRequestConfigArgumentCaptor = ArgumentCaptor.forClass(CfRequestConfig.class);
    CfRollbackCommandResponseNG cfRollbackCommandResponseNG =
        (CfRollbackCommandResponseNG) cfRollbackCommandTaskHandlerNG.executeTaskInternal(
            cfRollbackCommandRequestNG, logStreamingTaskClient, CommandUnitsProgress.builder().build());

    verify(cfCommandTaskHelperNG)
        .upsizeListOfInstancesAndRestoreRoutes(
            any(), any(), any(), cfRequestConfigArgumentCaptor.capture(), any(), any(), any());
    assertThat(cfRollbackCommandResponseNG.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);

    CfRequestConfig cfRequestConfig = cfRequestConfigArgumentCaptor.getValue();
    assertForCfRequestConfig(cfRequestConfig);
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void testExecuteTaskInternalWithRollbackNotComplete() throws Exception {
    TasApplicationInfo oldAppInfo = TasApplicationInfo.builder()
                                        .applicationName(APP_NAME_INACTIVE)
                                        .oldName(APP_NAME)
                                        .applicationGuid(APP_INACTIVE_ID)
                                        .runningCount(0)
                                        .build();
    TasApplicationInfo newAppInfo =
        TasApplicationInfo.builder().applicationName(APP_NAME).applicationGuid(APP_ID).runningCount(2).build();
    CfRollbackCommandRequestNG cfRollbackCommandRequestNG =
        CfRollbackCommandRequestNG.builder()
            .tasInfraConfig(tasInfraConfig)
            .cfCommandTypeNG(CfCommandTypeNG.APP_RESIZE)
            .cfCliVersion(CfCliVersion.V7)
            .commandUnitsProgress(CommandUnitsProgress.builder().build())
            .accountId(ACCOUNT)
            .timeoutIntervalInMin(10)
            .useAppAutoScalar(false)
            .cfAppNamePrefix(APP_NAME)
            .activeApplicationDetails(oldAppInfo)
            .newApplicationDetails(newAppInfo)
            .build();

    InstanceDetail instanceDetail = InstanceDetail.builder().index("idx1").build();
    ApplicationDetail applicationDetail = getApplicationDetail(List.of(instanceDetail));
    doReturn(applicationDetail).when(cfDeploymentManager).getApplicationByName(any());
    ArgumentCaptor<CfRequestConfig> cfRequestConfigArgumentCaptor = ArgumentCaptor.forClass(CfRequestConfig.class);
    CfRollbackCommandResponseNG cfRollbackCommandResponseNG =
        (CfRollbackCommandResponseNG) cfRollbackCommandTaskHandlerNG.executeTaskInternal(
            cfRollbackCommandRequestNG, logStreamingTaskClient, CommandUnitsProgress.builder().build());

    verify(cfCommandTaskHelperNG)
        .upsizeListOfInstancesAndRestoreRoutes(
            any(), any(), any(), cfRequestConfigArgumentCaptor.capture(), any(), any(), any());
    assertThat(cfRollbackCommandResponseNG.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);

    verify(cfDeploymentManager, times(0)).deleteApplication(any());
    CfRequestConfig cfRequestConfig = cfRequestConfigArgumentCaptor.getValue();

    assertForCfRequestConfig(cfRequestConfig);
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void testExecuteTaskInternalWithCompleteRollback() throws Exception {
    TasApplicationInfo oldAppInfo = TasApplicationInfo.builder()
                                        .applicationName(APP_NAME_INACTIVE)
                                        .oldName(APP_NAME)
                                        .applicationGuid(APP_INACTIVE_ID)
                                        .runningCount(1)
                                        .build();
    TasApplicationInfo newAppInfo =
        TasApplicationInfo.builder().applicationName(APP_NAME).applicationGuid(APP_ID).runningCount(2).build();
    CfRollbackCommandRequestNG cfRollbackCommandRequestNG =
        CfRollbackCommandRequestNG.builder()
            .tasInfraConfig(tasInfraConfig)
            .cfCommandTypeNG(CfCommandTypeNG.APP_RESIZE)
            .cfCliVersion(CfCliVersion.V7)
            .commandUnitsProgress(CommandUnitsProgress.builder().build())
            .accountId(ACCOUNT)
            .useAppAutoScalar(false)
            .cfAppNamePrefix(APP_NAME)
            .activeApplicationDetails(oldAppInfo)
            .newApplicationDetails(newAppInfo)
            .build();

    InstanceDetail instanceDetail = InstanceDetail.builder().index("idx1").build();
    ApplicationDetail applicationDetailForOldApp = getApplicationDetail(List.of(instanceDetail));
    ApplicationDetail applicationDetailForNewApp = getApplicationDetail(Collections.emptyList());
    when(cfDeploymentManager.getApplicationByName(any()))
        .thenReturn(applicationDetailForNewApp)
        .thenReturn(applicationDetailForOldApp);
    ArgumentCaptor<CfRequestConfig> cfRequestConfigArgumentCaptor = ArgumentCaptor.forClass(CfRequestConfig.class);
    CfRollbackCommandResponseNG cfRollbackCommandResponseNG =
        (CfRollbackCommandResponseNG) cfRollbackCommandTaskHandlerNG.executeTaskInternal(
            cfRollbackCommandRequestNG, logStreamingTaskClient, CommandUnitsProgress.builder().build());

    assertThat(cfRollbackCommandResponseNG.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);

    verify(cfDeploymentManager, times(1)).deleteApplication(cfRequestConfigArgumentCaptor.capture());
    CfRequestConfig cfRequestConfig = cfRequestConfigArgumentCaptor.getValue();
    assertThat(cfRequestConfig.getApplicationName()).isEqualTo(APP_NAME + "__interim");
    assertForCfRequestConfig(cfRequestConfig);
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void testExecuteTaskInternalWithExceptionThrownInBetween() throws Exception {
    TasApplicationInfo oldAppInfo = TasApplicationInfo.builder()
                                        .applicationName(APP_NAME_INACTIVE)
                                        .oldName(APP_NAME)
                                        .applicationGuid(APP_INACTIVE_ID)
                                        .runningCount(0)
                                        .build();
    TasApplicationInfo newAppInfo =
        TasApplicationInfo.builder().applicationName(APP_NAME).applicationGuid(APP_ID).runningCount(2).build();
    CfRollbackCommandRequestNG cfRollbackCommandRequestNG =
        CfRollbackCommandRequestNG.builder()
            .tasInfraConfig(tasInfraConfig)
            .cfCommandTypeNG(CfCommandTypeNG.APP_RESIZE)
            .cfCliVersion(CfCliVersion.V7)
            .commandUnitsProgress(CommandUnitsProgress.builder().build())
            .accountId(ACCOUNT)
            .timeoutIntervalInMin(10)
            .useAppAutoScalar(false)
            .cfAppNamePrefix(APP_NAME)
            .activeApplicationDetails(oldAppInfo)
            .newApplicationDetails(newAppInfo)
            .build();

    doThrow(new IOException()).when(cfCommandTaskHelperNG).generateWorkingDirectoryForDeployment();
    CfRollbackCommandResponseNG cfRollbackCommandResponseNG =
        (CfRollbackCommandResponseNG) cfRollbackCommandTaskHandlerNG.executeTaskInternal(
            cfRollbackCommandRequestNG, logStreamingTaskClient, CommandUnitsProgress.builder().build());

    verify(cfCommandTaskHelperNG, times(0))
        .upsizeListOfInstancesAndRestoreRoutes(any(), any(), any(), any(), any(), any(), any());
    verify(cfCommandTaskHelperNG, times(0)).downSizeListOfInstancesAndUnmapRoutes(any(), any(), any(), any(), any());
    verify(cfDeploymentManager, times(0)).deleteApplication(any());
    assertThat(cfRollbackCommandResponseNG.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.FAILURE);
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void testExecuteTaskInternalWithAutoScalarEnabled() throws Exception {
    TasApplicationInfo oldAppInfo = TasApplicationInfo.builder()
                                        .applicationName(APP_NAME_INACTIVE)
                                        .oldName(APP_NAME)
                                        .applicationGuid(APP_INACTIVE_ID)
                                        .runningCount(0)
                                        .isAutoScalarEnabled(true)
                                        .build();
    TasApplicationInfo newAppInfo =
        TasApplicationInfo.builder().applicationName(APP_NAME).applicationGuid(APP_ID).runningCount(2).build();
    CfRollbackCommandRequestNG cfRollbackCommandRequestNG =
        CfRollbackCommandRequestNG.builder()
            .tasInfraConfig(tasInfraConfig)
            .cfCommandTypeNG(CfCommandTypeNG.APP_RESIZE)
            .cfCliVersion(CfCliVersion.V7)
            .commandUnitsProgress(CommandUnitsProgress.builder().build())
            .accountId(ACCOUNT)
            .timeoutIntervalInMin(10)
            .useAppAutoScalar(true)
            .cfAppNamePrefix(APP_NAME)
            .activeApplicationDetails(oldAppInfo)
            .newApplicationDetails(newAppInfo)
            .build();

    ArgumentCaptor<CfAppAutoscalarRequestData> cfAppAutoscalarRequestDataArgumentCaptor =
        ArgumentCaptor.forClass(CfAppAutoscalarRequestData.class);
    CfRollbackCommandResponseNG cfRollbackCommandResponseNG =
        (CfRollbackCommandResponseNG) cfRollbackCommandTaskHandlerNG.executeTaskInternal(
            cfRollbackCommandRequestNG, logStreamingTaskClient, CommandUnitsProgress.builder().build());
    verify(cfCommandTaskHelperNG, times(1))
        .enableAutoscalerIfNeeded(eq(oldAppInfo), cfAppAutoscalarRequestDataArgumentCaptor.capture(), any());

    CfAppAutoscalarRequestData cfAppAutoscalarRequestData = cfAppAutoscalarRequestDataArgumentCaptor.getValue();
    CfRequestConfig cfRequestConfig = cfAppAutoscalarRequestData.getCfRequestConfig();
    assertForCfRequestConfig(cfRequestConfig);
    assertThat(cfAppAutoscalarRequestData.getTimeoutInMins()).isEqualTo(10);
    assertThat(cfRollbackCommandResponseNG.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void testExecuteTaskInternalWithNoActiveAppDetails() throws Exception {
    TasApplicationInfo newAppInfo =
        TasApplicationInfo.builder().applicationName(APP_NAME).applicationGuid(APP_ID).runningCount(2).build();
    CfRollbackCommandRequestNG cfRollbackCommandRequestNG =
        CfRollbackCommandRequestNG.builder()
            .tasInfraConfig(tasInfraConfig)
            .cfCommandTypeNG(CfCommandTypeNG.APP_RESIZE)
            .cfCliVersion(CfCliVersion.V7)
            .commandUnitsProgress(CommandUnitsProgress.builder().build())
            .accountId(ACCOUNT)
            .timeoutIntervalInMin(10)
            .useAppAutoScalar(true)
            .cfAppNamePrefix(APP_NAME)
            .newApplicationDetails(newAppInfo)
            .build();

    ArgumentCaptor<CfRequestConfig> cfRequestConfigArgumentCaptor = ArgumentCaptor.forClass(CfRequestConfig.class);
    CfRollbackCommandResponseNG cfRollbackCommandResponseNG =
        (CfRollbackCommandResponseNG) cfRollbackCommandTaskHandlerNG.executeTaskInternal(
            cfRollbackCommandRequestNG, logStreamingTaskClient, CommandUnitsProgress.builder().build());

    verify(cfCommandTaskHelperNG, times(0))
        .upsizeListOfInstancesAndRestoreRoutes(any(), any(), any(), any(), any(), any(), any());
    verify(cfDeploymentManager, times(1)).renameApplication(any(), any());
    verify(cfCommandTaskHelperNG, times(1))
        .downSizeListOfInstancesAndUnmapRoutes(any(), cfRequestConfigArgumentCaptor.capture(), any(), any(), any());

    CfRequestConfig cfRequestConfig = cfRequestConfigArgumentCaptor.getValue();
    assertForCfRequestConfig(cfRequestConfig);
    assertThat(cfRollbackCommandResponseNG.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
  }

  private void assertForCfRequestConfig(CfRequestConfig cfRequestConfig) {
    assertThat(cfRequestConfig.getCfCliVersion()).isEqualTo(CfCliVersion.V7);
    assertThat(cfRequestConfig.getEndpointUrl()).isEqualTo(ENDPOINT_URL);
    assertThat(cfRequestConfig.getOrgName()).isEqualTo(ORGANIZATION);
    assertThat(cfRequestConfig.getSpaceName()).isEqualTo(SPACE);
    assertThat(cfRequestConfig.getPassword()).isEqualTo(PASSWORD);
    assertThat(cfRequestConfig.getCfHomeDirPath()).contains(CF_ARTIFACT_DOWNLOAD_DIR_PATH);
    assertThat(cfRequestConfig.getTimeOutIntervalInMins()).isEqualTo(10);
  }

  private ApplicationDetail getApplicationDetail(List<InstanceDetail> instances) {
    return ApplicationDetail.builder()
        .diskQuota(1)
        .id("appId")
        .name("appName")
        .memoryLimit(1)
        .stack("stack")
        .runningInstances(1)
        .requestedState("RUNNING")
        .instances(instances.size())
        .instanceDetails(instances)
        .build();
  }
}