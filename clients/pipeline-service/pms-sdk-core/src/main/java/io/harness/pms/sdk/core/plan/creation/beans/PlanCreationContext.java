/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk.core.plan.creation.beans;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.async.AsyncCreatorContext;
import io.harness.pms.contracts.plan.Dependency;
import io.harness.pms.contracts.plan.ExecutionTriggerInfo;
import io.harness.pms.contracts.plan.PipelineStoreType;
import io.harness.pms.contracts.plan.PlanCreationContextValue;
import io.harness.pms.contracts.triggers.TriggerPayload;
import io.harness.pms.yaml.HarnessYamlVersion;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.google.common.base.Preconditions;
import com.google.protobuf.ByteString;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Singular;
import org.apache.commons.lang3.StringUtils;

@OwnedBy(HarnessTeam.PIPELINE)
@Data
@Builder
@EqualsAndHashCode
@JsonIgnoreProperties(ignoreUnknown = true)
public class PlanCreationContext implements AsyncCreatorContext {
  YamlField currentField;
  @Singular("globalContext") private Map<String, PlanCreationContextValue> globalContext;
  String yaml;
  Dependency dependency;
  String executionInputTemplate;

  public Map<String, PlanCreationContextValue> getGlobalContext() {
    return globalContext;
  }

  public Dependency getDependency() {
    return dependency;
  }

  public static PlanCreationContext cloneWithCurrentField(PlanCreationContext planCreationContext, YamlField field,
      String yaml, Dependency dependency, String executionInputTemplate) {
    return PlanCreationContext.builder()
        .currentField(field)
        .yaml(yaml)
        .dependency(dependency)
        .globalContext(planCreationContext.getGlobalContext())
        .executionInputTemplate(executionInputTemplate)
        .build();
  }

  public PlanCreationContextValue getMetadata() {
    return globalContext == null ? null : globalContext.get("metadata");
  }

  public String getAccountIdentifier() {
    PlanCreationContextValue metadata = getMetadata();
    if (metadata == null) {
      return "";
    }
    return metadata.getAccountIdentifier();
  }

  public String getOrgIdentifier() {
    PlanCreationContextValue metadata = getMetadata();
    if (metadata == null) {
      return "";
    }
    return metadata.getOrgIdentifier();
  }

  public String getProjectIdentifier() {
    PlanCreationContextValue metadata = getMetadata();
    if (metadata == null) {
      return "";
    }
    return metadata.getProjectIdentifier();
  }

  public String getPipelineIdentifier() {
    PlanCreationContextValue metadata = getMetadata();
    if (metadata == null) {
      return "";
    }
    return metadata.getMetadata().getPipelineIdentifier();
  }

  public String getExecutionUuid() {
    PlanCreationContextValue metadata = getMetadata();
    if (metadata == null) {
      return "";
    }
    return metadata.getMetadata().getExecutionUuid();
  }

  public TriggerPayload getTriggerPayload() {
    PlanCreationContextValue metadata = getMetadata();
    if (metadata == null) {
      return null;
    }
    return metadata.getTriggerPayload();
  }

  public ExecutionTriggerInfo getTriggerInfo() {
    PlanCreationContextValue metadata = getMetadata();
    if (metadata == null) {
      return null;
    }
    return metadata.getMetadata().getTriggerInfo();
  }

  public int getRunSequence() {
    PlanCreationContextValue metadata = getMetadata();
    if (metadata == null) {
      return -1;
    }
    return metadata.getMetadata().getRunSequence();
  }

  public String getPipelineConnectorRef() {
    PlanCreationContextValue metadata = getMetadata();
    if (metadata == null) {
      return "";
    }
    return metadata.getMetadata().getPipelineConnectorRef();
  }

  @Override
  public ByteString getGitSyncBranchContext() {
    PlanCreationContextValue value = getMetadata();
    if (value == null) {
      return null;
    }
    return getMetadata().getMetadata().getGitSyncBranchContext();
  }

  public List<YamlField> getStepYamlFields() {
    List<YamlNode> yamlNodes =
        Optional
            .of(Preconditions.checkNotNull(getCurrentField().getNode().getField(YAMLFieldNameConstants.STEPS))
                    .getNode()
                    .asArray())
            .orElse(Collections.emptyList());
    List<YamlField> stepFields = new LinkedList<>();

    yamlNodes.forEach(yamlNode -> {
      YamlField stepField = yamlNode.getField(YAMLFieldNameConstants.STEP);
      YamlField stepGroupField = yamlNode.getField(YAMLFieldNameConstants.STEP_GROUP);
      YamlField parallelStepField = yamlNode.getField(YAMLFieldNameConstants.PARALLEL);
      if (stepField != null) {
        stepFields.add(stepField);
      } else if (stepGroupField != null) {
        stepFields.add(stepGroupField);
      } else if (parallelStepField != null) {
        stepFields.add(parallelStepField);
      }
    });
    return stepFields;
  }

  public List<YamlField> getStepYamlFieldsFromStepsAsCurrentYamlField() {
    List<YamlNode> yamlNodes =
        Optional.of(Preconditions.checkNotNull(getCurrentField()).getNode().asArray()).orElse(Collections.emptyList());
    List<YamlField> stepFields = new LinkedList<>();

    yamlNodes.forEach(yamlNode -> {
      YamlField stepField = yamlNode.getField(YAMLFieldNameConstants.STEP);
      YamlField stepGroupField = yamlNode.getField(YAMLFieldNameConstants.STEP_GROUP);
      YamlField parallelStepField = yamlNode.getField(YAMLFieldNameConstants.PARALLEL);
      if (stepField != null) {
        stepFields.add(stepField);
      } else if (stepGroupField != null) {
        stepFields.add(stepGroupField);
      } else if (parallelStepField != null) {
        stepFields.add(parallelStepField);
      }
    });
    return stepFields;
  }

  public PipelineStoreType getPipelineStoreType() {
    PlanCreationContextValue value = getMetadata();
    if (value == null) {
      return PipelineStoreType.UNDEFINED;
    }
    return value.getMetadata().getPipelineStoreType();
  }

  public String getYamlVersion() {
    String harnessVersion = getMetadata().getMetadata().getHarnessVersion();
    return StringUtils.isEmpty(harnessVersion) ? HarnessYamlVersion.V0 : harnessVersion;
  }
}
