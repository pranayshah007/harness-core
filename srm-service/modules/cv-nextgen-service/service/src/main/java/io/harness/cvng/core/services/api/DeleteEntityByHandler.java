/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cvng.core.services.api;

import io.harness.persistence.PersistentEntity;

public interface DeleteEntityByHandler<T extends PersistentEntity> {
  void deleteByProjectIdentifier(Class<T> clazz, String accountId, String orgIdentifier, String projectIdentifier);

  void deleteByOrgIdentifier(Class<T> clazz, String accountId, String orgIdentifier);

  void deleteByAccountIdentifier(Class<T> clazz, String accountId);
}
