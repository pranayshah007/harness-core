/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.beans.stepinfo;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.plugin.compatible.PluginCompatibleStep;
import io.harness.beans.steps.CIStepInfoType;
import io.harness.beans.steps.TypeInfo;
import io.harness.filters.WithConnectorRef;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlNode;
import io.harness.ssca.beans.SscaConstants;
import io.harness.ssca.beans.attestation.verify.VerifyAttestation;
import io.harness.ssca.beans.policy.EnforcementPolicy;
import io.harness.ssca.beans.source.ImageSbomSource;
import io.harness.ssca.beans.source.SbomSource;
import io.harness.ssca.beans.source.SbomSourceType;
import io.harness.yaml.core.VariableExpression;
import io.harness.yaml.extended.ci.container.ContainerResource;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.annotations.ApiModelProperty;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.TypeAlias;

@Data
@NoArgsConstructor
@JsonTypeName(SscaConstants.SSCA_ENFORCEMENT)
@TypeAlias("SscaEnforcementStepInfo")
@OwnedBy(HarnessTeam.SSCA)
@Builder
@AllArgsConstructor
@RecasterAlias("io.harness.ssca.beans.stepinfo.SscaEnforcementStepInfo")
public class SscaEnforcementStepInfo implements PluginCompatibleStep, WithConnectorRef {
  @VariableExpression(skipVariableExpression = true) public static final int DEFAULT_RETRY = 1;

  @JsonProperty(YamlNode.UUID_FIELD_NAME)
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) })
  @ApiModelProperty(hidden = true)
  private String uuid;

  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) }) @ApiModelProperty(hidden = true) private String identifier;
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) }) @ApiModelProperty(hidden = true) private String name;

  @NotNull SbomSource source;
  VerifyAttestation verifyAttestation;
  @NotNull EnforcementPolicy policy;
  ContainerResource resources;

  @Override
  public TypeInfo getNonYamlInfo() {
    return TypeInfo.builder().stepInfoType(CIStepInfoType.SSCA_ENFORCEMENT).build();
  }

  @Override
  public StepType getStepType() {
    return SscaConstants.SSCA_ENFORCEMENT_STEP_TYPE;
  }

  @Override
  public String getFacilitatorType() {
    return OrchestrationFacilitatorType.ASYNC;
  }

  @Override
  @ApiModelProperty(hidden = true)
  public ParameterField<String> getConnectorRef() {
    if (source != null) {
      switch (source.getType()) {
        case IMAGE:
          return ((ImageSbomSource) source.getSbomSourceSpec()).getConnector();
        default:
          return null;
      }
    }
    return null;
  }

  @Override
  @ApiModelProperty(hidden = true)
  public ParameterField<Integer> getRunAsUser() {
    return null;
  }

  @Override
  public Map<String, ParameterField<String>> extractConnectorRefs() {
    Map<String, ParameterField<String>> connectorMap = new HashMap<>();
    if (source != null && SbomSourceType.IMAGE.equals(source.getType())) {
      connectorMap.put("source.spec.connector", ((ImageSbomSource) source.getSbomSourceSpec()).getConnector());
    }
    return connectorMap;
  }

  @Override
  @ApiModelProperty(hidden = true)
  public ParameterField<List<String>> getBaseImageConnectorRefs() {
    return new ParameterField<>();
  }
}
