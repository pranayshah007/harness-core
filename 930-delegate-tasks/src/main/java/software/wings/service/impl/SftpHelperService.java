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
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import static software.wings.helpers.ext.jenkins.BuildDetails.Builder.aBuildDetails;

import io.harness.annotations.dev.OwnedBy;
import io.harness.security.encryption.EncryptedDataDetail;

import software.wings.beans.SftpConfig;
import software.wings.helpers.ext.jenkins.BuildDetails;
import software.wings.helpers.ext.jenkins.BuildMetadataKeys;
import software.wings.service.intfc.security.EncryptionService;

import com.google.api.client.util.Lists;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import net.schmizz.sshj.DefaultConfig;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.common.DisconnectReason;
import net.schmizz.sshj.sftp.RemoteResourceInfo;
import net.schmizz.sshj.sftp.SFTPClient;
import net.schmizz.sshj.transport.TransportException;

@OwnedBy(CDC)
@Singleton
@Slf4j
public class SftpHelperService {
  private static final String ROOT_DIR_ARTIFACT_PATH = ".";
  @Inject private EncryptionService encryptionService;

  public List<String> getSftpPaths(SftpConfig sftpConfig, List<EncryptedDataDetail> encryptionDetails) {
    encryptionService.decrypt(sftpConfig, encryptionDetails, false);
    List<String> artifactPaths = new ArrayList<>();

    String hostKeyVerifier = "";
    boolean connectionEstablished = false;
    // Create a SSH client and try to make a connection
    try (SSHClient ssh = new SSHClient(new DefaultConfig())) {
      try {
        ssh.loadKnownHosts();
        ssh.connect(getSFTPConnectionHost(sftpConfig.getSftpUrl()));
        connectionEstablished = true;
      } catch (TransportException e) {
        if (e.getDisconnectReason() == DisconnectReason.HOST_KEY_NOT_VERIFIABLE) {
          String msg = e.getMessage();
          String[] split = msg.split("`");
          hostKeyVerifier = split[3];
        }
      } catch (IOException e) {
        log.error("SFTP server {} could not be reached. Exception Message {}", sftpConfig.getSftpUrl(), e.getMessage());
      }
    } catch (Exception e) {
      log.error("SFTP server {} could not be reached. Exception Message {}. Retrying with host key.",
          sftpConfig.getSftpUrl(), e.getMessage());
    }

    try (SSHClient ssh = new SSHClient(new DefaultConfig())) {
      // Host can be reached and host key is verified. Check if connection is established
      if (connectionEstablished) {
        ssh.loadKnownHosts();
      } else {
        ssh.addHostKeyVerifier(hostKeyVerifier);
      }

      ssh.connect(getSFTPConnectionHost(sftpConfig.getSftpUrl()));
      ssh.authPassword(sftpConfig.getUsername(), sftpConfig.getPassword());
      try (SFTPClient sftp = ssh.newSFTPClient()) {
        List<RemoteResourceInfo> resourceInfos = Collections.EMPTY_LIST;
        // Get artifact paths
        resourceInfos = sftp.ls(ROOT_DIR_ARTIFACT_PATH);
        for (RemoteResourceInfo resourceInfo : resourceInfos) {
          artifactPaths.add(resourceInfo.getName());
        }
      } finally {
        log.info("Closing SFTP connection :{}", sftpConfig.getSftpUrl());
      }
    } catch (IOException e) {
      log.error("SFTP server {} could not be reached. Exception Message {}", sftpConfig.getSftpUrl(), e.getMessage());
    } finally {
      log.info("Closing SSH connection for SFTP URL :{}", sftpConfig.getSftpUrl());
    }

    return artifactPaths;
  }

  public String getSFTPConnectionHost(String sftpUrl) {
    String sftpHost = sftpUrl;
    // Check for server prefix and unix and windows style URI
    if (sftpHost.contains("/")) {
      if (sftpHost.startsWith("sftp")) {
        sftpHost = sftpHost.replaceFirst("^(sftp?://)", "").split(Pattern.quote("/"))[0];
      } else if (sftpHost.startsWith("ftp")) {
        sftpHost = sftpHost.replaceFirst("^(ftp?://)", "").split(Pattern.quote("/"))[0];
      }
    } else if (sftpHost.contains("\\")) {
      if (sftpHost.startsWith("sftp")) {
        sftpHost = sftpHost.replaceFirst("^(sftp?:\\\\)", "").split(Pattern.quote("\\"))[1];
      } else if (sftpHost.startsWith("ftp")) {
        sftpHost = sftpHost.replaceFirst("^(ftp?:\\\\)", "").split(Pattern.quote("\\"))[1];
      }
    }
    return sftpHost;
  }

