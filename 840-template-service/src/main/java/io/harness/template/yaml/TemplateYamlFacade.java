/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.template.yaml;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.template.utils.NGTemplateFeatureFlagHelperService;
import io.harness.utils.YamlPipelineUtils;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.apache.commons.lang3.StringUtils;

@OwnedBy(HarnessTeam.CDC)
@Singleton
public final class TemplateYamlFacade {
  @Inject private NGTemplateFeatureFlagHelperService featureFlagHelperService;

  public String writeYamlString(Object value) {
    if (ffEnabledForMinimizeQuotes()) {
      return TemplateYamlUtils.writeYamlString(value);
    }
    return YamlPipelineUtils.writeYamlString(value);
  }

  private boolean ffEnabledForMinimizeQuotes() {
    // Empty Account ID means global AccountID
    return featureFlagHelperService.isFeatureFlagEnabled(
        StringUtils.EMPTY, FeatureName.CDS_ENTITY_REFRESH_DO_NOT_QUOTE_STRINGS);
  }
}
