/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.beans.storeconfig;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.task.filestore.FileStoreFetchFilesConfig;

import java.util.List;
import java.util.Map;
import lombok.EqualsAndHashCode;
import lombok.Singular;
import lombok.Value;
import lombok.experimental.SuperBuilder;

@Value
@SuperBuilder(toBuilder = true)
@EqualsAndHashCode(callSuper = true)
@OwnedBy(CDP)
public class S3StoreTFDelegateConfig extends FileStoreFetchFilesConfig {
  String bucketName;
  String region;
  @Singular List<String> paths;
  Map<String, String> versions;

  @Override
  public StoreDelegateConfigType getType() {
    return StoreDelegateConfigType.AMAZON_S3;
  }
}
