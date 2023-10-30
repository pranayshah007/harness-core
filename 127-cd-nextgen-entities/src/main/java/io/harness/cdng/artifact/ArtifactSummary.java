/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.artifact;

import static io.harness.annotations.dev.HarnessTeam.PIPELINE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.artifact.bean.ArtifactCorrelationDetails;

import io.swagger.annotations.ApiModel;

@OwnedBy(PIPELINE)
@ApiModel(subTypes = {NexusArtifactSummary.class, ArtifactoryArtifactSummary.class, DockerArtifactSummary.class,
              CustomArtifactSummary.class, AcrArtifactSummary.class, ArtifactoryGenericArtifactSummary.class,
              EcrArtifactSummary.class, GcrArtifactSummary.class, S3ArtifactSummary.class, JenkinsArtifactSummary.class,
              GithubPackagesArtifactSummary.class, AzureArtifactsSummary.class, AMIArtifactSummary.class,
              GarArtifactSummary.class, BambooArtifactSummary.class})
public interface ArtifactSummary {
  String getType();
  String getDisplayName();
  default ArtifactCorrelationDetails getArtifactIdentity() {
    return null;
  }
}
