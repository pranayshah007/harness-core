/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.rancher;

import io.harness.connector.task.rancher.RancherConfig;

import java.io.IOException;

public interface RancherHelperServiceDelegate {
  public void testRancherConnection(final RancherConfig rancherConfig) throws IOException;
}
