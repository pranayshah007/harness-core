/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.beans.entity.metadata;

import static com.fasterxml.jackson.annotation.JsonTypeInfo.As.EXTERNAL_PROPERTY;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class AuthToken {
  String type;

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = EXTERNAL_PROPERTY, property = "type", visible = true)
  AuthTokenSpec spec;

  @Builder
  public AuthToken(String type, AuthTokenSpec spec) {
    this.type = type;
    this.spec = spec;
  }
}
