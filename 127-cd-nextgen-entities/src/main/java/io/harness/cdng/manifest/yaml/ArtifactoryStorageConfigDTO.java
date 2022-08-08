/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.manifest.yaml;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.manifest.ManifestStoreType;
import io.harness.pms.yaml.ParameterField;

import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
@OwnedBy(HarnessTeam.CDP)
public class ArtifactoryStorageConfigDTO implements FileStorageConfigDTO {
  String connectorRef;
  String repositoryName;
  List<String> artifactPaths;

  @Override
  public String getKind() {
    return ManifestStoreType.ARTIFACTORY;
  }

  @Override
  public FileStorageStoreConfig toFileStorageStoreConfig() {
    return ArtifactoryStoreConfig.builder()
        .connectorRef(ParameterField.createValueField(connectorRef))
        .repositoryName(ParameterField.createValueField(repositoryName))
        .artifactPaths(ParameterField.createValueField(artifactPaths))
        .build();
  }
}
