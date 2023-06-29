/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.sto.beans.entities;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.Value;
import org.apache.commons.lang3.StringUtils;

@Value
@Getter
@Setter
@Builder
public class STOServiceConfig {
  String baseUrl;
  String globalToken;

  @Getter(AccessLevel.NONE) String internalUrl;

  public String getInternalUrl() {
    return StringUtils.isEmpty(this.internalUrl) ? this.baseUrl : this.internalUrl;
  }
}
