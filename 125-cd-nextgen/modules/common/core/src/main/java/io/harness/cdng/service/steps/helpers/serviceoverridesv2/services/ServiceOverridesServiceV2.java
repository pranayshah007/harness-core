/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.service.steps.helpers.serviceoverridesv2.services;

import io.harness.ng.core.serviceoverride.beans.NGServiceOverridesEntity;

import java.util.Optional;
import javax.validation.Valid;
import lombok.NonNull;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;

public interface ServiceOverridesServiceV2 {
  Optional<NGServiceOverridesEntity> get(@NonNull String accountId, String orgIdentifier, String projectIdentifier,
      @NonNull String serviceOverridesIdentifier);

  NGServiceOverridesEntity create(@NonNull @Valid NGServiceOverridesEntity requestedEntity);

  NGServiceOverridesEntity update(@NonNull @Valid NGServiceOverridesEntity requestedEntity);

  boolean delete(@NonNull String accountId, String orgIdentifier, String projectIdentifier, @NonNull String identifier,
      NGServiceOverridesEntity existingEntity);

  Page<NGServiceOverridesEntity> list(Criteria criteria, Pageable pageRequest);

  Pair<NGServiceOverridesEntity, Boolean> upsert(NGServiceOverridesEntity requestedServiceOverride);
}
