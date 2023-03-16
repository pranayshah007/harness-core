/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.delegate.task.pcf.response.CfInstanceSyncResponse.builder;
import static io.harness.logging.CommandExecutionStatus.SUCCESS;
import static io.harness.rule.OwnerRule.ANKIT;
import static io.harness.rule.OwnerRule.ANSHUL;
import static io.harness.rule.OwnerRule.IVAN;

import static software.wings.utils.WingsTestConstants.ACCOUNT_ID;

import static java.lang.String.format;
import static junit.framework.TestCase.assertEquals;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.beans.DelegateTask;
import io.harness.category.element.UnitTests;
import io.harness.delegate.task.pcf.response.CfCommandExecutionResponse;
import io.harness.delegate.task.pcf.response.CfInfraMappingDataResponse;
import io.harness.delegate.task.pcf.response.CfInstanceSyncResponse;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.ff.FeatureFlagService;
import io.harness.logging.CommandExecutionStatus;
import io.harness.pcf.PcfAppNotFoundException;
import io.harness.rule.Owner;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.WingsBaseTest;
import software.wings.beans.PcfConfig;
import software.wings.beans.infrastructure.instance.info.PcfInstanceInfo;
import software.wings.service.intfc.DelegateService;
import software.wings.service.intfc.security.SecretManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

@OwnedBy(CDP)
@TargetModule(HarnessModule._960_API_SERVICES)
public class PcfHelperServiceTest extends WingsBaseTest {
  private static final String ERROR_MSG = "Error msg";
  private static final String APP_PREFIX = "APP_PREFIX";
  private static final String APP_NAME = "APP_NAME";
  private static final String ORG_NAME = "ORG_NAME";
  private static final String SPACE = "SPACE";
  private static final String APP_ID = "APP_ID";
  private static final String ROUTE_MAP = "route.map";

  @Mock private DelegateService delegateService;
  @Mock private FeatureFlagService mockFeatureFlagService;
  @Mock private SecretManager secretManager;
  @InjectMocks private PcfHelperService pcfHelperService;

  @Test
  @Owner(developers = ANSHUL)
  @Category(UnitTests.class)
  public void testValidate() throws Exception {
    PcfConfig pcfConfig = PcfConfig.builder().accountId(ACCOUNT_ID).build();
    List<EncryptedDataDetail> encryptedDataDetails = new ArrayList<>();
    encryptedDataDetails.add(EncryptedDataDetail.builder().fieldName("password").build());

    when(delegateService.executeTaskV2(any(DelegateTask.class)))
        .thenReturn(CfCommandExecutionResponse.builder().build());
    doReturn(true).when(mockFeatureFlagService).isEnabled(any(), anyString());

    pcfHelperService.validate(pcfConfig, encryptedDataDetails);
    ArgumentCaptor<DelegateTask> captor = ArgumentCaptor.forClass(DelegateTask.class);
    verify(delegateService).executeTaskV2(captor.capture());
    DelegateTask delegateTask = captor.getValue();
    assertThat(delegateTask.getData().getParameters()).hasSize(2);
    List<EncryptedDataDetail> parameter = (List<EncryptedDataDetail>) (delegateTask.getData().getParameters()[1]);
    assertThat(parameter).isNotEmpty();
    assertThat(parameter.get(0).getFieldName()).isEqualTo("password");
  }

