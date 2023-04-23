/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.delegatetasks;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.expression.SecretString.SECRET_MASK;

import static org.apache.commons.lang3.StringUtils.replaceEach;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.data.structure.EmptyPredicate;
import io.harness.logging.LogSanitizerHelper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@OwnedBy(HarnessTeam.DEL)
@TargetModule(HarnessModule._930_DELEGATE_TASKS)
public abstract class LogSanitizer {
  public static Set<String> calculateSecretLines(Set<String> secrets) {
    if (isEmpty(secrets)) {
      return new HashSet<>();
    }
    return secrets.stream()
        .flatMap(secret -> {
          String[] split = secret.split("\\r?\\n");
          return Arrays.stream(split).map(ActivityBasedLogSanitizer::cleanup).filter(EmptyPredicate::isNotEmpty);
        })
        .collect(Collectors.toSet());
  }

  public static String cleanup(String secretLine) {
    if (secretLine == null) {
      return null;
    }
    String line = secretLine.trim();
    if (line.length() < 3) {
      return null;
    }
    return line;
  }

  public abstract String sanitizeLog(String activityId, String message);

  protected String sanitizeLogInternal(String message, Set<String> secrets) {
    String sanitizedLogMessage = message;
    if (isEmpty(sanitizedLogMessage)) {
      return sanitizedLogMessage;
    }

    if (!secrets.isEmpty()) {
      ArrayList<String> secretMasks = new ArrayList<>();
      ArrayList<String> secretValues = new ArrayList<>();
      for (String secret : secrets) {
        secretMasks.add(SECRET_MASK);
        secretValues.add(secret);
      }
      sanitizedLogMessage =
          replaceEach(message, secretValues.toArray(new String[] {}), secretMasks.toArray(new String[] {}));
    }

    // JWT sanitization
    sanitizedLogMessage = LogSanitizerHelper.sanitizeJWT(sanitizedLogMessage);

    return sanitizedLogMessage;
  }
}
