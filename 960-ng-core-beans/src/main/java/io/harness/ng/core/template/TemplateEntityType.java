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
import io.harness.pms.yaml.HarnessYamlVersion;

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

  // yaml type in template yaml v0 eg: Stage, Step, Pipeline, StepGroup, etc.
  private final String yamlTypeV0;
  // root yaml name to be added in pipeline yaml while merging template yaml v0 eg: step, stage, stepGroup, etc.
  private String rootYamlNameV0;
  private final List<String> yamlFieldKeys;
  @Getter private HarnessTeam ownerTeam;
  private boolean isGitEntity;
  // yaml type of template v1 eg: stage, step, group, etc.
  private String yamlTypeV1;

  TemplateEntityType(String yamlTypeV0, String rootYamlNameV0, List<String> yamlFieldKeys, HarnessTeam ownerTeam,
      boolean isGitEntity, String yamlTypeV1) {
    this.yamlTypeV0 = yamlTypeV0;
    this.rootYamlNameV0 = rootYamlNameV0;
    this.yamlFieldKeys = yamlFieldKeys;
    this.ownerTeam = ownerTeam;
    this.isGitEntity = isGitEntity;
    this.yamlTypeV1 = yamlTypeV1;
  }

  public static TemplateEntityType getTemplateType(@JsonProperty("type") String yamlType, String version) {
    switch (version) {
      case HarnessYamlVersion.V0:
        for (TemplateEntityType value : TemplateEntityType.values()) {
          if (value.yamlTypeV0.equalsIgnoreCase(yamlType)) {
            return value;
          }
        }
        break;
      case HarnessYamlVersion.V1:
        for (TemplateEntityType value : TemplateEntityType.values()) {
          if (value.yamlTypeV1.equalsIgnoreCase(yamlType)) {
            return value;
          }
        }
        break;
      default:
        throw new IllegalArgumentException(String.format("Yaml version %s does not exist", version));
    }
    throw new IllegalArgumentException(String.format(
        "Invalid value:%s, the expected values are: %s", yamlType, Arrays.toString(TemplateEntityType.values())));
  }

  @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
  public static TemplateEntityType getTemplateType(@JsonProperty("type") String yamlType) {
    return getTemplateType(yamlType, HarnessYamlVersion.V0);
  }

  @Override
  @JsonValue
  public String toString() {
    return this.yamlTypeV0;
  }

  public String getRootYamlName() {
    return this.rootYamlNameV0;
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

  public String getYamlTypeV1() {
    return yamlTypeV1;
  }
}
