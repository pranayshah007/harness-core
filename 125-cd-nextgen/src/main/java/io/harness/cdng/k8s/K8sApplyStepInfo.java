/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.k8s;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import static java.util.Objects.isNull;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.manifest.yaml.K8sStepCommandFlag;
import io.harness.cdng.manifest.yaml.ManifestAttributes;
import io.harness.cdng.manifest.yaml.ManifestConfigWrapper;
import io.harness.cdng.manifest.yaml.harness.HarnessStore;
import io.harness.cdng.pipeline.steps.CDAbstractStepInfo;
import io.harness.cdng.visitor.helpers.cdstepinfo.K8sApplyStepInfoVisitorHelper;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.filters.WithFileRefs;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.plancreator.steps.common.SpecParameters;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlNode;
import io.harness.walktree.visitor.SimpleVisitorHelper;
import io.harness.walktree.visitor.Visitable;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.swagger.annotations.ApiModelProperty;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(CDP)
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@JsonTypeName(StepSpecTypeConstants.K8S_APPLY)
@SimpleVisitorHelper(helperClass = K8sApplyStepInfoVisitorHelper.class)
@TypeAlias("k8sApplyStepInfo")
@RecasterAlias("io.harness.cdng.k8s.K8sApplyStepInfo")
public class K8sApplyStepInfo extends K8sApplyBaseStepInfo implements CDAbstractStepInfo, Visitable, WithFileRefs {
  @JsonProperty(YamlNode.UUID_FIELD_NAME)
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) })
  @ApiModelProperty(hidden = true)
  private String uuid;

  // For Visitor Framework Impl
  @Getter(onMethod_ = { @ApiModelProperty(hidden = true) }) @ApiModelProperty(hidden = true) String metadata;

  @Builder(builderMethodName = "infoBuilder")
  public K8sApplyStepInfo(ParameterField<Boolean> skipDryRun, ParameterField<Boolean> skipSteadyStateCheck,
      ParameterField<List<String>> filePaths, ParameterField<List<TaskSelectorYaml>> delegateSelectors,
      List<ManifestConfigWrapper> overrides, ParameterField<Boolean> skipRendering,
      List<K8sStepCommandFlag> commandFlags) {
    super(skipDryRun, skipSteadyStateCheck, filePaths, delegateSelectors, overrides, skipRendering, commandFlags);
  }

  @Override
  public StepType getStepType() {
    return K8sApplyStep.STEP_TYPE;
  }

  @Override
  public String getFacilitatorType() {
    return OrchestrationFacilitatorType.TASK_CHAIN;
  }

  @Override
  public SpecParameters getSpecParameters() {
    return K8sApplyStepParameters.infoBuilder()
        .filePaths(this.getFilePaths())
        .skipDryRun(this.getSkipDryRun())
        .skipSteadyStateCheck(skipSteadyStateCheck)
        .delegateSelectors(delegateSelectors)
        .overrides(overrides)
        .skipRendering(skipRendering)
        .commandFlags(commandFlags)
        .build();
  }

  @Override
  public ParameterField<List<TaskSelectorYaml>> fetchDelegateSelectors() {
    return getDelegateSelectors();
  }

  @Override
  public Map<String, ParameterField<List<String>>> extractFileRefs() {
    Map<String, ParameterField<List<String>>> fileRefMap = new HashMap<>();

    List<String> fileRefs = new ArrayList<>();
    if (isNull(overrides)) {
      return fileRefMap;
    }
    overrides.forEach(manifestWrapper -> {
      if (isNull(manifestWrapper.getManifest())) {
        return;
      }
      ManifestAttributes manifestAttributes = manifestWrapper.getManifest().getSpec();
      if (manifestAttributes.getStoreConfig() instanceof HarnessStore) {
        ParameterField<List<String>> files = ((HarnessStore) manifestAttributes.getStoreConfig()).getFiles();
        if (files.getValue() != null) {
          fileRefMap.put(String.format("overrides.manifest.%s.spec.store.spec.files",
                             manifestWrapper.getManifest().getIdentifier()),
              files);
        }
      }
    });
    return fileRefMap;
  }
}
