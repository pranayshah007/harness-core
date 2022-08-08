/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.polling.bean.artifact;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.artifact.bean.ArtifactConfig;
import io.harness.cdng.artifact.bean.yaml.AmazonS3ArtifactConfig;
import io.harness.delegate.task.artifacts.ArtifactSourceType;
import io.harness.pms.yaml.ParameterField;

import lombok.Builder;
import lombok.Value;

@OwnedBy(HarnessTeam.CDC)
@Value
@Builder
public class S3ArtifactInfo implements ArtifactInfo {
  String connectorRef;
  String bucketName;
  String filePathRegex;
  String filePath;

  @Override
  public ArtifactSourceType getType() {
    return ArtifactSourceType.AMAZONS3;
  }

  @Override
  public ArtifactConfig toArtifactConfig() {
    return AmazonS3ArtifactConfig.builder()
        .connectorRef(ParameterField.<String>builder().value(connectorRef).build())
        .bucketName(ParameterField.<String>builder().value(bucketName).build())
        .filePath(ParameterField.<String>builder().value(filePath).build())
        .filePathRegex(ParameterField.<String>builder().value(filePathRegex).build())
        .build();
  }
}
