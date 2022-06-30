/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.jersey;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import javax.ws.rs.core.Configuration;
import javax.ws.rs.core.Feature;
import javax.ws.rs.core.FeatureContext;

@Singleton
public class KryoFeature implements Feature {
  @Inject KryoMessageBodyProvider kryoMessageBodyProvider;

  @Override
  public boolean configure(FeatureContext context) {
    Configuration config = context.getConfiguration();
    if (kryoMessageBodyProvider != null && !config.isRegistered(kryoMessageBodyProvider)) {
      context.register(kryoMessageBodyProvider);
    }

    return true;
  }
}
