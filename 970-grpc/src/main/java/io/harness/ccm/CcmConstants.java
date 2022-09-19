/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ccm;

import io.harness.grpc.IdentifierKeys;

import java.time.ZoneOffset;
import java.util.TimeZone;

public interface CcmConstants {
  /**
   * The constant HARNESS_NAME.
   */
  String HARNESS_NAME = "Harness";

  /**
   * Offset used while saving into timescaleDB
   */
  ZoneOffset ZONE_OFFSET = ZoneOffset.UTC;

  TimeZone TIME_ZONE = TimeZone.getTimeZone("UTC");

  String CLUSTER_ID_IDENTIFIER = IdentifierKeys.PREFIX + "clusterId";
  String UID = IdentifierKeys.PREFIX + "uid";
}
