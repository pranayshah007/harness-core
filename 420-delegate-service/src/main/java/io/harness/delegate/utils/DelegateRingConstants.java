/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.utils;

import static io.harness.annotations.dev.HarnessTeam.DEL;

import io.harness.annotations.dev.OwnedBy;

import lombok.experimental.UtilityClass;

@UtilityClass
@OwnedBy(DEL)
public class DelegateRingConstants {
  public static final String RING_NAME_1 = "ring1";
  public static final String RING_NAME_2 = "ring2";
  public static final String RING_NAME_3 = "ring3";
  public static final String RING_NAME_4 = "ring4";
  public static final String DEFAULT_RING_NAME = "ring3";
  public static final String LATEST_DELEGATE_IMAGE = "harness/delegate:latest";
  public static final String LATEST_UPGRADER_IMAGE = "harness/upgrader:latest";
}
