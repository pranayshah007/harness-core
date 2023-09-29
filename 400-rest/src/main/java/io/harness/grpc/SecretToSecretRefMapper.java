/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.grpc;

import io.harness.delegate.Secret;
import io.harness.delegate.core.beans.Scope;
import io.harness.delegate.core.beans.SecretRef;
import lombok.experimental.UtilityClass;

@UtilityClass
public class SecretToSecretRefMapper {
    SecretRef map(Secret secret) {
        return SecretRef.newBuilder()
                .setSecretId(secret.getSecretId())
                .setScope(Scope.valueOf(secret.getScope().name()))
                .build();
    }
}
