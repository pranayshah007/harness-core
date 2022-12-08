package io.harness.delegate.clienttools;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RequiredArgsConstructor
@Getter
@Slf4j
public enum KubeloginVersion implements ClientToolVersion {
  V0_0_24("0.0.24");
  private final String version;
}
