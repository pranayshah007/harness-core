/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.version;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.VersionInfoException;
import io.harness.serializer.YamlUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;

@Slf4j
@OwnedBy(PL)
public class VersionInfoManager {
  private static final String INIT_VERSION_INFO = "BUILD_VERSION: 0.0.0\n"
          + "BUILD_TIME: 000000-0000\n"
          + "BRANCH_NAME: unknown\n"
          + "COMMIT_SHA: 000000";

  private final VersionInfo versionInfo;
  private String fullVersion;

  private static String initVersionInfo() {
    String versionInfoYaml = VersionInfoManager.INIT_VERSION_INFO;

    try {
      // Load version.yaml from the specified path
      final InputStream stream = VersionInfoManager.class.getClassLoader().getResourceAsStream("/opt/harness/version.yaml");
      if (stream != null) {
        versionInfoYaml = IOUtils.toString(stream, StandardCharsets.UTF_8);
      }
    } catch (IOException exception) {
      log.error("Error reading version info from file", exception);
      throw new VersionInfoException(String.format("Failed to parse yaml content %s", versionInfoYaml), exception);
    }

    return versionInfoYaml;
  }

  public VersionInfoManager() {
    this(initVersionInfo());
  }

  public VersionInfo getVersionInfo() {
    return this.versionInfo;
  }
}
