/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.subscription.params;

import io.harness.ModuleType;
import io.harness.subscription.dto.CustomerDTO;

import java.util.List;
import javax.validation.Valid;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SubscriptionRequest {
  private String accountIdentifier;
  private ModuleType moduleType;
  private List<SubscriptionItemRequest> items;
  private String paymentFrequency;
  private String edition;
  private boolean premiumSupport;
  @Valid private CustomerDTO customer;
}
