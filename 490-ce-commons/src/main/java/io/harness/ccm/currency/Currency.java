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
  AED("AED", "\u062f\u002e\u0625", "&#x062f;&#x002e;&#x0625;"),
  ARS("ARS", Constants.DOLLAR_SYMBOL, Constants.DOLLAR_UTF8_HEX_SYMBOL),
  AUD("AUD", "\u0041\u0024", "&#x0041;&#x0024;"),
  BRL("BRL", "\u0052\u0024", "&#x0052;&#x0024;"),
  CAD("CAD", Constants.DOLLAR_SYMBOL, Constants.DOLLAR_UTF8_HEX_SYMBOL),
  CNY("CNY", "\u00a5", "&#x00a5;"),
  EUR("EUR", "\u20ac", "&#x20ac;"),
  GBP("GBP", "\u00a3", "&#x00a3;"),
  INR("INR", "\u20b9", "&#x20b9;"),
  JPY("JPY", "\u00a5", "&#x00a5;"),
  MXN("MXN", Constants.DOLLAR_SYMBOL, Constants.DOLLAR_UTF8_HEX_SYMBOL),
  NOK("NOK", "\u006b\u0072", "&#x006b;&#x0072;"),
  NZD("NZD", Constants.DOLLAR_SYMBOL, Constants.DOLLAR_UTF8_HEX_SYMBOL),
  RUB("RUB", "\u0440\u0443\u0431", "&#x0440;&#x0443;&#x0431;"),
  SGD("SGD", "\u0053\u0024", "&#x0053;&#x0024;"),
  USD("USD", Constants.DOLLAR_SYMBOL, Constants.DOLLAR_UTF8_HEX_SYMBOL),
  NONE("NONE", "\u2205", "&#x2205;");

  private final String currencyValue;
  private final String symbol;
  private final String utf8HexSymbol;

  Currency(final String currencyValue, final String symbol, final String utf8HexSymbol) {
    this.currencyValue = currencyValue;
    this.symbol = symbol;
    this.utf8HexSymbol = utf8HexSymbol;
  }

  public String getCurrency() {
    return currencyValue;
  }

  public String getSymbol() {
    return symbol;
  }

  public String getUtf8HexSymbol() {
    return utf8HexSymbol;
  }

  private static class Constants {
    public static final String DOLLAR_SYMBOL = "\u0024";
    public static final String DOLLAR_UTF8_HEX_SYMBOL = "&#x0024;";
  }
}
