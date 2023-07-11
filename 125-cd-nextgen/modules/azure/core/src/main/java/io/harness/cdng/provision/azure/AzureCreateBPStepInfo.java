/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.provision.azure;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.manifest.ManifestStoreType;
import io.harness.cdng.manifest.yaml.harness.HarnessStore;
import io.harness.cdng.pipeline.steps.CDAbstractStepInfo;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.filters.WithConnectorRef;
import io.harness.filters.WithFileRefs;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.plancreator.steps.common.SpecParameters;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.yaml.ParameterField;
import io.harness.validation.Validator;
import io.harness.walktree.visitor.Visitable;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.annotation.TypeAlias;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@OwnedBy(HarnessTeam.CDP)
@TypeAlias("CreateBPStepInfo")
@JsonTypeName(StepSpecTypeConstants.AZURE_CREATE_BP_RESOURCE)
@FieldDefaults(level = AccessLevel.PRIVATE)
@RecasterAlias("io.harness.cdng.provision.azure.AzureCreateBPStepInfo")
public class AzureCreateBPStepInfo
    extends AzureCreateBPBaseStepInfo implements CDAbstractStepInfo, Visitable, WithConnectorRef, WithFileRefs {
  @NotNull @JsonProperty("configuration") AzureCreateBPStepConfiguration createStepBPConfiguration;

  @Builder(builderMethodName = "infoBuilder")
  public AzureCreateBPStepInfo(ParameterField<List<TaskSelectorYaml>> delegateSelector,
      AzureCreateBPStepConfiguration createStepBPConfiguration, String uuid) {
    super(delegateSelector, uuid);
    this.createStepBPConfiguration = createStepBPConfiguration;
  }

  @Override
  public Map<String, ParameterField<String>> extractConnectorRefs() {
    Map<String, ParameterField<String>> connectorRefMap = new HashMap<>();
    // Azure connector
    if (createStepBPConfiguration.getConnectorRef() != null) {
      connectorRefMap.put("configuration.spec.connectorRef", createStepBPConfiguration.getConnectorRef());
    }

    if (createStepBPConfiguration.getTemplate() != null
        && ManifestStoreType.isInGitSubset(createStepBPConfiguration.getTemplate().getStore().getSpec().getKind())) {
      connectorRefMap.put("configuration.spec.templateFile.store.spec.connectorRef",
          createStepBPConfiguration.getTemplate().getStore().getSpec().getConnectorReference());
    }

    return connectorRefMap;
  }

  @Override
  public StepType getStepType() {
    return AzureCreateBPStep.STEP_TYPE;
  }
  //
  @Override
  public String getFacilitatorType() {
    return OrchestrationFacilitatorType.TASK_CHAIN;
  }

  @Override
  public SpecParameters getSpecParameters() {
    validateSpecParameters();
    return AzureCreateBPStepParameters.infoBuilder()
        .delegateSelectors(getDelegateSelectors())
        .configuration(createStepBPConfiguration.toStepParameters())
        .build();
  }

  void validateSpecParameters() {
    Validator.notNullCheck("AzureCreateBPResource Step configuration is null", createStepBPConfiguration);
    createStepBPConfiguration.validateParams();
  }

  @Override
  public ParameterField<List<TaskSelectorYaml>> fetchDelegateSelectors() {
    return getDelegateSelectors();
  }

  @Override
  public Map<String, ParameterField<List<String>>> extractFileRefs() {
    Map<String, ParameterField<List<String>>> fileRefMap = new HashMap<>();
    if (createStepBPConfiguration.getTemplate().getStore().getSpec() instanceof HarnessStore) {
      HarnessStore harnessStore = (HarnessStore) createStepBPConfiguration.getTemplate().getStore().getSpec();
      ParameterField<List<String>> files = harnessStore.getFiles();
      fileRefMap.put("configuration.template.store.spec.files", files);
    }
    return fileRefMap;
  }
}
