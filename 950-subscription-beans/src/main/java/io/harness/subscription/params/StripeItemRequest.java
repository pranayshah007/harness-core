/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.subscription.params;

import lombok.Data;

@Data
public final class StripeItemRequest {
  private final String priceId;
  private final Long quantity;

  public StripeItemRequest(Builder builder) {
    this.priceId = builder.priceId;
    this.quantity = builder.quantity;
  }

  public static class Builder {
    private String priceId;
    private Long quantity;

    public static Builder newInstance() {
      return new Builder();
    }

    private Builder() {}

    public Builder withPriceId(String priceId) {
      this.priceId = priceId;
      return this;
    }
    public Builder withQuantity(Long quantity) {
      this.quantity = quantity;
      return this;
    }
    public StripeItemRequest build() {
      return new StripeItemRequest(this);
    }
  }
}
