/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.secrets;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.security.encryption.SecretUniqueIdentifier;

import java.util.function.Function;
import javax.validation.constraints.NotNull;
import org.hibernate.validator.constraints.NotEmpty;

@OwnedBy(PL)
public interface SecretsDelegateCacheService {
  char[] get(@NotNull SecretUniqueIdentifier key, @NotNull Function<SecretUniqueIdentifier, char[]> mappingFunction);
  void put(@NotNull SecretUniqueIdentifier key, @NotEmpty char[] value);
  void remove(@NotNull SecretUniqueIdentifier key);
}
