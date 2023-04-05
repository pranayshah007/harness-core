/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.ssh.config;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.expression.Expression.ALLOW_SECRETS;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.expression.Expression;
import io.harness.reflection.ExpressionReflectionUtils.NestedAnnotationResolver;
import io.harness.security.encryption.EncryptedDataDetail;

import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@OwnedBy(HarnessTeam.CDP)
public class ConfigFileParameters implements NestedAnnotationResolver {
  private String fileName;
  @Expression(ALLOW_SECRETS) private String fileContent;
  private long fileSize;
  private String destinationPath;
  private SecretConfigFile secretConfigFile;
  private boolean isEncrypted;
  List<EncryptedDataDetail> encryptionDataDetails;

  public void calculateFileSize() {
    this.fileSize = isEmpty(fileContent) ? 0 : fileContent.getBytes(StandardCharsets.UTF_8).length;
  }
}
