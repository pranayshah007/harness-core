/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.step;

import io.harness.ngmigration.beans.SupportStatus;
import io.harness.ngmigration.beans.WorkflowMigrationContext;
import io.harness.plancreator.steps.AbstractStepNode;
import io.harness.plancreator.steps.email.EmailStepInfo;
import io.harness.plancreator.steps.email.EmailStepNode;
import io.harness.pms.yaml.ParameterField;
import io.harness.steps.StepSpecTypeConstants;

import software.wings.beans.GraphNode;
import software.wings.sm.State;
import software.wings.sm.states.EmailState;

import java.util.Collections;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;

public class EmailStepMapperImpl extends StepMapper {
  @Override
  public SupportStatus stepSupportStatus(GraphNode graphNode) {
    return SupportStatus.SUPPORTED;
  }

  @Override
  public String getStepType(GraphNode stepYaml) {
    return StepSpecTypeConstants.EMAIL;
  }

  @Override
  public State getState(GraphNode stepYaml) {
    Map<String, Object> properties = getProperties(stepYaml);
    EmailState state = new EmailState(stepYaml.getName());
    state.parseProperties(properties);
    return state;
  }

  @Override
  public AbstractStepNode getSpec(WorkflowMigrationContext context, GraphNode graphNode) {
    EmailState state = (EmailState) getState(graphNode);
    EmailStepNode emailStepNode = new EmailStepNode();
    baseSetup(graphNode, emailStepNode, context.getIdentifierCaseFormat());
    EmailStepInfo emailStepInfo = EmailStepInfo.infoBuilder()
                                      .to(ParameterField.createValueField(state.getToAddress()))
                                      .cc(ParameterField.createValueField(state.getCcAddress()))
                                      .body(ParameterField.createValueField(state.getBody()))
                                      .subject(ParameterField.createValueField(state.getSubject()))
                                      .delegateSelectors(ParameterField.createValueField(Collections.emptyList()))
                                      .build();
    emailStepNode.setEmailStepInfo(emailStepInfo);
    return emailStepNode;
  }

  @Override
  public boolean areSimilar(GraphNode stepYaml1, GraphNode stepYaml2) {
    // We are not comparing other fields because to, cc are not the most import parts to compare.
    // Subject & Body would be the major differentiators
    EmailState state1 = (EmailState) getState(stepYaml1);
    EmailState state2 = (EmailState) getState(stepYaml2);
    if (!StringUtils.equals(state1.getBody(), state2.getBody())) {
      return false;
    }
    return StringUtils.equals(state1.getSubject(), state2.getSubject());
  }
}
