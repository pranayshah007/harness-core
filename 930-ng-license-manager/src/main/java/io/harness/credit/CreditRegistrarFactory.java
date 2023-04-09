/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */
package io.harness.credit;

import io.harness.ModuleType;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.credit.mappers.CreditObjectMapper;
import io.harness.credit.mappers.module.CICreditObjectMapper;
import io.harness.credit.services.CreditRegistrar;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@OwnedBy(HarnessTeam.GTM)
public class CreditRegistrarFactory {
  private static Map<ModuleType, CreditRegistrar> registrar = new HashMap<>();
  private CreditRegistrarFactory() {}

  static {
    registrar.put(ModuleType.CI, new CreditRegistrar(ModuleType.CI, CICreditObjectMapper.class));
  }

  public static Class<? extends CreditObjectMapper> getCreditObjectMapper(ModuleType moduleType) {
    return registrar.get(moduleType).getObjectMapper();
  }

  public static Set<ModuleType> getSupportedModuleTypes() {
    return registrar.keySet();
  }
}
