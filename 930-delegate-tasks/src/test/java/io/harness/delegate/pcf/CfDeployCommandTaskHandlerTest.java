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
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.powermock.api.mockito.PowerMockito.doThrow;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.connector.task.tas.TasNgConfigMapper;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.beans.logstreaming.NGDelegateLogCallback;
import io.harness.delegate.beans.pcf.TasApplicationInfo;
import io.harness.delegate.beans.pcf.TasResizeStrategyType;
import io.harness.delegate.task.cf.CfCommandTaskHelperNG;
import io.harness.delegate.task.pcf.CfCommandTypeNG;
import io.harness.delegate.task.pcf.TasTaskHelperBase;
import io.harness.delegate.task.pcf.request.CfDeployCommandRequestNG;
import io.harness.delegate.task.pcf.request.CfInfraMappingDataRequestNG;
import io.harness.delegate.task.pcf.response.CfDeployCommandResponseNG;
import io.harness.delegate.task.pcf.response.TasInfraConfig;
import io.harness.exception.InvalidArgumentsException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.pcf.model.CfAppAutoscalarRequestData;
import io.harness.pcf.model.CfCliVersion;
import io.harness.pcf.model.CfRequestConfig;
import io.harness.pcf.model.CloudFoundryConfig;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import java.io.IOException;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

public class CfDeployCommandTaskHandlerTest extends CategoryTest {
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
  @InjectMocks @Spy CfCommandTaskHelperNG cfCommandTaskHelperNG;

