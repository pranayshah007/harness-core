/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.provider;

import io.harness.secrets.serviceproviders.DelegatePropertiesServiceProvider;
import io.harness.managerclient.GetDelegatePropertiesRequest;
import io.harness.managerclient.GetDelegatePropertiesResponse;

public class NoopDelegatePropertiesServiceProviderImpl implements DelegatePropertiesServiceProvider {
  @Override
  public GetDelegatePropertiesResponse getDelegateProperties(GetDelegatePropertiesRequest request) {
    return null;
  }
}