  public boolean isConnectibleSFTPServer(String sftpUrl) {
    String hostKeyVerifier = "";
    try (SSHClient ssh = new SSHClient(new DefaultConfig())) {
      try {
        ssh.loadKnownHosts();
        ssh.connect(getSFTPConnectionHost(sftpUrl));
        return true;
      } catch (TransportException e) {
        if (e.getDisconnectReason() == DisconnectReason.HOST_KEY_NOT_VERIFIABLE) {
          String msg = e.getMessage();
          String[] split = msg.split("`");
          hostKeyVerifier = split[3];
        }
      }
    } catch (IOException e) {
      log.error("SFTP server {} could not be reached. Exception Message {}", sftpUrl, e.getMessage());
    }

    if (isEmpty(hostKeyVerifier)) {
      log.error("SFTP server {} host key could not be verified.", sftpUrl);
      return false;
    }

    // Try connecting again with host key verifier
    try (SSHClient ssh = new SSHClient(new DefaultConfig())) {
      ssh.addHostKeyVerifier(hostKeyVerifier);
      ssh.connect(getSFTPConnectionHost(sftpUrl));
      return true;
    } catch (IOException e) {
      log.error("SFTP server {} could not be reached. Exception Message {}", sftpUrl, e.getMessage());
    }
    return false;
  }

  public List<BuildDetails> getArtifactDetails(SftpConfig sftpConfig, List<EncryptedDataDetail> encryptionDetails,
      List<String> artifactPaths) throws IOException {
    List<BuildDetails> buildDetailsList = Lists.newArrayList();
    encryptionService.decrypt(sftpConfig, encryptionDetails, false);

    String hostKeyVerifier = "";
    boolean connectionEstablished = false;
    try (SSHClient ssh = new SSHClient(new DefaultConfig())) {
      try {
        ssh.loadKnownHosts();
        ssh.connect(getSFTPConnectionHost(sftpConfig.getSftpUrl()));
        connectionEstablished = true;
      } catch (TransportException e) {
        if (e.getDisconnectReason() == DisconnectReason.HOST_KEY_NOT_VERIFIABLE) {
          String msg = e.getMessage();
          String[] split = msg.split("`");
          hostKeyVerifier = split[3];
        }
      }
    } catch (IOException e) {
      log.error("SFTP server {} could not be reached. Exception Message {}", sftpConfig.getSftpUrl(), e.getMessage());
    } finally {
      log.info("Closing SSH connection for SFTP URL :{}", sftpConfig.getSftpUrl());
    }

    try (SSHClient ssh = new SSHClient(new DefaultConfig())) {
      // Host can be reached and host key is verified. Check if connection is established
      if (connectionEstablished) {
        ssh.loadKnownHosts();
      } else {
        ssh.addHostKeyVerifier(hostKeyVerifier);
      }

      ssh.connect(getSFTPConnectionHost(sftpConfig.getSftpUrl()));
      ssh.authPassword(sftpConfig.getUsername(), sftpConfig.getPassword());
      final SFTPClient sftp = ssh.newSFTPClient();

      List<RemoteResourceInfo> resourceInfos = Collections.EMPTY_LIST;
      // Get artifact details for each path
      try {
        for (String artifactPath : artifactPaths) {
          String searchPattern = getFileSearchPattern(artifactPath);
          String directory = getFileParentPath(artifactPath);
          String fileName = searchPattern;
          // if directory is empty, default is HOME
          if (isEmpty(directory)) {
            directory = ROOT_DIR_ARTIFACT_PATH;
          }

          resourceInfos = sftp.ls(directory, RemoteResourceInfo::isRegularFile);

          Pattern pattern = Pattern.compile(fileName.replace(".", "\\.").replace("?", ".?").replace("*", ".*?"));

          resourceInfos = resourceInfos.stream()
                              .filter(resourceInfo -> pattern.matcher(resourceInfo.getName()).find())
                              .collect(Collectors.toList());

          List<BuildDetails> buildDetailsListForArtifactPath = Lists.newArrayList();
          for (RemoteResourceInfo resourceInfo : resourceInfos) {
            Map<String, String> map = new HashMap<>();
            map.put(BuildMetadataKeys.artifactPath.name(), resourceInfo.getPath());
            map.put(BuildMetadataKeys.url.name(), sftpConfig.getSftpUrl());
            map.put(BuildMetadataKeys.artifactFileName.name(), resourceInfo.getName());
            map.put(BuildMetadataKeys.path.name(), resourceInfo.getPath());
            map.put(BuildMetadataKeys.parent.name(), resourceInfo.getParent());
            buildDetailsListForArtifactPath.add(aBuildDetails()
                                                    .withNumber(resourceInfo.getName())
                                                    .withArtifactPath(artifactPath)
                                                    .withBuildUrl(sftpConfig.getSftpUrl())
                                                    .withBuildParameters(map)
                                                    .withUiDisplayName("Build# " + resourceInfo.getName())
                                                    .build());
          }
          buildDetailsList.addAll(buildDetailsListForArtifactPath);
        }
      } finally {
        log.info("Closing SFTP connection :{}", sftpConfig.getSftpUrl());
        sftp.close();
      }
    } catch (IOException e) {
      log.error("SFTP server {} could not be reached. Exception Message {}", sftpConfig.getSftpUrl(), e.getMessage());
    } finally {
      log.info("Closing SSH connection for SFTP URL :{}", sftpConfig.getSftpUrl());
    }

    log.info("SFTP server {} returned {} build details for artifact paths : ", sftpConfig.getSftpUrl(),
        buildDetailsList.size());
    return buildDetailsList;
  }
}
