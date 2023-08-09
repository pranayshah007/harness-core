/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.featureflagfilter;
import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.beans.FeatureName.CDS_RANCHER_SUPPORT_NG;
import static io.harness.beans.FeatureName.NG_SVC_ENV_REDESIGN;
import static io.harness.remote.client.CGRestUtils.getResponse;

import io.harness.account.AccountClient;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.FeatureName;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.ff.filters.EnumFeatureFlagFilter;

import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.inject.Singleton;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_K8S, HarnessModuleComponent.CDS_AMI_ASG})
@OwnedBy(CDP)
@Singleton
public class ConnectorEnumFilter extends EnumFeatureFlagFilter {
  @Inject private AccountClient accountClient;

  public ConnectorEnumFilter() {
    put(NG_SVC_ENV_REDESIGN, Sets.newHashSet(ConnectorType.TAS));
    put(CDS_RANCHER_SUPPORT_NG, Sets.newHashSet(ConnectorType.RANCHER));
  }

  @Override
  public boolean isFeatureFlagEnabled(FeatureName featureName, String accountId) {
    return getResponse(accountClient.isFeatureFlagEnabled(featureName.name(), accountId));
  }
}
