/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.core.infrastructure.services;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.infrastructure.entity.InfrastructureEntity;

import java.util.List;
import java.util.Optional;
import javax.validation.constraints.NotNull;
import org.hibernate.validator.constraints.NotEmpty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;

@OwnedBy(HarnessTeam.CDC)
public interface InfrastructureEntityService {
  InfrastructureEntity create(@NotNull InfrastructureEntity infrastructureEntity);

  Optional<InfrastructureEntity> get(@NotEmpty String accountId, @NotEmpty String orgIdentifier,
      @NotEmpty String projectIdentifier, @NotEmpty String envIdentifier, @NotEmpty String infraIdentifier);

  InfrastructureEntity update(@NotNull InfrastructureEntity requestInfra);

  InfrastructureEntity upsert(@NotNull InfrastructureEntity requestInfra);

  Page<InfrastructureEntity> list(@NotNull Criteria criteria, @NotNull Pageable pageable);

  boolean delete(@NotEmpty String accountId, @NotEmpty String orgIdentifier, @NotEmpty String projectIdentifier,
      @NotEmpty String envIdentifier, @NotEmpty String infraIdentifier);

  Page<InfrastructureEntity> bulkCreate(
      @NotEmpty String accountId, @NotNull List<InfrastructureEntity> infrastructureEntities);

  InfrastructureEntity find(@NotEmpty String accountIdentifier, @NotEmpty String orgIdentifier,
      @NotEmpty String projectIdentifier, @NotEmpty String envIdentifier, @NotEmpty String infraIdentifier);

  List<InfrastructureEntity> getAllInfrastructureFromIdentifierList(String accountIdentifier, String orgIdentifier,
      String projectIdentifier, String value, List<String> infraIdentifier);

  List<InfrastructureEntity> getAllInfrastructureFromEnvIdentifier(
      String accountIdentifier, String orgIdentifier, String projectIdentifier, String envIdentifier);
}
