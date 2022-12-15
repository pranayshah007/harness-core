/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm.currency;

import static io.harness.annotations.dev.HarnessTeam.CE;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;

@OwnedBy(CE)
@TargetModule(HarnessModule._490_CE_COMMONS)
public enum Currency {
  AED("AED", "\u062f\u002e\u0625"),
  ARS("ARS", "\u0024"),
  AUD("AUD", "\u0041\u0024"),
  BRL("BRL", "\u0052\u0024"),
  CAD("CAD", "\u0024"),
  CNY("CNY", "\u00a5"),
  EUR("EUR", "\u20ac"),
  GBP("GBP", "\u00a3"),
  INR("INR", "\u20b9"),
  JPY("JPY", "\u00a5"),
  MXN("MXN", "\u0024"),
  NOK("NOK", "\u006b\u0072"),
  NZD("NZD", "\u0024"),
  RUB("RUB", "\u0440\u0443\u0431"),
  SGD("SGD", "\u0053\u0024"),
  USD("USD", "\u0024"),
  NONE("NONE", "\u2205");

  private final String currency;
  private final String symbol;

  Currency(final String currency, final String symbol) {
    this.currency = currency;
    this.symbol = symbol;
  }

  public String getCurrency() {
    return currency;
  }

  public String getSymbol() {
    return symbol;
  }
}
