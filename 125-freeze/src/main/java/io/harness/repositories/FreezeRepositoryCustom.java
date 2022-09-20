/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.repositories;

import io.harness.freeze.entity.FreezeConfigEntity;

import com.mongodb.client.result.DeleteResult;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;

public interface FreezeRepositoryCustom {
  Page<FreezeConfigEntity> findAll(Criteria criteria, Pageable pageable);
  FreezeConfigEntity upsert(Criteria criteria, FreezeConfigEntity serviceEntity);
  FreezeConfigEntity update(Criteria criteria, FreezeConfigEntity serviceEntity);
  boolean delete(Criteria criteria);
  DeleteResult deleteMany(Criteria criteria);

  Optional<FreezeConfigEntity> findByAccountIdAndOrgIdentifierAndProjectIdentifierAndIdentifier(
      String accountId, String orgIdentifier, String projectIdentifier, String freezeId);
}
