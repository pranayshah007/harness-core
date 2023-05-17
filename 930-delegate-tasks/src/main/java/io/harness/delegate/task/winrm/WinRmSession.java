/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.winrm;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.delegate.task.winrm.WinRmExecutorHelper.PARTITION_SIZE_IN_BYTES;
import static io.harness.delegate.utils.TaskExceptionUtils.calcPercentage;
import static io.harness.logging.CommandExecutionStatus.RUNNING;
import static io.harness.logging.LogLevel.INFO;
import static io.harness.windows.CmdUtils.escapeEnvValueSpecialChars;
import static io.harness.winrm.WinRmHelperUtils.buildErrorDetailsFromWinRmClientException;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.clienttools.ClientTool;
import io.harness.delegate.clienttools.HarnessPywinrmVersion;
import io.harness.delegate.clienttools.InstallUtils;
import io.harness.eraro.ResponseMessage;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.logging.LogCallback;
import io.harness.logging.LogLevel;
import io.harness.ssh.SshHelperUtils;
import io.harness.ssh.WinRmCommandResult;

import com.google.common.annotations.VisibleForTesting;
import com.jcraft.jsch.JSchException;
import io.cloudsoft.winrm4j.client.ShellCommand;
import io.cloudsoft.winrm4j.client.WinRmClient;
import io.cloudsoft.winrm4j.client.WinRmClientBuilder;
import io.cloudsoft.winrm4j.client.WinRmClientContext;
import io.cloudsoft.winrm4j.winrm.WinRmTool;
import io.cloudsoft.winrm4j.winrm.WinRmToolResponse;
import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;

