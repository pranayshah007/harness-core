/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.repositories.infrastructure.custom;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.service.beans.ServiceDefinitionType;
import io.harness.ng.core.infrastructure.entity.InfrastructureEntity;

import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Update;

@OwnedBy(HarnessTeam.PIPELINE)
public interface InfrastructureRepositoryCustom {
  Page<InfrastructureEntity> findAll(Criteria criteria, Pageable pageable);
  InfrastructureEntity saveGitAware(InfrastructureEntity infrastructureToSave);
  InfrastructureEntity upsert(Criteria criteria, InfrastructureEntity infrastructureEntity);
  InfrastructureEntity update(Criteria criteria, InfrastructureEntity infrastructureEntity);
  DeleteResult delete(Criteria criteria);
  InfrastructureEntity find(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String envIdentifier, String infraIdentifier);

  List<InfrastructureEntity> findAllFromInfraIdentifierList(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String envIdentifier, List<String> infraIdentifierList);

  List<InfrastructureEntity> findAllFromEnvIdentifier(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String envIdentifier);

  List<InfrastructureEntity> findAllFromEnvIdentifierAndDeploymentType(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String envIdentifier, ServiceDefinitionType deploymentType);

  UpdateResult batchUpdateInfrastructure(String accountIdentifier, String orgIdentifier, String projectIdentifier,
      String envIdentifier, List<String> infraIdentifierList, Update update);

  List<InfrastructureEntity> findAllFromProjectIdentifier(
      String accountIdentifier, String orgIdentifier, String projectIdentifier);

  Optional<InfrastructureEntity> findByAccountIdAndOrgIdentifierAndProjectIdentifierAndEnvIdentifierAndIdentifier(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String environmentIdentifier,
      String infraIdentifier, boolean loadFromCache, boolean loadFromFallbackBranch);
}
