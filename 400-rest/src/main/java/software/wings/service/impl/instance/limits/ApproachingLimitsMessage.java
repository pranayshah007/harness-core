/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.instance.limits;

import io.harness.limits.ActionType;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.helpers.MessageFormatter;

@UtilityClass
@Slf4j
public class ApproachingLimitsMessage {
  public static String warningMessage(int percent, ActionType actionType) {
    String template = null;
    switch (actionType) {
      case CREATE_APPLICATION:
        template =
            "You have consumed {}% of allowed Application creation limits. Please contact Harness Support to avoid any interruptions.";
        break;
      case CREATE_PIPELINE:
        template =
            "You have consumed {}% of allowed Pipeline creation limits. Please contact Harness Support to avoid any interruptions.";
        break;
      case CREATE_USER:
        template =
            "You have consumed {}% of allowed User creation limits. Please contact Harness Support to avoid any interruptions.";
        break;
      case CREATE_WORKFLOW:
        template =
            "You have consumed {}% of allowed Workflow creation limits. Please contact Harness Support to avoid any interruptions.";
        break;
      case CREATE_SERVICE:
        template =
            "You have consumed {}% of allowed Service creation limits. Please contact Harness Support to avoid any interruptions.";
        break;
      case CREATE_INFRA_PROVISIONER:
        template =
            "You have consumed {}% of allowed Infrastructure Provisioner creation limits. Please contact Harness Support to avoid any interruptions.";
        break;
      default:
        log.warn("No warning message configured. Please configure one or default message will be shown. ActionType: {}",
            actionType);
        return "Approaching resource usage limits. Please contact Harness Support to avoid any interruptions.";
    }

    return MessageFormatter.format(template, percent).getMessage();
  }
}
