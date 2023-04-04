/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.shell.ssh;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.shell.ssh.Constants.getCacheKey;

import io.harness.annotations.dev.OwnedBy;
import io.harness.logging.LogCallback;
import io.harness.shell.SshSessionConfig;
import io.harness.shell.ssh.client.SshClient;
import io.harness.shell.ssh.connection.ExecRequest;
import io.harness.shell.ssh.connection.ExecResponse;
import io.harness.shell.ssh.sftp.SftpRequest;
import io.harness.shell.ssh.sftp.SftpResponse;
import io.harness.shell.ssh.xfer.ScpRequest;
import io.harness.shell.ssh.xfer.ScpResponse;

import com.google.inject.Singleton;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(CDP)
@Singleton
public class SshClientManager {
  private static ConcurrentMap<String, SshClient> clientCache = new ConcurrentHashMap<>();

  private static SshClient updateCache(SshSessionConfig sshSessionConfig, LogCallback logCallback) {
    Optional<String> cacheKey = getCacheKey(sshSessionConfig);
    if (cacheKey.isPresent()) {
      String key = cacheKey.get();
      if (!clientCache.containsKey(key)) {
        clientCache.put(key, SshFactory.getSshClient(sshSessionConfig, logCallback));
      }
      return clientCache.get(key);
    } else {
      return SshFactory.getSshClient(sshSessionConfig, logCallback);
    }
  }

  public ExecResponse exec(ExecRequest execRequest, SshSessionConfig sshSessionConfig, LogCallback logCallback) throws Exception {
    SshClient sshClient = updateCache(sshSessionConfig, logCallback);
    try {
      ExecResponse response = sshClient.exec(execRequest);
      return response;
    } finally {
      if(getCacheKey(sshSessionConfig).isEmpty()){
        sshClient.close();
      }
    }
  }

  public SftpResponse sftpUpload(SftpRequest sftpRequest, SshSessionConfig sshSessionConfig, LogCallback logCallback) {
    SshClient sshClient = updateCache(sshSessionConfig, logCallback);
    return sshClient.sftpUpload(sftpRequest);
  }

  public ScpResponse scpUpload(ScpRequest scpRequest, SshSessionConfig sshSessionConfig, LogCallback logCallback) {
    SshClient sshClient = updateCache(sshSessionConfig, logCallback);
    return sshClient.scpUpload(scpRequest);
  }

  public void evictCache(SshSessionConfig config) throws Exception {
    Optional<String> cacheKey = getCacheKey(config);
    if (cacheKey.isPresent()) {
      SshClient sshClient = clientCache.get(cacheKey.get());
      if (null != sshClient) {
        sshClient.close();
      }
      clientCache.remove(cacheKey.get());
    }
  }
}
