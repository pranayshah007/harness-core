/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.pipeline.helpers;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.service.beans.ServiceDefinitionType;
import io.harness.exception.GeneralException;

import software.wings.utils.ArtifactType;

import com.google.common.io.Resources;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@OwnedBy(CDP)
public class DefaultTempateLoader {
  private static final String DEFAULT_INSTALL_JAR_BASH = "DefaultInstallJarBash";
  private static final String DEFAULT_INSTALL_WAR_BASH = "DefaultInstallWarBash";
  private static final String DEFAULT_INSTALL_TAR_BASH = "DefaultInstallTarBash";
  private static final String DEFAULT_IIS_APPLICATION_POWERSHELL = "DefaultIISApplicationPowerShell";
  private static final String DEFAULT_IIS_WEBSITE_POWERSHELL = "DefaultIISWebsitePowerShell";
  private static final String DEFAULT_IIS_VIRTUAL_DIR_POWERSHELL = "DefaultIISVirtualDirectoryPowerShell";
  private static final String DEFAULT_INSTALL_JAR_BASH_IDENTIFIER = "Default_Install_Jar_Bash";
  private static final String DEFAULT_INSTALL_WAR_BASH_IDENTIFIER = "Default_Install_War_Bash";
  private static final String DEFAULT_INSTALL_TAR_BASH_IDENTIFIER = "Default_Install_Tar_Bash";
  private static final String DEFAULT_IIS_APPLICATION_POWERSHELL_IDENTIFIER = "Default_IIS_Application_PowerShell";
  private static final String DEFAULT_IIS_WEBSITE_POWERSHELL_IDENTIFIER = "Default_IIS_Website_PowerShell";
  private static final String DEFAULT_IIS_VIRTUAL_DIR_POWERSHELL_IDENTIFIER =
      "Default_IIS_Virtual_Directory_PowerShell";
  private static final String DEFAULT_TEMPLATES_DIR = "snippets/Pipelines/execution/v2/templates";
  private static String defaultInstallWarBashTemplateYaml;
  private static String defaultInstallJarBashTemplateYaml;
  private static String defaultInstallTarBashTemplateYaml;
  private static String defaultIISApplicationPowershellTemplateYaml;
  private static String defaultIISWebsitePowershellTemplateYaml;
  private static String defaultIISVirtualDirPowershellTemplateYaml;

  static {
    try {
      loadDefaultTemplates(Thread.currentThread().getContextClassLoader());
    } catch (IOException e) {
      throw new GeneralException("Error initializing Execution Strategy snippets");
    }
  }

  private static void loadDefaultTemplates(ClassLoader classLoader) throws IOException {
    if (isEmpty(defaultInstallWarBashTemplateYaml)) {
      defaultInstallWarBashTemplateYaml = Resources.toString(
          Objects.requireNonNull(
              classLoader.getResource(String.format("%s/%s.yaml", DEFAULT_TEMPLATES_DIR, DEFAULT_INSTALL_WAR_BASH))),
          StandardCharsets.UTF_8);
    }
    if (isEmpty(defaultInstallJarBashTemplateYaml)) {
      defaultInstallJarBashTemplateYaml = Resources.toString(
          Objects.requireNonNull(
              classLoader.getResource(String.format("%s/%s.yaml", DEFAULT_TEMPLATES_DIR, DEFAULT_INSTALL_JAR_BASH))),
          StandardCharsets.UTF_8);
    }
    if (isEmpty(defaultInstallTarBashTemplateYaml)) {
      defaultInstallTarBashTemplateYaml = Resources.toString(
          Objects.requireNonNull(
              classLoader.getResource(String.format("%s/%s.yaml", DEFAULT_TEMPLATES_DIR, DEFAULT_INSTALL_TAR_BASH))),
          StandardCharsets.UTF_8);
    }
    if (isEmpty(defaultIISApplicationPowershellTemplateYaml)) {
      defaultIISApplicationPowershellTemplateYaml = Resources.toString(
          Objects.requireNonNull(classLoader.getResource(
              String.format("%s/%s.yaml", DEFAULT_TEMPLATES_DIR, DEFAULT_IIS_APPLICATION_POWERSHELL))),
          StandardCharsets.UTF_8);
    }
    if (isEmpty(defaultIISWebsitePowershellTemplateYaml)) {
      defaultIISWebsitePowershellTemplateYaml =
          Resources.toString(Objects.requireNonNull(classLoader.getResource(
                                 String.format("%s/%s.yaml", DEFAULT_TEMPLATES_DIR, DEFAULT_IIS_WEBSITE_POWERSHELL))),
              StandardCharsets.UTF_8);
    }
    if (isEmpty(defaultIISVirtualDirPowershellTemplateYaml)) {
      defaultIISVirtualDirPowershellTemplateYaml = Resources.toString(
          Objects.requireNonNull(classLoader.getResource(
              String.format("%s/%s.yaml", DEFAULT_TEMPLATES_DIR, DEFAULT_IIS_VIRTUAL_DIR_POWERSHELL))),
          StandardCharsets.UTF_8);
    }
  }

