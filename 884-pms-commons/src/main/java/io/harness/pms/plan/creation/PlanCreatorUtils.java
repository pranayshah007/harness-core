/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.pms.plan.creation;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.EmptyPredicate;
import io.harness.data.structure.UUIDGenerator;
import io.harness.exception.YamlException;
import io.harness.logging.AutoLogContext;
import io.harness.pms.contracts.plan.Dependency;
import io.harness.pms.contracts.plan.ExecutionMetadata;
import io.harness.pms.contracts.plan.YamlUpdates;
import io.harness.pms.yaml.OptionUtils;
import io.harness.pms.yaml.PipelineVersion;
import io.harness.pms.yaml.YAMLFieldNameConstants;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.serializer.KryoSerializer;
import io.harness.yaml.options.Options;

import com.google.common.collect.ImmutableMap;
import com.google.protobuf.ByteString;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;

@OwnedBy(HarnessTeam.PIPELINE)
@UtilityClass
public class PlanCreatorUtils {
  public final String ANY_TYPE = "__any__";
  public final String TEMPLATE_TYPE = "__template__";

  public boolean supportsField(Map<String, Set<String>> supportedTypes, YamlField field, String version) {
    if (EmptyPredicate.isEmpty(supportedTypes)) {
      return false;
    }

    switch (version) {
      case PipelineVersion.V1:
        Set<String> keys = supportedTypes.keySet();
        String type = field.getNode().getType();
        if (!EmptyPredicate.isEmpty(type)) {
          if (supportedTypes.values().stream().anyMatch(v -> v.contains(type))) {
            return true;
          }

          if (keys.contains(type)) {
            return true;
          }
        }
        if (EmptyPredicate.isEmpty(field.getName())) {
          return false;
        }
        return keys.contains(field.getName());
      case PipelineVersion.V0:
        String fieldName = field.getName();
        Set<String> types = supportedTypes.get(fieldName);
        if (EmptyPredicate.isEmpty(types)) {
          return false;
        }

        String fieldType = field.getNode().getType();
        if (EmptyPredicate.isEmpty(fieldType)) {
          if (field.getNode().getTemplate() == null) {
            fieldType = ANY_TYPE;
          } else {
            fieldType = TEMPLATE_TYPE;
          }
        }
        return types.contains(fieldType);
      default:
        throw new IllegalStateException("unsupported version");
    }
  }

  public YamlField getStageConfig(YamlField yamlField, String stageIdentifier) {
    if (EmptyPredicate.isEmpty(stageIdentifier)) {
      return null;
    }
    if (yamlField.getName().equals(YAMLFieldNameConstants.PIPELINE)
        || yamlField.getName().equals(YAMLFieldNameConstants.STAGES)) {
      return null;
    }
    YamlNode stages = YamlUtils.getGivenYamlNodeFromParentPath(yamlField.getNode(), YAMLFieldNameConstants.STAGES);
    List<YamlField> stageYamlFields = getStageYamlFields(stages);
    for (YamlField stageYamlField : stageYamlFields) {
      if (stageYamlField.getNode().getIdentifier().equals(stageIdentifier)) {
        return stageYamlField;
      }
    }
    return null;
  }

  private List<YamlField> getStageYamlFields(YamlNode stagesYamlNode) {
    List<YamlNode> yamlNodes = Optional.of(stagesYamlNode.asArray()).orElse(Collections.emptyList());
    List<YamlField> stageFields = new LinkedList<>();

    yamlNodes.forEach(yamlNode -> {
      YamlField stageField = yamlNode.getField(YAMLFieldNameConstants.STAGE);
      YamlField parallelStageField = yamlNode.getField(YAMLFieldNameConstants.PARALLEL);
      if (stageField != null) {
        stageFields.add(stageField);
      } else if (parallelStageField != null) {
        stageFields.addAll(getStageYamlFields(parallelStageField.getNode()));
      }
    });
    return stageFields;
  }

