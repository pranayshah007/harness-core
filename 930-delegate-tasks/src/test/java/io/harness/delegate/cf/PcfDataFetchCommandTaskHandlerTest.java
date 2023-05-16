/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.cf;

import static io.harness.delegate.task.pcf.request.CfInfraMappingDataRequest.ActionType.FETCH_ORG;
import static io.harness.delegate.task.pcf.request.CfInfraMappingDataRequest.ActionType.FETCH_ROUTE;
import static io.harness.delegate.task.pcf.request.CfInfraMappingDataRequest.ActionType.FETCH_SPACE;
import static io.harness.delegate.task.pcf.request.CfInfraMappingDataRequest.ActionType.RUNNING_COUNT;
import static io.harness.rule.OwnerRule.TMACARI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.beans.pcf.CfInternalConfig;
import io.harness.delegate.task.pcf.CfCommandRequest;
import io.harness.delegate.task.pcf.CfCommandResponse;
import io.harness.delegate.task.pcf.request.CfCommandDeployRequest;
import io.harness.delegate.task.pcf.request.CfInfraMappingDataRequest;
import io.harness.delegate.task.pcf.response.CfCommandExecutionResponse;
import io.harness.delegate.task.pcf.response.CfInfraMappingDataResponse;
import io.harness.exception.InvalidArgumentsException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.pcf.CfDeploymentManager;
import io.harness.pcf.PivotalClientApiException;
import io.harness.pcf.model.CfRequestConfig;
import io.harness.rule.Owner;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.SecretDecryptionService;

import com.google.inject.Inject;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.cloudfoundry.operations.applications.ApplicationSummary;
import org.jetbrains.annotations.NotNull;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.CDP)
public class PcfDataFetchCommandTaskHandlerTest extends CategoryTest {
  public static final String ENDPOINT_URL = "endpointUrl";
  public static final String USERNAME = "username";
  public static final String PASSWORD = "password";
  public static final String ORGANIZATION = "organization";
  public static final String SPACE = "space";
  public static final String APP_NAME = "appName";

  @Mock private CfDeploymentManager pcfDeploymentManager;
  @Mock private SecretDecryptionService encryptionService;
  @Mock ILogStreamingTaskClient logStreamingTaskClient;
  @Mock LogCallback executionLogCallback;

