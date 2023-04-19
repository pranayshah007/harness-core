/*
 * Copyright 2023 Harness Inc. All rights reserved.
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
import java.util.Map;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@OwnedBy(HarnessTeam.CDP)
public class S3StorageConfigDTO implements FileStorageConfigDTO {
  String connectorRef;
  String region;
  String bucket;
  String folderPath;
  List<String> paths;
  Map<String, String> versions;

  @Override
  public String getKind() {
    return ManifestStoreType.S3;
  }

  @Override
  public FileStorageStoreConfig toFileStorageStoreConfig() {
    return S3StoreConfig.builder()
        .connectorRef(ParameterField.createValueField(connectorRef))
        .region(ParameterField.createValueField(region))
        .bucketName(ParameterField.createValueField(bucket))
        .paths(ParameterField.createValueField(paths))
        .folderPath(ParameterField.createValueField(folderPath))
        .build();
  }
}
