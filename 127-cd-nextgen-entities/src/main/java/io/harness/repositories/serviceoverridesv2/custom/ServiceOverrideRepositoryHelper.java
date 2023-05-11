/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories.serviceoverridesv2.custom;

import io.harness.ng.core.serviceoverride.beans.NGServiceOverridesEntity;
import io.harness.ng.core.serviceoverride.beans.NGServiceOverridesEntity.NGServiceOverridesEntityKeys;

import lombok.experimental.UtilityClass;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Update;

@UtilityClass
public class ServiceOverrideRepositoryHelper {
  public static Update getUpdateOperationsForServiceOverrideV2(NGServiceOverridesEntity serviceOverridesEntity) {
    // This does not set yaml to if any override with yaml goes through it, yaml field will be unset
    Update update = new Update();
    update.set(NGServiceOverridesEntityKeys.accountId, serviceOverridesEntity.getAccountId());
    update.set(NGServiceOverridesEntityKeys.orgIdentifier, serviceOverridesEntity.getOrgIdentifier());
    update.set(NGServiceOverridesEntityKeys.projectIdentifier, serviceOverridesEntity.getProjectIdentifier());
    update.set(NGServiceOverridesEntityKeys.identifier, serviceOverridesEntity.getIdentifier());
    update.set(NGServiceOverridesEntityKeys.environmentRef, serviceOverridesEntity.getEnvironmentRef());
    update.set(NGServiceOverridesEntityKeys.serviceRef, serviceOverridesEntity.getServiceRef());
    update.set(NGServiceOverridesEntityKeys.infraIdentifier, serviceOverridesEntity.getInfraIdentifier());
    update.set(NGServiceOverridesEntityKeys.type, serviceOverridesEntity.getType());
    update.set(NGServiceOverridesEntityKeys.spec, serviceOverridesEntity.getSpec());
    update.setOnInsert(NGServiceOverridesEntityKeys.createdAt, System.currentTimeMillis());
    update.set(NGServiceOverridesEntityKeys.lastModifiedAt, System.currentTimeMillis());
    return update;
  }

  public Criteria getEqualityCriteriaForServiceOverride(
      String accountId, String orgIdentifier, String projectIdentifier, String identifier) {
    return Criteria.where(NGServiceOverridesEntityKeys.accountId)
        .is(accountId)
        .and(NGServiceOverridesEntityKeys.orgIdentifier)
        .is(orgIdentifier)
        .and(NGServiceOverridesEntityKeys.projectIdentifier)
        .is(projectIdentifier)
        .and(NGServiceOverridesEntityKeys.identifier)
        .is(identifier);
  }
}
