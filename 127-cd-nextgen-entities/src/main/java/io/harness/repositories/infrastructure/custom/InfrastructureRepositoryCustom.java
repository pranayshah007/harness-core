/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.repositories.infrastructure.custom;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.infrastructure.entity.InfrastructureEntity;

import com.mongodb.client.result.DeleteResult;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;

@OwnedBy(HarnessTeam.PIPELINE)
public interface InfrastructureRepositoryCustom {
  Page<InfrastructureEntity> findAll(Criteria criteria, Pageable pageable);
  InfrastructureEntity upsert(Criteria criteria, InfrastructureEntity infrastructureEntity);
  InfrastructureEntity update(Criteria criteria, InfrastructureEntity infrastructureEntity);
  DeleteResult delete(Criteria criteria);
  InfrastructureEntity find(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String envIdentifier, String infraIdentifier);

  List<InfrastructureEntity> findAllFromInfraIdentifierList(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String envIdentifier, List<String> infraIdentifierList);

  List<InfrastructureEntity> findAllFromEnvIdentifier(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String envIdentifier);
}
