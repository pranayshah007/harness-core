/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.artifactstream;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;

import software.wings.beans.artifact.ArtifactStream;
import software.wings.beans.artifact.ArtifactStreamType;

import com.google.common.collect.ImmutableMap;
import java.util.Map;

@OwnedBy(HarnessTeam.CDC)
public class ArtifactStreamFactory {
  private static final ArtifactStreamMapper gcrArtifactStreamMapper = new GCRArtifactStreamMapper();
  private static final ArtifactStreamMapper ecrArtifactStreamMapper = new ECRArtifactStreamMapper();
  private static final ArtifactStreamMapper dockerMapper = new DockerArtifactStreamMapper();
  private static final ArtifactStreamMapper artifactoryMapper = new ArtifactoryArtifactStreamMapper();

  private static final ArtifactStreamMapper nexusMapper = new NexusArtifactStreamMapper();

  private static final ArtifactStreamMapper jenkinsMapper = new JenkinsArtifactStreamMapper();

  private static final ArtifactStreamMapper customArtifactMapper = new CustomArtifactStreamMapper();

  private static final ArtifactStreamMapper azureArtifactMapper = new AzureArtifactsArtifactStreamMapper();

  private static final ArtifactStreamMapper amazonS3Mapper = new AmazonS3ArtifactStreamMapper();

  private static final ArtifactStreamMapper acrMapper = new ACRArtifactStreamMapper();
  private static final ArtifactStreamMapper amiMapper = new AmiArtifactStreamMapper();
  private static final Map<ArtifactStreamType, ArtifactStreamMapper> ARTIFACT_STREAM_MAPPER_MAP =
      ImmutableMap.<ArtifactStreamType, ArtifactStreamMapper>builder()
          .put(ArtifactStreamType.ARTIFACTORY, artifactoryMapper)
          .put(ArtifactStreamType.DOCKER, dockerMapper)
          .put(ArtifactStreamType.GCR, gcrArtifactStreamMapper)
          .put(ArtifactStreamType.ECR, ecrArtifactStreamMapper)
          .put(ArtifactStreamType.NEXUS, nexusMapper)
          .put(ArtifactStreamType.JENKINS, jenkinsMapper)
          .put(ArtifactStreamType.CUSTOM, customArtifactMapper)
          .put(ArtifactStreamType.AZURE_ARTIFACTS, azureArtifactMapper)
          .put(ArtifactStreamType.AMAZON_S3, amazonS3Mapper)
          .put(ArtifactStreamType.ACR, acrMapper)
          .put(ArtifactStreamType.AMI, amiMapper)
          .build();

  public static ArtifactStreamMapper getArtifactStreamMapper(ArtifactStream artifactStream) {
    return getArtifactStreamMapper(artifactStream.getArtifactStreamType());
  }

  public static ArtifactStreamMapper getArtifactStreamMapper(String streamType) {
    ArtifactStreamType artifactStreamType = ArtifactStreamType.valueOf(streamType);
    if (ARTIFACT_STREAM_MAPPER_MAP.containsKey(artifactStreamType)) {
      return ARTIFACT_STREAM_MAPPER_MAP.get(artifactStreamType);
    }
    throw new InvalidRequestException(String.format("Unsupported artifact stream of type %s", streamType));
  }
}
