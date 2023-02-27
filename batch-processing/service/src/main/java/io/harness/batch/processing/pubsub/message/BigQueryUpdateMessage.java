/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.pubsub.message;

import io.harness.ccm.commons.constants.CloudProvider;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class BigQueryUpdateMessage {
  private EventType eventType;
  private Message message;

  @Getter
  @Setter
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Message {
    private String accountId;
    private CloudProvider cloudProvider;
    private List<String> cloudProviderAccountIds; // awsUsageaccountid, gcpBillingAccountId, azureSubscriptionGuid
    private String startDate;
    private String endDate;
  }

  public enum EventType { COST_CATEGORY_UPDATE }
}
