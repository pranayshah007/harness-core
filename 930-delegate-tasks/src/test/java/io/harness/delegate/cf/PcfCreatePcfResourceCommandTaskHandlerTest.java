/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.cf;

import static io.harness.delegate.task.pcf.CfCommandRequest.PcfCommandType.CREATE_ROUTE;
import static io.harness.rule.OwnerRule.ANIL;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;

import io.harness.CategoryTest;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.beans.pcf.CfInternalConfig;
import io.harness.delegate.task.pcf.request.CfCommandDeployRequest;
import io.harness.delegate.task.pcf.request.CfInfraMappingDataRequest;
import io.harness.delegate.task.pcf.response.CfCommandExecutionResponse;
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
import java.util.Collections;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@OwnedBy(HarnessTeam.CDP)
public class PcfCreatePcfResourceCommandTaskHandlerTest extends CategoryTest {
  @Mock private CfDeploymentManager pcfDeploymentManager;
  @Mock private LogCallback logCallback;
  @Mock ILogStreamingTaskClient logStreamingTaskClient;
  @Mock SecretDecryptionService secretDecryptionService;
  @InjectMocks @Inject PcfCreatePcfResourceCommandTaskHandler pcfSetupCommandTaskHandler;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testExecuteInternalSuccess() {
    CfInfraMappingDataRequest mappingDataRequest =
        CfInfraMappingDataRequest.builder()
            .pcfCommandType(CREATE_ROUTE)
            .pcfConfig(CfInternalConfig.builder().username("test".toCharArray()).password("test".toCharArray()).build())
            .timeoutIntervalInMin(10)
            .build();
    List<EncryptedDataDetail> encryptedDataDetails = Collections.emptyList();
    CfCommandExecutionResponse response = pcfSetupCommandTaskHandler.executeTaskInternal(
        mappingDataRequest, encryptedDataDetails, logStreamingTaskClient, false);
    assertThat(response).isNotNull();
    assertThat(response.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
  }

  @Test
  @Owner(developers = ANIL)
  @Category(UnitTests.class)
  public void testExecuteInternalFailure() throws PivotalClientApiException, InterruptedException {
    reset(pcfDeploymentManager);
    CfInfraMappingDataRequest mappingDataRequest =
        CfInfraMappingDataRequest.builder()
            .pcfCommandType(CREATE_ROUTE)
            .pcfConfig(CfInternalConfig.builder().username("test".toCharArray()).password("test".toCharArray()).build())
            .timeoutIntervalInMin(10)
            .build();
    List<EncryptedDataDetail> encryptedDataDetails = Collections.emptyList();
    doAnswer(invocation -> { throw new Exception(); })
        .when(pcfDeploymentManager)
        .createRouteMap(any(CfRequestConfig.class), any(), any(), any(), anyBoolean(), anyBoolean(), any());
    CfCommandExecutionResponse response = pcfSetupCommandTaskHandler.executeTaskInternal(
        mappingDataRequest, encryptedDataDetails, logStreamingTaskClient, false);
    assertThat(response).isNotNull();
    assertThat(response.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.FAILURE);

    CfCommandDeployRequest deployRequest = CfCommandDeployRequest.builder().build();
    assertThatThrownBy(()
                           -> pcfSetupCommandTaskHandler.executeTaskInternal(
                               deployRequest, encryptedDataDetails, logStreamingTaskClient, false))
        .isInstanceOf(InvalidArgumentsException.class);
  }
}
