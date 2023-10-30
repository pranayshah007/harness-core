/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.template;
import static io.harness.NGCommonEntityConstants.IDENTIFIER_KEY;
import static io.harness.NGCommonEntityConstants.NAME_KEY;
import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.ng.core.template.TemplateEntityConstants.ARTIFACT_SOURCE;
import static io.harness.ng.core.template.TemplateEntityConstants.ARTIFACT_SOURCE_ROOT_FIELD;
import static io.harness.ng.core.template.TemplateEntityConstants.CUSTOM_DEPLOYMENT;
import static io.harness.ng.core.template.TemplateEntityConstants.CUSTOM_DEPLOYMENT_ROOT_FIELD;
import static io.harness.ng.core.template.TemplateEntityConstants.MONITORED_SERVICE;
import static io.harness.ng.core.template.TemplateEntityConstants.MONITORED_SERVICE_ROOT_FIELD;
import static io.harness.ng.core.template.TemplateEntityConstants.PIPELINE;
import static io.harness.ng.core.template.TemplateEntityConstants.PIPELINE_ROOT_FIELD;
import static io.harness.ng.core.template.TemplateEntityConstants.SECRET_MANAGER;
import static io.harness.ng.core.template.TemplateEntityConstants.SECRET_MANAGER_ROOT_FIELD;
import static io.harness.ng.core.template.TemplateEntityConstants.STAGE;
import static io.harness.ng.core.template.TemplateEntityConstants.STAGE_ROOT_FIELD;
import static io.harness.ng.core.template.TemplateEntityConstants.STEP;
import static io.harness.ng.core.template.TemplateEntityConstants.STEP_GROUP;
import static io.harness.ng.core.template.TemplateEntityConstants.STEP_GROUP_ROOT_FIELD;
import static io.harness.ng.core.template.TemplateEntityConstants.STEP_ROOT_FIELD;

import static java.util.Arrays.asList;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;
import java.util.List;
import lombok.Getter;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@OwnedBy(CDC)
public enum TemplateEntityType {
  @JsonProperty(STEP)
  STEP_TEMPLATE(STEP, STEP_ROOT_FIELD, asList(IDENTIFIER_KEY, NAME_KEY), HarnessTeam.PIPELINE, true, "step"),
  @JsonProperty(STAGE)
  STAGE_TEMPLATE(STAGE, STAGE_ROOT_FIELD, asList(IDENTIFIER_KEY, NAME_KEY), HarnessTeam.PIPELINE, true, "stage"),
  @JsonProperty(PIPELINE)
  PIPELINE_TEMPLATE(
      PIPELINE, PIPELINE_ROOT_FIELD, asList(IDENTIFIER_KEY, NAME_KEY), HarnessTeam.PIPELINE, true, "pipeline"),
  @JsonProperty(CUSTOM_DEPLOYMENT)
  CUSTOM_DEPLOYMENT_TEMPLATE(CUSTOM_DEPLOYMENT, CUSTOM_DEPLOYMENT_ROOT_FIELD, asList(IDENTIFIER_KEY, NAME_KEY),
      HarnessTeam.CDP, false, "customdeployment"),
  @JsonProperty(MONITORED_SERVICE)
  MONITORED_SERVICE_TEMPLATE(MONITORED_SERVICE, MONITORED_SERVICE_ROOT_FIELD, asList(IDENTIFIER_KEY, NAME_KEY),
      HarnessTeam.CV, true, "monitoredservice"),

  @JsonProperty(SECRET_MANAGER)
  SECRET_MANAGER_TEMPLATE(SECRET_MANAGER, SECRET_MANAGER_ROOT_FIELD, asList(IDENTIFIER_KEY, NAME_KEY), HarnessTeam.PL,
      false, "secretmanager"),

  @JsonProperty(ARTIFACT_SOURCE)
  ARTIFACT_SOURCE_TEMPLATE(ARTIFACT_SOURCE, ARTIFACT_SOURCE_ROOT_FIELD, asList(IDENTIFIER_KEY, NAME_KEY), CDC, false,
      "artifactsourcetemplate"),

  @JsonProperty(STEP_GROUP)
  STEPGROUP_TEMPLATE(
      STEP_GROUP, STEP_GROUP_ROOT_FIELD, asList(IDENTIFIER_KEY, NAME_KEY), HarnessTeam.PIPELINE, true, "stepgroup");

  private final String yamlType;
  private String rootYamlName;
  private final List<String> yamlFieldKeys;
  @Getter private HarnessTeam ownerTeam;
  private boolean isGitEntity;
  private String nodeGroup;

  TemplateEntityType(String yamlType, String rootYamlName, List<String> yamlFieldKeys, HarnessTeam ownerTeam,
      boolean isGitEntity, String nodeGroup) {
    this.yamlType = yamlType;
    this.rootYamlName = rootYamlName;
    this.yamlFieldKeys = yamlFieldKeys;
    this.ownerTeam = ownerTeam;
    this.isGitEntity = isGitEntity;
    this.nodeGroup = nodeGroup;
  }

  @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
  public static TemplateEntityType getTemplateType(@JsonProperty("type") String yamlType) {
    for (TemplateEntityType value : TemplateEntityType.values()) {
      if (value.yamlType.equalsIgnoreCase(yamlType)) {
        return value;
      }
    }
    throw new IllegalArgumentException(String.format(
        "Invalid value:%s, the expected values are: %s", yamlType, Arrays.toString(TemplateEntityType.values())));
  }

  @Override
  @JsonValue
  public String toString() {
    return this.yamlType;
  }

  public String getRootYamlName() {
    return this.rootYamlName;
  }

  public List<String> getYamlFieldKeys() {
    return this.yamlFieldKeys;
  }

  public HarnessTeam getOwnerTeam() {
    return ownerTeam;
  }

  public boolean isGitEntity() {
    return isGitEntity;
  }

  public String getNodeGroup() {
    return nodeGroup;
  }
}
