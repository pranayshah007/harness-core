/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.jenkins;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;
import static io.harness.rule.OwnerRule.SHIVAM;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;

import io.harness.CategoryTest;
import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.cdng.jenkins.jenkinsstep.JenkinsBuildStepHelperServiceImpl;
import io.harness.common.NGTimeConversionHelper;
import io.harness.connector.ConnectorDTO;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResourceClient;
import io.harness.delegate.beans.connector.docker.DockerConnectorDTO;
import io.harness.delegate.beans.connector.jenkins.JenkinsConnectorDTO;
import io.harness.delegate.task.artifacts.jenkins.JenkinsArtifactDelegateRequest;
import io.harness.delegate.task.artifacts.response.ArtifactTaskExecutionResponse;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.remote.client.NGRestUtils;
import io.harness.rule.Owner;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.serializer.KryoSerializer;
import io.harness.steps.StepUtils;

import java.util.Optional;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

@OwnedBy(PIPELINE)
@RunWith(MockitoJUnitRunner.class)
public class JenkinsBuildStepHelperServiceImplTest extends CategoryTest {
  @Mock private ConnectorResourceClient connectorResourceClient;
  @Mock private SecretManagerClientService secretManagerClientService;
  @Mock private KryoSerializer kryoSerializer;
  @InjectMocks JenkinsBuildStepHelperServiceImpl jenkinsBuildStepHelperService;
  ArtifactTaskExecutionResponse artifactTaskExecutionResponse;

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testPrepareTestRequest() {
    MockedStatic<NGRestUtils> aStatic = Mockito.mockStatic(NGRestUtils.class);
    MockedStatic<NGTimeConversionHelper> aStatic2 = Mockito.mockStatic(NGTimeConversionHelper.class);
    aStatic2.when(() -> NGTimeConversionHelper.convertTimeStringToMilliseconds(any())).thenReturn(0L);
    Mockito.mockStatic(StepUtils.class);
    Ambiance ambiance = Ambiance.newBuilder()
                            .putSetupAbstractions("accountId", "accountId")
                            .putSetupAbstractions("orgIdentifier", "orgIdentifier")
                            .putSetupAbstractions("projectIdentifier", "projectIdentifier")
                            .build();
    aStatic.when(() -> NGRestUtils.getResponse(any())).thenReturn(Optional.empty());
    assertThatCode(()
                       -> jenkinsBuildStepHelperService.prepareTaskRequest(
                           JenkinsArtifactDelegateRequest.builder(), ambiance, "connectorref", "time", "task"))
        .isInstanceOf(InvalidRequestException.class);
    aStatic.when(() -> NGRestUtils.getResponse(any()))
        .thenReturn(Optional.of(
            ConnectorDTO.builder()
                .connectorInfo(ConnectorInfoDTO.builder().connectorConfig(DockerConnectorDTO.builder().build()).build())
                .build()));
    assertThatCode(()
                       -> jenkinsBuildStepHelperService.prepareTaskRequest(
                           JenkinsArtifactDelegateRequest.builder(), ambiance, "connectorref", "time", "task"))
        .isInstanceOf(InvalidRequestException.class);
    aStatic.when(() -> NGRestUtils.getResponse(any()))
        .thenReturn(Optional.of(
            ConnectorDTO.builder()
                .connectorInfo(
                    ConnectorInfoDTO.builder().connectorConfig(JenkinsConnectorDTO.builder().build()).build())
                .build()));
    // when(artifactTaskExecutionResponse.getJenkinsBuildTaskNGResponse().getQueuedBuildUrl()).thenReturn(any());
    ArtifactTaskExecutionResponse artifactTaskExecutionResponse = mock(ArtifactTaskExecutionResponse.class);
    assertThatCode(()
                       -> jenkinsBuildStepHelperService.prepareTaskRequest(
                           JenkinsArtifactDelegateRequest.builder(), ambiance, "connectorref", "time", "task"))
        .isInstanceOf(RuntimeException.class);
  }
}
