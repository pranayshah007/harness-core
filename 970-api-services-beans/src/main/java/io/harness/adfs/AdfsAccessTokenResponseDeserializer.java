/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.adfs;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import io.harness.adfs.AdfsAccessTokenResponse;
import io.harness.annotations.dev.OwnedBy;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import java.io.IOException;

@OwnedBy(CDC)
public class AdfsAccessTokenResponseDeserializer extends StdDeserializer<AdfsAccessTokenResponse> {
  public AdfsAccessTokenResponseDeserializer() {
    this(null);
  }

  public AdfsAccessTokenResponseDeserializer(Class<?> vc) {
    super(vc);
  }

  @Override
  public AdfsAccessTokenResponse deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException {
    JsonNode node = jp.getCodec().readTree(jp);
    return new AdfsAccessTokenResponse(node);
  }
}
