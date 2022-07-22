/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.mappers.artifact;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.artifacts.docker.beans.DockerInternalConfig;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.connector.docker.DockerRegistryProviderType;

import software.wings.beans.DockerConfig;

import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.CDC)
@UtilityClass
public class DockerConfigToInternalMapper {
  public DockerInternalConfig toDockerInternalConfig(DockerConfig dockerConfig) {
    String password =
        EmptyPredicate.isNotEmpty(dockerConfig.getPassword()) ? new String(dockerConfig.getPassword()) : null;

    return DockerInternalConfig.builder()
        .providerType(DockerRegistryProviderType.OTHER)
        .dockerRegistryUrl(dockerConfig.getDockerRegistryUrl())
        .isCertValidationRequired(dockerConfig.isCertValidationRequired())
        .username(dockerConfig.getUsername())
        .password(password)
        .build();
  }
}
