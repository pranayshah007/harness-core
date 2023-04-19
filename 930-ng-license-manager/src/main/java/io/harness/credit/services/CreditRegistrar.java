/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */
package io.harness.credit.services;

import io.harness.ModuleType;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.credit.mappers.CreditObjectMapper;

import lombok.AllArgsConstructor;
import lombok.Value;

@OwnedBy(HarnessTeam.GTM)
@Value
@AllArgsConstructor
public class CreditRegistrar {
  ModuleType moduleType;

  Class<? extends CreditObjectMapper> objectMapper;
}
