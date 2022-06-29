/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.provision.cloudformation;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.pipeline.CDStepInfo;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.filters.WithConnectorRef;
import io.harness.plancreator.steps.TaskSelectorYaml;
import io.harness.plancreator.steps.common.SpecParameters;
import io.harness.pms.contracts.steps.StepType;
import io.harness.pms.execution.OrchestrationFacilitatorType;
import io.harness.pms.yaml.ParameterField;
import io.harness.validation.Validator;
import io.harness.walktree.visitor.Visitable;

import com.fasterxml.jackson.annotation.JsonIgnore;
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
@TypeAlias("cloudformationCreateStackStepInfo")
@JsonTypeName(StepSpecTypeConstants.CLOUDFORMATION_CREATE_STACK)
@FieldDefaults(level = AccessLevel.PRIVATE)
@RecasterAlias("io.harness.cdng.provision.cloudformation.CloudformationCreateStackStepInfo")
public class CloudformationCreateStackStepInfo
    extends CloudformationCreateStackBaseStepInfo implements CDStepInfo, Visitable, WithConnectorRef {
  @NotNull @JsonProperty("configuration") CloudformationCreateStackStepConfiguration cloudformationStepConfiguration;

  @Builder(builderMethodName = "infoBuilder")
  public CloudformationCreateStackStepInfo(ParameterField<String> provisionerIdentifier,
      ParameterField<List<TaskSelectorYaml>> delegateSelector,
      CloudformationCreateStackStepConfiguration cloudformationStepConfiguration, String uuid) {
    super(provisionerIdentifier, delegateSelector, uuid);
    this.cloudformationStepConfiguration = cloudformationStepConfiguration;
  }

  @Override
  public Map<String, ParameterField<String>> extractConnectorRefs() {
    Map<String, ParameterField<String>> connectorRefMap = new HashMap<>();
    // Extract connector refs from the step configuration
    if (cloudformationStepConfiguration.getConnectorRef() != null) {
      connectorRefMap.put("configuration.connectorRef", cloudformationStepConfiguration.getConnectorRef());
    }
    if (cloudformationStepConfiguration.getTemplateFile().getSpec().getType().equals(
            CloudformationTemplateFileTypes.Remote)) {
      RemoteCloudformationTemplateFileSpec remoteTemplateFile =
          (RemoteCloudformationTemplateFileSpec) cloudformationStepConfiguration.getTemplateFile().getSpec();
      connectorRefMap.put("configuration.spec.templateFile.store.spec.connectorRef",
          remoteTemplateFile.getStore().getSpec().getConnectorReference());
    }

    if (cloudformationStepConfiguration.getTags() != null
        && cloudformationStepConfiguration.getTags().getSpec().getType().equals(CloudformationTagsFileTypes.Remote)) {
      RemoteCloudformationTagsFileSpec remoteTemplateFile =
          (RemoteCloudformationTagsFileSpec) cloudformationStepConfiguration.getTags().getSpec();
      connectorRefMap.put("configuration.spec.tags.store.spec.connectorRef",
          remoteTemplateFile.getStore().getSpec().getConnectorReference());
    }

    if (isNotEmpty(cloudformationStepConfiguration.getParametersFilesSpecs())) {
      cloudformationStepConfiguration.getParametersFilesSpecs().forEach(cloudformationParametersFileSpec
          -> connectorRefMap.put("configuration.spec.parameters." + cloudformationParametersFileSpec.getIdentifier()
                  + ".store.spec.connectorRef",
              cloudformationParametersFileSpec.getStore().getSpec().getConnectorReference()));
    }

    return connectorRefMap;
  }

  @Override
  @JsonIgnore
  public StepType getStepType() {
    return CloudformationCreateStackStep.STEP_TYPE;
  }

  @Override
  public String getFacilitatorType() {
    return OrchestrationFacilitatorType.TASK_CHAIN;
  }

  @Override
  public SpecParameters getSpecParameters() {
    validateSpecParameters();
    return CloudformationCreateStackStepParameters.infoBuilder()
        .delegateSelectors(getDelegateSelectors())
        .provisionerIdentifier(getProvisionerIdentifier())
        .configuration(cloudformationStepConfiguration.toStepParameters())
        .build();
  }

  void validateSpecParameters() {
    Validator.notNullCheck("Cloudformation Step configuration is null", cloudformationStepConfiguration);
    cloudformationStepConfiguration.validateParams();
  }

  @Override
  public ParameterField<List<TaskSelectorYaml>> fetchDelegateSelectors() {
    return getDelegateSelectors();
  }
}
