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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.logstreaming.ILogStreamingTaskClient;
import io.harness.delegate.beans.logstreaming.NGDelegateLogCallback;
import io.harness.delegate.googlefunction.GoogleFunctionDeployWithoutTrafficCommandTaskHandler;
import io.harness.delegate.task.googlefunction.GoogleFunctionCommandTaskHelper;
import io.harness.delegate.task.googlefunctionbeans.GcpGoogleFunctionInfraConfig;
import io.harness.delegate.task.googlefunctionbeans.GoogleCloudStorageArtifactConfig;
import io.harness.delegate.task.googlefunctionbeans.GoogleFunction;
import io.harness.delegate.task.googlefunctionbeans.request.GoogleFunctionDeployWithoutTrafficRequest;
import io.harness.delegate.task.googlefunctionbeans.response.GoogleFunctionCommandResponse;
import io.harness.delegate.task.googlefunctionbeans.response.GoogleFunctionDeployWithoutTrafficResponse;
import io.harness.logging.CommandExecutionStatus;
import io.harness.rule.Owner;

import com.google.cloud.functions.v2.Function;
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
public class GoogleFunctionDeployWithoutTrafficCommandTaskHandlerTest extends CategoryTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  @Spy
  @InjectMocks
  private GoogleFunctionDeployWithoutTrafficCommandTaskHandler googleFunctionDeployWithoutTrafficCommandTaskHandler;

  @Mock private ILogStreamingTaskClient iLogStreamingTaskClient;
  @Mock private GoogleFunctionCommandTaskHelper googleFunctionCommandTaskHelper;

  @Test
  @Owner(developers = PRAGYESH)
  @Category(UnitTests.class)
  public void executeTaskInternalTest() throws Exception {
    GoogleFunctionDeployWithoutTrafficRequest googleFunctionDeployWithoutTrafficRequest =
        GoogleFunctionDeployWithoutTrafficRequest.builder()
            .googleFunctionInfraConfig(GcpGoogleFunctionInfraConfig.builder().build())
            .googleFunctionArtifactConfig(GoogleCloudStorageArtifactConfig.builder().build())
            .googleFunctionDeployManifestContent("deploy")
            .updateFieldMaskContent("update")
            .build();
    Function function = Function.newBuilder().build();
    GoogleFunction googleFunction = GoogleFunction.builder()
                                        .cloudRunService(GoogleFunction.GoogleCloudRunService.builder().build())
                                        .activeCloudRunRevisions(Lists.newArrayList())
                                        .build();
    doReturn(function)
        .when(googleFunctionCommandTaskHelper)
        .deployFunction(
            eq((GcpGoogleFunctionInfraConfig) googleFunctionDeployWithoutTrafficRequest.getGoogleFunctionInfraConfig()),
            eq(googleFunctionDeployWithoutTrafficRequest.getGoogleFunctionDeployManifestContent()),
            eq(googleFunctionDeployWithoutTrafficRequest.getUpdateFieldMaskContent()),
            eq(googleFunctionDeployWithoutTrafficRequest.getGoogleFunctionArtifactConfig()), eq(false),
            isA(NGDelegateLogCallback.class));
    doReturn(googleFunction)
        .when(googleFunctionCommandTaskHelper)
        .getGoogleFunction(eq(function),
            eq((GcpGoogleFunctionInfraConfig) googleFunctionDeployWithoutTrafficRequest.getGoogleFunctionInfraConfig()),
            isA(NGDelegateLogCallback.class));
    GoogleFunctionCommandResponse response = googleFunctionDeployWithoutTrafficCommandTaskHandler.executeTask(
        googleFunctionDeployWithoutTrafficRequest, iLogStreamingTaskClient, null);
    assertThat(response).isInstanceOf(GoogleFunctionDeployWithoutTrafficResponse.class);
    assertThat(response.getCommandExecutionStatus()).isEqualTo(CommandExecutionStatus.SUCCESS);
    assertThat(response.getFunction()).isEqualTo(googleFunction);
    verify(googleFunctionCommandTaskHelper)
        .deployFunction(
            eq((GcpGoogleFunctionInfraConfig) googleFunctionDeployWithoutTrafficRequest.getGoogleFunctionInfraConfig()),
            eq(googleFunctionDeployWithoutTrafficRequest.getGoogleFunctionDeployManifestContent()),
            eq(googleFunctionDeployWithoutTrafficRequest.getUpdateFieldMaskContent()),
            eq(googleFunctionDeployWithoutTrafficRequest.getGoogleFunctionArtifactConfig()), eq(false),
            isA(NGDelegateLogCallback.class));
    verify(googleFunctionCommandTaskHelper)
        .getGoogleFunction(eq(function),
            eq((GcpGoogleFunctionInfraConfig) googleFunctionDeployWithoutTrafficRequest.getGoogleFunctionInfraConfig()),
            isA(NGDelegateLogCallback.class));
  }
}
