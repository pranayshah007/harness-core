/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.artifact.ArtifactUtilities.getFileParentPath;
import static io.harness.artifact.ArtifactUtilities.getFileSearchPattern;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static software.wings.helpers.ext.jenkins.BuildDetails.Builder.aBuildDetails;

import io.harness.annotations.dev.OwnedBy;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.helpers.ext.jenkins.BuildDetails;
import software.wings.helpers.ext.jenkins.BuildMetadataKeys;
import software.wings.service.intfc.security.EncryptionService;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.hierynomus.msfscc.fileinformation.FileIdBothDirectoryInformation;
import com.hierynomus.smbj.SMBClient;
import com.hierynomus.smbj.SmbConfig;
import com.hierynomus.smbj.auth.AuthenticationContext;
import com.hierynomus.smbj.connection.Connection;
import com.hierynomus.smbj.session.Session;
import com.hierynomus.smbj.share.DiskShare;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDC)
@Singleton
@Slf4j
public class SmbHelperService {
  @Inject private EncryptionService encryptionService;
  private String buildNo = "";

  public void checkConnection(software.wings.beans.SmbConfig smbConfig, List<EncryptedDataDetail> encryptionDetails)
      throws IOException {
    encryptionService.decrypt(smbConfig, encryptionDetails, false);
    try (SMBClient client = new SMBClient(getSMBConnectionConfig());
         Connection connection = client.connect(getSMBConnectionHost(smbConfig.getSmbUrl()))) {
      AuthenticationContext ac =
          new AuthenticationContext(smbConfig.getUsername(), smbConfig.getPassword(), smbConfig.getDomain());
      Session session = connection.authenticate(ac);
      session.close();
    }
  }

  public List<String> getSmbPaths(software.wings.beans.SmbConfig smbConfig, List<EncryptedDataDetail> encryptionDetails)
      throws IOException {
    encryptionService.decrypt(smbConfig, encryptionDetails, false);
    List<String> artifactPaths = new ArrayList<>();

    try (SMBClient client = new SMBClient(getSMBConnectionConfig());
         Connection connection = client.connect(getSMBConnectionHost(smbConfig.getSmbUrl()))) {
      AuthenticationContext ac =
          new AuthenticationContext(smbConfig.getUsername(), smbConfig.getPassword(), smbConfig.getDomain());
      Session session = connection.authenticate(ac);

      // Connect to Shared folder
      String sharedFolderName = getSharedFolderName(smbConfig.getSmbUrl());
      try (DiskShare share = (DiskShare) session.connectShare(sharedFolderName)) {
        for (FileIdBothDirectoryInformation f : share.list("", "*")) {
          if (f.getFileName().equals(".") || f.getFileName().equals("..")) {
            continue;
          }
          artifactPaths.add(f.getFileName());
        }
      }
    }

    log.info("SMB server {} returned {} artifact paths : ", smbConfig.getSmbUrl(), artifactPaths.size());
    return artifactPaths;
  }

  public String getSMBConnectionHost(String smbUrl) {
    String smbHost = smbUrl;
    if (smbHost.contains("/")) {
      smbHost = smbHost.replaceFirst("^(smb?://)", "").split("/")[0];
    } else if (smbHost.contains("\\")) {
      String[] split = smbHost.replaceFirst("^(smb?:\\\\)", "").split(Pattern.quote("\\"));
      smbHost = split[1];
    }
    return smbHost;
  }

  public boolean isConnectibleSOBServer(String smbUrl) {
    try (SMBClient client = new SMBClient(getSMBConnectionConfig());
         Connection connection = client.connect(getSMBConnectionHost(smbUrl))) {
      return true;

    } catch (Exception ex) {
      log.warn("SMB server {} could not be reached. Exception Message {}", smbUrl, ex.getMessage());
    }
    return false;
  }

  private SmbConfig getSMBConnectionConfig() {
    return SmbConfig.builder()
        .withTimeout(120, TimeUnit.SECONDS) // Timeout sets Read, Write, and Transact timeouts (default is 60 seconds)
        .withSoTimeout(180, TimeUnit.SECONDS) // Socket Timeout (default is 0 seconds, blocks forever)
        .build();
  }

  public String getSharedFolderName(String smbUrl) {
    String smbHost = smbUrl;
    String sharedFolderName = "";
    if (smbHost.contains("/")) {
      sharedFolderName = smbHost.replaceFirst("^(smb?://)", "").split("/")[1];
    } else if (smbHost.contains("\\")) {
      String[] split = smbHost.replaceFirst("^(smb?:\\\\)", "").split(Pattern.quote("\\"));
      sharedFolderName = split[2];
    }
    return sharedFolderName;
  }

