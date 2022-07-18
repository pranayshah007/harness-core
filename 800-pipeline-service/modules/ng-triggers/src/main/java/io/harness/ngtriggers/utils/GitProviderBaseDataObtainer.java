/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.utils;

import io.harness.ngtriggers.beans.dto.TriggerDetails;
import io.harness.ngtriggers.eventmapper.filters.dto.FilterRequestData;

import java.util.List;

public interface GitProviderBaseDataObtainer {
  void acquireProviderData(FilterRequestData filterRequestData, List<TriggerDetails> triggers);
}