@Slf4j
@OwnedBy(CDP)
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public class WinRmSession implements AutoCloseable {
  private static final int retryCount = 1;
  @VisibleForTesting static final String FILE_CACHE_TYPE = "FILE";
  @VisibleForTesting static final String KERBEROS_CACHE_NAME_ENV = "KRB5CCNAME";
  @VisibleForTesting static final String COMMAND_PLACEHOLDER = "%s %s";

  private final ShellCommand shell;
  private final WinRmTool winRmTool;
  private final LogCallback logCallback;

  private WinRmClient client;
  private WinRmClientContext context;
  private PyWinrmArgs args;
  private Path cacheFilePath;
  private final AuthenticationScheme authenticationScheme;

  public WinRmSession(WinRmSessionConfig config, LogCallback logCallback) throws JSchException {
    Map<String, String> processedEnvironmentMap = new HashMap<>();
    if (config.getEnvironment() != null) {
      for (Entry<String, String> entry : config.getEnvironment().entrySet()) {
        processedEnvironmentMap.put(entry.getKey(), escapeEnvValueSpecialChars(entry.getValue()));
      }
    }
    this.logCallback = logCallback;
    this.authenticationScheme = config.getAuthenticationScheme();
    Map<String, String> generateTGTEnv = new HashMap<>();
    if (authenticationScheme == AuthenticationScheme.KERBEROS) {
      if (config.isUseKerberosUniqueCacheFile()) {
        this.cacheFilePath = config.getSessionCacheFilePath();
        String cache = String.format("%s:%s", FILE_CACHE_TYPE, cacheFilePath);
        logCallback.saveExecutionLog(String.format("Using kerberos cache: %s", cache));
        processedEnvironmentMap.put(KERBEROS_CACHE_NAME_ENV, cache);
        generateTGTEnv.put(KERBEROS_CACHE_NAME_ENV, cache);
      }

      args = PyWinrmArgs.builder()
                 .hostname(getEndpoint(config.getHostname(), config.getPort(), config.isUseSSL()))
                 .username(getUserPrincipal(config.getUsername(), config.getDomain()))
                 .environmentMap(processedEnvironmentMap)
                 .workingDir(config.getWorkingDirectory())
                 .timeout(config.getTimeout())
                 .build();

      if (!EmptyPredicate.isEmpty(config.getPassword()) || !EmptyPredicate.isEmpty(config.getKeyTabFilePath())) {
        SshHelperUtils.generateTGT(getUserPrincipal(config.getUsername(), config.getDomain()), config.getPassword(),
            config.getKeyTabFilePath(), logCallback, generateTGTEnv);
      }

      shell = null;
      if (executeCommandString("echo 'checking connection'", null, null, false) != 0) {
        throw new InvalidRequestException("Cannot reach remote host");
      }
      winRmTool = null;
      return;
    }

    context = WinRmClientContext.newInstance();

    WinRmClientBuilder clientBuilder =
        WinRmClient.builder(getEndpoint(config.getHostname(), config.getPort(), config.isUseSSL()))
            .disableCertificateChecks(config.isSkipCertChecks())
            .authenticationScheme(getAuthSchemeString(authenticationScheme))
            .credentials(config.getDomain(), config.getUsername(), config.getPassword())
            .workingDirectory(config.getWorkingDirectory())
            .environment(processedEnvironmentMap)
            .retriesForConnectionFailures(retryCount)
            .context(context)
            .operationTimeout(config.getTimeout())
            .retryReceiveAfterOperationTimeout(WinRmClientBuilder.neverRetryReceiveAfterOperationTimeout());

    client = clientBuilder.build();
    shell = client.createShell();

    winRmTool = WinRmTool.Builder.builder(config.getHostname(), config.getUsername(), config.getPassword())
                    .disableCertificateChecks(config.isSkipCertChecks())
                    .authenticationScheme(getAuthSchemeString(authenticationScheme))
                    .workingDirectory(config.getWorkingDirectory())
                    .environment(processedEnvironmentMap)
                    .port(config.getPort())
                    .useHttps(config.isUseSSL())
                    .context(context)
                    .build();
  }

  public int executeCommandString(String command, Writer output, Writer error, boolean isOutputWriter) {
    if (args != null) {
      return executeCommandWithKerberos(command, output, isOutputWriter);
    }
    return shell.execute(command, output, error);
  }

  public int executeCommandWithKerberos(String command, Writer output, boolean isOutputWriter) {
    String commandFilePath = null;
    try {
      File commandFile = File.createTempFile("winrm-kerberos-command", null);
      commandFilePath = commandFile.getPath();
      byte[] buff = command.getBytes(StandardCharsets.UTF_8);
      Files.write(Paths.get(commandFilePath), buff);

      WinRmCommandResult winRmCommandResult = SshHelperUtils.executeLocalCommand(
          format(COMMAND_PLACEHOLDER, InstallUtils.getPath(ClientTool.HARNESS_PYWINRM, HarnessPywinrmVersion.V0_4),
              args.getArgs(commandFile.getAbsolutePath())),
          logCallback, output, isOutputWriter, args.getEnvironmentMap());

      if (winRmCommandResult != null && winRmCommandResult.isSuccess()) {
        return 0;
      } else {
        return 1;
      }
    } catch (IOException e) {
      log.error(format("Error while creating temporary file: %s", e));
      logCallback.saveExecutionLog("Error while creating temporary file");
      return 1;
    } finally {
      deleteSilently(commandFilePath);
    }
  }

  private void deleteSilently(String path) {
    if (path != null) {
      try {
        Files.deleteIfExists(Paths.get(path));
      } catch (IOException e) {
        log.error("Failed to delete file {}", path, e);
      }
    }
  }

  public int executeCommandsList(List<List<String>> commandList, Writer output, Writer error, boolean isOutputWriter,
      String scriptExecCommand) throws IOException {
    WinRmToolResponse winRmToolResponse = null;
    if (commandList.isEmpty()) {
      return -1;
    }
    int statusCode = 0;
    if (args != null) {
      if (isNotEmpty(scriptExecCommand)) {
        commandList.get(commandList.size() - 1).add(scriptExecCommand);
      }
      for (List<String> list : commandList) {
        String command = String.join(" & ", list);
        statusCode = executeCommandString(command, output, error, isOutputWriter);
        if (statusCode != 0) {
          return statusCode;
        }
      }
    } else {
      for (List<String> list : commandList) {
        winRmToolResponse = winRmTool.executeCommand(list);
        writeLogs(winRmToolResponse, output, error);
        statusCode = winRmToolResponse.getStatusCode();
        if (statusCode != 0) {
          return statusCode;
        }
      }
      if (isNotEmpty(scriptExecCommand)) {
        statusCode = shell.execute(scriptExecCommand, output, error);
      }
    }

    return statusCode;
  }

  public int copyScriptToRemote(List<String> commandList, Writer output, Writer error) throws IOException {
    if (commandList.isEmpty()) {
      return -1;
    }
    if (authenticationScheme == AuthenticationScheme.KERBEROS) {
      return executeCopyCommandsWithKerberos(commandList, output);
    } else {
      return executeCopyCommands(commandList, output, error);
    }
  }

  public int executeScript(String scriptExecCommand, Writer output, Writer error) {
    try {
      if (authenticationScheme == AuthenticationScheme.KERBEROS) {
        return executeCommandWithKerberos(scriptExecCommand, output, false);
      } else {
        return shell.execute(scriptExecCommand, output, error);
      }
    } catch (Exception e) {
      ResponseMessage details = buildErrorDetailsFromWinRmClientException(e);
      log.error("Script execution failed.", e);
      logCallback.saveExecutionLog(details.getMessage(), INFO, RUNNING);
      return 1;
    }
  }

  private int executeCopyCommands(List<String> commandList, Writer output, Writer error) {
    try {
      int statusCode = 0;
      int chunkNumber = 1;
      int fileLength = commandList.stream().mapToInt(c -> c.getBytes().length).sum();
      logCallback.saveExecutionLog(
          format("Transferring encoded script to a remote file. File size: %s Bytes, Chunk size: %s Bytes\n",
              fileLength, PARTITION_SIZE_IN_BYTES),
          INFO, RUNNING);
      for (String command : commandList) {
        statusCode = shell.execute(command, output, error);
        if (statusCode != 0) {
          logCallback.saveExecutionLog("Transferring encoded script data to remote file FAILED.", INFO, RUNNING);
          return statusCode;
        }
        logCallback.saveExecutionLog(format("Transferred %s data to remote file...\n",
                                         calcPercentage(chunkNumber * PARTITION_SIZE_IN_BYTES, fileLength)),
            INFO, RUNNING);
        chunkNumber++;
      }
      return statusCode;
    } catch (Exception e) {
      ResponseMessage details = buildErrorDetailsFromWinRmClientException(e);
      log.error("Transferring encoded script data to remote file FAILED.", e);
      logCallback.saveExecutionLog(
          "Transferring encoded script data to remote file FAILED.\n\n" + details.getMessage(), INFO, RUNNING);
      return 1;
    }
  }

  private int executeCopyCommandsWithKerberos(List<String> commandList, Writer output) {
    int statusCode = 0;
    int chunkNumber = 1;
    int fileLength = commandList.stream().mapToInt(c -> c.getBytes().length).sum();
    logCallback.saveExecutionLog(
        format("Transferring encoded script to a remote file. File size: %s Bytes, Chunk size: %s Bytes\n", fileLength,
            PARTITION_SIZE_IN_BYTES),
        INFO, RUNNING);
    for (String command : commandList) {
      statusCode = executeCommandWithKerberos(command, output, false);
      if (statusCode != 0) {
        logCallback.saveExecutionLog("Transferring encoded script data to remote file FAILED.", INFO, RUNNING);
        return statusCode;
      }
      logCallback.saveExecutionLog(format("Transferred %s data to remote file...\n",
                                       calcPercentage(chunkNumber * PARTITION_SIZE_IN_BYTES, fileLength)),
          INFO, RUNNING);
      chunkNumber++;
    }
    return statusCode;
  }

  private void writeLogs(WinRmToolResponse winRmToolResponse, Writer output, Writer error) throws IOException {
    if (!winRmToolResponse.getStdOut().isEmpty()) {
      output.write(winRmToolResponse.getStdOut());
    }

    if (!winRmToolResponse.getStdErr().isEmpty()) {
      error.write(winRmToolResponse.getStdErr());
    }
  }

  private static String getEndpoint(String hostname, int port, boolean useHttps) {
    return format("%s://%s:%d/wsman", useHttps ? "https" : "http", hostname, port);
  }

  private static String getAuthSchemeString(AuthenticationScheme authenticationScheme) {
    switch (authenticationScheme) {
      case BASIC:
        return "Basic";
      case NTLM:
        return "NTLM";
      case KERBEROS:
        return "Kerberos";
      default:
        return "Unknown";
    }
  }

  @Override
  public void close() {
    if (shell != null) {
      shell.close();
    }
    if (client != null) {
      client.close();
    }
    if (context != null) {
      context.shutdown();
    }
    if (cacheFilePath != null) {
      File cacheFile = cacheFilePath.toFile();
      if (cacheFile.exists()) {
        try {
          FileUtils.forceDelete(cacheFile);
        } catch (IOException e) {
          logCallback.saveExecutionLog(
              format("Failed to delete cache file '%s', due to: %s. Try to delete it manually.", cacheFilePath,
                  e.getMessage()),
              LogLevel.ERROR);
        }
      }
    }
  }

  @VisibleForTesting
  public String getUserPrincipal(String username, String domain) {
    if (username == null || domain == null) {
      throw new InvalidRequestException("Username or domain cannot be null", WingsException.USER);
    }
    if (username.contains("@")) {
      username = username.substring(0, username.indexOf('@'));
    }
    return format("%s@%s", username, domain.toUpperCase());
  }
}
