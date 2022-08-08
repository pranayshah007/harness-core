/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.mappers.artifact;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.artifactory.ArtifactoryConfigRequest;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.beans.config.ArtifactoryConfig;
import software.wings.service.intfc.security.EncryptionService;

import java.util.List;

@OwnedBy(HarnessTeam.CDC)
public class ArtifactoryConfigToArtifactoryRequestMapper {
  public static ArtifactoryConfigRequest toArtifactoryRequest(
      ArtifactoryConfig config, EncryptionService encryptionService, List<EncryptedDataDetail> encryptedDetails) {
    if (config.hasCredentials()) {
      encryptionService.decrypt(config, encryptedDetails, false);
    }
    return ArtifactoryConfigRequest.builder()
        .artifactoryUrl(config.getArtifactoryUrl())
        .username(config.getUsername())
        .password(config.getPassword())
        .hasCredentials(config.hasCredentials())
        .build();
  }
}
