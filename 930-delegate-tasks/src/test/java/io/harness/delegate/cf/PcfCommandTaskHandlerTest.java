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
import static io.harness.delegate.cf.CfTestConstants.URL;
import static io.harness.delegate.cf.CfTestConstants.USER_NAME_DECRYPTED;
import static io.harness.delegate.cf.PcfCommandTaskBaseHelper.DELIMITER;
import static io.harness.delegate.task.pcf.request.CfInfraMappingDataRequest.ActionType.RUNNING_COUNT;
import static io.harness.rule.OwnerRule.ADWAIT;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
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
import io.harness.delegate.beans.pcf.CfInternalConfig;
import io.harness.delegate.beans.pcf.CfServiceData;
import io.harness.delegate.beans.pcf.ResizeStrategy;
import io.harness.delegate.task.pcf.CfCommandRequest;
import io.harness.delegate.task.pcf.CfCommandRequest.PcfCommandType;
import io.harness.delegate.task.pcf.PcfManifestsPackage;
import io.harness.delegate.task.pcf.request.CfCommandDeployRequest;
import io.harness.delegate.task.pcf.request.CfInfraMappingDataRequest;
import io.harness.delegate.task.pcf.request.CfInfraMappingDataRequest.ActionType;
import io.harness.delegate.task.pcf.response.CfCommandExecutionResponse;
import io.harness.delegate.task.pcf.response.CfDeployCommandResponse;
import io.harness.delegate.task.pcf.response.CfInfraMappingDataResponse;
import io.harness.exception.InvalidArgumentsException;
import io.harness.filesystem.FileIo;
import io.harness.logging.CommandExecutionStatus;
import io.harness.logging.LogCallback;
import io.harness.pcf.CfDeploymentManager;
import io.harness.pcf.model.CfAppAutoscalarRequestData;
import io.harness.pcf.model.CfRequestConfig;
import io.harness.rule.Owner;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.SecretDecryptionService;

import com.google.inject.Inject;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.cloudfoundry.operations.applications.ApplicationDetail;
import org.cloudfoundry.operations.applications.ApplicationSummary;
import org.cloudfoundry.operations.applications.InstanceDetail;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

