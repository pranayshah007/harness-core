/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.helpers.ext.trigger.request;

import software.wings.beans.trigger.TriggerCommand.TriggerCommandType;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.hibernate.validator.constraints.NotEmpty;

@Data
@AllArgsConstructor
public class TriggerRequest {
  @NotEmpty private TriggerCommandType triggerCommandType;
  String accountId;
  String appId;

  public TriggerRequest(TriggerCommandType triggerCommandType) {
    this.triggerCommandType = triggerCommandType;
  }
}
