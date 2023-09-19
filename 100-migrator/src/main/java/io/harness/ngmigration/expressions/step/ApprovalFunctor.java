/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.expressions.step;

import io.harness.ngmigration.beans.StepOutput;

import java.util.HashMap;
import org.apache.commons.lang3.StringUtils;

public class ApprovalFunctor extends StepExpressionFunctor {
  private StepOutput stepOutput;
  public ApprovalFunctor(StepOutput stepOutput) {
    super(stepOutput);
    this.stepOutput = stepOutput;
  }

  @Override
  public synchronized Object get(Object key) {
    String newKey = key.toString();

    if ("approvedBy".equals(key.toString())) {
      return new HashMap<>() {
        {
          put("name", getFQN("user.name"));
          put("email", getFQN("user.email"));
        }
      };
    }

    if ("approvalStatus".equals(key.toString())) {
      newKey = "action";
    }

    if ("approvedOn".equals(key.toString())) {
      newKey = "approvedAt";
    }

    if ("comments".equals(key.toString())) {
      newKey = "comments";
    }

    return getFQN(newKey);
  }

  private String getFQN(String newKey) {
    if (StringUtils.equals(stepOutput.getStageIdentifier(), getCurrentStageIdentifier())) {
      return String.format("%s.output.approvalActivities[0].%s>", getStepFQN(), newKey);
    }

    return String.format("%s.output.approvalActivities[0].%s>", getStageFQN(), newKey);
  }
}
