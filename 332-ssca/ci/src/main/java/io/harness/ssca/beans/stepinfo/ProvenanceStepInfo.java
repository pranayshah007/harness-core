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
import io.harness.ssca.beans.Attestation;
import io.harness.ssca.beans.SscaConstants;
import io.harness.ssca.beans.provenance.DockerSourceSpec;
import io.harness.ssca.beans.provenance.ProvenanceSource;
import io.harness.ssca.beans.provenance.ProvenanceSourceType;
import io.harness.yaml.extended.ci.container.ContainerResource;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.TypeAlias;

@Data
@Builder
@TypeAlias("ProvenanceStepInfo")
@NoArgsConstructor
@AllArgsConstructor
public class ProvenanceStepInfo implements PluginCompatibleStep {
  @JsonProperty(YamlNode.UUID_FIELD_NAME)
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) })
  @ApiModelProperty(hidden = true)
  private String uuid;

  private Attestation attestation;
  private ProvenanceSource source;

  @Override
  public TypeInfo getNonYamlInfo() {
    return TypeInfo.builder().stepInfoType(CIStepInfoType.PROVENANCE).build();
  }

  @Override
  public StepType getStepType() {
    return SscaConstants.PROVENANCE_STEP_TYPE;
  }

  @Override
  public ParameterField<String> getConnectorRef() {
    if (source != null) {
      if (source.getType() == ProvenanceSourceType.DOCKER) {
        return ((DockerSourceSpec) source.getSpec()).getConnector();
      }
    }
    return null;
  }

  @Override
  public ContainerResource getResources() {
    return null;
  }

  @Override
  public ParameterField<Integer> getRunAsUser() {
    return null;
  }

  @Override
  public String getFacilitatorType() {
    return OrchestrationFacilitatorType.ASYNC;
  }
}
