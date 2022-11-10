/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.beans;

/**
 * This interface defines the minimum required fields for heart beat processing.
 * Please note that fields here are either new values (that are not already persisted in Delegate collection)
 * or transient fields of Delegate class.
 */
public interface DelegateHeartbeatParams {
  String getDelegateId();
  String getAccountId();
  long getLastHeartBeat();
}
