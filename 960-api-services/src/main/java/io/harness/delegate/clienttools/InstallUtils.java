/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.clienttools;

import static io.harness.annotations.dev.HarnessTeam.DEL;
import static io.harness.data.structure.HarnessStringUtils.join;
import static io.harness.filesystem.FileIo.createDirectoryIfDoesNotExist;
import static io.harness.network.Http.getBaseUrl;

import static com.google.common.base.Strings.isNullOrEmpty;

import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.configuration.DelegateConfiguration;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Table;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.exec.ProcessResult;
import org.zeroturnaround.exec.stream.LogOutputStream;

@UtilityClass
@Slf4j
@OwnedBy(DEL)
public class InstallUtils {
  private static final Table<ClientTool, ClientToolVersion, Path> toolPaths = HashBasedTable.create();
  private static final String x86_64 = "x86_64";
  private static final String aarch64 = "aarch64";
  private static final String amd64 = "amd64";
  private static final String arm64 = "arm64";

  private List<ClientTool> internalToolsList = Arrays.asList(ClientTool.SCM);

  public static String getPath(final ClientTool tool, final ClientToolVersion version) {
    final Path toolPath = toolPaths.get(tool, version);
    if (toolPath != null) {
      return toolPath.toString();
    }
    final String msg = String.format("%s is not installed for version %s. Available versions are: %s",
        tool.getBinaryName(), version, toolPaths.row(tool).keySet());
    throw new IllegalArgumentException(msg);
  }

  public static String getLatestVersionPath(final ClientTool tool) {
    return getPath(tool, tool.getLatestVersion());
  }

  public static boolean areClientToolsInstalled() {
    return Arrays.stream(ClientTool.values()).allMatch(InstallUtils::isInstalled);
  }

  public static boolean isInstalled(final ClientTool tool) {
    return !toolPaths.row(tool).isEmpty();
  }

  public static void setupClientTools(final DelegateConfiguration configuration) {
    log.info("Setting up client tools {}", Arrays.toString(ClientTool.values()));
    Arrays.stream(ClientTool.values())
        .forEach(tool -> toolPaths.row(tool).putAll(installTool(tool, tool.getVersions(), configuration)));
    log.info("Initializing client tools");
    Arrays.stream(ClientTool.values())
        .forEach(tool -> toolPaths.row(tool).keySet().forEach(version -> initTool(tool, version)));
  }

  @VisibleForTesting
  static void initTool(final ClientTool tool, final ClientToolVersion version) {
    final String initCommand = getInitCommand(tool, version);
    if (StringUtils.isNotBlank(initCommand)) {
      final Path toolPath = toolPaths.get(tool, version);
      if (toolPath != null) {
        runToolCommand(toolPath, initCommand);
      } else {
        log.warn("Tool {} is not installed for version {}, skipping init", tool, version);
      }
    } else {
      log.info("No init command for {} version {}", tool, version);
    }
  }

  private static String getManagerBaseUrl(String managerUrl) {
    if (managerUrl.contains("localhost") || managerUrl.contains("127.0.0.1")) {
      return "https://app.harness.io/";
    }
    return getBaseUrl(managerUrl);
  }

  private static String getHelmInitCommand(final ClientToolVersion helmVersion) {
    return HelmVersion.V2.equals(helmVersion) ? "init -c --skip-refresh \n" : StringUtils.EMPTY;
  }

  private static Map<ClientToolVersion, Path> installTool(
      final ClientTool tool, final List<ClientToolVersion> versions, final DelegateConfiguration configuration) {
    final ImmutableMap.Builder<ClientToolVersion, Path> mapBuilder = ImmutableMap.builder();
    for (final ClientToolVersion version : versions) {
      final Path versionedPath = installTool(tool, version, configuration);
      if (versionedPath != null) {
        mapBuilder.put(version, versionedPath);
      } else {
        log.error("Failed to install {} for version {}", tool.getBinaryName(), version);
      }
    }
    return mapBuilder.build();
  }

