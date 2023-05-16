/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.entities;

import io.harness.ChangeHandler;
import io.harness.changehandlers.HealthSourceHandler;
import io.harness.changehandlers.VerifyStepExecutionHandler;
import io.harness.cvng.verificationjob.entities.VerificationJobInstance;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class VerifyStepCDCEntity implements CDCEntity<VerificationJobInstance> {
  @Inject private VerifyStepExecutionHandler verifyStepExecutionHandler;
  @Inject private HealthSourceHandler healthSourceHandler;

  @Override
  public ChangeHandler getChangeHandler(String handlerClass) {
    if (handlerClass.contentEquals("VerifyStepExecutionHandler")) {
      return verifyStepExecutionHandler;
    } else if (handlerClass.contentEquals("HealthSourceHandler")) {
      return healthSourceHandler;
    }
    return null;
  }

  @Override
  public Class<VerificationJobInstance> getSubscriptionEntity() {
    return VerificationJobInstance.class;
  }
}
