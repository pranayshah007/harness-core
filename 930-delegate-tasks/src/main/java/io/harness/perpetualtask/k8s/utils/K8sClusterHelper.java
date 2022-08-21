/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.perpetualtask.k8s.utils;

import io.harness.filesystem.FileIo;

import com.google.common.collect.Sets;
import com.google.inject.Singleton;
import java.io.IOException;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.Set;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Singleton
@Service
public class K8sClusterHelper {
  public static final String SEEN_CLUSTER_FILE_NAME = "k8sSeenClusters.txt";
  private static final String SEPARATOR = "$";
  private static final String DELIMITER = ",";
  private static final String CACHE_FORMAT = "%s%s%s";
  private static Set<String> seenClusterList;

  static {
    try {
      seenClusterList = loadFile();
      updateFile();
    } catch (Exception ex) {
      log.error("Error initializing seenClusterList, loadFile(). Harmless error.", ex);
      seenClusterList = new HashSet<>();
    }
  }

  public static boolean isSeen(@NonNull String clusterId, @NonNull String kubeSystemUid) {
    return isSeen(String.format(CACHE_FORMAT, clusterId, SEPARATOR, kubeSystemUid));
  }

  public static void setAsSeen(@NonNull String clusterId, @NonNull String kubeSystemUid) {
    setAsSeen(String.format(CACHE_FORMAT, clusterId, SEPARATOR, kubeSystemUid));
  }

  // Practically list of clusterIds shouldn't be much large, else we can 1) delete file after months/year or, 2) have
  // timestamp with each clusterId and evict only those which hasn't been accessed in a long time months/year

  private static Set<String> loadFile() throws Exception {
    log.info("loadFile {}", SEEN_CLUSTER_FILE_NAME);

    String fileContent = "";
    if (FileIo.checkIfFileExist(SEEN_CLUSTER_FILE_NAME)) {
      fileContent = readFileContent();
    } else {
      log.info("{} file was not present, creating one", SEEN_CLUSTER_FILE_NAME);
      FileIo.writeWithExclusiveLockAcrossProcesses("", SEEN_CLUSTER_FILE_NAME, StandardOpenOption.CREATE);
    }

    return Sets.newHashSet(fileContent.split(DELIMITER));
  }

  private static void updateFile() throws IOException {
    log.info("updateFile {}", SEEN_CLUSTER_FILE_NAME);
    String toWrite = String.join(DELIMITER, seenClusterList);
    FileIo.writeWithExclusiveLockAcrossProcesses(toWrite, SEEN_CLUSTER_FILE_NAME, StandardOpenOption.TRUNCATE_EXISTING);
  }

  private static String readFileContent() throws IOException {
    String rawContent = FileIo.getFileContentsWithSharedLockAcrossProcesses(SEEN_CLUSTER_FILE_NAME).trim();
    log.info("fileContent: {}", rawContent);

    if (!rawContent.contains(SEPARATOR)) {
      log.info("file is dirty, initializing to empty");
      rawContent = "";
    }
    return rawContent;
  }

  private static boolean isSeen(String clusterInfo) {
    log.info("Cluster {} is seen:{}", clusterInfo, seenClusterList.contains(clusterInfo));
    return seenClusterList.contains(clusterInfo);
  }

  private static void setAsSeen(String clusterInfo) {
    seenClusterList.add(clusterInfo);
    try {
      updateFile();
    } catch (IOException ex) {
      log.error("Failed to updateFile {} after setAsSeen", SEEN_CLUSTER_FILE_NAME, ex);
    }
  }

  public static void clean() throws IOException {
    FileIo.deleteFileIfExists(SEEN_CLUSTER_FILE_NAME);
  }
}