  @Test
  @Owner(developers = ANKIT)
  @Category(UnitTests.class)
  public void testInstancesCount() {
    List<String> instances = new ArrayList<>();
    instances.add("DummyInstanceId1");
    instances.add("DummyInstanceId2");

    CfInstanceSyncResponse cfInstanceSyncResponse = builder().instanceIndicesx(instances).build();
    CfCommandExecutionResponse response = CfCommandExecutionResponse.builder()
                                              .commandExecutionStatus(SUCCESS)
                                              .pcfCommandResponse(cfInstanceSyncResponse)
                                              .build();

    assertEquals(2, pcfHelperService.getInstanceCount(response));
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetApplicationDetails() throws PcfAppNotFoundException, InterruptedException {
    PcfConfig pcfConfig = PcfConfig.builder().build();
    String appIndex = "index_0";
    CfInstanceSyncResponse cfInstanceSyncResponse = builder()
                                                        .commandExecutionStatus(SUCCESS)
                                                        .name(APP_NAME)
                                                        .guid(APP_ID)
                                                        .organization(ORG_NAME)
                                                        .space(SPACE)
                                                        .instanceIndicesx(Collections.singletonList(appIndex))
                                                        .build();
    CfCommandExecutionResponse perpetualTaskResponse =
        CfCommandExecutionResponse.builder().pcfCommandResponse(cfInstanceSyncResponse).build();
    when(secretManager.getEncryptionDetails(pcfConfig, null, null)).thenReturn(Collections.emptyList());
    when(delegateService.executeTaskV2(any(DelegateTask.class))).thenReturn(perpetualTaskResponse);

    List<PcfInstanceInfo> applicationDetails =
        pcfHelperService.getApplicationDetails(APP_NAME, ORG_NAME, SPACE, pcfConfig, null);
    assertThat(applicationDetails.size()).isEqualTo(1);
    assertThat(applicationDetails.get(0)).isNotNull();
    PcfInstanceInfo pcfInstanceInfo = applicationDetails.get(0);
    assertThat(pcfInstanceInfo.getId()).isEqualTo(format("%s:%s", APP_ID, appIndex));
    assertThat(pcfInstanceInfo.getOrganization()).isEqualTo(ORG_NAME);
    assertThat(pcfInstanceInfo.getPcfApplicationName()).isEqualTo(APP_NAME);
    assertThat(pcfInstanceInfo.getSpace()).isEqualTo(SPACE);
    assertThat(pcfInstanceInfo.getPcfApplicationGuid()).isEqualTo(APP_ID);
    assertThat(pcfInstanceInfo.getInstanceIndex()).isEqualTo(appIndex);
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetRunningInstanceCount() throws InterruptedException {
    PcfConfig pcfConfig = PcfConfig.builder().build();
    String responseOutput = "Response output";
    int runningInstanceCount = 3;
    CfInfraMappingDataResponse cfInstanceSyncResponse = CfInfraMappingDataResponse.builder()
                                                            .commandExecutionStatus(SUCCESS)
                                                            .organizations(Collections.singletonList(ORG_NAME))
                                                            .spaces(Collections.singletonList(SPACE))
                                                            .output(responseOutput)
                                                            .runningInstanceCount(runningInstanceCount)
                                                            .routeMaps(Collections.singletonList(ROUTE_MAP))
                                                            .build();
    CfCommandExecutionResponse perpetualTaskResponse = CfCommandExecutionResponse.builder()
                                                           .pcfCommandResponse(cfInstanceSyncResponse)
                                                           .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                                           .build();
    when(secretManager.getEncryptionDetails(pcfConfig, null, null)).thenReturn(Collections.emptyList());
    when(delegateService.executeTaskV2(any(DelegateTask.class))).thenReturn(perpetualTaskResponse);

    Integer runningInstanceCountResult =
        pcfHelperService.getRunningInstanceCount(pcfConfig, ORG_NAME, SPACE, APP_PREFIX);

    assertThat(runningInstanceCountResult).isEqualTo(runningInstanceCount);
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetRunningInstanceCountWithFailureExecutionStatus() throws InterruptedException {
    PcfConfig pcfConfig = PcfConfig.builder().build();
    CfCommandExecutionResponse perpetualTaskResponse = CfCommandExecutionResponse.builder()
                                                           .pcfCommandResponse(null)
                                                           .commandExecutionStatus(CommandExecutionStatus.FAILURE)
                                                           .errorMessage(ERROR_MSG)
                                                           .build();
    when(secretManager.getEncryptionDetails(pcfConfig, null, null)).thenReturn(Collections.emptyList());
    when(delegateService.executeTaskV2(any(DelegateTask.class))).thenReturn(perpetualTaskResponse);

    assertThatThrownBy(() -> pcfHelperService.getRunningInstanceCount(pcfConfig, ORG_NAME, SPACE, APP_PREFIX))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(ERROR_MSG);
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testGetRunningInstanceCountDelegateTaskInterruptedException() throws InterruptedException {
    PcfConfig pcfConfig = PcfConfig.builder().build();

    when(secretManager.getEncryptionDetails(pcfConfig, null, null)).thenReturn(Collections.emptyList());
    when(delegateService.executeTaskV2(any(DelegateTask.class))).thenThrow(new InterruptedException(ERROR_MSG));

    assertThatThrownBy(() -> pcfHelperService.getRunningInstanceCount(pcfConfig, ORG_NAME, SPACE, APP_PREFIX))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(format("InterruptedException: %s", ERROR_MSG));
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testValidatePcfInstanceSyncResponse() throws PcfAppNotFoundException {
    CfInstanceSyncResponse cfInstanceSyncResponse = CfInstanceSyncResponse.builder().build();
    CfCommandExecutionResponse perpetualTaskResponse =
        CfCommandExecutionResponse.builder().pcfCommandResponse(cfInstanceSyncResponse).build();

    CfInstanceSyncResponse cfInstanceSyncResponseResult =
        pcfHelperService.validatePcfInstanceSyncResponse(APP_NAME, ORG_NAME, SPACE, perpetualTaskResponse);

    assertThat(cfInstanceSyncResponseResult).isEqualTo(cfInstanceSyncResponse);
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testValidatePcfInstanceSyncResponseWithPcfAppNotFoundException() {
    CfInstanceSyncResponse cfInstanceSyncResponse = CfInstanceSyncResponse.builder().build();
    String errorMessage = APP_NAME + " does not exist";
    CfCommandExecutionResponse perpetualTaskResponse = CfCommandExecutionResponse.builder()
                                                           .commandExecutionStatus(CommandExecutionStatus.FAILURE)
                                                           .errorMessage(errorMessage)
                                                           .pcfCommandResponse(cfInstanceSyncResponse)
                                                           .build();

    assertThatThrownBy(
        () -> pcfHelperService.validatePcfInstanceSyncResponse(APP_NAME, ORG_NAME, SPACE, perpetualTaskResponse))
        .isInstanceOf(PcfAppNotFoundException.class)
        .hasMessage(errorMessage);
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testValidatePcfInstanceSyncResponseWithWingsException() {
    String failedConnectingToServerErrMsg = "Failed connecting to server";
    CfInstanceSyncResponse cfInstanceSyncResponse =
        CfInstanceSyncResponse.builder().output(failedConnectingToServerErrMsg).build();
    CfCommandExecutionResponse perpetualTaskResponse = CfCommandExecutionResponse.builder()
                                                           .commandExecutionStatus(CommandExecutionStatus.FAILURE)
                                                           .errorMessage(ERROR_MSG)
                                                           .pcfCommandResponse(cfInstanceSyncResponse)
                                                           .build();

    assertThatThrownBy(
        () -> pcfHelperService.validatePcfInstanceSyncResponse(APP_NAME, ORG_NAME, SPACE, perpetualTaskResponse))
        .isInstanceOf(WingsException.class)
        .hasMessage(
            format("Failed to fetch app details for PCF APP: APP_NAME with Error: %s", failedConnectingToServerErrMsg));
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testListOrganizations() throws InterruptedException {
    PcfConfig pcfConfig = PcfConfig.builder().build();
    CfInfraMappingDataResponse cfInstanceSyncResponse = CfInfraMappingDataResponse.builder()
                                                            .commandExecutionStatus(SUCCESS)
                                                            .organizations(Collections.singletonList(ORG_NAME))
                                                            .spaces(Collections.singletonList(SPACE))
                                                            .build();
    CfCommandExecutionResponse perpetualTaskResponse = CfCommandExecutionResponse.builder()
                                                           .pcfCommandResponse(cfInstanceSyncResponse)
                                                           .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                                           .build();

    when(secretManager.getEncryptionDetails(pcfConfig, null, null)).thenReturn(Collections.emptyList());
    when(delegateService.executeTaskV2(any(DelegateTask.class))).thenReturn(perpetualTaskResponse);

    List<String> organizationsResult = pcfHelperService.listOrganizations(pcfConfig);

    assertThat(organizationsResult.get(0)).isEqualTo(ORG_NAME);
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testListSpaces() throws InterruptedException {
    PcfConfig pcfConfig = PcfConfig.builder().build();
    CfInfraMappingDataResponse cfInstanceSyncResponse = CfInfraMappingDataResponse.builder()
                                                            .commandExecutionStatus(SUCCESS)
                                                            .organizations(Collections.singletonList(ORG_NAME))
                                                            .spaces(Collections.singletonList(SPACE))
                                                            .build();
    CfCommandExecutionResponse perpetualTaskResponse = CfCommandExecutionResponse.builder()
                                                           .pcfCommandResponse(cfInstanceSyncResponse)
                                                           .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                                           .build();

    when(secretManager.getEncryptionDetails(pcfConfig, null, null)).thenReturn(Collections.emptyList());
    when(delegateService.executeTaskV2(any(DelegateTask.class))).thenReturn(perpetualTaskResponse);

    List<String> spacesResult = pcfHelperService.listSpaces(pcfConfig, ORG_NAME);

    assertThat(spacesResult.get(0)).isEqualTo(SPACE);
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testListSpacesWithFailureExecutionStatus() throws InterruptedException {
    PcfConfig pcfConfig = PcfConfig.builder().build();
    CfCommandExecutionResponse perpetualTaskResponse = CfCommandExecutionResponse.builder()
                                                           .pcfCommandResponse(null)
                                                           .commandExecutionStatus(CommandExecutionStatus.FAILURE)
                                                           .errorMessage(ERROR_MSG)
                                                           .build();
    when(secretManager.getEncryptionDetails(pcfConfig, null, null)).thenReturn(Collections.emptyList());
    when(delegateService.executeTaskV2(any(DelegateTask.class))).thenReturn(perpetualTaskResponse);

    assertThatThrownBy(() -> pcfHelperService.listSpaces(pcfConfig, ORG_NAME))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(ERROR_MSG);
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testListRoutes() throws InterruptedException {
    PcfConfig pcfConfig = PcfConfig.builder().build();
    CfInfraMappingDataResponse cfInstanceSyncResponse = CfInfraMappingDataResponse.builder()
                                                            .commandExecutionStatus(SUCCESS)
                                                            .organizations(Collections.singletonList(ORG_NAME))
                                                            .routeMaps(Collections.singletonList(ROUTE_MAP))
                                                            .spaces(Collections.singletonList(SPACE))
                                                            .build();
    CfCommandExecutionResponse perpetualTaskResponse = CfCommandExecutionResponse.builder()
                                                           .pcfCommandResponse(cfInstanceSyncResponse)
                                                           .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                                           .build();

    when(secretManager.getEncryptionDetails(pcfConfig, null, null)).thenReturn(Collections.emptyList());
    when(delegateService.executeTaskV2(any(DelegateTask.class))).thenReturn(perpetualTaskResponse);

    List<String> listRoutes = pcfHelperService.listRoutes(pcfConfig, ORG_NAME, SPACE);

    assertThat(listRoutes.get(0)).isEqualTo(ROUTE_MAP);
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testListRoutesWithFailureExecutionStatus() throws InterruptedException {
    PcfConfig pcfConfig = PcfConfig.builder().build();
    CfCommandExecutionResponse perpetualTaskResponse = CfCommandExecutionResponse.builder()
                                                           .pcfCommandResponse(null)
                                                           .commandExecutionStatus(CommandExecutionStatus.FAILURE)
                                                           .errorMessage(ERROR_MSG)
                                                           .build();
    when(secretManager.getEncryptionDetails(pcfConfig, null, null)).thenReturn(Collections.emptyList());
    when(delegateService.executeTaskV2(any(DelegateTask.class))).thenReturn(perpetualTaskResponse);

    assertThatThrownBy(() -> pcfHelperService.listRoutes(pcfConfig, ORG_NAME, SPACE))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(ERROR_MSG);
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testCreateRoute() throws InterruptedException {
    PcfConfig pcfConfig = PcfConfig.builder().build();
    CfInfraMappingDataResponse cfInstanceSyncResponse = CfInfraMappingDataResponse.builder()
                                                            .commandExecutionStatus(SUCCESS)
                                                            .organizations(Collections.singletonList(ORG_NAME))
                                                            .routeMaps(Collections.singletonList(ROUTE_MAP))
                                                            .spaces(Collections.singletonList(SPACE))
                                                            .build();
    CfCommandExecutionResponse perpetualTaskResponse = CfCommandExecutionResponse.builder()
                                                           .pcfCommandResponse(cfInstanceSyncResponse)
                                                           .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
                                                           .build();

    when(secretManager.getEncryptionDetails(pcfConfig, null, null)).thenReturn(Collections.emptyList());
    when(delegateService.executeTaskV2(any(DelegateTask.class))).thenReturn(perpetualTaskResponse);

    String route =
        pcfHelperService.createRoute(pcfConfig, ORG_NAME, SPACE, "host", "domain", "path", false, false, 8080);

    assertThat(route).isEqualTo(ROUTE_MAP);
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testCreateRouteWithFailureExecutionStatus() throws InterruptedException {
    PcfConfig pcfConfig = PcfConfig.builder().build();
    CfCommandExecutionResponse perpetualTaskResponse = CfCommandExecutionResponse.builder()
                                                           .pcfCommandResponse(null)
                                                           .commandExecutionStatus(CommandExecutionStatus.FAILURE)
                                                           .errorMessage(ERROR_MSG)
                                                           .build();
    when(secretManager.getEncryptionDetails(pcfConfig, null, null)).thenReturn(Collections.emptyList());
    when(delegateService.executeTaskV2(any(DelegateTask.class))).thenReturn(perpetualTaskResponse);

    assertThatThrownBy(
        () -> pcfHelperService.createRoute(pcfConfig, ORG_NAME, SPACE, "host", "domain", "path", false, false, 8080))
        .isInstanceOf(InvalidRequestException.class)
        .hasMessage(ERROR_MSG);
  }
}
