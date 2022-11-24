/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.security;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.context.GlobalContextData;
import io.harness.security.dto.Principal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;
import lombok.Value;
import org.springframework.data.annotation.TypeAlias;

@OwnedBy(PL)
@Value
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
@TypeAlias("PrincipalContextData")
public class PrincipalContextData implements GlobalContextData {
  public static final String PRINCIPAL_CONTEXT = "PRINCIPAL_CONTEXT";

  Principal principal;

  @Override
  public String getKey() {
    return PRINCIPAL_CONTEXT;
  }
}
