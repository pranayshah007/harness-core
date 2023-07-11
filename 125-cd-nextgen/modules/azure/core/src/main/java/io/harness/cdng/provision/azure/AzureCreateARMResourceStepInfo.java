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
@TypeAlias("CreateStepInfo")
@JsonTypeName(StepSpecTypeConstants.AZURE_CREATE_ARM_RESOURCE)
@FieldDefaults(level = AccessLevel.PRIVATE)
@RecasterAlias("io.harness.cdng.provision.azure.AzureCreateARMResourceStepInfo")
public class AzureCreateARMResourceStepInfo extends AzureCreateARMResourceStepBaseStepInfo
    implements CDAbstractStepInfo, Visitable, WithConnectorRef, WithFileRefs {
  @NotNull @JsonProperty("configuration") AzureCreateARMResourceStepConfiguration createStepConfiguration;

  @Builder(builderMethodName = "infoBuilder")
  public AzureCreateARMResourceStepInfo(ParameterField<String> provisionerIdentifier,
      ParameterField<List<TaskSelectorYaml>> delegateSelector,
      AzureCreateARMResourceStepConfiguration createStepConfiguration, String uuid) {
    super(provisionerIdentifier, delegateSelector, uuid);
    this.createStepConfiguration = createStepConfiguration;
  }

  @Override
  public Map<String, ParameterField<String>> extractConnectorRefs() {
    Map<String, ParameterField<String>> connectorRefMap = new HashMap<>();
    // Azure connector
    if (createStepConfiguration.getConnectorRef() != null) {
      connectorRefMap.put("configuration.spec.connectorRef",
          ParameterField.createValueField(createStepConfiguration.getConnectorRef().getValue()));
    }

    if (createStepConfiguration.getTemplate() != null
        && ManifestStoreType.isInGitSubset(createStepConfiguration.getTemplate().getStore().getSpec().getKind())) {
      connectorRefMap.put("configuration.spec.templateFile.store.spec.connectorRef",
          createStepConfiguration.getTemplate().getStore().getSpec().getConnectorReference());
    }
    if (createStepConfiguration.getParameters() != null
        && ManifestStoreType.isInGitSubset(createStepConfiguration.getParameters().getStore().getSpec().getKind())) {
      connectorRefMap.put("configuration.spec.parameters.store.spec.connectorRef",
          createStepConfiguration.getParameters().getStore().getSpec().getConnectorReference());
    }

    return connectorRefMap;
  }

  @Override
  public StepType getStepType() {
    return AzureCreateARMResourceStep.STEP_TYPE;
  }

  @Override
  public String getFacilitatorType() {
    return OrchestrationFacilitatorType.TASK_CHAIN;
  }

  @Override
  public SpecParameters getSpecParameters() {
    validateSpecParameters();
    return AzureCreateARMResourceStepParameters.infoBuilder()
        .delegateSelectors(getDelegateSelectors())
        .provisionerIdentifier(getProvisionerIdentifier())
        .configurationParameters(createStepConfiguration.toStepParameters())
        .build();
  }

  void validateSpecParameters() {
    Validator.notNullCheck("AzureCreateARMResource Step configuration is null", createStepConfiguration);
    createStepConfiguration.validateParams();
  }

  @Override
  public ParameterField<List<TaskSelectorYaml>> fetchDelegateSelectors() {
    return getDelegateSelectors();
  }

  @Override
  public Map<String, ParameterField<List<String>>> extractFileRefs() {
    Map<String, ParameterField<List<String>>> fileRefMap = new HashMap<>();

    if (createStepConfiguration.getTemplate().getStore().getSpec() instanceof HarnessStore) {
      HarnessStore harnessStore = (HarnessStore) createStepConfiguration.getTemplate().getStore().getSpec();
      ParameterField<List<String>> files = harnessStore.getFiles();
      fileRefMap.put("configuration.template.store.spec.files", files);
    }

    if (createStepConfiguration.getParameters() != null
        && createStepConfiguration.getParameters().getStore().getSpec() instanceof HarnessStore) {
      HarnessStore harnessStore = (HarnessStore) createStepConfiguration.getParameters().getStore().getSpec();
      ParameterField<List<String>> files = harnessStore.getFiles();
      fileRefMap.put("configuration.parameters.store.spec.files", files);
    }
    return fileRefMap;
  }
}
