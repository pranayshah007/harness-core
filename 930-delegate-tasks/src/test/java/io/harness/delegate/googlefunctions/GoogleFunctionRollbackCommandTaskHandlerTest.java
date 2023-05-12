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
import io.harness.delegate.googlefunction.GoogleFunctionRollbackCommandTaskHandler;
import io.harness.delegate.task.googlefunction.GoogleFunctionCommandTaskHelper;
import io.harness.delegate.task.googlefunctionbeans.GcpGoogleFunctionInfraConfig;
import io.harness.delegate.task.googlefunctionbeans.GoogleFunction;
import io.harness.delegate.task.googlefunctionbeans.request.GoogleFunctionRollbackRequest;
import io.harness.delegate.task.googlefunctionbeans.response.GoogleFunctionCommandResponse;
import io.harness.delegate.task.googlefunctionbeans.response.GoogleFunctionRollbackResponse;
import io.harness.logging.CommandExecutionStatus;
import io.harness.rule.Owner;

import com.google.cloud.functions.v2.Function;
import java.util.Optional;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import wiremock.com.google.common.collect.Lists;

@OwnedBy(CDP)
public class GoogleFunctionRollbackCommandTaskHandlerTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Spy @InjectMocks private GoogleFunctionRollbackCommandTaskHandler googleFunctionRollbackCommandTaskHandler;
  @Mock private ILogStreamingTaskClient iLogStreamingTaskClient;
  @Mock private GoogleFunctionCommandTaskHelper googleFunctionCommandTaskHelper;

  @Test
  @Owner(developers = PRAGYESH)
  @Category(UnitTests.class)
  public void executeTaskInternalSecondDeploymentTest() throws Exception {
    GoogleFunctionRollbackRequest googleFunctionRollbackRequest =
        GoogleFunctionRollbackRequest.builder()
            .googleFunctionInfraConfig(GcpGoogleFunctionInfraConfig.builder()
                                           .gcpConnectorDTO(GcpConnectorDTO.builder().build())
                                           .project("project")
                                           .region("region")
                                           .build())
            .isFirstDeployment(false)
            .googleFunctionAsString("function")
            .googleCloudRunServiceAsString("cloud-run")
            .build();
    Function function = Function.newBuilder().build();
    GoogleFunction googleFunction = GoogleFunction.builder()
                                        .cloudRunService(GoogleFunction.GoogleCloudRunService.builder().build())
                                        .activeCloudRunRevisions(Lists.newArrayList())
                                        .build();
    doNothing()
        .when(googleFunctionCommandTaskHelper)
        .parseStringContentAsClassBuilder(anyString(), any(), isA(NGDelegateLogCallback.class), anyString());
    doNothing()
        .when(googleFunctionCommandTaskHelper)
        .updateFullTrafficToSingleRevision(
            anyString(), anyString(), any(), anyString(), anyString(), isA(NGDelegateLogCallback.class));

    doReturn(Optional.of(function))
        .when(googleFunctionCommandTaskHelper)
        .getFunction(eq(""), isA(GcpConnectorDTO.class), anyString(), anyString(), isA(NGDelegateLogCallback.class));
    doReturn(googleFunction)
        .when(googleFunctionCommandTaskHelper)
        .getGoogleFunction(eq(function),
            eq((GcpGoogleFunctionInfraConfig) googleFunctionRollbackRequest.getGoogleFunctionInfraConfig()),
            isA(NGDelegateLogCallback.class));
    GoogleFunctionCommandResponse response = googleFunctionRollbackCommandTaskHandler.executeTask(
        googleFunctionRollbackRequest, iLogStreamingTaskClient, null);
    assertThat(response).isInstanceOf(GoogleFunctionRollbackResponse.class);
    assertThat(response.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    assertThat(response.getFunction()).isEqualTo(googleFunction);
    verify(googleFunctionCommandTaskHelper)
        .getFunction(eq(""), isA(GcpConnectorDTO.class), anyString(), anyString(), isA(NGDelegateLogCallback.class));
    verify(googleFunctionCommandTaskHelper, times(2))
        .parseStringContentAsClassBuilder(anyString(), any(), isA(NGDelegateLogCallback.class), anyString());
    verify(googleFunctionCommandTaskHelper)
        .getGoogleFunction(eq(function),
            eq((GcpGoogleFunctionInfraConfig) googleFunctionRollbackRequest.getGoogleFunctionInfraConfig()),
            isA(NGDelegateLogCallback.class));
  }

  @Test
  @Owner(developers = PRAGYESH)
  @Category(UnitTests.class)
  public void executeTaskInternalFirstDeploymentTest() throws Exception {
    GoogleFunctionRollbackRequest googleFunctionRollbackRequest =
        GoogleFunctionRollbackRequest.builder()
            .googleFunctionInfraConfig(GcpGoogleFunctionInfraConfig.builder()
                                           .gcpConnectorDTO(GcpConnectorDTO.builder().build())
                                           .project("project")
                                           .region("region")
                                           .build())
            .isFirstDeployment(true)
            .googleFunctionAsString("function")
            .googleCloudRunServiceAsString("cloud-run")
            .googleFunctionDeployManifestContent("func")
            .build();
    Function function = Function.newBuilder().build();
    GoogleFunction googleFunction = GoogleFunction.builder()
                                        .cloudRunService(GoogleFunction.GoogleCloudRunService.builder().build())
                                        .activeCloudRunRevisions(Lists.newArrayList())
                                        .build();
    doNothing()
        .when(googleFunctionCommandTaskHelper)
        .parseStringContentAsClassBuilder(anyString(), any(), isA(NGDelegateLogCallback.class), anyString());

    doNothing()
        .when(googleFunctionCommandTaskHelper)
        .deleteFunction(eq(""), isA(GcpConnectorDTO.class), anyString(), anyString(), isA(NGDelegateLogCallback.class));
    GoogleFunctionCommandResponse response = googleFunctionRollbackCommandTaskHandler.executeTask(
        googleFunctionRollbackRequest, iLogStreamingTaskClient, null);
    assertThat(response).isInstanceOf(GoogleFunctionRollbackResponse.class);
    assertThat(response.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    verify(googleFunctionCommandTaskHelper, times(1))
        .parseStringContentAsClassBuilder(anyString(), any(), isA(NGDelegateLogCallback.class), anyString());
  }
}
