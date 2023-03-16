/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.repositories.user.custom;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.core.user.entities.UserMetadata;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.util.CloseableIterator;

@OwnedBy(PL)
public interface UserMetadataRepositoryCustom {
  Page<UserMetadata> findAll(Criteria criteria, Pageable pageable);

  List<UserMetadata> findAll(Criteria criteria);

  List<String> findAllIds(Criteria criteria);

  UserMetadata updateFirst(String userId, Update update);

  long insertAllIgnoringDuplicates(List<UserMetadata> userMetadata);

  CloseableIterator<UserMetadata> stream(Criteria criteria);
}
