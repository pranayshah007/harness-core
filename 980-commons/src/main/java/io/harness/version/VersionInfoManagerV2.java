/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.version;

import static io.harness.annotations.dev.HarnessTeam.PL;
import io.harness.annotations.dev.OwnedBy;

import io.harness.version.VersionInfoException;
import java.io.FileNotFoundException;
import org.yaml.snakeyaml.error.YAMLException;
import org.yaml.snakeyaml.Yaml;
import lombok.extern.slf4j.Slf4j;
import java.io.FileInputStream;
import java.io.InputStream;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Map;

@Slf4j
@OwnedBy(PL)
public class VersionInfoManagerV2 {

  // this file path will be same for all services
  private String versionFilePath = "/opt/harness/version.yaml";
  private VersionInfoV2 cachedVersionInfo;

  public VersionInfoV2 getVersionInfo() throws VersionInfoException {
    // Check if the version info is already cached
    if (cachedVersionInfo != null) {
      log.info("Returning cached version info.");
      return cachedVersionInfo;
    }
    try {
      InputStream inputStream = new FileInputStream(versionFilePath);

      // Parse YAML file
      Yaml yaml = new Yaml();
      Map<String, Object> data = yaml.load(inputStream);

      // Create a VersionInfo object to store the data
      VersionInfoV2 versionInfo = VersionInfoV2.builder()
              .buildVersion((String) data.get("BUILD_VERSION"))
              .buildTime(((Date) data.get("BUILD_TIME")).toInstant().truncatedTo(ChronoUnit.SECONDS))
              .branchName((String) data.get("BRANCH_NAME"))
              .commitSha((String) data.get("COMMIT_SHA"))
              .build(); // Build the VersionInfoV2 instance

      // Cache the version info
      cachedVersionInfo = versionInfo;

      return versionInfo;
    } catch (FileNotFoundException e) {
      log.error("Version file not found: " + e.getMessage(), e);
      throw new VersionInfoException("Version file not found: " + e.getMessage(), e);
    } catch (YAMLException | ClassCastException e) {
      log.error("Error parsing YAML file: " + e.getMessage(), e);
      throw new VersionInfoException("Error parsing YAML file: " + e.getMessage(), e);
    } catch (Exception e) {
      log.error("Failed to retrieve version info: " + e.getMessage(), e);
      throw new VersionInfoException("Failed to retrieve version info: " + e.getMessage(), e);
    }
  }
}
