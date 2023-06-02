/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.artifacts.mappers;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.rule.OwnerRule.ALLU_VAMSI;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.annotations.dev.OwnedBy;
import io.harness.category.element.UnitTests;
import io.harness.delegate.beans.connector.gcpconnector.GcpConnectorCredentialDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpConnectorDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpCredentialType;
import io.harness.delegate.task.artifacts.googlecloudstorage.GoogleCloudStorageArtifactDelegateRequest;
import io.harness.delegate.task.artifacts.googlecloudstorage.GoogleCloudStorageArtifactDelegateResponse;
import io.harness.rule.Owner;

import software.wings.helpers.ext.jenkins.BuildDetails;

import java.util.Arrays;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@OwnedBy(CDP)
public class GoogleCloudStorageRequestResponseMapperTest {
  @Rule public MockitoRule mockitoRule = MockitoJUnit.rule();

  private final String PROJECT = "cd-play";
  private final String BUCKET = "bucket";
  private final String PATH = "path";

  @Test
  @Owner(developers = ALLU_VAMSI)
  @Category(UnitTests.class)
  public void toGoogleCloudStorageResponseListTest() {
    GcpConnectorCredentialDTO credential =
        GcpConnectorCredentialDTO.builder().gcpCredentialType(GcpCredentialType.INHERIT_FROM_DELEGATE).build();
    GcpConnectorDTO gcpConnectorDTO = GcpConnectorDTO.builder().credential(credential).build();
    GoogleCloudStorageArtifactDelegateRequest artifactDelegateRequest =
        GoogleCloudStorageArtifactDelegateRequest.builder()
            .project(PROJECT)
            .bucket(BUCKET)
            .artifactPath(PATH)
            .gcpConnectorDTO(gcpConnectorDTO)
            .build();
    BuildDetails build =
        BuildDetails.Builder.aBuildDetails().withBuildUrl("url").withNumber("1").withArtifactPath(PATH).build();
    List<BuildDetails> builds = Arrays.asList(build);
    GoogleCloudStorageArtifactDelegateResponse gcsResponse =
        GoogleCloudStorageRequestResponseMapper.toGoogleCloudStorageResponseList(builds, artifactDelegateRequest)
            .get(0);
    assertThat(gcsResponse.getBuildDetails().getBuildUrl()).isEqualTo(builds.get(0).getBuildUrl());
    assertThat(gcsResponse.getBuildDetails().getNumber()).isEqualTo(builds.get(0).getNumber());
    assertThat(gcsResponse.getProject()).isEqualTo(artifactDelegateRequest.getProject());
    assertThat(gcsResponse.getBucket()).isEqualTo(artifactDelegateRequest.getBucket());
    assertThat(gcsResponse.getArtifactPath()).isEqualTo(build.getArtifactPath());
  }
}
