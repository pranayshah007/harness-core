/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.step.cloudformation;

import static software.wings.beans.ServiceVariableType.ENCRYPTED_TEXT;
import static software.wings.ngmigration.NGMigrationEntityType.SECRET;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.provision.cloudformation.CloudformationCreateStackStepConfiguration;
import io.harness.cdng.provision.cloudformation.CloudformationCreateStackStepInfo;
import io.harness.cdng.provision.cloudformation.CloudformationCreateStackStepNode;
import io.harness.data.structure.EmptyPredicate;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.ngmigration.beans.WorkflowMigrationContext;
import io.harness.ngmigration.utils.MigratorUtility;
import io.harness.plancreator.steps.AbstractStepNode;
import io.harness.pms.yaml.ParameterField;

import software.wings.beans.GraphNode;
import software.wings.ngmigration.CgEntityId;
import software.wings.ngmigration.NGMigrationEntityType;
import software.wings.sm.State;
import software.wings.sm.states.provision.CloudFormationCreateStackState;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;

@OwnedBy(HarnessTeam.CDC)
public class CloudformationCreateStepMapperImpl extends BaseCloudformationProvisionerMapper {
  @Override
  public Set<String> getExpressions(GraphNode graphNode) {
    return new HashSet<>();
  }

  @Override
  public List<CgEntityId> getReferencedEntities(
      String accountId, GraphNode graphNode, Map<String, String> stepIdToServiceIdMap) {
    CloudFormationCreateStackState state = (CloudFormationCreateStackState) getState(graphNode);

    List<CgEntityId> references = new ArrayList<>();

    if (StringUtils.isNotBlank(state.getProvisionerId())) {
      references.add(
          CgEntityId.builder().id(state.getProvisionerId()).type(NGMigrationEntityType.INFRA_PROVISIONER).build());
    }

    if (EmptyPredicate.isNotEmpty(state.getVariables())) {
      references.addAll(state.getVariables()
                            .stream()
                            .filter(item -> ENCRYPTED_TEXT.name().equals(item.getValueType()))
                            .map(item -> CgEntityId.builder().type(SECRET).id(item.getValue()).build())
                            .collect(Collectors.toList()));
    }

    return references;
  }

  @Override
  public String getStepType(GraphNode stepYaml) {
    return StepSpecTypeConstants.CLOUDFORMATION_CREATE_STACK;
  }

  @Override
  public State getState(GraphNode stepYaml) {
    Map<String, Object> properties = super.getProperties(stepYaml);
    CloudFormationCreateStackState state = new CloudFormationCreateStackState(stepYaml.getName());
    state.parseProperties(properties);
    return state;
  }

  @Override
  public AbstractStepNode getSpec(WorkflowMigrationContext context, GraphNode graphNode) {
    CloudFormationCreateStackState state = (CloudFormationCreateStackState) getState(graphNode);

    CloudformationCreateStackStepConfiguration cfConfiguration =
        CloudformationCreateStackStepConfiguration.builder()
            .connectorRef(MigratorUtility.RUNTIME_INPUT)
            .region(ParameterField.createValueField(state.getRegion()))
            .stackName(getStackName(state.getCustomStackName(), state.isUseCustomStackName()))
            .roleArn(getRoleArn(state.getCloudFormationRoleArn()))
            .capabilities(getCapabilities(state))
            .tags(getTags(state.getTags()))
            .templateFile(getTemplateFile(context.getEntities(), state.getProvisionerId()))
            .parametersFilesSpecs(getParametersFile(
                context.getEntities(), context.getMigratedEntities(), state.getProvisionerId(), state))
            .skipOnStackStatuses(getSkipStatuses(state.getStackStatusesToMarkAsSuccess()))
            .parameterOverrides(getVariables(context.getMigratedEntities(), state.getVariables()))
            .build();

    CloudformationCreateStackStepInfo stepInfo = CloudformationCreateStackStepInfo.infoBuilder()
                                                     .provisionerIdentifier(ParameterField.createValueField("<+input>"))
                                                     .cloudformationStepConfiguration(cfConfiguration)
                                                     .build();

    CloudformationCreateStackStepNode cloudformationCreateStackStepNode = new CloudformationCreateStackStepNode();
    cloudformationCreateStackStepNode.setCloudformationCreateStackStepInfo(stepInfo);
    baseSetup(graphNode, cloudformationCreateStackStepNode, context.getIdentifierCaseFormat());
    return cloudformationCreateStackStepNode;
  }

  @Override
  public boolean areSimilar(GraphNode stepYaml1, GraphNode stepYaml2) {
    CloudFormationCreateStackState state1 = (CloudFormationCreateStackState) getState(stepYaml1);
    CloudFormationCreateStackState state2 = (CloudFormationCreateStackState) getState(stepYaml2);
    return false;
  }
}
