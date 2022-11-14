/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.expression;

import static io.harness.annotations.dev.HarnessTeam.CDC;

import static software.wings.expression.SecretManagerPreviewFunctor.SECRET_NAME_FORMATTER;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.expression.ExpressionFunctor;
import io.harness.security.SimpleEncryption;

import java.util.Base64;
import java.util.HashSet;
import java.util.Set;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Value;

@OwnedBy(CDC)
@Value
@Builder
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
public class SweepingOutputSecretFunctor implements ExpressionFunctor {
  @Default private Set<String> evaluatedSecrets = new HashSet<>();
  SecretManagerMode mode;
  SimpleEncryption simpleEncryption;

  public Object obtain(String secretKey, String secretValue) {
    if (SecretManagerMode.DRY_RUN == mode) {
      return "${sweepingOutputSecrets.obtain(\"" + secretKey + "\",\"" + secretValue + "\")}";
    } else if (SecretManagerMode.CHECK_FOR_SECRETS == mode) {
      return format(SECRET_NAME_FORMATTER, secretKey);
    }

    String decryptedValue = new String(simpleEncryption.decrypt(Base64.getDecoder().decode(secretValue)));
    evaluatedSecrets.add(decryptedValue);
    return decryptedValue;
  }
}