  @InjectMocks @Inject private PcfDataFetchCommandTaskHandler pcfDataFetchCommandTaskHandler;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testWithDifferentPcfCommandRequest() {
    CfCommandRequest cfCommandRequest = CfCommandDeployRequest.builder().build();
    List<EncryptedDataDetail> encryptedDataDetails = Collections.emptyList();
    assertThatThrownBy(()
                           -> pcfDataFetchCommandTaskHandler.executeTaskInternal(
                               cfCommandRequest, encryptedDataDetails, logStreamingTaskClient, false))
        .isInstanceOf(InvalidArgumentsException.class);
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testNoActionTypeSpecified() {
    CfCommandRequest cfCommandRequest = getPcfInfraMappingDataRequest(null);
    List<EncryptedDataDetail> encryptedDataDetails = Collections.emptyList();

    CfCommandExecutionResponse response = pcfDataFetchCommandTaskHandler.executeTaskInternal(
        cfCommandRequest, encryptedDataDetails, logStreamingTaskClient, false);

    CfInfraMappingDataResponse pcfCommandResponse = (CfInfraMappingDataResponse) response.getPcfCommandResponse();
    assertThat(pcfCommandResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.FAILURE);
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testGetOrganization() throws PivotalClientApiException {
    CfInfraMappingDataRequest pcfCommandRequest = getPcfInfraMappingDataRequest(FETCH_ORG);
    List<EncryptedDataDetail> encryptedDataDetails = Collections.emptyList();
    List<String> organizations = Arrays.asList("org1", "org1");
    doReturn(organizations).when(pcfDeploymentManager).getOrganizations(any());

    CfCommandExecutionResponse response = pcfDataFetchCommandTaskHandler.executeTaskInternal(
        pcfCommandRequest, encryptedDataDetails, logStreamingTaskClient, false);

    verify(encryptionService, times(1)).decrypt(pcfCommandRequest.getPcfConfig(), encryptedDataDetails, false);

    ArgumentCaptor<CfRequestConfig> argument = ArgumentCaptor.forClass(CfRequestConfig.class);
    verify(pcfDeploymentManager).getOrganizations(argument.capture());
    CfRequestConfig cfRequestConfig = argument.getValue();

    assertPcfRequestConfig(pcfCommandRequest, cfRequestConfig);

    CfCommandResponse cfCommandResponse = response.getPcfCommandResponse();
    assertThat(cfCommandResponse).isInstanceOf(CfInfraMappingDataResponse.class);
    assertThat(((CfInfraMappingDataResponse) cfCommandResponse).getOrganizations()).isEqualTo(organizations);
    assertThat(cfCommandResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    assertThat(cfCommandResponse.getOutput()).isEqualTo(StringUtils.EMPTY);
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testGetSpaces() throws PivotalClientApiException {
    CfCommandRequest cfCommandRequest = getPcfInfraMappingDataRequest(FETCH_SPACE);
    List<EncryptedDataDetail> encryptedDataDetails = Collections.emptyList();
    List<String> spaces = Arrays.asList("space1", "space2");
    doReturn(spaces).when(pcfDeploymentManager).getSpacesForOrganization(any());

    CfCommandExecutionResponse response = pcfDataFetchCommandTaskHandler.executeTaskInternal(
        cfCommandRequest, encryptedDataDetails, logStreamingTaskClient, false);

    ArgumentCaptor<CfRequestConfig> argument = ArgumentCaptor.forClass(CfRequestConfig.class);
    verify(pcfDeploymentManager).getSpacesForOrganization(argument.capture());
    CfRequestConfig cfRequestConfig = argument.getValue();

    assertPcfRequestConfig(cfCommandRequest, cfRequestConfig);

    CfCommandResponse cfCommandResponse = response.getPcfCommandResponse();
    assertThat(cfCommandResponse).isInstanceOf(CfInfraMappingDataResponse.class);
    assertThat(((CfInfraMappingDataResponse) cfCommandResponse).getSpaces()).isEqualTo(spaces);
    assertThat(cfCommandResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    assertThat(cfCommandResponse.getOutput()).isEqualTo(StringUtils.EMPTY);
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testGetRoutes() throws PivotalClientApiException {
    CfCommandRequest cfCommandRequest = getPcfInfraMappingDataRequest(FETCH_ROUTE);
    List<EncryptedDataDetail> encryptedDataDetails = Collections.emptyList();
    List<String> routes = Arrays.asList("route1", "route2");
    doReturn(routes).when(pcfDeploymentManager).getRouteMaps(any());

    CfCommandExecutionResponse response = pcfDataFetchCommandTaskHandler.executeTaskInternal(
        cfCommandRequest, encryptedDataDetails, logStreamingTaskClient, false);

    ArgumentCaptor<CfRequestConfig> argument = ArgumentCaptor.forClass(CfRequestConfig.class);
    verify(pcfDeploymentManager).getRouteMaps(argument.capture());
    CfRequestConfig cfRequestConfig = argument.getValue();

    assertPcfRequestConfig(cfCommandRequest, cfRequestConfig);

    CfCommandResponse cfCommandResponse = response.getPcfCommandResponse();
    assertThat(cfCommandResponse).isInstanceOf(CfInfraMappingDataResponse.class);
    assertThat(((CfInfraMappingDataResponse) cfCommandResponse).getRouteMaps()).isEqualTo(routes);
    assertThat(cfCommandResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    assertThat(cfCommandResponse.getOutput()).isEqualTo(StringUtils.EMPTY);
  }

  @Test
  @Owner(developers = TMACARI)
  @Category(UnitTests.class)
  public void testRunningCount() throws PivotalClientApiException {
    CfCommandRequest cfCommandRequest = getPcfInfraMappingDataRequest(RUNNING_COUNT);
    List<EncryptedDataDetail> encryptedDataDetails = Collections.emptyList();
    ApplicationSummary applicationSummary1 = getApplicationSummary("__1", "RUNNING", "id1");
    ApplicationSummary applicationSummary2 = getApplicationSummary("__2", "STOPPED", "id2");
    List<ApplicationSummary> apps = Arrays.asList(applicationSummary1, applicationSummary2);

    doReturn(apps).when(pcfDeploymentManager).getPreviousReleases(any(), any());

    CfCommandExecutionResponse response = pcfDataFetchCommandTaskHandler.executeTaskInternal(
        cfCommandRequest, encryptedDataDetails, logStreamingTaskClient, false);

    ArgumentCaptor<CfRequestConfig> argument = ArgumentCaptor.forClass(CfRequestConfig.class);
    verify(pcfDeploymentManager).getPreviousReleases(argument.capture(), any());
    CfRequestConfig cfRequestConfig = argument.getValue();

    assertPcfRequestConfig(cfCommandRequest, cfRequestConfig);

    CfCommandResponse cfCommandResponse = response.getPcfCommandResponse();
    assertThat(cfCommandResponse).isInstanceOf(CfInfraMappingDataResponse.class);
    assertThat(((CfInfraMappingDataResponse) cfCommandResponse).getRunningInstanceCount()).isEqualTo(1);
    assertThat(cfCommandResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    assertThat(cfCommandResponse.getOutput()).isEqualTo(StringUtils.EMPTY);
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

  private void assertPcfRequestConfig(CfCommandRequest cfCommandRequest, CfRequestConfig cfRequestConfig) {
    assertThat(cfRequestConfig.getEndpointUrl()).isEqualTo(cfCommandRequest.getPcfConfig().getEndpointUrl());
    assertThat(cfRequestConfig.getUserName()).isEqualTo(String.valueOf(cfCommandRequest.getPcfConfig().getUsername()));
    assertThat(cfRequestConfig.getPassword()).isEqualTo(String.valueOf(cfCommandRequest.getPcfConfig().getPassword()));
    assertThat(cfRequestConfig.isLimitPcfThreads()).isEqualTo(cfCommandRequest.isLimitPcfThreads());
    assertThat(cfRequestConfig.isLimitPcfThreads()).isEqualTo(cfCommandRequest.isLimitPcfThreads());

    assertThat(cfRequestConfig.getTimeOutIntervalInMins()).isEqualTo(cfCommandRequest.getTimeoutIntervalInMin());
  }

  private CfInfraMappingDataRequest getPcfInfraMappingDataRequest(CfInfraMappingDataRequest.ActionType actionType) {
    return CfInfraMappingDataRequest.builder()
        .applicationNamePrefix(APP_NAME)
        .actionType(actionType)
        .limitPcfThreads(false)
        .timeoutIntervalInMin(1)
        .organization(ORGANIZATION)
        .space(SPACE)
        .pcfConfig(CfInternalConfig.builder()
                       .endpointUrl(ENDPOINT_URL)
                       .username(USERNAME.toCharArray())
                       .password(PASSWORD.toCharArray())
                       .build())
        .build();
  }
}
