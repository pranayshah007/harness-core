/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.pms.sdk.core.plugin;

import io.harness.encryption.SecretRefData;
import io.harness.pms.contracts.plan.SecretVariable;
import io.harness.pms.yaml.ParameterField;
import io.harness.yaml.core.variables.NGVariableType;
import io.harness.yaml.core.variables.SecretNGVariable;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@UtilityClass
@Slf4j
public class SecretNgVariableUtils {
  public SecretNGVariable getSecretNgVariable(SecretVariable secretVariable) {
    return SecretNGVariable.builder()
        .name(secretVariable.getName())
        .value(ParameterField.<SecretRefData>builder().value(new SecretRefData(secretVariable.getValue())).build())
        .type(NGVariableType.SECRET)
        .build();
  }

  public SecretVariable getSecretVariable(SecretNGVariable secretVariable) {
    return SecretVariable.newBuilder()
        .setName(secretVariable.getName())
        .setValue(secretVariable.getValue().getValue().toSecretRefStringValue())
        .build();
  }
}
