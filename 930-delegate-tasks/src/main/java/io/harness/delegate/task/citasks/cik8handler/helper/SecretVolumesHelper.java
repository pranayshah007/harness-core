/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.citasks.cik8handler.helper;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.SystemWrapper;

import com.google.inject.Singleton;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class SecretVolumesHelper {
  public static final String CI_MOUNT_VOLUMES = "CI_MOUNT_VOLUMES";

  // Reads the list of volumes to be mounted on the build containers and returns
  // a mapping from source paths to a list of destination paths.
  // This is a comma separated list of <src-path>:<dest-path> mappings. All these volumes would be available in the
  // build containers.
  // Example value of this setting on the delegate:
  // CI_MOUNT_VOLUMES = /src/path/a.crt:/dest/path/b.crt,/src/path/a.crt:/dest/path/c.crt,/src/path/d.crt:/dest/d.crt
  public Map<String, List<String>> getSecretVolumeMappings() {
    Map<String, List<String>> secretVolumeMappings = new HashMap<>();
    if (!checkSecretVolumesConfigured()) {
      return secretVolumeMappings;
    }

    String mountVolumes = SystemWrapper.getenv(CI_MOUNT_VOLUMES);
    String mountVolumesList[] = mountVolumes.split(",");
    if (isEmpty(mountVolumesList)) {
      return secretVolumeMappings;
    }

    HashMap<String, Boolean> map = new HashMap<>();
    for (String mountVolume : mountVolumesList) {
      String srcDest[] = mountVolume.split(":");
      if (srcDest.length != 2) {
        log.warn(
            "String: {} does not match the expected format of <src>:<dest>. Skipping: {}", mountVolume, mountVolume);
        continue;
      }
      String srcPath = srcDest[0];
      String destPath = srcDest[1];

      if (!fileExists(srcPath)) {
        log.warn("Could not find file at path: {}. Skipping: {}", srcPath, mountVolume);
        continue;
      }

      // If the destination path was already part of some other mapping, log a warning and ignore this mapping
      if (map.containsKey(destPath)) {
        log.warn("Destination: {} is part of multiple mounted volumes. Skipping {}", destPath, mountVolume);
        continue;
      }
      map.put(destPath, true);
      secretVolumeMappings.computeIfAbsent(srcPath, k -> new ArrayList<>()).add(destPath);
    }
    return secretVolumeMappings;
  }

  public boolean checkSecretVolumesConfigured() {
    try {
      String mountVolumes = SystemWrapper.getenv(CI_MOUNT_VOLUMES);
      if (isEmpty(mountVolumes)) {
        return false;
      }
    } catch (SecurityException e) {
      log.error("Don't have sufficient permission to query CI_MOUNT_VOLUMES", e);
      return false;
    }
    return true;
  }

  public boolean fileExists(String path) {
    File file = new File(path);
    if (!file.exists()) {
      return false;
    }
    return true;
  }

  // Converts a file path to a unique valid identifier in kubernetes
  // /path/to/a.txt and /other/path/to/a.txt should generate different hashes
  // Example: a-txt-100928 and a-txt-14257
  public String hashFilePath(String filePath) {
    return getName(filePath).replace(".", "-") + filePath.hashCode();
  }

  public String getName(String filePath) {
    String[] tokens = filePath.split("[/\\\\]");
    return tokens[tokens.length - 1];
  }

  public String getSecretKey(String prefix, String srcPath) {
    return prefix + "-" + hashFilePath(srcPath);
  }

  public List<String> getAllSecretKeys(String prefix) {
    Map<String, List<String>> secretVolumeMappings = getSecretVolumeMappings();
    List<String> secretKeys = new ArrayList<>();
    secretVolumeMappings.forEach((k, v) -> secretKeys.add(getSecretKey(prefix, k)));
    return secretKeys;
  }
}