  public List<YamlField> getStepYamlFields(List<YamlNode> stepYamlNodes) {
    List<YamlField> stepFields = new LinkedList<>();

    stepYamlNodes.forEach(yamlNode -> {
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

  public List<YamlField> getDependencyNodeIdsForParallelNode(YamlField parallelYamlField) {
    List<YamlField> childYamlFields = getStageChildFields(parallelYamlField);
    if (childYamlFields.isEmpty()) {
      List<YamlNode> yamlNodes = Optional.of(parallelYamlField.getNode().asArray()).orElse(Collections.emptyList());

      yamlNodes.forEach(yamlNode -> {
        YamlField stageField = yamlNode.getField(YAMLFieldNameConstants.STEP);
        YamlField stepGroupField = yamlNode.getField(YAMLFieldNameConstants.STEP_GROUP);
        if (stageField != null) {
          childYamlFields.add(stageField);
        } else if (stepGroupField != null) {
          childYamlFields.add(stepGroupField);
        }
      });
    }
    return childYamlFields;
  }

  public List<YamlField> getStageChildFields(YamlField parallelYamlField) {
    return Optional.of(parallelYamlField.getNode().asArray())
        .orElse(Collections.emptyList())
        .stream()
        .map(el -> el.getField(YAMLFieldNameConstants.STAGE))
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
  }

  public static YamlUpdates.Builder setYamlUpdate(YamlField yamlField, YamlUpdates.Builder yamlUpdates) {
    try {
      return yamlUpdates.putFqnToYaml(yamlField.getYamlPath(), YamlUtils.writeYamlString(yamlField));
    } catch (IOException e) {
      throw new YamlException(
          "Yaml created for yamlField at " + yamlField.getYamlPath() + " could not be converted into a yaml string");
    }
  }

  public AutoLogContext autoLogContext(
      ExecutionMetadata executionMetadata, String accountId, String orgIdentifier, String projectIdentifier) {
    return autoLogContext(accountId, orgIdentifier, projectIdentifier, executionMetadata.getPipelineIdentifier(),
        executionMetadata.getExecutionUuid());
  }

  public AutoLogContext autoLogContext(String accountId, String orgIdentifier, String projectIdentifier,
      String pipelineIdentifier, String planExecutionId) {
    Map<String, String> logContextMap =
        new HashMap<>(ImmutableMap.of("planExecutionId", planExecutionId, "pipelineIdentifier", pipelineIdentifier,
            "accountIdentifier", accountId, "orgIdentifier", orgIdentifier, "projectIdentifier", projectIdentifier));
    return new AutoLogContext(logContextMap, AutoLogContext.OverrideBehavior.OVERRIDE_NESTS);
  }

  public AutoLogContext autoLogContextWithRandomRequestId(
      ExecutionMetadata executionMetadata, String accountId, String orgIdentifier, String projectIdentifier) {
    Map<String, String> logContextMap = new HashMap<>();
    logContextMap.put("planExecutionId", executionMetadata.getExecutionUuid());
    logContextMap.put("pipelineIdentifier", executionMetadata.getPipelineIdentifier());
    logContextMap.put("accountIdentifier", accountId);
    logContextMap.put("orgIdentifier", orgIdentifier);
    logContextMap.put("projectIdentifier", projectIdentifier);
    logContextMap.put("sdkPlanCreatorRequestId", UUIDGenerator.generateUuid());
    return new AutoLogContext(logContextMap, AutoLogContext.OverrideBehavior.OVERRIDE_NESTS);
  }

  public Dependency createGlobalDependency(KryoSerializer kryoSerializer, String pipelineVersion, String pipelineYaml) {
    switch (pipelineVersion) {
      case PipelineVersion.V1:
        return Dependency.newBuilder().putAllMetadata(getOptionsDependency(kryoSerializer, pipelineYaml)).build();
      default:
        return null;
    }
  }

  private Map<String, ByteString> getOptionsDependency(KryoSerializer kryoSerializer, String pipelineYaml) {
    Optional<Options> optionalOptions = OptionUtils.getOptions(pipelineYaml);
    Options options = optionalOptions.orElse(Options.builder().build());
    return Map.of(YAMLFieldNameConstants.OPTIONS, ByteString.copyFrom(kryoSerializer.asBytes(options)));
  }
}
