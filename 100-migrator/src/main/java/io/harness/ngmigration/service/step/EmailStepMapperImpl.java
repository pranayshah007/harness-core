/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.service.step;

import io.harness.plancreator.steps.AbstractStepNode;
import io.harness.plancreator.steps.email.EmailStepInfo;
import io.harness.plancreator.steps.email.EmailStepNode;
import io.harness.pms.yaml.ParameterField;
import io.harness.steps.StepSpecTypeConstants;

import software.wings.sm.states.EmailState;
import software.wings.yaml.workflow.StepYaml;

import java.util.Collections;
import java.util.Map;

public class EmailStepMapperImpl implements StepMapper {
  @Override
  public String getStepType(StepYaml stepYaml) {
    return StepSpecTypeConstants.EMAIL;
  }

  @Override
  public AbstractStepNode getSpec(StepYaml stepYaml) {
    Map<String, Object> properties = StepMapper.super.getProperties(stepYaml);
    EmailState state = new EmailState(stepYaml.getName());
    state.parseProperties(properties);
    EmailStepNode emailStepNode = new EmailStepNode();
    baseSetup(stepYaml, emailStepNode);
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
}
