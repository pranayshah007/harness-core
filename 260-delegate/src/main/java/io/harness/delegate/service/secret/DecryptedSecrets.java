/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.service.secret;

import io.harness.beans.IdentifierRef;
import io.harness.encryption.Scope;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import javax.validation.constraints.NotNull;
import lombok.Builder;

public class DecryptedSecrets {
  private final Map<String, char[]> decrypted = new HashMap<>();

  public char[] getDecryptedValue(final IdentifierRef ref) {
    return decrypted.get(ref.getFullyQualifiedName());
  }

  public char[] addDecryptedSecret(final IdentifierRef ref, final char[] value) {
    return decrypted.put(ref.getFullyQualifiedName(), value);
  }
}