  private Map<String, String> getArtifactBuildNumbers(software.wings.beans.SmbConfig smbConfig,
      List<EncryptedDataDetail> encryptionDetails, String artifactPath) throws IOException {
    Map<String, String> buildNumbers = new HashMap<>();

    encryptionService.decrypt(smbConfig, encryptionDetails, false);
    try (SMBClient client = new SMBClient(getSMBConnectionConfig());
         Connection connection = client.connect(getSMBConnectionHost(smbConfig.getSmbUrl()))) {
      AuthenticationContext ac =
          new AuthenticationContext(smbConfig.getUsername(), smbConfig.getPassword(), smbConfig.getDomain());
      Session session = connection.authenticate(ac);

      // Connect to Shared folder
      String sharedFolderName = getSharedFolderName(smbConfig.getSmbUrl());
      try (DiskShare share = (DiskShare) session.connectShare(sharedFolderName)) {
        String searchPattern = getFileSearchPattern(artifactPath);
        String path = getFileParentPath(artifactPath);
        if (path.contains("*")) {
          String[] split = path.split("\\*");
          String prePath = split[0];

          // Get all folders matching search pattern, these will be release numbers
          List<FileIdBothDirectoryInformation> fileList = share.list(prePath, "*");
          for (FileIdBothDirectoryInformation f : fileList) {
            if (f.getFileName().equals(".") || f.getFileName().equals("..")) {
              continue;
            }

            // all folders matching regex
            if (share.folderExists(Paths.get(prePath, f.getFileName()).toString())) {
              buildNumbers.put(Paths.get(prePath, f.getFileName(), searchPattern).toString(), f.getFileName());
            }
          }
        } else {
          buildNumbers.put(path, buildNo);
        }
      }
    }
    return buildNumbers;
  }

  public List<BuildDetails> getArtifactDetails(software.wings.beans.SmbConfig smbConfig,
      List<EncryptedDataDetail> encryptionDetails, List<String> artifactPaths) throws IOException {
    List<BuildDetails> buildDetailsList = Lists.newArrayList();
    Map<String, String> buildNos = Collections.EMPTY_MAP;
    encryptionService.decrypt(smbConfig, encryptionDetails, false);
    try (SMBClient client = new SMBClient(getSMBConnectionConfig());
         Connection connection = client.connect(getSMBConnectionHost(smbConfig.getSmbUrl()))) {
      AuthenticationContext ac =
          new AuthenticationContext(smbConfig.getUsername(), smbConfig.getPassword(), smbConfig.getDomain());
      Session session = connection.authenticate(ac);

      // Connect to Shared folder
      String sharedFolderName = getSharedFolderName(smbConfig.getSmbUrl());
      try (DiskShare share = (DiskShare) session.connectShare(sharedFolderName)) {
        // Get artifact details for each artifact path
        for (String artifactPath : artifactPaths) {
          // buildNos map has artifact path to traverse and corresponding build nos.
          buildNos = getArtifactBuildNumbers(smbConfig, encryptionDetails, artifactPath);
          if (isNotEmpty(buildNos)) {
            String searchPattern = getFileSearchPattern(artifactPath);
            String path = getFileParentPath(artifactPath);

            // for each folder construct build details
            for (Entry<String, String> entry : buildNos.entrySet()) {
              List<BuildDetails> buildDetailsListForArtifactPath = Lists.newArrayList();
              if (buildNos.get(entry.getKey()) != null && isNotEmpty(buildNos.get(entry.getKey()))) {
                Map<String, String> map = new HashMap<>();
                map.put(BuildMetadataKeys.artifactPath.name(), entry.getKey());
                buildDetailsListForArtifactPath.add(aBuildDetails()
                                                        .withNumber(buildNos.get(entry.getKey()))
                                                        .withArtifactPath(entry.getKey())
                                                        .withBuildParameters(map)
                                                        .withUiDisplayName("Build# " + buildNos.get(entry.getKey()))
                                                        .build());
              } else {
                List<FileIdBothDirectoryInformation> fileList = share.list(entry.getKey(), searchPattern);

                for (FileIdBothDirectoryInformation f : fileList) {
                  if (f.getFileName().equals(".") || f.getFileName().equals("..")) {
                    continue;
                  }
                  boolean isFolder = share.folderExists(Paths.get(path, f.getFileName()).toString());
                  boolean isFile = share.fileExists(Paths.get(path, f.getFileName()).toString());

                  if (isFile || isFolder) {
                    Map<String, String> map = new HashMap<>();
                    String aPath = Paths.get(path, f.getFileName()).toString();
                    map.put(BuildMetadataKeys.artifactPath.name(), aPath);
                    map.put(BuildMetadataKeys.url.name(), smbConfig.getSmbUrl());
                    map.put(BuildMetadataKeys.artifactFileName.name(), f.getFileName());
                    map.put(BuildMetadataKeys.allocationSize.name(), Long.toString(f.getAllocationSize()));
                    map.put(BuildMetadataKeys.fileAttributes.name(), Long.toString(f.getFileAttributes()));
                    buildDetailsListForArtifactPath.add(aBuildDetails()
                                                            .withNumber(f.getFileName())
                                                            .withArtifactPath(aPath)
                                                            .withBuildUrl(smbConfig.getSmbUrl())
                                                            .withBuildParameters(map)
                                                            .withUiDisplayName("Build# " + f.getFileName())
                                                            .build());
                  }
                }
              }
              buildDetailsList.addAll(buildDetailsListForArtifactPath);
            }
          }
        }
      }
    }

    log.info("SMB server {} returned {} build details for artifact paths : ", smbConfig.getSmbUrl(),
        buildDetailsList.size());
    return buildDetailsList;
  }
}
