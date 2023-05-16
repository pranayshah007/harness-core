/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.connector.awsconnector;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;

@OwnedBy(HarnessTeam.DX)
public class AwsConstants {
  public static final String INHERIT_FROM_DELEGATE = "InheritFromDelegate";
  public static final String MANUAL_CONFIG = "ManualConfig";
  public static final String IRSA = "Irsa";
  public static final String FIXED_DELAY_BACKOFF_STRATEGY = "FixedDelayBackoffStrategy";
  public static final String EQUAL_JITTER_BACKOFF_STRATEGY = "EqualJitterBackoffStrategy";
  public static final String FULL_JITTER_BACKOFF_STRATEGY = "FullJitterBackoffStrategy";
}
