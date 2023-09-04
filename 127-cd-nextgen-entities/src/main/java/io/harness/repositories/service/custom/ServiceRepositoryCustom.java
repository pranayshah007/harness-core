/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.repositories.service.custom;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.exception.InvalidRequestException;
import io.harness.ng.core.service.entity.ServiceEntity;

import com.mongodb.DuplicateKeyException;
import com.mongodb.client.result.DeleteResult;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;

@OwnedBy(HarnessTeam.PIPELINE)
public interface ServiceRepositoryCustom {
  Page<ServiceEntity> findAll(Criteria criteria, Pageable pageable);
  @Deprecated List<ServiceEntity> findAll(Criteria criteria); // Use find all  with pageable
  ServiceEntity upsert(Criteria criteria, ServiceEntity serviceEntity);
  ServiceEntity update(Criteria criteria, ServiceEntity serviceEntity);
  @Deprecated boolean softDelete(Criteria criteria);
  boolean delete(Criteria criteria);
  DeleteResult deleteMany(Criteria criteria);

  Long findActiveServiceCountAtGivenTimestamp(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, long timestampInMs);

  List<ServiceEntity> findAllRunTimePermission(Criteria criteria);

  ServiceEntity find(String accountIdentifier, String orgIdentifier, String projectIdentifier, String serviceIdentifier,
      boolean deleted);

  List<String> getServiceIdentifiers(String accountIdentifier, String orgIdentifier, String projectIdentifier);

  // Introducing an additional method to disambiguate from the one defined in CrudRepository and prevent ambiguous
  // behavior.
  ServiceEntity saveGitAware(ServiceEntity serviceToSave) throws InvalidRequestException, DuplicateKeyException;

  Optional<ServiceEntity> findByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifierAndDeletedNot(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String serviceIdentifier,
      boolean notDeleted, boolean loadFromCache, boolean loadFromFallbackBranch);
}
