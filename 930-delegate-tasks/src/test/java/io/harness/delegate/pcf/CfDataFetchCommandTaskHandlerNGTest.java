/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */
package io.harness.delegate.pcf;

import static io.harness.delegate.task.pcf.request.CfDataFetchActionType.FETCH_ROUTE;
import static io.harness.delegate.task.pcf.request.CfDataFetchActionType.FETCH_SPACE;
import static io.harness.delegate.task.pcf.request.CfDataFetchActionType.RUNNING_COUNT;
import static io.harness.rule.OwnerRule.SOURABH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.category.element.UnitTests;
import io.harness.connector.task.tas.TasNgConfigMapper;
import io.harness.delegate.beans.logstreaming.CommandUnitsProgress;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.beans.pcf.CfInfraMappingDataResult;
import io.harness.delegate.task.pcf.request.CfDataFetchActionType;
import io.harness.delegate.task.pcf.request.CfDeployCommandRequestNG;
import io.harness.delegate.task.pcf.request.CfInfraMappingDataRequestNG;
import io.harness.delegate.task.pcf.response.CfInfraMappingDataResponseNG;
import io.harness.delegate.task.pcf.response.TasInfraConfig;
import io.harness.exception.InvalidArgumentsException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.pcf.CfDeploymentManager;
import io.harness.pcf.model.CfRequestConfig;
import io.harness.pcf.model.CloudFoundryConfig;
import io.harness.rule.Owner;
import io.harness.security.encryption.SecretDecryptionService;

