/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.provision.terraform;

import io.harness.cdng.provision.terraform.TerraformConfig.TerraformConfigKeys;
import io.harness.persistence.HIterator;
import io.harness.persistence.HPersistence;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import dev.morphia.query.Sort;

@Singleton
public class TerraformConfigHelper {
  @Inject private HPersistence persistence;

  public HIterator<TerraformConfig> getIterator(Ambiance ambiance, String entityId) {
    return new HIterator(persistence.createQuery(TerraformConfig.class)
                             .filter(TerraformConfigKeys.accountId, AmbianceUtils.getAccountId(ambiance))
                             .filter(TerraformConfigKeys.orgId, AmbianceUtils.getOrgIdentifier(ambiance))
                             .filter(TerraformConfigKeys.projectId, AmbianceUtils.getProjectIdentifier(ambiance))
                             .filter(TerraformConfigKeys.entityId, entityId)
                             .order(Sort.descending(TerraformConfigKeys.createdAt))
                             .fetch());
  }
}
