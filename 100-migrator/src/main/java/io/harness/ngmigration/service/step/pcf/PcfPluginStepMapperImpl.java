/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.step.pcf;

import io.harness.cdng.manifest.yaml.InlineStoreConfig;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigType;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfigWrapper;
import io.harness.cdng.service.beans.ServiceDefinitionType;
import io.harness.cdng.tas.TasCommandScript;
import io.harness.cdng.tas.TasCommandStepInfo;
import io.harness.cdng.tas.TasCommandStepNode;
import io.harness.executions.steps.StepSpecTypeConstants;
import io.harness.ngmigration.beans.MigrationContext;
import io.harness.ngmigration.beans.SupportStatus;
import io.harness.ngmigration.beans.WorkflowMigrationContext;
import io.harness.ngmigration.utils.MigratorUtility;
import io.harness.plancreator.steps.AbstractStepNode;
import io.harness.pms.yaml.ParameterField;

import software.wings.beans.GraphNode;
import software.wings.sm.State;
import software.wings.sm.states.pcf.PcfPluginState;

import java.util.Map;

public class PcfPluginStepMapperImpl extends PcfAbstractStepMapper {
  @Override
  public SupportStatus stepSupportStatus(GraphNode graphNode) {
    return SupportStatus.SUPPORTED;
  }

  @Override
  public String getStepType(GraphNode stepYaml) {
    return StepSpecTypeConstants.TANZU_COMMAND;
  }

  @Override
  public ServiceDefinitionType inferServiceDef(WorkflowMigrationContext context, GraphNode graphNode) {
    return ServiceDefinitionType.TAS;
  }

  @Override
  public State getState(GraphNode stepYaml) {
    Map<String, Object> properties = getProperties(stepYaml);
    PcfPluginState state = new PcfPluginState(stepYaml.getName());
    state.parseProperties(properties);
    return state;
  }

  @Override
  public AbstractStepNode getSpec(
      MigrationContext migrationContext, WorkflowMigrationContext context, GraphNode graphNode) {
    PcfPluginState state = (PcfPluginState) getState(graphNode);

    TasCommandStepNode tasCommandStepNode = new TasCommandStepNode();
    baseSetup(state, tasCommandStepNode, context.getIdentifierCaseFormat());

    TasCommandStepInfo tasCommandStepInfo =
        TasCommandStepInfo.infoBuilder()
            .script(getTasScript(state.getScriptString()))
            .delegateSelectors(MigratorUtility.getDelegateSelectors(state.getTags()))
            .build();
    tasCommandStepNode.setTasCommandStepInfo(tasCommandStepInfo);
    return tasCommandStepNode;
  }

  private TasCommandScript getTasScript(String scriptString) {
    return TasCommandScript.builder()
        .store(StoreConfigWrapper.builder()
                   .type(StoreConfigType.INLINE)
                   .spec(InlineStoreConfig.builder().content(ParameterField.createValueField(scriptString)).build())
                   .build())
        .build();
  }

  @Override
  public boolean areSimilar(GraphNode stepYaml1, GraphNode stepYaml2) {
    return false;
  }
}
