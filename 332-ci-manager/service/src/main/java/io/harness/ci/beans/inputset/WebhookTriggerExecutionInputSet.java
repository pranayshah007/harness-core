/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.beans.inputset;

import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.Builder;
import lombok.Value;
import org.hibernate.validator.constraints.NotEmpty;
@Value
@Builder
@JsonTypeName("Webhook")
public class WebhookTriggerExecutionInputSet implements InputSet {
  @Builder.Default @NotEmpty private String payload;

  @Override
  public InputSet.Type getType() {
    return Type.Webhook;
  }
}
