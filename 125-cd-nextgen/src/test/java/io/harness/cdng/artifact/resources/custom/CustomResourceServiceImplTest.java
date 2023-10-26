/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.artifact.resources.custom;

import static io.harness.rule.OwnerRule.SHIVAM;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.harness.CategoryTest;
import io.harness.beans.DelegateTaskRequest;
import io.harness.category.element.UnitTests;
import io.harness.connector.services.ConnectorService;
import io.harness.data.algorithm.HashGenerator;
import io.harness.delegate.task.artifacts.custom.CustomArtifactDelegateRequest;
import io.harness.delegate.task.artifacts.request.ArtifactTaskParameters;
import io.harness.delegate.task.artifacts.response.ArtifactTaskExecutionResponse;
import io.harness.delegate.task.artifacts.response.ArtifactTaskResponse;
import io.harness.logging.CommandExecutionStatus;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.rule.Owner;
import io.harness.secretmanagerclient.services.api.SecretManagerClientService;
import io.harness.service.DelegateGrpcClientWrapper;

import software.wings.helpers.ext.jenkins.BuildDetails;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

public class CustomResourceServiceImplTest extends CategoryTest {
  private static String ACCOUNT_ID = "accountId";
  private static String IMAGE_PATH = "imagePath";
  private static String ORG_IDENTIFIER = "orgIdentifier";
  private static String PROJECT_IDENTIFIER = "projectIdentifier";

  @Mock ConnectorService connectorService;
  @Mock SecretManagerClientService secretManagerClientService;
  @Mock DelegateGrpcClientWrapper delegateGrpcClientWrapper;
  @Spy @InjectMocks CustomResourceServiceImpl customResourceService;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  @Owner(developers = SHIVAM)
  @Category(UnitTests.class)
  public void testGetBuilds() {
    BuildDetails buildDetails = BuildDetails.Builder.aBuildDetails().withNumber("custom").build();
    ArtifactTaskResponse artifactTaskResponse =
        ArtifactTaskResponse.builder()
            .commandExecutionStatus(CommandExecutionStatus.SUCCESS)
            .artifactTaskExecutionResponse(
                ArtifactTaskExecutionResponse.builder().buildDetails(Collections.singletonList(buildDetails)).build())
            .build();

    Map<String, String> inputs = new HashMap<>();
    inputs.put("key", "value");

    when(delegateGrpcClientWrapper.executeSyncTaskV2(any())).thenReturn(artifactTaskResponse);

    List<BuildDetails> customResourceServiceBuilds = customResourceService.getBuilds("script", "versionPath",
        "arrayPath", inputs, ACCOUNT_ID, ORG_IDENTIFIER, PROJECT_IDENTIFIER, HashGenerator.generateIntegerHash(),
        Collections.singletonList(new TaskSelectorYaml("accountGroup")));
    assertThat(customResourceServiceBuilds).isNotNull();
    assertThat(customResourceServiceBuilds.get(0).getNumber()).isEqualTo("custom");

    ArgumentCaptor<DelegateTaskRequest> argumentCaptor = ArgumentCaptor.forClass(DelegateTaskRequest.class);
    verify(delegateGrpcClientWrapper).executeSyncTaskV2(argumentCaptor.capture());
    DelegateTaskRequest delegateTaskRequest = argumentCaptor.getValue();
    ArtifactTaskParameters artifactTaskParameters = (ArtifactTaskParameters) delegateTaskRequest.getTaskParameters();
    CustomArtifactDelegateRequest customArtifactDelegateRequest =
        (CustomArtifactDelegateRequest) artifactTaskParameters.getAttributes();
    assertThat(customArtifactDelegateRequest.getScript()).isEqualTo("script");
    assertThat(customArtifactDelegateRequest.getInputs()).isEqualTo(inputs);
  }
}
