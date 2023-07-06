/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.service.feature;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.utils.NGFeatureFlagHelperService;

import com.google.common.collect.ImmutableList;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Singleton
@OwnedBy(HarnessTeam.CDP)
public class NGServiceV2FFCalculator {
  @Inject private NGFeatureFlagHelperService featureFlagHelperService;
  private static List<FeatureName> featureFlags = ImmutableList.of(FeatureName.CDS_HELM_MULTIPLE_MANIFEST_SUPPORT_NG);

  public Map<FeatureName, Boolean> computeFlags(String accountId) {
    return computeFlags(accountId, featureFlags);
  }

  private Map<FeatureName, Boolean> computeFlags(String account, List<FeatureName> featureFlags) {
    Map<FeatureName, Boolean> featureFlagMap = new HashMap<>();
    for (FeatureName feature : featureFlags) {
      featureFlagMap.put(feature, featureFlagHelperService.isEnabled(account, feature));
    }
    return featureFlagMap;
  }
}
