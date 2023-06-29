/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.serviceoverridev2.service;

import io.harness.encryption.Scope;
import io.harness.logstreaming.NGLogCallback;
import io.harness.ng.core.serviceoverride.beans.NGServiceOverridesEntity;
import io.harness.ng.core.serviceoverridev2.beans.NGServiceOverrideConfigV2;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.validation.Valid;
import lombok.NonNull;
import org.apache.commons.lang3.tuple.Pair;
import org.hibernate.validator.constraints.NotEmpty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;

public interface ServiceOverridesServiceV2 {
  Optional<NGServiceOverridesEntity> get(@NonNull String accountId, String orgIdentifier, String projectIdentifier,
      @NonNull String serviceOverridesIdentifier);

  NGServiceOverridesEntity create(@NonNull @Valid NGServiceOverridesEntity requestedEntity);

  NGServiceOverridesEntity update(@NonNull @Valid NGServiceOverridesEntity requestedEntity) throws IOException;

  boolean delete(@NonNull String accountId, String orgIdentifier, String projectIdentifier, @NonNull String identifier,
      NGServiceOverridesEntity existingEntity);

  Page<NGServiceOverridesEntity> list(Criteria criteria, Pageable pageRequest);

  List<NGServiceOverridesEntity> findAll(Criteria criteria);

  Pair<NGServiceOverridesEntity, Boolean> upsert(@NonNull NGServiceOverridesEntity requestedServiceOverride)
      throws IOException;

  Map<Scope, NGServiceOverridesEntity> getEnvOverride(
      @NonNull String accountId, String orgId, String projectId, @NonNull String envRef, NGLogCallback logCallback);

  Map<Scope, NGServiceOverridesEntity> getEnvServiceOverride(@NonNull String accountId, String orgId, String projectId,
      @NonNull String envRef, @NonNull String serviceRef, NGLogCallback logCallback);

  Map<Scope, NGServiceOverridesEntity> getInfraOverride(@NonNull String accountId, String orgId, String projectId,
      @NonNull String envRef, @NonNull String infraId, NGLogCallback logCallback);

  Map<Scope, NGServiceOverridesEntity> getInfraServiceOverride(@NonNull String accountId, String orgId,
      String projectId, @NonNull String envRef, @NonNull String serviceRef, @NonNull String infraId,
      NGLogCallback logCallback);

  String createServiceOverrideInputsYaml(@NonNull String accountId, String orgIdentifier, String projectIdentifier,
      @NonNull String environmentRef, @NonNull String serviceRef);

  String createEnvOverrideInputsYaml(
      @NonNull String accountId, String orgIdentifier, String projectIdentifier, @NonNull String environmentRef);

  Optional<NGServiceOverrideConfigV2> mergeOverridesGroupedByType(
      @NonNull List<NGServiceOverridesEntity> overridesEntities);

  boolean deleteAllInOrg(@NotEmpty String accountId, @NotEmpty String orgIdentifier);
  boolean deleteAllInProject(
      @NotEmpty String accountId, @NotEmpty String orgIdentifier, @NotEmpty String projectIdentifier);

  boolean deleteAllForEnv(@NotEmpty String accountId, @NotEmpty String orgIdentifier,
      @NotEmpty String projectIdentifier, @NotEmpty String environmentRef);

  boolean deleteAllForInfra(@NotEmpty String accountId, @NotEmpty String orgIdentifier,
      @NotEmpty String projectIdentifier, @NotEmpty String environmentRef, @NotEmpty String infraIdentifier);

  boolean deleteAllOfService(@NotEmpty String accountId, @NotEmpty String orgIdentifier,
      @NotEmpty String projectIdentifier, @NotEmpty String serviceRef);
}
