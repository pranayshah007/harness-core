/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.provider;

import io.harness.secrets.serviceproviders.DelegatePropertiesServiceProvider;
import io.harness.delegate.service.DelegatePropertyService;
import io.harness.managerclient.GetDelegatePropertiesRequest;
import io.harness.managerclient.GetDelegatePropertiesResponse;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.concurrent.ExecutionException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class DelegatePropertiesServiceProviderImpl implements DelegatePropertiesServiceProvider {
  @Inject DelegatePropertyService delegatePropertyService;

  @Override
  public GetDelegatePropertiesResponse getDelegateProperties(GetDelegatePropertiesRequest request) {
    try {
      return delegatePropertyService.getDelegateProperties(request);
    } catch (ExecutionException e) {
      log.warn("Unable to fetch Delegate Properties", e);
    }
    return null;
  }
}
