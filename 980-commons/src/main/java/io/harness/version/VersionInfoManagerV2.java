/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.version;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;

import org.yaml.snakeyaml.Yaml;
import lombok.extern.slf4j.Slf4j;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Date;
import java.util.Map;


@OwnedBy(PL)
public class VersionInfoManagerV2 {
  private static final String versionFilePath = "/opt/harness/version.yaml";

  public VersionInfoManagerV2(String versionFilePath) {
    this.versionFilePath = versionFilePath;
  }

  public VersionInfoV2 getVersionInfo() {
    try {
      InputStream inputStream = new FileInputStream(versionFilePath);

      // Parse YAML file
      Yaml yaml = new Yaml();
      Map<String, Object> data = yaml.load(inputStream);

      // Create a VersionInfo object to store the data
      VersionInfoV2 versionInfo = new VersionInfo();
      versionInfo.setBuildVersion((String) data.get("BUILD_VERSION"));
      versionInfo.setBuildTime((Date) data.get("BUILD_TIME")); // Cast to Date
      versionInfo.setBranchName((String) data.get("BRANCH_NAME"));
      versionInfo.setCommitSha((String) data.get("COMMIT_SHA"));

      return versionInfo;
    } catch (Exception e) {
      throw new RuntimeException("Failed to retrieve version info: " + e.getMessage(), e);
    }
  }

}