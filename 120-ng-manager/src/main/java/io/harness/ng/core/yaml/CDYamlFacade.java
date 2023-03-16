/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.yaml;

import io.harness.beans.FeatureName;
import io.harness.utils.YamlPipelineUtils;
import io.harness.utils.featureflaghelper.NGFeatureFlagHelperService;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.apache.commons.lang3.StringUtils;

@Singleton
public final class CDYamlFacade {
  @Inject private NGFeatureFlagHelperService featureFlagHelperService;

  public String writeYamlString(Object value) {
    if (ffEnabledForMinimizeQuotes()) {
      return CDYamlUtils.writeYamlString(value);
    }
    return YamlPipelineUtils.writeYamlString(value);
  }

  private boolean ffEnabledForMinimizeQuotes() {
    // Empty Account ID means global AccountID
    return featureFlagHelperService.isEnabled(StringUtils.EMPTY, FeatureName.CDS_ENTITY_REFRESH_DO_NOT_QUOTE_STRINGS);
  }
}
