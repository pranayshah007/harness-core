/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.core.service.services;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.service.entity.ServiceEntity;
import io.harness.repositories.UpsertOptions;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;

@OwnedBy(HarnessTeam.PIPELINE)
public interface ServiceEntityService {
  ServiceEntity create(ServiceEntity serviceEntity);

  Optional<ServiceEntity> get(
      String accountId, String orgIdentifier, String projectIdentifier, String serviceIdentifier, boolean deleted);

  // TODO(archit): make it transactional
  ServiceEntity update(ServiceEntity requestService);

  // TODO(archit): make it transactional
  ServiceEntity upsert(ServiceEntity requestService, UpsertOptions upsertOptions);

  Page<ServiceEntity> list(Criteria criteria, Pageable pageable);

  List<ServiceEntity> listRunTimePermission(Criteria criteria);

  boolean delete(
      String accountId, String orgIdentifier, String projectIdentifier, String serviceIdentifier, Long version);

  Page<ServiceEntity> bulkCreate(String accountId, List<ServiceEntity> serviceEntities);

  List<ServiceEntity> getAllServices(String accountIdentifier, String orgIdentifier, String projectIdentifier);

  List<ServiceEntity> getAllNonDeletedServices(
      String accountIdentifier, String orgIdentifier, String projectIdentifier);

  Integer findActiveServicesCountAtGivenTimestamp(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, long timestampInMs);

  ServiceEntity find(String accountIdentifier, String orgIdentifier, String projectIdentifier, String serviceIdentifier,
      boolean deleted);

  String createServiceInputsYaml(String yaml);

  boolean forceDeleteAllInProject(String accountId, String orgIdentifier, String projectIdentifier);
}
