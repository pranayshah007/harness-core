/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.common;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;

import com.google.common.collect.ImmutableList;
import java.util.List;

@OwnedBy(HarnessTeam.DX)
@TargetModule(HarnessModule._957_CG_BEANS)
public interface TemplateConstants {
  String LATEST_TAG = "latest";
  String DEFAULT_TAG = "default";
  char PATH_DELIMITER = '/';
  String PREFIX_FOR_APP = "App";
  String APP_PREFIX = PREFIX_FOR_APP + PATH_DELIMITER;
  char GALLERY_TOP_LEVEL_PATH_DELIMITER = ':';
  String IMPORTED_TEMPLATE_PREFIX = "imported" + GALLERY_TOP_LEVEL_PATH_DELIMITER;
  /**
   * Gallery constants
   */
  String HARNESS_GALLERY = "Harness";

  /**
   * Constants for templates
   */
  String COMMAND_PATH = "templategalleries/harness/command/";
  String TOMCAT_WAR_STOP_PATH = COMMAND_PATH + "tomcat/WarStop.yaml";
  String TOMCAT_WAR_START_PATH = COMMAND_PATH + "tomcat/WarStart.yaml";
  String TOMCAT_WAR_INSTALL_PATH = COMMAND_PATH + "tomcat/WarInstall.yaml";
  String JBOSS_WAR_STOP_PATH = COMMAND_PATH + "jboss/WarStop.yaml";
  String JBOSS_WAR_START_PATH = COMMAND_PATH + "jboss/WarStart.yaml";
  String JBOSS_WAR_INSTALL_PATH = COMMAND_PATH + "jboss/WarInstall.yaml";
  String POWER_SHELL_IIS_INSTALL_PATH = COMMAND_PATH + "powershell/Install.yaml";
  String POWER_SHELL_IIS_V2_INSTALL_PATH = COMMAND_PATH + "powershell/Install-V2.yaml";
  String POWER_SHELL_IIS_V3_INSTALL_PATH = COMMAND_PATH + "powershell/Install-V3.yaml";
  String POWER_SHELL_IIS_V4_INSTALL_PATH = COMMAND_PATH + "powershell/Install-V4.yaml";
  String POWER_SHELL_IIS_V5_INSTALL_PATH = COMMAND_PATH + "powershell/Install-V5.yaml";
  String POWER_SHELL_IIS_V6_INSTALL_PATH = COMMAND_PATH + "powershell/Install-V6.yaml";
  String POWER_SHELL_IIS_WEBSITE_INSTALL_PATH = COMMAND_PATH + "powershell/Install-IISWebsite.yaml";
  String POWER_SHELL_IIS_WEBSITE_V2_INSTALL_PATH = COMMAND_PATH + "powershell/Install-IISWebsite-V2.yaml";
  String POWER_SHELL_IIS_WEBSITE_V3_INSTALL_PATH = COMMAND_PATH + "powershell/Install-IISWebsite-V3.yaml";
  String POWER_SHELL_IIS_WEBSITE_V4_INSTALL_PATH = COMMAND_PATH + "powershell/Install-IISWebsite-V4.yaml";
  String POWER_SHELL_IIS_WEBSITE_V5_INSTALL_PATH = COMMAND_PATH + "powershell/Install-IISWebsite-V5.yaml";
  String POWER_SHELL_IIS_APP_INSTALL_PATH = COMMAND_PATH + "powershell/Install-IISApp.yaml";
  String POWER_SHELL_IIS_APP_V2_INSTALL_PATH = COMMAND_PATH + "powershell/Install-IISApp-V2.yaml";
  String POWER_SHELL_IIS_APP_V3_INSTALL_PATH = COMMAND_PATH + "powershell/Install-IISApp-V3.yaml";
  String POWER_SHELL_IIS_APP_V4_INSTALL_PATH = COMMAND_PATH + "powershell/Install-IISApp-V4.yaml";
  String POWER_SHELL_IIS_APP_V5_INSTALL_PATH = COMMAND_PATH + "powershell/Install-IISApp-V5.yaml";
  String GENERIC_JSON_PATH = COMMAND_PATH + "generic/ExportJsonToEnvVariables.yaml";

  String HTTP_PATH = "templategalleries/harness/http/";

  String HTTP_HEALTH_CHECK = HTTP_PATH + "HealthCheck.yaml";

  String SHELL_SCRIPT_PATH = "templategalleries/harness/shellscript/";
  String SHELL_SCRIPT_EXAMPLE = SHELL_SCRIPT_PATH + "ShellScript.yaml";

  /**
   * Template Folder Constants
   */
  String LOAD_BALANCERS = "Load Balancers";
  String F5_LOAD_BALANCER = "F5 Load Balancer";
  String TOMCAT_COMMANDS = "Tomcat Commands";
  String JBOSS_COMMANDS = "JBoss Commands";
  String POWER_SHELL_COMMANDS = "Power Shell Commands";
  String GENERIC_COMMANDS = "Generic Commands";
  String HTTP_VERIFICATION = "HTTP Verifications";
  String SHELL_SCRIPTS = "Shell Scripts";

  /**
   * Template types
   */
  String SSH = "SSH";
  String SHELL_SCRIPT = "SHELL_SCRIPT";
  String HTTP = "HTTP";
  String ARTIFACT_SOURCE = "ARTIFACT_SOURCE";
  String PCF_PLUGIN = "PCF_PLUGIN";
  String CUSTOM_DEPLOYMENT_TYPE = "CUSTOM_DEPLOYMENT_TYPE";

  List<String> TEMPLATE_TYPES_WITH_YAML_SUPPORT =
      ImmutableList.of(SHELL_SCRIPT, HTTP, SSH, ARTIFACT_SOURCE, PCF_PLUGIN, CUSTOM_DEPLOYMENT_TYPE);

  String TEMPLATE_REF_COMMAND = "TEMPLATE_REF_COMMAND";

  /**
   * Artifact Types
   */

  String CUSTOM = "CUSTOM";

  /**
   * Template Metadata
   */

  String COPIED_TEMPLATE_METADATA = "COPIED_TEMPLATE_METADATA";
  String IMPORTED_TEMPLATE_METADATA = "IMPORTED_TEMPLATE_METADATA";

  /**
   * Imported Template Details
   */

  String HARNESS_COMMAND_LIBRARY_GALLERY = "HARNESS_COMMAND_LIBRARY_GALLERY";
}
