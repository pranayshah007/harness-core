/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.cf;

import static io.harness.delegate.cf.CfTestConstants.ACCOUNT_ID;
import static io.harness.delegate.cf.CfTestConstants.ACTIVITY_ID;
import static io.harness.delegate.cf.CfTestConstants.APP_NAME;
import static io.harness.delegate.cf.CfTestConstants.URL;
import static io.harness.delegate.cf.CfTestConstants.USER_NAME_DECRYPTED;
import static io.harness.rule.OwnerRule.ADWAIT;
import static io.harness.rule.OwnerRule.BOJANA;
import static io.harness.rule.OwnerRule.IVAN;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.beans.pcf.CfInternalConfig;
import io.harness.delegate.task.pcf.CfCommandRequest;
import io.harness.delegate.task.pcf.request.CfCommandDeployRequest;
import io.harness.delegate.task.pcf.request.CfInstanceSyncRequest;
import io.harness.delegate.task.pcf.response.CfCommandExecutionResponse;
import io.harness.delegate.task.pcf.response.CfInstanceSyncResponse;
import io.harness.exception.InvalidArgumentsException;
import io.harness.logging.CommandExecutionStatus;
import io.harness.pcf.CfDeploymentManager;
import io.harness.pcf.PivotalClientApiException;
import io.harness.rule.Owner;
import io.harness.security.encryption.EncryptedDataDetail;
import io.harness.security.encryption.SecretDecryptionService;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import org.cloudfoundry.operations.applications.ApplicationDetail;
import org.cloudfoundry.operations.applications.InstanceDetail;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@OwnedBy(HarnessTeam.CDP)
@RunWith(MockitoJUnitRunner.class)
public class PcfApplicationDetailsCommandTaskHandlerTest extends CategoryTest {
  @Mock private SecretDecryptionService encryptionService;
  @Mock private ILogStreamingTaskClient logStreamingTaskClient;
  @Mock private CfDeploymentManager pcfDeploymentManager;

  @InjectMocks private PcfApplicationDetailsCommandTaskHandler pcfApplicationDetailsCommandTaskHandler;

  @Test
  @Owner(developers = {ADWAIT, IVAN})
  @Category(UnitTests.class)
  public void testPerformAppDetails() throws PivotalClientApiException {
    CfInstanceSyncRequest cfInstanceSyncRequest = CfInstanceSyncRequest.builder()
                                                      .pcfCommandType(CfCommandRequest.PcfCommandType.APP_DETAILS)
                                                      .pcfApplicationName(APP_NAME)
                                                      .pcfConfig(getPcfConfig())
                                                      .accountId(ACCOUNT_ID)
                                                      .timeoutIntervalInMin(5)
                                                      .build();

    doReturn(ApplicationDetail.builder()
                 .id("10")
                 .diskQuota(1)
                 .instances(1)
                 .memoryLimit(1)
                 .name("a_s_e__6")
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
                 .id("Guid:a_s_e__3")
                 .diskQuota(1)
                 .instances(1)
                 .memoryLimit(1)
                 .name("a_s_e__3")
                 .requestedState("RUNNING")
                 .stack("")
                 .runningInstances(1)
                 .build())
        .when(pcfDeploymentManager)
        .getApplicationByName(any());

    // Fetch Orgs
    CfCommandExecutionResponse cfCommandExecutionResponse = pcfApplicationDetailsCommandTaskHandler.executeTaskInternal(
        cfInstanceSyncRequest, null, logStreamingTaskClient, false);

    assertThat(cfCommandExecutionResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    CfInstanceSyncResponse pcfInstanceSyncResponse =
        (CfInstanceSyncResponse) cfCommandExecutionResponse.getPcfCommandResponse();

    assertThat(pcfInstanceSyncResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    assertThat(pcfInstanceSyncResponse.getInstanceIndices()).isNotNull();
    assertThat(pcfInstanceSyncResponse.getInstanceIndices()).hasSize(1);
    assertThat(pcfInstanceSyncResponse.getInstanceIndices().get(0)).isEqualTo("2");
  }

  @Test
  @Owner(developers = {BOJANA, IVAN})
  @Category(UnitTests.class)
  public void testPcfApplicationDetailsCommandTaskHandlerInvalidArgumentsException() throws IOException {
    CfCommandRequest cfCommandRequest = mock(CfCommandDeployRequest.class);
    when(cfCommandRequest.getActivityId()).thenReturn(ACTIVITY_ID);
    List<EncryptedDataDetail> encryptedDataDetails = Collections.singletonList(EncryptedDataDetail.builder().build());
    when(encryptionService.getDecryptedValue(any())).thenReturn("decryptedValue".toCharArray());
    try {
      pcfApplicationDetailsCommandTaskHandler.executeTask(
          cfCommandRequest, encryptedDataDetails, false, logStreamingTaskClient);
    } catch (Exception e) {
      assertThatExceptionOfType(InvalidArgumentsException.class);
      InvalidArgumentsException invalidArgumentsException = (InvalidArgumentsException) e;
      assertThat(invalidArgumentsException.getParams())
          .containsValue("cfCommandRequest: Must be instance of CfInstanceSyncRequest");
    }
  }

  @Test
  @Owner(developers = IVAN)
  @Category(UnitTests.class)
  public void testFailedCollectingPCFApplicationDetails() throws PivotalClientApiException {
    CfInstanceSyncRequest cfInstanceSyncRequest = CfInstanceSyncRequest.builder()
                                                      .pcfCommandType(CfCommandRequest.PcfCommandType.APP_DETAILS)
                                                      .pcfApplicationName(APP_NAME)
                                                      .pcfConfig(getPcfConfig())
                                                      .accountId(ACCOUNT_ID)
                                                      .timeoutIntervalInMin(5)
                                                      .build();

    doThrow(new RuntimeException("Error msg")).when(pcfDeploymentManager).getApplicationByName(any());

    CfCommandExecutionResponse cfCommandExecutionResponse = pcfApplicationDetailsCommandTaskHandler.executeTaskInternal(
        cfInstanceSyncRequest, null, logStreamingTaskClient, false);

    assertThat(cfCommandExecutionResponse.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.FAILURE);
    assertThat(cfCommandExecutionResponse.getErrorMessage()).isEqualTo("RuntimeException: Error msg");
  }

  private CfInternalConfig getPcfConfig() {
    return CfInternalConfig.builder().username(USER_NAME_DECRYPTED).endpointUrl(URL).password(new char[0]).build();
  }
}
