/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.googlefunctions;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.PRAGYESH;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.connector.gcpconnector.GcpConnectorDTO;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.beans.logstreaming.NGDelegateLogCallback;
import io.harness.delegate.googlefunction.GoogleFunctionPrepareRollbackCommandTaskHandler;
import io.harness.delegate.task.googlefunction.GoogleFunctionCommandTaskHelper;
import io.harness.delegate.task.googlefunctionbeans.GcpGoogleFunctionInfraConfig;
import io.harness.delegate.task.googlefunctionbeans.request.GoogleFunctionPrepareRollbackRequest;
import io.harness.delegate.task.googlefunctionbeans.response.GoogleFunctionCommandResponse;
import io.harness.delegate.task.googlefunctionbeans.response.GoogleFunctionPrepareRollbackResponse;
import io.harness.logging.CommandExecutionStatus;
import io.harness.rule.Owner;

import com.google.cloud.functions.v2.Function;
import com.google.cloud.functions.v2.ServiceConfig;
import com.google.cloud.run.v2.Service;
import java.util.Optional;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@OwnedBy(CDP)
public class GoogleFunctionPrepareRollbackCommandTaskHandlerTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Spy
  @InjectMocks
  private GoogleFunctionPrepareRollbackCommandTaskHandler googleFunctionPrepareRollbackCommandTaskHandler;
  @Mock private ILogStreamingTaskClient iLogStreamingTaskClient;
  @Mock private GoogleFunctionCommandTaskHelper googleFunctionCommandTaskHelper;

  @Test
  @Owner(developers = PRAGYESH)
  @Category(UnitTests.class)
  public void executeTaskInternalSecondDeploymentTest() throws Exception {
    GoogleFunctionPrepareRollbackRequest googleFunctionPrepareRollbackRequest =
        GoogleFunctionPrepareRollbackRequest.builder()
            .googleFunctionInfraConfig(GcpGoogleFunctionInfraConfig.builder()
                                           .gcpConnectorDTO(GcpConnectorDTO.builder().build())
                                           .project("project")
                                           .region("region")
                                           .build())
            .googleFunctionDeployManifestContent("deploy")
            .build();
    Function function =
        Function.newBuilder().setServiceConfig(ServiceConfig.newBuilder().setService("abc").build()).build();
    Service service = Service.newBuilder().build();
    doNothing()
        .when(googleFunctionCommandTaskHelper)
        .parseStringContentAsClassBuilder(anyString(), any(), isA(NGDelegateLogCallback.class), anyString());

    doReturn(Optional.of(function))
        .when(googleFunctionCommandTaskHelper)
        .getFunction(eq(null), isA(GcpConnectorDTO.class), anyString(), anyString(), isA(NGDelegateLogCallback.class));
    doReturn(true).when(googleFunctionCommandTaskHelper).validateTrafficInExistingRevisions(any());
    doReturn(service)
        .when(googleFunctionCommandTaskHelper)
        .getCloudRunService(
            anyString(), isA(GcpConnectorDTO.class), eq("project"), eq("region"), isA(NGDelegateLogCallback.class));
    doReturn(Optional.of("abc")).when(googleFunctionCommandTaskHelper).getCloudRunServiceName(isA(Function.class));
    GoogleFunctionCommandResponse response = googleFunctionPrepareRollbackCommandTaskHandler.executeTask(
        googleFunctionPrepareRollbackRequest, iLogStreamingTaskClient, null);
    assertThat(response).isInstanceOf(GoogleFunctionPrepareRollbackResponse.class);
    assertThat(response.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    assertThat(((GoogleFunctionPrepareRollbackResponse) response).isFirstDeployment()).isFalse();
    verify(googleFunctionCommandTaskHelper, times(1))
        .parseStringContentAsClassBuilder(anyString(), any(), isA(NGDelegateLogCallback.class), anyString());
  }

  @Test
  @Owner(developers = PRAGYESH)
  @Category(UnitTests.class)
  public void executeTaskInternalFirstDeploymentTest() throws Exception {
    GoogleFunctionPrepareRollbackRequest googleFunctionPrepareRollbackRequest =
        GoogleFunctionPrepareRollbackRequest.builder()
            .googleFunctionInfraConfig(GcpGoogleFunctionInfraConfig.builder()
                                           .gcpConnectorDTO(GcpConnectorDTO.builder().build())
                                           .project("project")
                                           .region("region")
                                           .build())
            .googleFunctionDeployManifestContent("deploy")
            .build();
    Function function = Function.newBuilder().setServiceConfig(ServiceConfig.newBuilder().build()).build();
    Service service = Service.newBuilder().build();
    doNothing()
        .when(googleFunctionCommandTaskHelper)
        .parseStringContentAsClassBuilder(anyString(), any(), isA(NGDelegateLogCallback.class), anyString());

    doReturn(Optional.empty())
        .when(googleFunctionCommandTaskHelper)
        .getFunction(
            anyString(), isA(GcpConnectorDTO.class), anyString(), anyString(), isA(NGDelegateLogCallback.class));
    GoogleFunctionCommandResponse response = googleFunctionPrepareRollbackCommandTaskHandler.executeTask(
        googleFunctionPrepareRollbackRequest, iLogStreamingTaskClient, null);
    assertThat(response).isInstanceOf(GoogleFunctionPrepareRollbackResponse.class);
    assertThat(response.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    assertThat(((GoogleFunctionPrepareRollbackResponse) response).isFirstDeployment()).isTrue();
    verify(googleFunctionCommandTaskHelper, times(1))
        .parseStringContentAsClassBuilder(anyString(), any(), isA(NGDelegateLogCallback.class), anyString());
  }
}
