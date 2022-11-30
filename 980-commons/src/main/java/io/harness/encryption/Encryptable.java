/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.encryption;

import io.harness.beans.DecryptableEntity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.github.reinert.jjschema.SchemaIgnore;
import java.lang.reflect.Field;
import java.util.List;

public interface Encryptable extends DecryptableEntity {
  String getAccountId();

  void setAccountId(String accountId);

  @JsonIgnore
  @SchemaIgnore
  default List<Field> getEncryptedFields() {
    return EncryptionReflectUtils.getEncryptedFields(this.getClass());
  }
}
