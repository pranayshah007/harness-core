/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.tasklet.util;

import static io.harness.annotations.dev.HarnessTeam.CE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.currency.Currency;
import io.harness.ccm.graphql.dto.common.CloudServiceProvider;

import lombok.NonNull;

@OwnedBy(CE)
public interface CurrencyPreferenceHelper {
  Double getDestinationCurrencyConversionFactor(
      @NonNull String accountId, @NonNull CloudServiceProvider cloudServiceProvider, @NonNull Currency sourceCurrency);
}
