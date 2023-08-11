/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.logstreaming;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.expression.SecretString.SECRET_MASK;
import static io.harness.windows.CmdUtils.WIN_RM_MARKER;

import static org.apache.commons.lang3.StringUtils.replaceEach;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.data.structure.EmptyPredicate;
import io.harness.logging.LogSanitizerHelper;
import io.harness.windows.CmdUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Builder;

@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_COMMON_STEPS})
@Builder
public class LogStreamingSanitizer {
  private final Set<String> secrets;
  public LogStreamingSanitizer(Set<String> secrets) {
    this.secrets = calculateSecretLines(secrets);
  }

  public void sanitizeLogMessage(LogLine logLine) {
    sanitizeLogMessage(logLine, null);
  }

  public void sanitizeLogMessage(LogLine logLine, Set<String> markers) {
    String sanitizedLogMessage = logLine.getMessage();
    if (isEmpty(sanitizedLogMessage)) {
      logLine.setMessage(sanitizedLogMessage);
      return;
    }

    if (!isEmpty(secrets)) {
      boolean isWinRm = markers != null && markers.contains(WIN_RM_MARKER);
      Set<String> allSecrets = isWinRm ? new HashSet<>(secrets) : secrets;
      if (isWinRm) {
        secrets.stream().map(CmdUtils::escapeEnvValueSpecialChars).collect(Collectors.toCollection(() -> allSecrets));
      }

      ArrayList<String> secretMasks = new ArrayList<>();
      ArrayList<String> secretValues = new ArrayList<>();
      for (String secret : allSecrets) {
        secretMasks.add(SECRET_MASK);
        secretValues.add(secret);

        addSecretMasksWithQuotesRemoved(secret, secretMasks, secretValues);
      }
      sanitizedLogMessage = replaceEach(
          logLine.getMessage(), secretValues.toArray(new String[] {}), secretMasks.toArray(new String[] {}));
    }

    // JWT mask
    sanitizedLogMessage = LogSanitizerHelper.sanitizeJWT(sanitizedLogMessage);
    logLine.setMessage(sanitizedLogMessage);
  }

  private static Set<String> calculateSecretLines(Set<String> secrets) {
    if (isEmpty(secrets)) {
      return new HashSet<>();
    }
    return secrets.stream()
        .flatMap(secret -> {
          String[] split = secret.split("\\r?\\n");
          return Arrays.stream(split).filter(EmptyPredicate::isNotEmpty);
        })
        .collect(Collectors.toSet());
  }

  private void addSecretMasksWithQuotesRemoved(
      String secret, ArrayList<String> secretMasks, ArrayList<String> secretValues) {
    String secretWithDoubleQuoteRemoved = secret.replaceAll("\"", "");
    if (!secretWithDoubleQuoteRemoved.equals(secret)) {
      secretMasks.add(SECRET_MASK);
      secretValues.add(secretWithDoubleQuoteRemoved);
    }
    String secretWithSingleQuoteRemoved = secret.replaceAll("\'", "");
    if (!secretWithSingleQuoteRemoved.equals(secret)) {
      secretMasks.add(SECRET_MASK);
      secretValues.add(secretWithSingleQuoteRemoved);
    }
  }
}