  public static TemplateConfig resolveTemplateConfig(
      ServiceDefinitionType serviceDefinitionType, ArtifactType artifactType) {
    if (ServiceDefinitionType.SSH.equals(serviceDefinitionType) && ArtifactType.JAR.equals(artifactType)) {
      return TemplateConfig.builder()
          .templateIdentifier(DEFAULT_INSTALL_JAR_BASH_IDENTIFIER)
          .templateYaml(defaultInstallJarBashTemplateYaml)
          .templateComment("Default Template for SSH service and Artifact type Jar")
          .build();
    } else if (ServiceDefinitionType.SSH.equals(serviceDefinitionType) && ArtifactType.TAR.equals(artifactType)) {
      return TemplateConfig.builder()
          .templateIdentifier(DEFAULT_INSTALL_TAR_BASH_IDENTIFIER)
          .templateYaml(defaultInstallTarBashTemplateYaml)
          .templateComment("Default Template for SSH service and Artifact type Tar")
          .build();
    } else if (ServiceDefinitionType.SSH.equals(serviceDefinitionType) && ArtifactType.WAR.equals(artifactType)) {
      return TemplateConfig.builder()
          .templateIdentifier(DEFAULT_INSTALL_WAR_BASH_IDENTIFIER)
          .templateYaml(defaultInstallWarBashTemplateYaml)
          .templateComment("Default Template for SSH service and Artifact type War")
          .build();
    } else if (ServiceDefinitionType.WINRM.equals(serviceDefinitionType) && ArtifactType.IIS_APP.equals(artifactType)) {
      return TemplateConfig.builder()
          .templateIdentifier(DEFAULT_IIS_APPLICATION_POWERSHELL_IDENTIFIER)
          .templateYaml(defaultIISApplicationPowershellTemplateYaml)
          .templateComment("Default Template for WinRm service and Artifact type IIS Application")
          .build();
    } else if (ServiceDefinitionType.WINRM.equals(serviceDefinitionType)
        && ArtifactType.IIS_VirtualDirectory.equals(artifactType)) {
      return TemplateConfig.builder()
          .templateIdentifier(DEFAULT_IIS_VIRTUAL_DIR_POWERSHELL_IDENTIFIER)
          .templateYaml(defaultIISVirtualDirPowershellTemplateYaml)
          .templateComment("Default Template for WinRm service and Artifact type IIS Virtual Directory")
          .build();
    } else if (ServiceDefinitionType.WINRM.equals(serviceDefinitionType) && ArtifactType.IIS.equals(artifactType)) {
      return TemplateConfig.builder()
          .templateIdentifier(DEFAULT_IIS_WEBSITE_POWERSHELL_IDENTIFIER)
          .templateYaml(defaultIISWebsitePowershellTemplateYaml)
          .templateComment("Default Template for WinRm service and Artifact type IIS Website")
          .build();
    }
    return TemplateConfig.builder().build();
  }

  @Getter
  @Builder
  protected static class TemplateConfig {
    private String templateIdentifier;
    private String templateYaml;
    private String templateComment;
  }
}
