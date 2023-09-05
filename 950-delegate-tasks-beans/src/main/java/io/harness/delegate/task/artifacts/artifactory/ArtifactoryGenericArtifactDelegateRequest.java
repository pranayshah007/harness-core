/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.artifacts.artifactory;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.delegate.beans.connector.artifactoryconnector.ArtifactoryConnectorDTO;
import io.harness.delegate.task.artifacts.ArtifactSourceType;
import io.harness.security.encryption.EncryptedDataDetail;

import java.util.List;
import lombok.Builder;
import lombok.Value;

/**
 * DTO object to be passed to delegate tasks.
 */

@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = false, components = {HarnessModuleComponent.CDS_ARTIFACTS})
@Value
@Builder
@OwnedBy(HarnessTeam.CDP)
public class ArtifactoryGenericArtifactDelegateRequest implements ArtifactoryBaseArtifactDelegateRequest {
  /** Images in repos need to be referenced via a path. */
  String artifactDirectory;
  String artifactPathFilter;
  String repositoryName;
  String artifactPath;
  /** Repository format - package type */
  String repositoryFormat;
  String connectorRef;
  /** Encrypted details for decrypting.*/
  List<EncryptedDataDetail> encryptedDataDetails;
  /** Artifactory Connector*/
  ArtifactoryConnectorDTO artifactoryConnectorDTO;
  /** Artifact Source type.*/
  ArtifactSourceType sourceType;
  String artifactFilter;
}