/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.serializer.morphia;

import io.harness.credit.entities.CICredit;
import io.harness.credit.entities.Credit;
import io.harness.licensing.entities.modules.CDModuleLicense;
import io.harness.licensing.entities.modules.CEModuleLicense;
import io.harness.licensing.entities.modules.CFModuleLicense;
import io.harness.licensing.entities.modules.CIModuleLicense;
import io.harness.licensing.entities.modules.ChaosModuleLicense;
import io.harness.licensing.entities.modules.IACMModuleLicense;
import io.harness.licensing.entities.modules.ModuleLicense;
import io.harness.licensing.entities.modules.SRMModuleLicense;
import io.harness.licensing.entities.modules.STOModuleLicense;
import io.harness.morphia.MorphiaRegistrar;
import io.harness.morphia.MorphiaRegistrarHelperPut;
import io.harness.subscription.entities.StripeCustomer;
import io.harness.subscription.entities.SubscriptionDetail;

import java.util.Set;

public class LicenseManagerMorphiaClassesRegistrar implements MorphiaRegistrar {
  @Override
  public void registerClasses(Set<Class> set) {
    set.add(ModuleLicense.class);
    set.add(CDModuleLicense.class);
    set.add(CEModuleLicense.class);
    set.add(CFModuleLicense.class);
    set.add(CIModuleLicense.class);
    set.add(SRMModuleLicense.class);
    set.add(STOModuleLicense.class);
    set.add(ChaosModuleLicense.class);
    set.add(StripeCustomer.class);
    set.add(SubscriptionDetail.class);
    set.add(IACMModuleLicense.class);
    set.add(Credit.class);
    set.add(CICredit.class);
  }

  @Override
  public void registerImplementationClasses(MorphiaRegistrarHelperPut h, MorphiaRegistrarHelperPut w) {
    // No Implementation
  }
}
