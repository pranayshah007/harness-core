/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.shell.ssh;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.annotations.dev.OwnedBy;
import io.harness.logging.LogCallback;
import io.harness.shell.SshSessionConfig;
import io.harness.shell.ssh.client.SshClient;
import io.harness.shell.ssh.connection.ExecRequest;
import io.harness.shell.ssh.connection.ExecResponse;
import io.harness.shell.ssh.exception.SshClientException;
import io.harness.shell.ssh.sftp.SftpRequest;
import io.harness.shell.ssh.sftp.SftpResponse;
import io.harness.shell.ssh.xfer.ScpRequest;
import io.harness.shell.ssh.xfer.ScpResponse;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(CDP)
@UtilityClass
public class SshClientManager {
  private final ConcurrentHashMap<String, SshClient> clientCache = new ConcurrentHashMap<>();

  private SshClient getSshClient(SshSessionConfig sshSessionConfig, LogCallback logCallback) {
    Optional<String> cacheKey = SshUtils.getCacheKey(sshSessionConfig);
    if (cacheKey.isPresent()) {
      SshClient sshClient =
          clientCache.computeIfAbsent(cacheKey.get(), k -> SshFactory.getSshClient(sshSessionConfig, logCallback));
      sshClient.setLogCallback(logCallback);
      return sshClient;
    } else {
      return SshFactory.getSshClient(sshSessionConfig, logCallback);
    }
  }

  public ExecResponse exec(ExecRequest execRequest, SshSessionConfig sshSessionConfig, LogCallback logCallback)
      throws SshClientException {
    SshClient sshClient = getSshClient(sshSessionConfig, logCallback);
    try {
      return sshClient.exec(execRequest);
    } catch (SshClientException se) {
      handleSshException(se, "exec");
      throw se;
    } finally {
      cleanUpIfNotInCache(sshSessionConfig, sshClient);
    }
  }

  private static void handleSshException(SshClientException se, String op) {
    log.error("Failed due to {}", op, se);
  }

  public void test(SshSessionConfig sshSessionConfig) {
    try (SshClient sshClient = SshFactory.getSshClient(sshSessionConfig)) {
      sshClient.testConnection();
    }
  }

  public SftpResponse sftpDownload(SftpRequest sftpRequest, SshSessionConfig sshSessionConfig, LogCallback logCallback)
      throws SshClientException {
    SshClient sshClient = getSshClient(sshSessionConfig, logCallback);
    try {
      return sshClient.sftpDownload(sftpRequest);
    } catch (SshClientException se) {
      handleSshException(se, "sftp");
      throw se;
    } finally {
      cleanUpIfNotInCache(sshSessionConfig, sshClient);
    }
  }

  public ScpResponse scpUpload(ScpRequest scpRequest, SshSessionConfig sshSessionConfig, LogCallback logCallback)
      throws SshClientException {
    SshClient sshClient = getSshClient(sshSessionConfig, logCallback);
    try {
      return sshClient.scpUpload(scpRequest);
    } catch (SshClientException se) {
      handleSshException(se, "scp");
      throw se;
    } finally {
      cleanUpIfNotInCache(sshSessionConfig, sshClient);
    }
  }

  public void evictCacheAndDisconnect(String executionId, String host) throws SshClientException {
    Optional<String> cacheKey = SshUtils.getCacheKey(executionId, host);
    if (cacheKey.isPresent()) {
      String key = cacheKey.get();
      SshClient sshClient = clientCache.get(key);
      if (null != sshClient) {
        sshClient.close();
      }
      clientCache.remove(cacheKey.get());
    }
  }

  private void cleanUpIfNotInCache(SshSessionConfig sshSessionConfig, SshClient sshClient) throws SshClientException {
    if (SshUtils.getCacheKey(sshSessionConfig).isEmpty()) {
      sshClient.close();
    }
  }
}
