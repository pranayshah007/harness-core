/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.logstreaming;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.secret.ConfigSecret;

import lombok.Builder;
import lombok.Value;
import lombok.experimental.FieldDefaults;

@Value
@FieldDefaults(makeFinal = false)
@Builder
public class LogStreamingServiceConfig {
  private String baseUrl;
  private String externalUrl;
  @ConfigSecret private String serviceToken;

  public String getExternalUrl() {
    if (isEmpty(externalUrl)) {
      return baseUrl;
    }
    return externalUrl;
  }
}
