/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ngtriggers.utils;

import io.harness.ngtriggers.beans.dto.TriggerDetails;
import io.harness.ngtriggers.eventmapper.filters.dto.FilterRequestData;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;

@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Singleton
public class GitProviderDataObtainmentManager {
  private final Map<String, GitProviderBaseDataObtainer> obtainerMap;

  public void acquireProviderData(FilterRequestData filterRequestData, List<TriggerDetails> triggers) {
    String sourceRepoType = filterRequestData.getWebhookPayloadData().getOriginalEvent().getSourceRepoType();
    if (obtainerMap.containsKey(sourceRepoType)) {
      obtainerMap.get(sourceRepoType).acquireProviderData(filterRequestData, triggers);
    }
  }
}
