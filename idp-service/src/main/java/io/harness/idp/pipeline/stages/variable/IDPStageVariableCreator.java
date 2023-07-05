/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.idp.pipeline.stages.variable;

import io.harness.idp.pipeline.stages.IDPStepSpecTypeConstants;
import io.harness.idp.pipeline.stages.node.IDPStageNode;
import io.harness.pms.contracts.plan.YamlProperties;
import io.harness.pms.sdk.core.variables.AbstractStageVariableCreator;
import io.harness.pms.sdk.core.variables.VariableCreatorHelper;
import io.harness.pms.sdk.core.variables.beans.VariableCreationContext;
import io.harness.pms.sdk.core.variables.beans.VariableCreationResponse;
import io.harness.pms.yaml.*;

import java.util.*;

import static io.harness.pms.yaml.YAMLFieldNameConstants.STRATEGY;

public class IDPStageVariableCreator extends AbstractStageVariableCreator<IDPStageNode> {
    @Override
    public LinkedHashMap<String, VariableCreationResponse> createVariablesForChildrenNodes(
            VariableCreationContext ctx, YamlField config) {
        LinkedHashMap<String, VariableCreationResponse> responseMap = new LinkedHashMap<>();

        YamlField executionField =
                config.getNode().getField(YAMLFieldNameConstants.SPEC).getNode().getField(YAMLFieldNameConstants.EXECUTION);
        if (VariableCreatorHelper.isNotYamlFieldEmpty(executionField)) {
            Map<String, YamlField> executionDependencyMap = new HashMap<>();
            executionDependencyMap.put(executionField.getNode().getUuid(), executionField);
            responseMap.put(executionField.getNode().getUuid(),
                    VariableCreationResponse.builder()
                            .dependencies(DependenciesUtils.toDependenciesProto(executionDependencyMap))
                            .build());
        }

        YamlField strategyField = config.getNode().getField(STRATEGY);

        if (strategyField != null) {
            Map<String, YamlField> strategyDependencyMap = new HashMap<>();
            strategyDependencyMap.put(strategyField.getNode().getUuid(), strategyField);
            responseMap.put(strategyField.getNode().getUuid(),
                    VariableCreationResponse.builder()
                            .dependencies(DependenciesUtils.toDependenciesProto(strategyDependencyMap))
                            .build());
        }

        return responseMap;
    }

    @Override
    public VariableCreationResponse createVariablesForParentNode(VariableCreationContext ctx, YamlField config) {
        YamlNode node = config.getNode();
        String stageUUID = node.getUuid();
        Map<String, YamlProperties> yamlPropertiesMap = new LinkedHashMap<>();
        yamlPropertiesMap.put(stageUUID,
                YamlProperties.newBuilder()
                        .setLocalName(YAMLFieldNameConstants.STAGE)
                        .setFqn(YamlUtils.getFullyQualifiedName(node))
                        .build());
        addVariablesForStage(yamlPropertiesMap, node);
        return VariableCreationResponse.builder().yamlProperties(yamlPropertiesMap).build();
    }

    private void addVariablesForStage(Map<String, YamlProperties> yamlPropertiesMap, YamlNode yamlNode) {
        YamlField nameField = yamlNode.getField(YAMLFieldNameConstants.NAME);
        if (nameField != null) {
            String nameFQN = YamlUtils.getFullyQualifiedName(nameField.getNode());
            yamlPropertiesMap.put(nameField.getNode().getCurrJsonNode().textValue(),
                    YamlProperties.newBuilder().setLocalName(getStageLocalName(nameFQN)).setFqn(nameFQN).build());
        }
        YamlField descriptionField = yamlNode.getField(YAMLFieldNameConstants.DESCRIPTION);
        if (descriptionField != null) {
            String descriptionFQN = YamlUtils.getFullyQualifiedName(descriptionField.getNode());
            yamlPropertiesMap.put(descriptionField.getNode().getCurrJsonNode().textValue(),
                    YamlProperties.newBuilder().setLocalName(getStageLocalName(descriptionFQN)).setFqn(descriptionFQN).build());
        }
    }

    private String getStageLocalName(String fqn) {
        String[] split = fqn.split("\\.");
        return fqn.replaceFirst(split[0], YAMLFieldNameConstants.STAGE);
    }

    @Override
    public Map<String, Set<String>> getSupportedTypes() {
        return Collections.singletonMap(YAMLFieldNameConstants.STAGE,Collections.singleton(IDPStepSpecTypeConstants.IDP_STAGE));
    }

    //
    //  @Override
    //  public Map<String, Set<String>> getSupportedTypes() {
    //    return Collections.singletonMap(YAMLFieldNameConstants.STAGE,
    //            Set.of(IACMStepSpecTypeConstants.IACM_STAGE, IACMStepSpecTypeConstants.IACM_STAGE_V1));
    //  }

    @Override
    public LinkedHashMap<String, VariableCreationResponse> createVariablesForChildrenNodesV2(
            VariableCreationContext ctx, IDPStageNode config) {
        return createVariablesForChildrenNodes(ctx, ctx.getCurrentField());
    }

    @Override
    public Class<IDPStageNode> getFieldClass() {
        return IDPStageNode.class;
    }
}
