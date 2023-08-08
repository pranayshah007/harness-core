/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.delegate.task.cf.artifact;

import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.DecryptableEntity;
import io.harness.connector.helper.DecryptionHelper;
import io.harness.delegate.task.pcf.artifact.TasContainerArtifactConfig;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.NestedExceptionUtils;
import io.harness.exception.sanitizer.ExceptionMessageSanitizer;
import io.harness.security.encryption.EncryptedDataDetail;

import java.util.List;
import org.apache.commons.lang3.tuple.Pair;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PCF})
@OwnedBy(HarnessTeam.CDP)
public abstract class AbstractTasRegistrySettingsProvider implements TasRegistrySettingsProvider {
  protected void validateSettings(
      TasContainerArtifactConfig config, String registryUrl, String username, String password) {
    validateSettings(config, registryUrl);
    if (isBlank(username)) {
      throw NestedExceptionUtils.hintWithExplanationException(
          format("Configure username for %s container registry connector", config.getRegistryType().getValue()),
          format("Username is blank for '%s' container registry but is required", config.getRegistryType().getValue()),
          new InvalidArgumentsException(Pair.of("username", "Null or blank value")));
    }

    if (isBlank(password)) {
      throw NestedExceptionUtils.hintWithExplanationException(
          format("Configure password for %s container registry connector", config.getRegistryType().getValue()),
          format("Password is blank for '%s' container registry but is required", config.getRegistryType().getValue()),
          new InvalidArgumentsException(Pair.of("password", "Null or blank value")));
    }
  }

  protected void validateSettings(TasContainerArtifactConfig config, String registryUrl) {
    if (isBlank(registryUrl)) {
      throw NestedExceptionUtils.hintWithExplanationException(
          format("Check if connector provided %s is properly configured", config.getRegistryType().getValue()),
          format("Registry url is '%s' which is an invalid value for '%s' container registry", registryUrl,
              config.getRegistryType().getValue()),
          new InvalidArgumentsException(Pair.of("registry", "Null or blank value")));
    }
  }

  protected TasArtifactCreds populateDockerSettings(String dockerRegistryUrl, String userName, String decryptedSecret) {
    return TasArtifactCreds.builder().url(dockerRegistryUrl).username(userName).password(decryptedSecret).build();
  }

  protected void decryptEntity(DecryptionHelper decryptionHelper, List<DecryptableEntity> decryptableEntities,
      List<EncryptedDataDetail> encryptedDataDetails) {
    if (isNotEmpty(decryptableEntities)) {
      for (DecryptableEntity decryptableEntity : decryptableEntities) {
        decryptionHelper.decrypt(decryptableEntity, encryptedDataDetails);
        ExceptionMessageSanitizer.storeAllSecretsForSanitizing(decryptableEntity, encryptedDataDetails);
      }
    }
  }
}
