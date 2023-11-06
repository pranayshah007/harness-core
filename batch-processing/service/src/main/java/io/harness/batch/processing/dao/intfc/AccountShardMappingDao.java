/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.dao.intfc;

import io.harness.batch.processing.entities.AccountShardMapping;

import java.util.List;

public interface AccountShardMappingDao {
  List<AccountShardMapping> getAccountShardMapping();

  long count(String accountId);

  boolean delete(String accountId);
}
