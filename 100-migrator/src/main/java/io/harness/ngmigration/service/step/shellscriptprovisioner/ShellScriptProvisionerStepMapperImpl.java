/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.step.shellscriptprovisioner;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.ngmigration.utils.MigratorUtility.RUNTIME_INPUT;
import static io.harness.ngmigration.utils.NGMigrationConstants.SECRET_FORMAT;

import static software.wings.beans.ServiceVariableType.ENCRYPTED_TEXT;
import static software.wings.ngmigration.NGMigrationEntityType.INFRA_PROVISIONER;
import static software.wings.ngmigration.NGMigrationEntityType.SECRET;

import io.harness.cdng.provision.shellscript.ShellScriptProvisionStepInfo;
import io.harness.cdng.provision.shellscript.ShellScriptProvisionStepNode;
import io.harness.data.structure.EmptyPredicate;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.ngmigration.beans.NGYamlFile;
import io.harness.ngmigration.beans.SupportStatus;
import io.harness.ngmigration.beans.WorkflowMigrationContext;
import io.harness.ngmigration.service.step.StepMapper;
import io.harness.ngmigration.utils.MigratorUtility;
import io.harness.plancreator.steps.AbstractStepNode;
import io.harness.pms.yaml.ParameterField;
import io.harness.steps.shellscript.ShellScriptBaseSource;
import io.harness.steps.shellscript.ShellScriptInlineSource;
import io.harness.steps.shellscript.ShellScriptSourceWrapper;
import io.harness.yaml.core.variables.NGVariable;
import io.harness.yaml.core.variables.NGVariableType;
import io.harness.yaml.core.variables.StringNGVariable;

import software.wings.beans.GraphNode;
import software.wings.beans.NameValuePair;
import software.wings.beans.shellscript.provisioner.ShellScriptInfrastructureProvisioner;
import software.wings.ngmigration.CgEntityId;
import software.wings.ngmigration.CgEntityNode;
import software.wings.ngmigration.NGMigrationEntityType;
import software.wings.sm.State;
import software.wings.sm.states.provision.ShellScriptProvisionState;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Slf4j
public class ShellScriptProvisionerStepMapperImpl extends StepMapper {
  public List<CgEntityId> getReferencedEntities(
      String accountId, GraphNode graphNode, Map<String, String> stepIdToServiceIdMap) {
    ShellScriptProvisionState state = (ShellScriptProvisionState) getState(graphNode);

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
    return StepSpecTypeConstants.SHELL_SCRIPT_PROVISION;
  }

  @Override
  public State getState(GraphNode stepYaml) {
    Map<String, Object> properties = super.getProperties(stepYaml);
    ShellScriptProvisionState state = new ShellScriptProvisionState(stepYaml.getName());
    state.parseProperties(properties);
    return state;
  }

  @Override
  public AbstractStepNode getSpec(WorkflowMigrationContext context, GraphNode graphNode) {
    ShellScriptProvisionState state = (ShellScriptProvisionState) getState(graphNode);

    ShellScriptProvisionStepNode node = new ShellScriptProvisionStepNode();
    baseSetup(graphNode, node, context.getIdentifierCaseFormat());
    ShellScriptProvisionStepInfo stepInfo =
        ShellScriptProvisionStepInfo.infoBuilder()
            .environmentVariables(getVariables(context.getMigratedEntities(), state.getVariables()))
            .source(ShellScriptSourceWrapper.builder()
                        .type(ShellScriptBaseSource.INLINE)
                        .spec(ShellScriptInlineSource.builder()
                                  .script(getScript(context.getEntities(), state.getProvisionerId()))
                                  .build())
                        .build())
            .build();
    node.setShellScriptProvisionStepInfo(stepInfo);

    return node;
  }

  private ParameterField<String> getScript(Map<CgEntityId, CgEntityNode> entities, String provisionerId) {
    Optional<ShellScriptInfrastructureProvisioner> provisioner = getProvisioner(entities, provisionerId);
    if (provisioner.isPresent() && isNotEmpty(provisioner.get().getScriptBody())) {
      return ParameterField.createValueField(provisioner.get().getScriptBody());
    }
    return RUNTIME_INPUT;
  }

  protected List<NGVariable> getVariables(Map<CgEntityId, NGYamlFile> migratedEntities, List<NameValuePair> variables) {
    List<NGVariable> ngVariables = new ArrayList<>();
    if (EmptyPredicate.isEmpty(variables)) {
      return ngVariables;
    }

    ngVariables.addAll(variables.stream()
                           .filter(variable -> "ENCRYPTED_TEXT".equals(variable.getValueType()))
                           .map(variable
                               -> StringNGVariable.builder()
                                      .name(variable.getName())
                                      .value(ParameterField.createValueField(String.format(SECRET_FORMAT,
                                          MigratorUtility.getSecretRef(migratedEntities, variable.getValue())
                                              .toSecretRefStringValue())))
                                      .type(NGVariableType.STRING)
                                      .build())
                           .collect(Collectors.toList()));

    ngVariables.addAll(variables.stream()
                           .filter(variable -> !"ENCRYPTED_TEXT".equals(variable.getValueType()))
                           .map(variable
                               -> StringNGVariable.builder()
                                      .name(variable.getName())
                                      .value(ParameterField.createValueField(variable.getValue()))
                                      .type(NGVariableType.STRING)
                                      .build())
                           .collect(Collectors.toList()));

    return ngVariables;
  }

  @Override
  public boolean areSimilar(GraphNode stepYaml1, GraphNode stepYaml2) {
    return false;
  }

  @Override
  public SupportStatus stepSupportStatus(GraphNode graphNode) {
    return SupportStatus.SUPPORTED;
  }

  private Optional<ShellScriptInfrastructureProvisioner> getProvisioner(
      Map<CgEntityId, CgEntityNode> entities, String provisionerId) {
    CgEntityId provisioner = CgEntityId.builder().id(provisionerId).type(INFRA_PROVISIONER).build();
    if (!entities.containsKey(provisioner)) {
      log.error("Provisioner not found with id {}", provisionerId);
      return Optional.empty();
    }
    ShellScriptInfrastructureProvisioner infraProv =
        (ShellScriptInfrastructureProvisioner) entities.get(provisioner).getEntity();
    return Optional.of(infraProv);
  }
}
