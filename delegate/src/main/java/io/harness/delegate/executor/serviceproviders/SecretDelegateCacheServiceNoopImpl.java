/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.executor.serviceproviders;

import io.harness.secrets.SecretsDelegateCacheService;
import io.harness.security.encryption.SecretUniqueIdentifier;

import java.util.function.Function;
import org.hibernate.validator.constraints.NotEmpty;

public class SecretDelegateCacheServiceNoopImpl implements SecretsDelegateCacheService {
  @Override
  public char[] get(SecretUniqueIdentifier key, Function<SecretUniqueIdentifier, char[]> mappingFunction) {
    // FIXME: add support soon
    throw new UnsupportedOperationException();
  }

  @Override
  public void put(SecretUniqueIdentifier key, @NotEmpty char[] value) {
    // FIXME: add support soon
    throw new UnsupportedOperationException();
  }

  @Override
  public void remove(SecretUniqueIdentifier key) {
    // FIXME: add support soon
    throw new UnsupportedOperationException();
  }
}
