/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories.serviceoverridesv2.custom;

import io.harness.ng.core.serviceoverride.beans.NGServiceOverridesEntity;

import com.mongodb.client.result.DeleteResult;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;

public interface ServiceOverrideRepositoryCustomV2 {
  NGServiceOverridesEntity update(Criteria criteria, NGServiceOverridesEntity serviceOverridesEntity);

  DeleteResult delete(Criteria criteria);

  Page<NGServiceOverridesEntity> findAll(Criteria criteria, Pageable pageRequest);

  List<NGServiceOverridesEntity> findAll(Criteria criteria);
}