import com.google.inject.Inject;
import java.util.Arrays;
import java.util.List;
import org.cloudfoundry.operations.applications.ApplicationSummary;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class CfDataFetchCommandTaskHandlerNGTest extends CategoryTest {
  public static final String ENDPOINT_URL = "endpointUrl";
  public static final String USERNAME = "username";
  public static final String PASSWORD = "password";
  public static final String ORGANIZATION = "organization";
  public static final String SPACE = "space";
  public static final String APP_NAME = "appName";

  private final CloudFoundryConfig cloudFoundryConfig = CloudFoundryConfig.builder()
                                                            .endpointUrl(ENDPOINT_URL)
                                                            .userName(USERNAME.toCharArray())
                                                            .password(PASSWORD.toCharArray())
                                                            .build();
  private final TasInfraConfig tasInfraConfig =
      TasInfraConfig.builder().organization(ORGANIZATION).space(SPACE).build();

  @Mock private CfDeploymentManager pcfDeploymentManager;
  @Mock private SecretDecryptionService encryptionService;
  @Mock TasNgConfigMapper tasNgConfigMapper;
  @Mock ILogStreamingTaskClient logStreamingTaskClient;
  @Mock LogCallback executionLogCallback;

  @InjectMocks @Inject private CfDataFetchCommandTaskHandlerNG cfDataFetchCommandTaskHandlerNG;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    doReturn(cloudFoundryConfig).when(tasNgConfigMapper).mapTasConfigWithDecryption(any(), any());
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void testWithDifferentCfCommandRequest() {
    CfDeployCommandRequestNG cfDeployCommandRequestNG =
        CfDeployCommandRequestNG.builder().tasInfraConfig(tasInfraConfig).build();
    assertThatThrownBy(()
                           -> cfDataFetchCommandTaskHandlerNG.executeTaskInternal(
                               cfDeployCommandRequestNG, logStreamingTaskClient, null))
        .isInstanceOf(InvalidArgumentsException.class);
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void testNoActionTypeSpecified() throws Exception {
    CfInfraMappingDataRequestNG cfCommandRequest = getPcfInfraMappingDataRequest(null, tasInfraConfig);

    CfInfraMappingDataResponseNG response =
        (CfInfraMappingDataResponseNG) cfDataFetchCommandTaskHandlerNG.executeTaskInternal(
            cfCommandRequest, logStreamingTaskClient, CommandUnitsProgress.builder().build());

    assertThat(response.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.FAILURE);
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void testGetOrganization() throws Exception {
    CfInfraMappingDataRequestNG cfCommandRequest =
        getPcfInfraMappingDataRequest(CfDataFetchActionType.FETCH_ORG, tasInfraConfig);
    List<String> organizations = Arrays.asList("org1", "org1");
    doReturn(organizations).when(pcfDeploymentManager).getOrganizations(any());

    CfInfraMappingDataResponseNG response =
        (CfInfraMappingDataResponseNG) cfDataFetchCommandTaskHandlerNG.executeTaskInternal(
            cfCommandRequest, logStreamingTaskClient, CommandUnitsProgress.builder().build());

    ArgumentCaptor<CfRequestConfig> argument = ArgumentCaptor.forClass(CfRequestConfig.class);
    verify(pcfDeploymentManager).getOrganizations(argument.capture());
    CfRequestConfig cfRequestConfig = argument.getValue();

    assertCfRequestConfig(cloudFoundryConfig, cfRequestConfig);

    CfInfraMappingDataResult cfInfraMappingDataResult = response.getCfInfraMappingDataResult();
    assertThat(cfInfraMappingDataResult.getOrganizations()).isEqualTo(organizations);
    assertThat(response.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void testGetSpaces() throws Exception {
    CfInfraMappingDataRequestNG cfCommandRequest = getPcfInfraMappingDataRequest(FETCH_SPACE, tasInfraConfig);
    List<String> spaces = Arrays.asList("space1", "space2");
    doReturn(spaces).when(pcfDeploymentManager).getSpacesForOrganization(any());

    CfInfraMappingDataResponseNG response =
        (CfInfraMappingDataResponseNG) cfDataFetchCommandTaskHandlerNG.executeTaskInternal(
            cfCommandRequest, logStreamingTaskClient, CommandUnitsProgress.builder().build());

    ArgumentCaptor<CfRequestConfig> argument = ArgumentCaptor.forClass(CfRequestConfig.class);
    verify(pcfDeploymentManager).getSpacesForOrganization(argument.capture());
    CfRequestConfig cfRequestConfig = argument.getValue();

    assertCfRequestConfig(cloudFoundryConfig, cfRequestConfig);

    CfInfraMappingDataResult cfInfraMappingDataResult = response.getCfInfraMappingDataResult();
    assertThat(cfInfraMappingDataResult.getSpaces()).isEqualTo(spaces);
    assertThat(response.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void testGetRoutes() throws Exception {
    CfInfraMappingDataRequestNG cfCommandRequest = getPcfInfraMappingDataRequest(FETCH_ROUTE, tasInfraConfig);
    List<String> routes = Arrays.asList("route1", "route2");
    doReturn(routes).when(pcfDeploymentManager).getRouteMaps(any());

    CfInfraMappingDataResponseNG response =
        (CfInfraMappingDataResponseNG) cfDataFetchCommandTaskHandlerNG.executeTaskInternal(
            cfCommandRequest, logStreamingTaskClient, CommandUnitsProgress.builder().build());

    ArgumentCaptor<CfRequestConfig> argument = ArgumentCaptor.forClass(CfRequestConfig.class);
    verify(pcfDeploymentManager).getRouteMaps(argument.capture());
    CfRequestConfig cfRequestConfig = argument.getValue();

    assertCfRequestConfig(cloudFoundryConfig, cfRequestConfig);

    CfInfraMappingDataResult cfInfraMappingDataResult = response.getCfInfraMappingDataResult();
    assertThat(cfInfraMappingDataResult.getRouteMaps()).isEqualTo(routes);
    assertThat(response.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
  }

  @Test
  @Owner(developers = SOURABH)
  @Category(UnitTests.class)
  public void testRunningCount() throws Exception {
    CfInfraMappingDataRequestNG cfCommandRequest = getPcfInfraMappingDataRequest(RUNNING_COUNT, tasInfraConfig);
    ApplicationSummary applicationSummary1 = getApplicationSummary("__1", "RUNNING", "id1");
    ApplicationSummary applicationSummary2 = getApplicationSummary("__2", "STOPPED", "id2");
    List<ApplicationSummary> apps = Arrays.asList(applicationSummary1, applicationSummary2);

    doReturn(apps).when(pcfDeploymentManager).getPreviousReleases(any(), any());

    CfInfraMappingDataResponseNG response =
        (CfInfraMappingDataResponseNG) cfDataFetchCommandTaskHandlerNG.executeTaskInternal(
            cfCommandRequest, logStreamingTaskClient, CommandUnitsProgress.builder().build());

    ArgumentCaptor<CfRequestConfig> argument = ArgumentCaptor.forClass(CfRequestConfig.class);
    verify(pcfDeploymentManager).getPreviousReleases(argument.capture(), any());
    CfRequestConfig cfRequestConfig = argument.getValue();

    assertCfRequestConfig(cloudFoundryConfig, cfRequestConfig);

    CfInfraMappingDataResult cfInfraMappingDataResult = response.getCfInfraMappingDataResult();
    assertThat(cfInfraMappingDataResult.getRunningInstanceCount()).isEqualTo(1);
    assertThat(response.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
  }

  @NotNull
  private ApplicationSummary getApplicationSummary(String s, String running, String id) {
    return ApplicationSummary.builder()
        .name(APP_NAME + s)
        .runningInstances(1)
        .instances(2)
        .requestedState(running)
        .diskQuota(2)
        .id(id)
        .memoryLimit(1)
        .build();
  }
  private void assertCfRequestConfig(CloudFoundryConfig cloudFoundryConfig, CfRequestConfig cfRequestConfig) {
    assertThat(cloudFoundryConfig.getEndpointUrl()).isEqualTo(cfRequestConfig.getEndpointUrl());
    assertThat(String.valueOf(cloudFoundryConfig.getUserName())).isEqualTo(cfRequestConfig.getUserName());
    assertThat(String.valueOf(cloudFoundryConfig.getPassword())).isEqualTo(cfRequestConfig.getPassword());
  }

  private CfInfraMappingDataRequestNG getPcfInfraMappingDataRequest(
      CfDataFetchActionType actionType, TasInfraConfig tasInfraConfig) {
    return CfInfraMappingDataRequestNG.builder()
        .tasInfraConfig(tasInfraConfig)
        .applicationNamePrefix(APP_NAME)
        .actionType(actionType)
        .timeoutIntervalInMin(1)
        .build();
  }
}
