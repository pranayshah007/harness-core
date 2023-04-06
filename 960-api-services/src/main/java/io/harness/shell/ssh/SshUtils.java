/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.shell.ssh;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.shell.SshSessionConfig;

import java.util.Optional;
import java.util.regex.Pattern;

public class SshUtils {
  public static final String SSH_NETWORK_PROXY = "SSH_NETWORK_PROXY";
  /**
   * The constant log.
   * // TODO: Read from config. 1 GB per channel for now.
   */
  public static final int MAX_BYTES_READ_PER_CHANNEL = 1024 * 1024 * 1024;

  public static final int CHUNK_SIZE = 512 * 1024; // 512KB
  /**
   * The constant DEFAULT_SUDO_PROMPT_PATTERN.
   */
  public static final String DEFAULT_SUDO_PROMPT_PATTERN = "^\\[sudo\\] password for .+: .*";
  /**
   * The constant LINE_BREAK_PATTERN.
   */
  public static final String LINE_BREAK_PATTERN_STRING = "\\R+";
  public static final Pattern LINE_BREAK_PATTERN = Pattern.compile(LINE_BREAK_PATTERN_STRING);
  public static final Pattern SUDO_PASSWORD_PROMPT_PATTERN = Pattern.compile(DEFAULT_SUDO_PROMPT_PATTERN);
  /**
   * The constant log.
   */
  public static final String CHANNEL_IS_NOT_OPENED = "channel is not opened.";
  public static final int JSCH_SCP_ALLOWED_BYTES = 1024 * 1024; // 1MB

  public static Optional<String> getCacheKey(SshSessionConfig config) {
    if (null == config) {
      return Optional.empty();
    } else {
      return getCacheKey(config.getExecutionId(), config.getHost());
    }
  }
  public static Optional<String> getCacheKey(String executionId, String host) {
    if (isEmpty(executionId) || isEmpty(host)) {
      return Optional.empty();
    } else {
      return Optional.of(executionId + "~" + host.trim());
    }
  }
}
