/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.serviceoverridev2.service;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.encryption.Scope;
import io.harness.logstreaming.NGLogCallback;
import io.harness.ng.core.serviceoverride.beans.NGServiceOverridesEntity;
import io.harness.ng.core.serviceoverridev2.beans.NGServiceOverrideConfigV2;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.NonNull;
import org.apache.commons.lang3.tuple.Pair;
import org.hibernate.validator.constraints.NotEmpty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_SERVICE_ENVIRONMENT})
public class DummyServiceOverridesServiceV2Impl implements ServiceOverridesServiceV2 {
  @Override
  public Optional<NGServiceOverridesEntity> get(@NonNull String accountId, String orgIdentifier,
      String projectIdentifier, @NonNull String serviceOverridesIdentifier) {
    return Optional.empty();
  }

  @Override
  public NGServiceOverridesEntity create(@NonNull NGServiceOverridesEntity requestedEntity) {
    return null;
  }

  @Override
  public NGServiceOverridesEntity update(@NonNull NGServiceOverridesEntity requestedEntity) {
    return null;
  }

  @Override
  public boolean delete(@NonNull String accountId, String orgIdentifier, String projectIdentifier,
      @NonNull String identifier, NGServiceOverridesEntity existingEntity) {
    return false;
  }

  @Override
  public Page<NGServiceOverridesEntity> list(Criteria criteria, Pageable pageRequest) {
    return null;
  }

  @Override
  public List<NGServiceOverridesEntity> findAll(Criteria criteria) {
    return null;
  }

  @Override
  public Pair<NGServiceOverridesEntity, Boolean> upsert(@NonNull NGServiceOverridesEntity requestedServiceOverride) {
    return null;
  }

  @Override
  public Map<Scope, NGServiceOverridesEntity> getEnvOverride(
      @NonNull String accountId, String orgId, String projectId, @NonNull String envRef, NGLogCallback logCallback) {
    return null;
  }

  @Override
  public Map<Scope, NGServiceOverridesEntity> getEnvServiceOverride(@NonNull String accountId, String orgId,
      String projectId, @NonNull String envRef, @NonNull String serviceRef, NGLogCallback logCallback) {
    return null;
  }

  @Override
  public Map<Scope, NGServiceOverridesEntity> getInfraOverride(@NonNull String accountId, String orgId,
      String projectId, @NonNull String envRef, @NonNull String infraId, NGLogCallback logCallback) {
    return null;
  }

  @Override
  public Map<Scope, NGServiceOverridesEntity> getInfraServiceOverride(@NonNull String accountId, String orgId,
      String projectId, @NonNull String envRef, @NonNull String serviceRef, @NonNull String infraId,
      NGLogCallback logCallback) {
    return null;
  }

  @Override
  public String createServiceOverrideInputsYaml(@NonNull String accountId, String orgIdentifier,
      String projectIdentifier, @NonNull String environmentRef, @NonNull String serviceRef) {
    return null;
  }

  @Override
  public String createEnvOverrideInputsYaml(
      @NonNull String accountId, String orgIdentifier, String projectIdentifier, @NonNull String environmentRef) {
    return null;
  }

  @Override
  public Optional<NGServiceOverrideConfigV2> mergeOverridesGroupedByType(
      @NonNull List<NGServiceOverridesEntity> overridesEntities) {
    return Optional.empty();
  }

  @Override
  public boolean deleteAllInOrg(@NotEmpty String accountId, @NotEmpty String orgIdentifier) {
    return false;
  }

  @Override
  public boolean deleteAllInProject(
      @NotEmpty String accountId, @NotEmpty String orgIdentifier, @NotEmpty String projectIdentifier) {
    return false;
  }

  @Override
  public boolean deleteAllForEnv(@NotEmpty String accountId, @NotEmpty String orgIdentifier,
      @NotEmpty String projectIdentifier, @NotEmpty String environmentRef) {
    return false;
  }

  @Override
  public boolean deleteAllForInfra(@NotEmpty String accountId, @NotEmpty String orgIdentifier,
      @NotEmpty String projectIdentifier, @NotEmpty String environmentRef, @NotEmpty String infraIdentifier) {
    return false;
  }

  @Override
  public boolean deleteAllOfService(@NotEmpty String accountId, @NotEmpty String orgIdentifier,
      @NotEmpty String projectIdentifier, @NotEmpty String serviceRef) {
    return false;
  }
}
