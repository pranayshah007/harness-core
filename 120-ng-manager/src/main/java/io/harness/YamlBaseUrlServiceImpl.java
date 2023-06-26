/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness;

import io.harness.ng.NextGenConfiguration;
import io.harness.ng.core.schema.YamlBaseUrlService;

import com.google.inject.Inject;

public class YamlBaseUrlServiceImpl implements YamlBaseUrlService {
  @Inject NextGenConfiguration nextGenConfiguration;

  

  @Override
  public String getBaseUrl() {
    return nextGenConfiguration.getNgManagerClientConfig().getBaseUrl();
  }
}
