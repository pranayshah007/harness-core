/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.beans.storeconfig;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.task.filestore.FileStoreFetchFilesConfig;

import java.util.List;
import lombok.EqualsAndHashCode;
import lombok.Value;
import lombok.experimental.SuperBuilder;

@Value
@SuperBuilder(toBuilder = true)
@EqualsAndHashCode(callSuper = true)
@OwnedBy(CDP)
@RecasterAlias("io.harness.delegate.beans.storeconfig.ArtifactoryStoreDelegateConfig")
public class ArtifactoryStoreDelegateConfig extends FileStoreFetchFilesConfig {
  String repositoryName;
  List<String> artifacts;

  @Override
  public StoreDelegateConfigType getType() {
    return StoreDelegateConfigType.ARTIFACTORY;
  }
}