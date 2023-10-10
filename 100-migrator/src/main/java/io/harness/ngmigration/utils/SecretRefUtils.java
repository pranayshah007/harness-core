/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ngmigration.utils;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.EncryptedData;
import io.harness.data.structure.EmptyPredicate;
import io.harness.secrets.SecretService;

import software.wings.ngmigration.CgEntityId;
import software.wings.ngmigration.NGMigrationEntityType;

import com.google.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_MIGRATOR})
@Slf4j
public class SecretRefUtils {
  public static final Pattern secretNamePattern = Pattern.compile("secrets.getValue\\([^{}]+\\)");

  @Inject private SecretService secretService;

  public List<CgEntityId> getSecretRefFromExpressions(String accountId, Set<String> expressions) {
    if (EmptyPredicate.isEmpty(expressions)) {
      return Collections.emptyList();
    }

    List<String> secretExpressions = expressions.stream()
                                         .filter(str -> secretNamePattern.matcher(str).matches())
                                         .map(str -> str.substring("secrets.getValue(\"".length(), str.length() - 2))
                                         .collect(Collectors.toList());
    if (EmptyPredicate.isEmpty(secretExpressions)) {
      return Collections.emptyList();
    }

    List<CgEntityId> secretRefs = new ArrayList<>();
    for (String secretName : secretExpressions) {
      try {
        Optional<EncryptedData> secret = secretService.getSecretByName(accountId, secretName);
        secret.ifPresent(encryptedData
            -> secretRefs.add(
                CgEntityId.builder().type(NGMigrationEntityType.SECRET).id(encryptedData.getUuid()).build()));
      } catch (Exception e) {
        log.error("There was an error getting secret by name", e);
      }
    }
    return secretRefs;
  }
}
