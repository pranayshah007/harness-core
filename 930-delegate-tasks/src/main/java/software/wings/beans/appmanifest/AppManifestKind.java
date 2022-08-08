/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans.appmanifest;

import static io.harness.annotations.dev.HarnessModule._950_DELEGATE_TASKS_BEANS;
import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.pcf.model.PcfConstants.VARS_YML;

import static software.wings.beans.yaml.YamlConstants.KUSTOMIZE_PATCHES_FILE;
import static software.wings.beans.yaml.YamlConstants.OC_PARAMS_FILE;
import static software.wings.beans.yaml.YamlConstants.VALUES_YAML_KEY;

import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;

import software.wings.beans.yaml.YamlConstants;

@OwnedBy(CDP)
@TargetModule(_950_DELEGATE_TASKS_BEANS)
public enum AppManifestKind {
  // K8sManifest is not specific to K8s, this simply represents a service manifest, pcf, ecs and k8s use the same enum
  // for service spec. We could not rename it to manifest only because its already saved in DB.
  VALUES(VALUES_YAML_KEY, YamlConstants.VALUES_FOLDER),
  KUSTOMIZE_PATCHES(KUSTOMIZE_PATCHES_FILE, YamlConstants.KUSTOMIZE_PATCHES_FOLDER),
  K8S_MANIFEST(VALUES_YAML_KEY),
  PCF_OVERRIDE(VARS_YML, YamlConstants.PCF_OVERRIDES_FOLDER),
  AZURE_APP_SERVICE_MANIFEST(YamlConstants.APP_SERVICE_MANIFEST_FILE_NAME),
  AZURE_APP_SETTINGS_OVERRIDE(YamlConstants.APP_SETTINGS_FILE, YamlConstants.AZURE_APP_SETTINGS_OVERRIDES_FOLDER),
  AZURE_CONN_STRINGS_OVERRIDE(YamlConstants.CONN_STRINGS_FILE, YamlConstants.AZURE_CONN_STRINGS_OVERRIDES_FOLDER),
  HELM_CHART_OVERRIDE(VALUES_YAML_KEY),
  OC_PARAMS(OC_PARAMS_FILE, YamlConstants.OC_PARAMS_FOLDER);

  private String defaultFileName;
  private String yamlFolderName;

  AppManifestKind(String defaultFileName, String yamlFolderName) {
    this.defaultFileName = defaultFileName;
    this.yamlFolderName = yamlFolderName;
  }

  AppManifestKind(String defaultFileName) {
    this.defaultFileName = defaultFileName;
  }

  public String getDefaultFileName() {
    return defaultFileName;
  }

  public String getYamlFolderName() {
    return yamlFolderName;
  }
}
