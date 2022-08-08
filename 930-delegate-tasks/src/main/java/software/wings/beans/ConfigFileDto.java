/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans;

import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Value;

@Value
@AllArgsConstructor
public class ConfigFileDto {
  private final String uuid;
  private final String relativeFilePath;
  private final Map<String, Integer> envVersionMap;
  private final long size;
  private final boolean encrypted;
  private final int defaultVersion;

  public int getVersionForEnv(String envId) {
    if (envVersionMap.containsKey(envId)) {
      return envVersionMap.get(envId);
    }

    return defaultVersion;
  }
}