  @InjectMocks @Inject private CfDeployCommandTaskHandlerNG cfDeployCommandTaskHandlerNG;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    LogCallback logCallback = mock(NGDelegateLogCallback.class);
    doReturn(logCallback).when(tasTaskHelperBase).getLogCallback(any(), any(), anyBoolean(), any());
    doReturn(cloudFoundryConfig).when(tasNgConfigMapper).mapTasConfigWithDecryption(any(), any());
    doReturn("cfCliPath").when(cfCommandTaskHelperNG).getCfCliPathOnDelegate(anyBoolean(), any());
    doReturn(null).when(cfCommandTaskHelperNG).getNewlyCreatedApplication(any(), any(), any());
    doNothing().when(cfCommandTaskHelperNG).downsizePreviousReleases(any(), any(), any(), any(), any(), any(), any());
    doNothing().when(cfCommandTaskHelperNG).upsizeNewApplication(any(), any(), any(), any(), any(), any(), any());
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
                           -> cfDeployCommandTaskHandlerNG.executeTaskInternal(
                               cfInfraMappingDataRequestNG, null, CommandUnitsProgress.builder().build()))
        .isInstanceOf(InvalidArgumentsException.class);
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void testExecuteTaskInternalWithDownsizeFirstStrategy() throws Exception {
    TasApplicationInfo oldAppInfo = TasApplicationInfo.builder()
                                        .applicationName(APP_NAME_INACTIVE)
                                        .oldName(APP_NAME)
                                        .applicationGuid(APP_INACTIVE_ID)
                                        .build();
    CfDeployCommandRequestNG cfDeployCommandRequestNG =
        CfDeployCommandRequestNG.builder()
            .tasInfraConfig(tasInfraConfig)
            .cfCommandTypeNG(CfCommandTypeNG.APP_RESIZE)
            .cfCliVersion(CfCliVersion.V7)
            .commandUnitsProgress(CommandUnitsProgress.builder().build())
            .accountId(ACCOUNT)
            .isStandardBlueGreen(false)
            .newReleaseName(APP_NAME)
            .timeoutIntervalInMin(10)
            .resizeStrategy(TasResizeStrategyType.DOWNSCALE_OLD_FIRST)
            .useAppAutoScalar(false)
            .downSizeCount(2)
            .downsizeAppDetail(oldAppInfo)
            .upsizeCount(2)
            .build();
    ArgumentCaptor<CfRequestConfig> cfRequestConfigArgumentCaptor = ArgumentCaptor.forClass(CfRequestConfig.class);
    CfDeployCommandResponseNG cfDeployCommandResponse =
        (CfDeployCommandResponseNG) cfDeployCommandTaskHandlerNG.executeTaskInternal(
            cfDeployCommandRequestNG, logStreamingTaskClient, CommandUnitsProgress.builder().build());
    assertThat(cfDeployCommandResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);

    verify(cfCommandTaskHelperNG)
        .downsizePreviousReleases(any(), cfRequestConfigArgumentCaptor.capture(), any(), any(), any(), any(), any());
    CfRequestConfig cfRequestConfig = cfRequestConfigArgumentCaptor.getValue();

    assertThat(cfRequestConfig.getCfCliVersion()).isEqualTo(CfCliVersion.V7);
    assertThat(cfRequestConfig.getEndpointUrl()).isEqualTo(ENDPOINT_URL);
    assertThat(cfRequestConfig.getOrgName()).isEqualTo(ORGANIZATION);
    assertThat(cfRequestConfig.getCfHomeDirPath()).contains(CF_ARTIFACT_DOWNLOAD_DIR_PATH);
    assertThat(cfRequestConfig.getSpaceName()).isEqualTo(SPACE);
    assertThat(cfRequestConfig.getPassword()).isEqualTo(PASSWORD);
    assertThat(cfRequestConfig.getTimeOutIntervalInMins()).isEqualTo(10);
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void testExecuteTaskInternalWithUpsizeFirstStrategy() throws Exception {
    TasApplicationInfo oldAppInfo = TasApplicationInfo.builder()
                                        .applicationName(APP_NAME_INACTIVE)
                                        .oldName(APP_NAME)
                                        .applicationGuid(APP_INACTIVE_ID)
                                        .build();
    CfDeployCommandRequestNG cfDeployCommandRequestNG =
        CfDeployCommandRequestNG.builder()
            .tasInfraConfig(tasInfraConfig)
            .cfCommandTypeNG(CfCommandTypeNG.APP_RESIZE)
            .cfCliVersion(CfCliVersion.V7)
            .commandUnitsProgress(CommandUnitsProgress.builder().build())
            .accountId(ACCOUNT)
            .isStandardBlueGreen(false)
            .newReleaseName(APP_NAME)
            .timeoutIntervalInMin(10)
            .resizeStrategy(TasResizeStrategyType.UPSCALE_NEW_FIRST)
            .useAppAutoScalar(false)
            .downSizeCount(2)
            .downsizeAppDetail(oldAppInfo)
            .upsizeCount(2)
            .build();
    ArgumentCaptor<CfRequestConfig> cfRequestConfigArgumentCaptor = ArgumentCaptor.forClass(CfRequestConfig.class);
    CfDeployCommandResponseNG cfDeployCommandResponse =
        (CfDeployCommandResponseNG) cfDeployCommandTaskHandlerNG.executeTaskInternal(
            cfDeployCommandRequestNG, logStreamingTaskClient, CommandUnitsProgress.builder().build());
    assertThat(cfDeployCommandResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);

    verify(cfCommandTaskHelperNG)
        .downsizePreviousReleases(any(), cfRequestConfigArgumentCaptor.capture(), any(), any(), any(), any(), any());
    CfRequestConfig cfRequestConfig = cfRequestConfigArgumentCaptor.getValue();

    assertThat(cfRequestConfig.getCfCliVersion()).isEqualTo(CfCliVersion.V7);
    assertThat(cfRequestConfig.getEndpointUrl()).isEqualTo(ENDPOINT_URL);
    assertThat(cfRequestConfig.getOrgName()).isEqualTo(ORGANIZATION);
    assertThat(cfRequestConfig.getSpaceName()).isEqualTo(SPACE);
    assertThat(cfRequestConfig.getPassword()).isEqualTo(PASSWORD);
    assertThat(cfRequestConfig.getTimeOutIntervalInMins()).isEqualTo(10);
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void testExecuteTaskInternalFailure() throws Exception {
    TasApplicationInfo oldAppInfo = TasApplicationInfo.builder()
                                        .applicationName(APP_NAME_INACTIVE)
                                        .oldName(APP_NAME)
                                        .applicationGuid(APP_INACTIVE_ID)
                                        .build();
    CfDeployCommandRequestNG cfDeployCommandRequestNG =
        CfDeployCommandRequestNG.builder()
            .tasInfraConfig(tasInfraConfig)
            .cfCommandTypeNG(CfCommandTypeNG.APP_RESIZE)
            .cfCliVersion(CfCliVersion.V7)
            .commandUnitsProgress(CommandUnitsProgress.builder().build())
            .accountId(ACCOUNT)
            .isStandardBlueGreen(false)
            .newReleaseName(APP_NAME)
            .timeoutIntervalInMin(10)
            .resizeStrategy(TasResizeStrategyType.UPSCALE_NEW_FIRST)
            .useAppAutoScalar(false)
            .downSizeCount(2)
            .downsizeAppDetail(oldAppInfo)
            .upsizeCount(2)
            .build();

    doThrow(new IOException()).when(cfCommandTaskHelperNG).generateWorkingDirectoryForDeployment();

    CfDeployCommandResponseNG cfDeployCommandResponse =
        (CfDeployCommandResponseNG) cfDeployCommandTaskHandlerNG.executeTaskInternal(
            cfDeployCommandRequestNG, logStreamingTaskClient, CommandUnitsProgress.builder().build());
    assertThat(cfDeployCommandResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.FAILURE);
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void testExecuteTaskInternalForAutoScaler() throws Exception {
    TasApplicationInfo oldAppInfo = TasApplicationInfo.builder()
                                        .applicationName(APP_NAME_INACTIVE)
                                        .oldName(APP_NAME)
                                        .applicationGuid(APP_INACTIVE_ID)
                                        .build();
    CfDeployCommandRequestNG cfDeployCommandRequestNG =
        CfDeployCommandRequestNG.builder()
            .tasInfraConfig(tasInfraConfig)
            .cfCommandTypeNG(CfCommandTypeNG.APP_RESIZE)
            .cfCliVersion(CfCliVersion.V7)
            .commandUnitsProgress(CommandUnitsProgress.builder().build())
            .accountId(ACCOUNT)
            .isStandardBlueGreen(false)
            .newReleaseName(APP_NAME)
            .timeoutIntervalInMin(10)
            .resizeStrategy(TasResizeStrategyType.UPSCALE_NEW_FIRST)
            .useAppAutoScalar(true)
            .downSizeCount(2)
            .downsizeAppDetail(oldAppInfo)
            .upsizeCount(2)
            .build();

    ArgumentCaptor<CfAppAutoscalarRequestData> cfAppAutoscalarRequestDataArgumentCaptor =
        ArgumentCaptor.forClass(CfAppAutoscalarRequestData.class);

    CfDeployCommandResponseNG cfDeployCommandResponse =
        (CfDeployCommandResponseNG) cfDeployCommandTaskHandlerNG.executeTaskInternal(
            cfDeployCommandRequestNG, logStreamingTaskClient, CommandUnitsProgress.builder().build());

    verify(cfCommandTaskHelperNG)
        .downsizePreviousReleases(
            any(), any(), any(), any(), any(), any(), cfAppAutoscalarRequestDataArgumentCaptor.capture());
    CfAppAutoscalarRequestData cfAppAutoscalarRequestData = cfAppAutoscalarRequestDataArgumentCaptor.getValue();

    assertThat(cfDeployCommandResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    assertThat(cfAppAutoscalarRequestData.getCfRequestConfig().getOrgName()).isEqualTo(ORGANIZATION);
    assertThat(cfAppAutoscalarRequestData.getCfRequestConfig().getSpaceName()).isEqualTo(SPACE);
    assertThat(cfAppAutoscalarRequestData.getCfRequestConfig().getEndpointUrl()).isEqualTo(ENDPOINT_URL);
    assertThat(cfAppAutoscalarRequestData.getCfRequestConfig().getTimeOutIntervalInMins()).isEqualTo(10);
    assertThat(cfAppAutoscalarRequestData.getTimeoutInMins()).isEqualTo(10);
  }
}