  /**
   * This method discovers the actual location of the tool which can be used for running task scripts.
   * As soon as tool is discovered from locations in below order, we don't attempt other locations:
   * <ol>
   *     <li> Custom paths configured through environment variables like KUBECTL_PATH
   *     <li> $PATH environment variable
   *     <li> Default tool location (under ./client-tools/)
   *     <li> Download the tool to the default tool location (if download is enabled)
   * </ol>
   * @param tool tool to install
   * @param version tool version
   * @param configuration delegate configuration to look for settings
   * @return Path to the tool if discovered. null if there is no tool discovered
   */
  private static Path installTool(
      final ClientTool tool, final ClientToolVersion version, final DelegateConfiguration configuration) {
    // 1. Check if custom path is configured for a tool (assume it's installed there)
    final String customPathStr = getCustomPath(tool, version, configuration);
    if (!isNullOrEmpty(customPathStr)) {
      log.info("Custom {} is installed at {}", tool.getBinaryName(), customPathStr);
      return Paths.get(customPathStr).normalize().toAbsolutePath();
    }

    // 2. Check if the tool is on $PATH (only for immutable delegate)
    if (configuration.isImmutable()) {
      final Path toolName = Paths.get(tool.getBinaryName());
      if (runToolCommand(toolName, tool.getValidateCommandArgs())) {
        log.info("{} Tool is found on $PATH", tool.getBinaryName());
        return toolName;
      }
    }

    // 3. Check if tool is already installed
    final Path versionedToolPath = getVersionedPath(tool, version);
    if (validateToolExists(versionedToolPath, tool)) {
      log.info("{} already installed at {}", tool.getBinaryName(), versionedToolPath);
      return versionedToolPath;
    }

    // 4. Download the tool
    if (!configuration.isClientToolsDownloadDisabled() || isInternalTool(tool)) {
      try {
        log.info("{} not found at {}. Installing.", tool.getBinaryName(), versionedToolPath);
        createDirectoryIfDoesNotExist(versionedToolPath.getParent());

        final String downloadUrl = getDownloadUrl(tool, version, configuration);
        log.info("{} download url is {}", tool.getBinaryName(), downloadUrl);
        final String permissionsCommand = "chmod +x " + tool.getBinaryName();

        final String script = "curl $MANAGER_PROXY_CURL -kLO " + downloadUrl + "\n" + permissionsCommand;

        final boolean isInstalled = runCommand(versionedToolPath.getParent(), script);
        if (isInstalled) {
          if (validateToolExists(versionedToolPath, tool)) {
            log.info("{} successfully installed to {}", tool.getBinaryName(), versionedToolPath);
            return versionedToolPath;
          } else {
            log.error("{} not validated after download {}", tool.getBinaryName(), versionedToolPath);
            return null;
          }
        } else {
          log.error("Failed installing {} to {}", tool.getBinaryName(), versionedToolPath);
          return null;
        }
      } catch (final Exception e) {
        log.error("Exception installing " + tool.getBinaryName(), e);
        return null;
      }
    } else {
      log.info("{} download disabled. Skipping install.", tool.getBinaryName());
      return null;
    }
  }

  private static boolean isInternalTool(ClientTool tool) {
    return internalToolsList.contains(tool);
  }

  private static Path getVersionedPath(final ClientTool tool, final ClientToolVersion toolVersion) {
    return Paths.get(tool.getBaseDir(), toolVersion.getVersion(), tool.getBinaryName()).toAbsolutePath().normalize();
  }

  private static String getDownloadUrl(
      final ClientTool tool, final ClientToolVersion toolVersion, final DelegateConfiguration configuration) {
    if (configuration.isUseCdn()) {
      return join("/", configuration.getCdnUrl(),
          String.format(tool.getCdnPath(), toolVersion.getVersion(), getOsPath(), getArchPath()));
    }
    return getManagerBaseUrl(configuration.getManagerUrl())
        + String.format(tool.getOnPremPath(), toolVersion.getVersion(), getOsPath(), getArchPath());
  }

