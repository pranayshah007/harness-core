/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.citasks.cik8handler.helper;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.impl.scm.ScmGitProviderMapper.DESTINATION_CA_PATH;
import static io.harness.impl.scm.ScmGitProviderMapper.SHARED_CA_CERTS_PATH;

import io.harness.utils.system.SystemWrapper;

import com.google.inject.Singleton;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class SecretVolumesHelper {
  public static final String CI_MOUNT_VOLUMES = "CI_MOUNT_VOLUMES";
  public static final String HARNESS_SHARED_CERTS_PATH = "/harness-shared-certs-path/";

  // Reads the list of volumes to be mounted on the build containers and returns
  // a mapping from source paths to a list of destination paths.
  // SHARED_CA_CERTS_PATH- This is internal directory path contains all relevant certs bundle
  // DESTINATION_CA_PATH-This is list of all paths is set by user which is the list of all destination paths in build
  // pods
  // Add all certs files mapping present in SHARED_CA_CERTS_PATH to DESTINATION_CA_PATH(list) if it is set. else add all
  // the certs from CI_MOUNT_VOLUMES to maintain backward compatibility(remove this later) This is a comma separated
  // list of <src-path>:<dest-path> mappings. All these volumes would be available in the build containers. Example
  // value of this setting on the delegate: CI_MOUNT_VOLUMES =
  // /src/path/a.crt:/dest/path/b.crt,/src/path/a.crt:/dest/path/c.crt,/src/path/d.crt:/dest/d.crt
  public Map<String, List<String>> getSecretVolumeMappings() {
    Map<String, List<String>> secretVolumeMappings = new HashMap<>();

    String sourceDirPath = getEnvVariable(SHARED_CA_CERTS_PATH);
    String destinationPaths = getEnvVariable(DESTINATION_CA_PATH);

    // Check if SHARED_CA_CERTS_PATH is set, then create secretVolumeMappings with the default location within pod
    if (!isEmpty(sourceDirPath)) {
      List<String> fileList = listFilesInDir(sourceDirPath);

      fileList.forEach(
          (f) -> secretVolumeMappings.put(f, Collections.singletonList(HARNESS_SHARED_CERTS_PATH + getName(f))));
    }

    // Check if SHARED_CA_CERTS_PATH and DESTINATION_CA_PATH is set, then create the secretVolumeMappings from these
    // if not then add from CI_MOUNT_VOLUMES
    if (checkSecretVolumesConfiguredV2(sourceDirPath, destinationPaths)) {
      String sourceCertPathConcatenated = sourceDirPath + "/single-cert-path/all-certs.pem";

      List<String> destinationList = Arrays.asList(destinationPaths.split(","));

      if (fileExists(sourceCertPathConcatenated)) {
        log.info("Adding cert from source path {} ", sourceCertPathConcatenated);
        secretVolumeMappings.put(sourceCertPathConcatenated, destinationList);
        return secretVolumeMappings;
      } else {
        log.warn("Could not add certs from {} as source file {} does not exist", SHARED_CA_CERTS_PATH,
            sourceCertPathConcatenated);
      }
    }

    // Add all certs from CI_MOUNT_VOLUMES if it is set else return empty map
    if (!checkSecretVolumesConfigured()) {
      log.warn("cannot find valid certs set with CI_MOUNT_VOLUMES");
      return secretVolumeMappings;
    }
    log.warn("cannot find valid certs or destination not set. Moving on to use certs specified with CI_MOUNT_VOLUMES");

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

  private String getEnvVariable(String variableName) {
    String envVariableValue = "";
    try {
      envVariableValue = SystemWrapper.getenv(variableName);
      log.info("{} is set with value: {}", variableName, envVariableValue);
    } catch (SecurityException e) {
      log.error("Don't have sufficient permissions to query {}", variableName, e);
      return "";
    }
    return envVariableValue;
  }

  public boolean checkSecretVolumesConfiguredV2(String srcPath, String destPath) {
    return !isEmpty(srcPath) && !isEmpty(destPath);
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

  private static List<String> listFilesInDir(String directoryPath) {
    File directory = new File(directoryPath);
    List<String> filePaths = new ArrayList<>();

    if (directory.exists() && directory.isDirectory()) {
      File[] files = directory.listFiles();

      if (files != null) {
        for (File file : files) {
          if (file.isFile()) {
            filePaths.add(file.getAbsolutePath());
          }
        }
      }
    }
    return filePaths;
  }
}
