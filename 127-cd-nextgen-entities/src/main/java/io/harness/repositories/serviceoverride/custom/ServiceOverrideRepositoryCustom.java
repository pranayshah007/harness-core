/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.repositories.serviceoverride.custom;

import io.harness.ng.core.serviceoverride.beans.NGServiceOverridesEntity;

import com.mongodb.client.result.DeleteResult;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;

public interface ServiceOverrideRepositoryCustom {
  Page<NGServiceOverridesEntity> findAll(Criteria criteria, Pageable pageable);
  NGServiceOverridesEntity upsert(Criteria criteria, NGServiceOverridesEntity serviceOverridesEntity);
  DeleteResult delete(Criteria criteria);
}
