package io.harness.shell.ssh;

import java.util.Optional;
import java.util.regex.Pattern;

import io.harness.shell.SshSessionConfig;
import lombok.experimental.UtilityClass;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

@UtilityClass
public class Constants {
  public final String SSH_NETWORK_PROXY = "SSH_NETWORK_PROXY";
  /**
   * The constant log.
   * // TODO: Read from config. 1 GB per channel for now.
   */
  public final int MAX_BYTES_READ_PER_CHANNEL = 1024 * 1024 * 1024;

  public final int CHUNK_SIZE = 512 * 1024; // 512KB
  /**
   * The constant DEFAULT_SUDO_PROMPT_PATTERN.
   */
  public final String DEFAULT_SUDO_PROMPT_PATTERN = "^\\[sudo\\] password for .+: .*";
  /**
   * The constant LINE_BREAK_PATTERN.
   */
  public final String LINE_BREAK_PATTERN = "\\R+";
  public final Pattern lineBreakPattern = Pattern.compile(LINE_BREAK_PATTERN);
  public final Pattern sudoPasswordPromptPattern = Pattern.compile(DEFAULT_SUDO_PROMPT_PATTERN);
  /**
   * The constant log.
   */
  public final String CHANNEL_IS_NOT_OPENED = "channel is not opened.";

  public Optional<String> getCacheKey(SshSessionConfig config) {
    if(null == config || isEmpty(config.getExecutionId()) || isEmpty(config.getHost())){
      return Optional.empty();
    } else{
      return Optional.of(getKey(config.getExecutionId(), config.getHost()));
    }
  }
  private String getKey(String executionId, String host) {
    return executionId + "~" + host.trim();
  }
}
