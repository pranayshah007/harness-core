/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.analysis.beans;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import java.io.IOException;

public class RiskDeserializer extends StdDeserializer<Integer> {
  public RiskDeserializer() {
    this(null);
  }

  protected RiskDeserializer(Class<?> vc) {
    super(vc);
  }

  @Override
  public Integer deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException {
    String riskText = jsonParser.getText();
    if (isNumber(riskText)) {
      return Integer.parseInt(riskText);
    } else {
      return Risk.valueOf(jsonParser.getText()).getValue();
    }
  }

  private boolean isNumber(String text) {
    try {
      Integer.parseInt(text);
      return true;
    } catch (NumberFormatException e) {
      return false;
    }
  }
}
