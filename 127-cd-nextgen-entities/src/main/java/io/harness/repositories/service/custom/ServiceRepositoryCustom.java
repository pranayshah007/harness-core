/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.repositories.service.custom;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.service.entity.ServiceEntity;
import io.harness.ng.core.service.entity.ServiceGitXMetadataDTO;

import com.mongodb.client.result.DeleteResult;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;

@OwnedBy(HarnessTeam.PIPELINE)
public interface ServiceRepositoryCustom {
  Page<ServiceEntity> findAll(Criteria criteria, Pageable pageable);
  List<ServiceEntity> findAll(Criteria criteria);
  ServiceEntity upsert(Criteria criteria, ServiceEntity serviceEntity, ServiceGitXMetadataDTO serviceGitXMetadataDTO);
  ServiceEntity update(Criteria criteria, ServiceEntity serviceEntity, ServiceGitXMetadataDTO serviceGitXMetadataDTO);
  @Deprecated boolean softDelete(Criteria criteria);
  boolean delete(Criteria criteria);
  DeleteResult deleteMany(Criteria criteria);

  Long findActiveServiceCountAtGivenTimestamp(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, long timestampInMs);

  List<ServiceEntity> findAllRunTimePermission(Criteria criteria);

  Optional<ServiceEntity> find(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String serviceIdentifier, boolean deleted, String branch, boolean getMetadataOnly);

  /**
   * To support git experience, This method expects serviceEntity field to be populated with GitX related fields
   *
   * @param serviceEntity
   * @param serviceGitXMetadataDTO
   * @return serviceEntity
   */
  ServiceEntity save(ServiceEntity serviceEntity, ServiceGitXMetadataDTO serviceGitXMetadataDTO);
}
