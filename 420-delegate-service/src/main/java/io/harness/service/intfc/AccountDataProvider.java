/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.service.intfc;

import io.harness.delegate.beans.DelegateRing;

/*
Common interface to provide account level data to DMS and delegate-service (within manager)
 */
public interface AccountDataProvider {
  DelegateRing getDelegateRing(String accountId);
}
