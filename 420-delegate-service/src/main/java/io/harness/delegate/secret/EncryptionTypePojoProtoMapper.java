/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.secret;

import io.harness.delegate.core.beans.EncryptionType;
import lombok.experimental.UtilityClass;

@UtilityClass
public class EncryptionTypePojoProtoMapper {
    public static EncryptionType map(io.harness.security.encryption.EncryptionType type) {
        switch (type) {
            case KMS:
                return EncryptionType.AWS_KMS;
            case VAULT:
                return EncryptionType.HASHICORP_VAULT;
            default:
                return EncryptionType.valueOf(type.name());
        }
    }
}
