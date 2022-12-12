/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.graphql.core.currency;

import static io.harness.annotations.dev.HarnessTeam.CE;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.currency.Currency;
import io.harness.ccm.graphql.dto.common.CloudServiceProvider;
import io.harness.ccm.graphql.dto.currency.CurrencyConversionFactorDTO;
import io.harness.ccm.graphql.dto.currency.CurrencyDTO;

import lombok.NonNull;

@OwnedBy(CE)
public interface CurrencyPreferenceService {
  CurrencyDTO getCurrencies();
  CurrencyConversionFactorDTO getCurrencyConversionFactorData(
      @NonNull String accountId, @NonNull Currency destinationCurrency);
  boolean createCurrencyConversionFactors(@NonNull String accountId, @NonNull Currency destinationCurrency,
      @NonNull CurrencyConversionFactorDTO currencyConversionFactorDTO);
  void updateCEMetadataCurrencyPreferenceRecord(@NonNull String accountId, @NonNull Currency destinationCurrency);
  Double getDestinationCurrencyConversionFactor(
      @NonNull String accountId, @NonNull CloudServiceProvider cloudServiceProvider, @NonNull Currency sourceCurrency);
}
