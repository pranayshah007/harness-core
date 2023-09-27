/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.infra;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import static java.nio.charset.StandardCharsets.UTF_8;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.cdng.service.steps.ServiceStepOutcome;
import io.harness.exception.UnexpectedException;
import io.harness.steps.environment.EnvironmentOutcome;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import lombok.Value;
import lombok.experimental.UtilityClass;
import org.apache.commons.codec.binary.Hex;
@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true,
    components = {HarnessModuleComponent.CDS_SERVICE_ENVIRONMENT})
@OwnedBy(CDP)
@UtilityClass
public class InfrastructureKeyGenerator {
  private static final String HASH_ALGORITHM = "SHA-1";
  private static final String INFRA_KEY_DELIMITER = "-";

  public InfraKey createInfraKey(ServiceStepOutcome service, EnvironmentOutcome env, String... params) {
    String hashedInfrastructureKey = createFullInfraKey(service, env, params);
    return new InfraKey(hashedInfrastructureKey);
  }

  public String createFullInfraKey(ServiceStepOutcome service, EnvironmentOutcome env, String... params) {
    String formattedParams = String.join(INFRA_KEY_DELIMITER, params);
    String rawKey;
    if (service == null) {
      rawKey = String.join(INFRA_KEY_DELIMITER, env.getIdentifier(), formattedParams);
    } else {
      rawKey = String.join(INFRA_KEY_DELIMITER, service.getIdentifier(), env.getIdentifier(), formattedParams);
    }
    return hashKey(rawKey.getBytes(UTF_8));
  }

  private String hashKey(byte[] key) {
    try {
      MessageDigest messageDigest = MessageDigest.getInstance(HASH_ALGORITHM);
      byte[] keyBytes = messageDigest.digest(key);
      return Hex.encodeHexString(keyBytes);
    } catch (NoSuchAlgorithmException e) {
      throw new UnexpectedException(String.format("Algorithm %s not available", HASH_ALGORITHM), e);
    }
  }

  @Value
  static class InfraKey {
    private static final int SHORT_INFRA_KEY_LENGTH = 6;
    String key;

    InfraKey(String key) {
      this.key = key;
    }

    String getShortKey() {
      return key.substring(0, SHORT_INFRA_KEY_LENGTH);
    }
  }
}
