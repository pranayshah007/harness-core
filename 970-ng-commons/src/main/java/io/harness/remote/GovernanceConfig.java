/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.remote;

import com.google.inject.Singleton;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Singleton
public class GovernanceConfig {
  private boolean useDkron;
  private String callbackApiEndpoint;
  private boolean dkronJobEnabled;
  private String awsFaktoryJobType;
  private String awsFaktoryQueueName;
  private String OOTBAccount;
  private String tagsKey;
  private String tagsValue;
  private int policiesInPack;
  private int policiesInEnforcement;
  private int packsInEnforcement;
  private int regionLimit;
  private int accountLimit;
  private int policyPerAccountLimit;
  private int sleepTime;
}
