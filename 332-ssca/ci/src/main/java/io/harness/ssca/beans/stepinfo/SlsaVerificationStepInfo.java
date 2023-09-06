/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.beans.stepinfo;

import io.harness.beans.plugin.compatible.PluginCompatibleStep;
import io.harness.beans.steps.CIStepInfoType;
import io.harness.beans.steps.TypeInfo;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlNode;
import io.harness.slsa.beans.verification.source.SlsaDockerSourceSpec;
import io.harness.slsa.beans.verification.source.SlsaVerificationSource;
import io.harness.slsa.beans.verification.source.SlsaVerificationSourceType;
import io.harness.slsa.beans.verification.verify.SlsaVerifyAttestation;
import io.harness.ssca.beans.SscaConstants;
import io.harness.yaml.extended.ci.container.ContainerResource;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.annotations.ApiModelProperty;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.TypeAlias;

@Data
@Builder
@JsonTypeName(SscaConstants.SLSA_VERIFICATION)
@TypeAlias("SlsaVerificationStepInfo")
@NoArgsConstructor
@AllArgsConstructor
public class SlsaVerificationStepInfo implements PluginCompatibleStep {
  @JsonProperty(YamlNode.UUID_FIELD_NAME)
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) })
  @ApiModelProperty(hidden = true)
  private String uuid;

  private SlsaVerificationSource source;
  @JsonProperty("verify_attestation") private SlsaVerifyAttestation slsaVerifyAttestation;

  @Override
  public TypeInfo getNonYamlInfo() {
    return TypeInfo.builder().stepInfoType(CIStepInfoType.SLSA_VERIFICATION).build();
  }

  @Override
  public StepType getStepType() {
    return SscaConstants.SLSA_VERIFICATION_STEP_TYPE;
  }

  @Override
  @ApiModelProperty(hidden = true)
  public ParameterField<String> getConnectorRef() {
    if (source != null) {
      if (source.getType() == SlsaVerificationSourceType.DOCKER) {
        return ((SlsaDockerSourceSpec) source.getSpec()).getConnector();
      }
    }
    return null;
  }

  @Override
  @ApiModelProperty(hidden = true)
  public ContainerResource getResources() {
    return null;
  }

  @Override
  @ApiModelProperty(hidden = true)
  public ParameterField<Integer> getRunAsUser() {
    return null;
  }

  @Override
  public String getFacilitatorType() {
    return OrchestrationFacilitatorType.ASYNC;
  }

  @Override
  @ApiModelProperty(hidden = true)
  public ParameterField<List<String>> getBaseImageConnectorRefs() {
    return new ParameterField<>();
  }
}
