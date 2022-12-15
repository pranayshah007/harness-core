/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.entities;

import io.harness.cvng.core.beans.WebhookType;

import com.fasterxml.jackson.annotation.JsonTypeName;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import lombok.experimental.SuperBuilder;

@JsonTypeName("PAGER_DUTY")
@Data
@SuperBuilder
@FieldNameConstants(innerTypeName = "PagerDutyWebhookKeys")
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class PagerDutyWebhook extends Webhook {
  @NotNull private String monitoredServiceIdentifier;
  String pagerdutyChangeSourceId;
  String webhookId;

  public WebhookType getType() {
    return WebhookType.PAGER_DUTY;
  }
}
