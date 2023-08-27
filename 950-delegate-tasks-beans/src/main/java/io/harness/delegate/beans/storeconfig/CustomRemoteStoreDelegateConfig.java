/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.beans.storeconfig;

import io.harness.manifest.CustomManifestSource;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class CustomRemoteStoreDelegateConfig implements StoreDelegateConfig {
  CustomManifestSource customManifestSource;

  @Override
  public StoreDelegateConfigType getType() {
    return StoreDelegateConfigType.CUSTOM_REMOTE;
  }
  @Override
  public String getRepoUrl() {
    if (this.getCustomManifestSource().getFilePaths().size() == 1) {
      return new StringBuilder("custom://").append(this.getCustomManifestSource().getFilePaths().get(0)).toString();
    }
    return "custom";
  }
}
