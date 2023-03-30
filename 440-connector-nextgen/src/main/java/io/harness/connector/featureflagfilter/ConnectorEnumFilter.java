/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.connector.featureflagfilter;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.beans.FeatureName.AZURE_ARTIFACTS_NG;
import static io.harness.beans.FeatureName.BAMBOO_ARTIFACT_NG;
import static io.harness.beans.FeatureName.CDS_TAS_NG;
import static io.harness.beans.FeatureName.CDS_TERRAFORM_CLOUD;
import static io.harness.remote.client.CGRestUtils.getResponse;

import io.harness.account.AccountClient;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureName;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.ff.filters.EnumFeatureFlagFilter;

import com.google.common.collect.Sets;
import com.google.inject.Inject;
import com.google.inject.Singleton;

@OwnedBy(CDP)
@Singleton
public class ConnectorEnumFilter extends EnumFeatureFlagFilter {
  @Inject private AccountClient accountClient;

  public ConnectorEnumFilter() {
    put(AZURE_ARTIFACTS_NG, Sets.newHashSet(ConnectorType.AZURE_ARTIFACTS));
    put(CDS_TAS_NG, Sets.newHashSet(ConnectorType.TAS));
    put(CDS_TERRAFORM_CLOUD, Sets.newHashSet(ConnectorType.TERRAFORM_CLOUD));
    put(BAMBOO_ARTIFACT_NG, Sets.newHashSet(ConnectorType.BAMBOO));
  }

  @Override
  public boolean isFeatureFlagEnabled(FeatureName featureName, String accountId) {
    return getResponse(accountClient.isFeatureFlagEnabled(featureName.name(), accountId));
  }
}
