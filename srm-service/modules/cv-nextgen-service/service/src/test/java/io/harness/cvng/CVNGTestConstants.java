/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

public class CVNGTestConstants {
  public static Instant TIME_FOR_TESTS = Instant.parse("2020-07-27T10:50:00Z");
  public static Clock FIXED_TIME_FOR_TESTS = Clock.fixed(TIME_FOR_TESTS, ZoneOffset.UTC);
}
