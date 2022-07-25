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
}
