/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.azure.appservice.settings;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.expression.Expression.ALLOW_SECRETS;

import static java.util.Collections.emptyList;

import io.harness.annotation.RecasterAlias;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.beans.executioncapability.ExecutionCapability;
import io.harness.delegate.capability.EncryptedDataDetailsCapabilityHelper;
import io.harness.expression.Expression;
import io.harness.expression.ExpressionEvaluator;
import io.harness.reflection.ExpressionReflectionUtils.NestedAnnotationResolver;
import io.harness.security.encryption.EncryptedDataDetail;

import java.util.List;
import lombok.Builder;
import lombok.Setter;
import lombok.Value;
import lombok.experimental.NonFinal;

@Value
@Builder
@OwnedBy(HarnessTeam.CDP)
@RecasterAlias("io.harness.delegate.task.azure.appservice.settings.AppSettingsFile")
public class AppSettingsFile implements NestedAnnotationResolver {
  boolean encrypted;
  @Setter @NonFinal @Expression(ALLOW_SECRETS) String fileContent;
  EncryptedAppSettingsFile encryptedFile;
  List<EncryptedDataDetail> encryptedDataDetails;

  public String fetchFileContent() {
    if (encrypted && encryptedFile != null) {
      return new String(encryptedFile.getSecretFileReference().getDecryptedValue());
    }

    return fileContent;
  }

  public List<ExecutionCapability> fetchRequiredExecutionCapabilities(ExpressionEvaluator maskingEvaluator) {
    if (encrypted && isNotEmpty(encryptedDataDetails)) {
      return EncryptedDataDetailsCapabilityHelper.fetchExecutionCapabilitiesForEncryptedDataDetails(
          encryptedDataDetails, maskingEvaluator);
    }

    return emptyList();
  }

  public static AppSettingsFile create(String fileContent) {
    return AppSettingsFile.builder().encrypted(false).fileContent(fileContent).build();
  }

  public static AppSettingsFile create(
      EncryptedAppSettingsFile encryptedFile, List<EncryptedDataDetail> encryptedDataDetails) {
    return AppSettingsFile.builder()
        .encrypted(true)
        .encryptedFile(encryptedFile)
        .encryptedDataDetails(encryptedDataDetails)
        .build();
  }
}
