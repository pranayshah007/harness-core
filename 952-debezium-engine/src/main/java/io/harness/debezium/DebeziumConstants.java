/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.debezium;

import lombok.experimental.UtilityClass;

@UtilityClass
public class DebeziumConstants {
  public static final String DEBEZIUM_PREFIX = "DEBEZIUM_";
  public static final int DEBEZIUM_TOPIC_SIZE = 10_000;

  public static final String DEBEZIUM_LOCK_PREFIX = "DEBEZIUM_LOCKER_";
  public static final String DEBEZIUM_OFFSET_PREFIX = "DEBEZIUM_OFFSETS_";
}
