/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.managerclient;

import io.harness.verificationclient.CVNextGenServiceClient;
import io.harness.verificationclient.CVNextGenServiceClientFactory;

import com.google.inject.AbstractModule;

public class VerificationServiceClientModule extends AbstractModule {
  @Override
  protected void configure() {
    bind(VerificationServiceClient.class).toProvider(VerificationServiceClientFactory.class);
    bind(CVNextGenServiceClient.class).toProvider(CVNextGenServiceClientFactory.class);
  }
}
