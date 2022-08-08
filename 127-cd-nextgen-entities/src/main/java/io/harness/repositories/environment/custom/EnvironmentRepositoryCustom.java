/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.repositories.environment.custom;

import io.harness.ng.core.environment.beans.Environment;

import com.mongodb.client.result.UpdateResult;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.query.Criteria;

public interface EnvironmentRepositoryCustom {
  Page<Environment> findAll(Criteria criteria, Pageable pageable);

  Environment upsert(Criteria criteria, Environment environment);

  Environment update(Criteria criteria, Environment environment);

  @Deprecated boolean softDelete(Criteria criteria);

  boolean delete(Criteria criteria);

  UpdateResult deleteMany(Criteria criteria);

  List<Environment> findAllRunTimeAccess(Criteria criteria);

  List<String> fetchesNonDeletedEnvIdentifiersFromList(Criteria criteria);

  List<Environment> fetchesNonDeletedEnvironmentFromListOfIdentifiers(Criteria criteria);
}