  private static String getCustomPath(
      final ClientTool tool, final ClientToolVersion version, final DelegateConfiguration configuration) {
    switch (tool) {
      case HELM:
        return HelmVersion.V2.equals(version) ? configuration.getHelmPath() : configuration.getHelm3Path();
      case KUBECTL:
        return configuration.getKubectlPath();
      case KUSTOMIZE:
        return configuration.getKustomizePath();
      case OC:
        return configuration.getOcPath();
      case SCM:
      case TERRAFORM_CONFIG_INSPECT:
      case GO_TEMPLATE:
      case HARNESS_PYWINRM:
      case CHARTMUSEUM:
        return StringUtils.EMPTY;
      default:
        throw new IllegalArgumentException("Unknown tool: " + tool);
    }
  }

  private static String getInitCommand(final ClientTool tool, final ClientToolVersion version) {
    switch (tool) {
      case HELM:
        return getHelmInitCommand(version);
      case KUBECTL:
      case KUSTOMIZE:
      case OC:
      case SCM:
      case TERRAFORM_CONFIG_INSPECT:
      case GO_TEMPLATE:
      case HARNESS_PYWINRM:
      case CHARTMUSEUM:
        return StringUtils.EMPTY;
      default:
        throw new IllegalArgumentException("Unknown tool: " + tool);
    }
  }

  @VisibleForTesting
  static boolean validateToolExists(final Path toolPath, final ClientTool tool) {
    if (!Files.exists(toolPath)) {
      log.info("{} does not exist", toolPath);
      return false;
    }
    return runToolCommand(toolPath, tool.getValidateCommandArgs());
  }

  static boolean runToolCommand(final Path toolPath, final String script) {
    final String command = String.format("%s %s", toolPath, script);
    try {
      return runCommand(null, command);
    } catch (final Exception e) {
      log.error("Failed running {} command: {}", toolPath.getFileName(), command, e);
      return false;
    }
  }

  private static boolean runCommand(final Path dir, final String script)
      throws IOException, InterruptedException, TimeoutException {
    final ProcessExecutor processExecutor = new ProcessExecutor()
                                                .timeout(5, TimeUnit.MINUTES)
                                                .directory(dir != null ? dir.toFile() : null)
                                                .command("/bin/bash", "-c", script)
                                                .readOutput(true)
                                                .redirectOutput(new LogOutputStream() {
                                                  @Override
                                                  protected void processLine(final String line) {
                                                    log.info(line);
                                                  }
                                                })
                                                .redirectError(new LogOutputStream() {
                                                  @Override
                                                  protected void processLine(final String line) {
                                                    log.error(line);
                                                  }
                                                });

    final ProcessResult result = processExecutor.execute();

    if (result.getExitValue() == 0) {
      log.info(result.outputUTF8());
      return true;
    } else {
      log.error(result.outputUTF8());
      return false;
    }
  }

  private static String getOsPath() {
    if (SystemUtils.IS_OS_WINDOWS) {
      throw new UnsupportedOperationException("Windows is not supported");
    }
    if (SystemUtils.IS_OS_MAC) {
      return "darwin";
    }
    return "linux";
  }

  private static String getArchPath() {
    if (SystemUtils.IS_OS_MAC) {
      return amd64;
    }
    if (x86_64.equals(SystemUtils.OS_ARCH) || amd64.equals(SystemUtils.OS_ARCH)) {
      return amd64;
    } else if (aarch64.equals(SystemUtils.OS_ARCH) || arm64.equals(SystemUtils.OS_ARCH)) {
      return arm64;
    } else {
      throw new UnsupportedOperationException("Unsupported arch");
    }
  }
}
