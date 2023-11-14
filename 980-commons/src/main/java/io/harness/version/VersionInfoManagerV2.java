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

@Slf4j
@OwnedBy(PL)
public class VersionInfoManagerV2 {

  // this file path will be same for all services
  private String versionFilePath = "/opt/harness/version.yaml";

  public VersionInfoV2 getVersionInfo() {
    try {
      InputStream inputStream = new FileInputStream(versionFilePath);

      // Parse YAML file
      Yaml yaml = new Yaml();
      Map<String, Object> data = yaml.load(inputStream);

      // Create a VersionInfo object to store the data
      VersionInfoV2 versionInfo = VersionInfoV2.builder()
              .buildVersion((String) data.get("BUILD_VERSION"))
              .buildTime((Date) data.get("BUILD_TIME")) // Cast to Date
              .branchName((String) data.get("BRANCH_NAME"))
              .commitSha((String) data.get("COMMIT_SHA"))
              .build(); // Build the VersionInfoV2 instance

      return versionInfo;
    } catch (Exception e) {
      log.error("Failed to retrieve version info: " + e.getMessage(), e);
      throw new RuntimeException("Failed to retrieve version info: " + e.getMessage(), e);
    }
  }
}
