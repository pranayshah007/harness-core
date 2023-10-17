/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.refresh.helper;

import io.harness.EntityType;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.IdentifierRef;
import io.harness.common.EntityReferenceHelper;
import io.harness.data.structure.EmptyPredicate;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.service.entity.ServiceEntity;
import io.harness.ng.core.service.services.ServiceEntityService;
import io.harness.persistence.PersistentEntity;
import io.harness.utils.IdentifierRefHelper;

import com.google.inject.Inject;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@OwnedBy(HarnessTeam.CDC)
public class EntityFetchHelper {
  @Inject ServiceEntityService serviceEntityService;

  public ServiceEntity getService(
      String accountId, String orgId, String projectId, String serviceRef, Map<String, PersistentEntity> cacheMap) {
    IdentifierRef serviceIdentifierRef =
        IdentifierRefHelper.getIdentifierRefOrThrowException(serviceRef, accountId, orgId, projectId, "service");
    String uniqueServiceIdentifier =
        generateUniqueIdentifier(serviceIdentifierRef.getAccountIdentifier(), serviceIdentifierRef.getOrgIdentifier(),
            serviceIdentifierRef.getProjectIdentifier(), serviceIdentifierRef.getIdentifier(), EntityType.SERVICE);
    if (cacheMap.containsKey(uniqueServiceIdentifier)) {
      return (ServiceEntity) cacheMap.get(uniqueServiceIdentifier);
    }

    Optional<ServiceEntity> serviceEntity =
        serviceEntityService.get(serviceIdentifierRef.getAccountIdentifier(), serviceIdentifierRef.getOrgIdentifier(),
            serviceIdentifierRef.getProjectIdentifier(), serviceIdentifierRef.getIdentifier(), false);
    if (serviceEntity.isEmpty()) {
      throw new InvalidRequestException(String.format(
          "Service with identifier [%s] in project [%s], org [%s] not found", serviceIdentifierRef.getIdentifier(),
          serviceIdentifierRef.getProjectIdentifier(), serviceIdentifierRef.getOrgIdentifier()));
    }
    cacheMap.put(uniqueServiceIdentifier, serviceEntity.get());
    return serviceEntity.get();
  }

  private String generateUniqueIdentifier(
      String accountId, String orgId, String projectId, String entityIdentifier, EntityType entityType) {
    List<String> fqnList = new LinkedList<>();
    fqnList.add(accountId);
    if (EmptyPredicate.isNotEmpty(orgId)) {
      fqnList.add(orgId);
    }
    if (EmptyPredicate.isNotEmpty(projectId)) {
      fqnList.add(projectId);
    }
    fqnList.add(entityIdentifier);
    fqnList.add(entityType.getYamlName());
    return EntityReferenceHelper.createFQN(fqnList);
  }
}
