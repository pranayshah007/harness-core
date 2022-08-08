/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.mappers.artifact;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.nexus.NexusRequest;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.beans.config.NexusConfig;
import software.wings.service.intfc.security.EncryptionService;

import java.util.List;

@OwnedBy(HarnessTeam.CDC)
public class NexusConfigToNexusRequestMapper {
  public static NexusRequest toNexusRequest(
      NexusConfig nexusConfig, EncryptionService encryptionService, List<EncryptedDataDetail> encryptedDetails) {
    if (nexusConfig.hasCredentials()) {
      encryptionService.decrypt(nexusConfig, encryptedDetails, false);
    }
    return NexusRequest.builder()
        .nexusUrl(nexusConfig.getNexusUrl())
        .version(nexusConfig.getVersion())
        .username(nexusConfig.getUsername())
        .hasCredentials(nexusConfig.hasCredentials())
        .isCertValidationRequired(nexusConfig.isCertValidationRequired())
        .password(nexusConfig.getPassword())
        .build();
  }
}