@OwnedBy(HarnessTeam.CDP)
public class PcfCommandTaskHandlerTest extends CategoryTest {
  @Mock CfDeploymentManager cfDeploymentManager;
  @Mock SecretDecryptionService encryptionService;
  @Mock EncryptedDataDetail encryptedDataDetail;
  @Mock LogCallback executionLogCallback;
  @Mock ILogStreamingTaskClient logStreamingTaskClient;
  @InjectMocks @Spy PcfCommandTaskBaseHelper pcfCommandTaskHelper;
  @InjectMocks @Inject PcfDeployCommandTaskHandler pcfDeployCommandTaskHandler;
  @InjectMocks @Inject PcfDataFetchCommandTaskHandler pcfDataFetchCommandTaskHandler;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    doReturn(executionLogCallback).when(logStreamingTaskClient).obtainLogCallback(anyString());
  }

  private CfInternalConfig getPcfConfig() {
    return CfInternalConfig.builder().username(USER_NAME_DECRYPTED).endpointUrl(URL).password(new char[0]).build();
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testPerformDeployNonBlueGreen() throws Exception {
    CfCommandRequest cfCommandRequest =
        CfCommandDeployRequest.builder()
            .pcfCommandType(PcfCommandType.RESIZE)
            .resizeStrategy(ResizeStrategy.DOWNSIZE_OLD_FIRST)
            .pcfConfig(getPcfConfig())
            .accountId(ACCOUNT_ID)
            .newReleaseName("a_s_e__6")
            .organization(ORG)
            .space(SPACE)
            .updateCount(2)
            .downSizeCount(1)
            .totalPreviousInstanceCount(2)
            .timeoutIntervalInMin(2)
            .downsizeAppDetail(
                CfAppSetupTimeDetails.builder().applicationName("a_s_e__4").initialInstanceCount(2).build())
            .build();

    ApplicationDetail applicationDetailNew = ApplicationDetail.builder()
                                                 .id("10")
                                                 .diskQuota(1)
                                                 .instances(0)
                                                 .memoryLimit(1)
                                                 .name("a_s_e__6")
                                                 .requestedState("STOPPED")
                                                 .stack("")
                                                 .runningInstances(0)
                                                 .build();

    ApplicationDetail applicationDetailOld = ApplicationDetail.builder()
                                                 .id("10")
                                                 .diskQuota(1)
                                                 .instances(2)
                                                 .memoryLimit(1)
                                                 .name("a_s_e__4")
                                                 .requestedState("RUNNING")
                                                 .stack("")
                                                 .runningInstances(0)
                                                 .build();
    doReturn(applicationDetailNew)
        .doReturn(applicationDetailOld)
        .doReturn(applicationDetailNew)
        .when(cfDeploymentManager)
        .getApplicationByName(any());

    List<ApplicationSummary> previousReleases = new ArrayList<>();
    previousReleases.add(ApplicationSummary.builder()
                             .name("a_s_e__6")
                             .diskQuota(1)
                             .requestedState(RUNNING)
                             .id("1")
                             .instances(0)
                             .memoryLimit(1)
                             .runningInstances(1)
                             .build());
    previousReleases.add(ApplicationSummary.builder()
                             .name("a_s_e__4")
                             .diskQuota(1)
                             .requestedState(RUNNING)
                             .id("1")
                             .instances(2)
                             .memoryLimit(1)
                             .runningInstances(1)
                             .build());
    previousReleases.add(ApplicationSummary.builder()
                             .name("a_s_e__3")
                             .diskQuota(1)
                             .requestedState(RUNNING)
                             .id("1")
                             .instances(0)
                             .memoryLimit(1)
                             .runningInstances(1)
                             .build());

    doReturn(previousReleases).when(cfDeploymentManager).getPreviousReleases(any(), anyString());
    doReturn(previousReleases).when(cfDeploymentManager).getDeployedServicesWithNonZeroInstances(any(), anyString());

    doReturn(ApplicationDetail.builder()
                 .id("10")
                 .diskQuota(1)
                 .instances(1)
                 .memoryLimit(1)
                 .name("a_s_e__4")
                 .requestedState("STOPPED")
                 .stack("")
                 .runningInstances(0)
                 .instanceDetails(InstanceDetail.builder()
                                      .cpu(1.0)
                                      .diskQuota((long) 1.23)
                                      .diskUsage((long) 1.23)
                                      .index("0")
                                      .memoryQuota((long) 1)
                                      .memoryUsage((long) 1)
                                      .build())
                 .build())
        .when(cfDeploymentManager)
        .resizeApplication(any());

    doReturn(ApplicationDetail.builder()
                 .id("10")
                 .requestedState("RUNNING")
                 .stack("")
                 .diskQuota(1)
                 .instances(2)
                 .memoryLimit(1)
                 .name("a_s_e__6")
                 .runningInstances(2)
                 .instanceDetails(InstanceDetail.builder()
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
                         .build())
                 .build())
        .when(cfDeploymentManager)
        .upsizeApplicationWithSteadyStateCheck(any(), any());

    CfCommandExecutionResponse cfCommandExecutionResponse =
        pcfDeployCommandTaskHandler.executeTaskInternal(cfCommandRequest, null, logStreamingTaskClient, false);

    assertThat(cfCommandExecutionResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    CfDeployCommandResponse pcfDeployCommandResponse =
        (CfDeployCommandResponse) cfCommandExecutionResponse.getPcfCommandResponse();

    assertThat(pcfDeployCommandResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    List<CfServiceData> cfServiceData = pcfDeployCommandResponse.getInstanceDataUpdated();
    assertThat(cfServiceData).hasSize(2);
    for (CfServiceData data : cfServiceData) {
      if (data.getName().equals("a_s_e__4")) {
        assertThat(data.getPreviousCount()).isEqualTo(2);
        assertThat(data.getDesiredCount()).isEqualTo(1);
      } else if (data.getName().equals("a_s_e__6")) {
        assertThat(data.getPreviousCount()).isEqualTo(0);
        assertThat(data.getDesiredCount()).isEqualTo(2);
      }
    }
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testConfigureAutoscalarIfNeeded() throws Exception {
    CfCommandDeployRequest pcfCommandRequest = CfCommandDeployRequest.builder()
                                                   .downSizeCount(1)
                                                   .totalPreviousInstanceCount(2)
                                                   .timeoutIntervalInMin(2)
                                                   .useAppAutoscalar(false)
                                                   .maxCount(1)
                                                   .updateCount(1)
                                                   .build();

    ApplicationDetail applicationDetail = ApplicationDetail.builder()
                                              .id(APP_ID)
                                              .diskQuota(1)
                                              .instances(0)
                                              .memoryLimit(1)
                                              .name(APP_NAME)
                                              .requestedState("STOPPED")
                                              .stack("")
                                              .runningInstances(0)
                                              .build();

    reset(cfDeploymentManager);
    doReturn(applicationDetail).when(cfDeploymentManager).getApplicationByName(any());

    // Autoscalar is false
    pcfDeployCommandTaskHandler.configureAutoscalarIfNeeded(
        pcfCommandRequest, applicationDetail, null, executionLogCallback);
    verify(cfDeploymentManager, never()).performConfigureAutoscalar(any(), any());

    // Autoscalar is true, but no autosaclar file
    pcfCommandRequest.setUseAppAutoscalar(true);
    pcfDeployCommandTaskHandler.configureAutoscalarIfNeeded(
        pcfCommandRequest, applicationDetail, null, executionLogCallback);
    verify(cfDeploymentManager, never()).performConfigureAutoscalar(any(), any());

    // Autoscalar is true, autoscalar file is present
    String path = "./test" + System.currentTimeMillis();
    FileIo.createDirectoryIfDoesNotExist(path);

    pcfCommandRequest.setPcfManifestsPackage(PcfManifestsPackage.builder().autoscalarManifestYml("abc").build());
    CfAppAutoscalarRequestData autoscalarRequestData = CfAppAutoscalarRequestData.builder()
                                                           .applicationName(APP_NAME)
                                                           .applicationGuid(APP_ID)
                                                           .configPathVar(path)
                                                           .build();

    pcfDeployCommandTaskHandler.configureAutoscalarIfNeeded(
        pcfCommandRequest, applicationDetail, autoscalarRequestData, executionLogCallback);
    ArgumentCaptor<CfAppAutoscalarRequestData> captor = ArgumentCaptor.forClass(CfAppAutoscalarRequestData.class);
    verify(cfDeploymentManager, times(1)).performConfigureAutoscalar(captor.capture(), any());
    autoscalarRequestData = captor.getValue();
    assertThat(autoscalarRequestData.getApplicationName()).isEqualTo(APP_NAME);
    assertThat(autoscalarRequestData.getApplicationGuid()).isEqualTo(APP_ID);
    String filePath = autoscalarRequestData.getAutoscalarFilePath();

    String content = new String(Files.readAllBytes(Paths.get(filePath)));
    assertThat(content).isEqualTo("abc");
    FileIo.deleteDirectoryAndItsContentIfExists(path);
  }

  @Test
  @Owner(developers = ADWAIT)
  @Category(UnitTests.class)
  public void testperformDataFetch() throws Exception {
    CfInfraMappingDataRequest pcfCommandRequest = CfInfraMappingDataRequest.builder()
                                                      .pcfCommandType(PcfCommandType.DATAFETCH)
                                                      .pcfConfig(getPcfConfig())
                                                      .accountId(ACCOUNT_ID)
                                                      .timeoutIntervalInMin(5)
                                                      .actionType(ActionType.FETCH_ORG)
                                                      .build();

    doReturn(Arrays.asList(ORG)).when(cfDeploymentManager).getOrganizations(any());
    doReturn(Arrays.asList(SPACE)).when(cfDeploymentManager).getSpacesForOrganization(any());
    doReturn(Arrays.asList("R1", "R2")).when(cfDeploymentManager).getRouteMaps(any());

    // Fetch Orgs
    CfCommandExecutionResponse cfCommandExecutionResponse =
        pcfDataFetchCommandTaskHandler.executeTaskInternal(pcfCommandRequest, null, logStreamingTaskClient, false);

    assertThat(cfCommandExecutionResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    CfInfraMappingDataResponse pcfInfraMappingDataResponse =
        (CfInfraMappingDataResponse) cfCommandExecutionResponse.getPcfCommandResponse();

    assertThat(pcfInfraMappingDataResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    assertThat(pcfInfraMappingDataResponse.getOrganizations()).isNotNull();
    assertThat(pcfInfraMappingDataResponse.getOrganizations()).hasSize(1);
    assertThat(pcfInfraMappingDataResponse.getOrganizations().get(0)).isEqualTo(ORG);

    // Fetch Spaces for org
    pcfCommandRequest.setActionType(ActionType.FETCH_SPACE);
    pcfCommandRequest.setOrganization(ORG);
    cfCommandExecutionResponse =
        pcfDataFetchCommandTaskHandler.executeTaskInternal(pcfCommandRequest, null, logStreamingTaskClient, false);

    assertThat(cfCommandExecutionResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    pcfInfraMappingDataResponse = (CfInfraMappingDataResponse) cfCommandExecutionResponse.getPcfCommandResponse();

    assertThat(pcfInfraMappingDataResponse.getSpaces()).isNotNull();
    assertThat(pcfInfraMappingDataResponse.getSpaces()).hasSize(1);
    assertThat(pcfInfraMappingDataResponse.getSpaces().get(0)).isEqualTo(SPACE);

    // Fetch Routes
    pcfCommandRequest.setActionType(ActionType.FETCH_ROUTE);
    pcfCommandRequest.setSpace(SPACE);
    cfCommandExecutionResponse =
        pcfDataFetchCommandTaskHandler.executeTaskInternal(pcfCommandRequest, null, logStreamingTaskClient, false);
    pcfInfraMappingDataResponse = (CfInfraMappingDataResponse) cfCommandExecutionResponse.getPcfCommandResponse();
    assertThat(pcfInfraMappingDataResponse.getRouteMaps()).isNotNull();
    assertThat(pcfInfraMappingDataResponse.getRouteMaps()).hasSize(2);
    assertThat(pcfInfraMappingDataResponse.getRouteMaps().contains("R1")).isTrue();
    assertThat(pcfInfraMappingDataResponse.getRouteMaps().contains("R2")).isTrue();

    // Fetch running count
    String appNamePrefix = "App";
    ApplicationSummary applicationSummary = ApplicationSummary.builder()
                                                .id("id1")
                                                .name(appNamePrefix + DELIMITER + "1")
                                                .diskQuota(1)
                                                .instances(1)
                                                .memoryLimit(1)
                                                .requestedState("RUNNING")
                                                .runningInstances(2)
                                                .build();
    doReturn(Collections.singletonList(applicationSummary))
        .when(cfDeploymentManager)
        .getPreviousReleases(any(CfRequestConfig.class), eq(appNamePrefix));
    pcfCommandRequest.setApplicationNamePrefix(appNamePrefix);
    pcfCommandRequest.setActionType(RUNNING_COUNT);
    cfCommandExecutionResponse =
        pcfDataFetchCommandTaskHandler.executeTaskInternal(pcfCommandRequest, null, logStreamingTaskClient, false);
    pcfInfraMappingDataResponse = (CfInfraMappingDataResponse) cfCommandExecutionResponse.getPcfCommandResponse();
    assertThat(pcfInfraMappingDataResponse).isNotNull();
    assertThat(pcfInfraMappingDataResponse.getRunningInstanceCount()).isEqualTo(2);

    // Fetch running count failure
    doAnswer(invocation -> { throw new Exception(); })
        .when(cfDeploymentManager)
        .getPreviousReleases(any(CfRequestConfig.class), eq(appNamePrefix));
    cfCommandExecutionResponse =
        pcfDataFetchCommandTaskHandler.executeTaskInternal(pcfCommandRequest, null, logStreamingTaskClient, false);
    assertThat(cfCommandExecutionResponse).isNotNull();
    assertThat(cfCommandExecutionResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.FAILURE);

    CfCommandDeployRequest deployRequest = CfCommandDeployRequest.builder().build();
    assertThatThrownBy(
        () -> pcfDataFetchCommandTaskHandler.executeTaskInternal(deployRequest, null, logStreamingTaskClient, false))
        .isInstanceOf(InvalidArgumentsException.class);
  }
}
