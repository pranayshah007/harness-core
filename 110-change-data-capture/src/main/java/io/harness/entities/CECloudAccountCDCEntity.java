/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.entities;

import io.harness.ChangeHandler;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.commons.entities.billing.CECloudAccount;
import io.harness.changehandlers.CECloudAccountBigQueryChangeHandler;
import io.harness.changehandlers.TimeScaleDBChangeHandler;

import com.google.inject.Inject;

@OwnedBy(HarnessTeam.CE)
public class CECloudAccountCDCEntity implements CDCEntity<CECloudAccount> {
  @Inject TimeScaleDBChangeHandler timeScaleDBChangeHandler;
  @Inject CECloudAccountBigQueryChangeHandler ceCloudAccountBigQueryChangeHandler;

  @Override
  public ChangeHandler getChangeHandler(String handlerClass) {
    if (handlerClass.equals(CECloudAccountBigQueryChangeHandler.class.getSimpleName())) {
      return ceCloudAccountBigQueryChangeHandler;
    }
    return timeScaleDBChangeHandler;
  }

  @Override
  public Class<CECloudAccount> getSubscriptionEntity() {
    return CECloudAccount.class;
  }
}
