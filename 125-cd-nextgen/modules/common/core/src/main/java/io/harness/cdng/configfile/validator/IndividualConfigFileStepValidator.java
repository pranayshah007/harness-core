/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.configfile.validator;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.cdng.manifest.yaml.harness.HarnessStoreConstants.HARNESS_STORE_TYPE;
import static io.harness.common.ParameterFieldHelper.getParameterFieldValue;
import static io.harness.common.ParameterFieldHelper.hasListValue;
import static io.harness.common.ParameterFieldHelper.hasStringValue;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.utils.Utils.isNotInstanceOf;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.configfile.ConfigFileAttributes;
import io.harness.cdng.manifest.ManifestStoreType;
import io.harness.cdng.manifest.yaml.GitStoreConfig;
import io.harness.cdng.manifest.yaml.harness.HarnessStore;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfig;
import io.harness.delegate.beans.storeconfig.FetchType;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.yaml.ParameterField;

import java.util.List;
import java.util.Set;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.tuple.Pair;

@OwnedBy(CDP)
@UtilityClass
public class IndividualConfigFileStepValidator {
  public static Set<String> validateConfigFileAttributes(
      final String configFileIdentifier, ConfigFileAttributes finalConfigFile, boolean allowExpression) {
    StoreConfig store = finalConfigFile.getStore().getValue().getSpec();
    if (ManifestStoreType.isInGitSubset(store.getKind())) {
      validateGitStoreConfig(configFileIdentifier, (GitStoreConfig) store, allowExpression);
    }

    if (HARNESS_STORE_TYPE.equals(store.getKind())) {
      validateHarnessStore(configFileIdentifier, (HarnessStore) store, allowExpression);
    }
    return store.validateAtRuntime();
  }

  private static void validateGitStoreConfig(
      String configFileIdentifier, GitStoreConfig store, boolean allowExpression) {
    if (!hasStringValue(store.getConnectorReference(), allowExpression)) {
      throw new InvalidArgumentsException(
          format("Missing or empty connectorRef in %s store spec for configFile with identifier: %s", store.getKind(),
              configFileIdentifier));
    }

    if (FetchType.BRANCH == store.getGitFetchType()) {
      if (hasStringValue(store.getCommitId(), allowExpression)) {
        throw new InvalidArgumentsException(Pair.of("commitId", "Not allowed for gitFetchType: Branch"));
      }

      if (!hasStringValue(store.getBranch(), allowExpression)) {
        throw new InvalidArgumentsException(Pair.of("branch", "Cannot be empty or null for gitFetchType: Branch"));
      }
    }

    if (FetchType.COMMIT == store.getGitFetchType()) {
      if (hasStringValue(store.getBranch(), allowExpression)) {
        throw new InvalidArgumentsException(Pair.of("branch", "Not allowed for gitFetchType: Commit"));
      }

      if (!hasStringValue(store.getCommitId(), allowExpression)) {
        throw new InvalidArgumentsException(Pair.of("commitId", "Cannot be empty or null for gitFetchType: Commit"));
      }
    }
  }

  private static void validateHarnessStore(String configFileIdentifier, HarnessStore store, boolean allowExpression) {
    ParameterField<List<String>> files = store.getFiles();
    ParameterField<List<String>> secretFiles = store.getSecretFiles();
    validateFiles(files, configFileIdentifier, allowExpression);
    validateSecretFiles(secretFiles, configFileIdentifier, allowExpression);
  }

  private static void validateFiles(
      ParameterField<List<String>> files, final String configFileIdentifier, boolean allowExpression) {
    if (isNotInstanceOf(String.class, getParameterFieldValue(files))) {
      if (hasListValue(files, allowExpression)) {
        List<String> filesValue = getParameterFieldValue(files);
        if (isNotEmpty(filesValue)) {
          filesValue.forEach(file -> {
            if (isEmpty(file)) {
              throw new InvalidRequestException(format(
                  "Config file reference cannot be null or empty, ConfigFile identifier: %s", configFileIdentifier));
            }
          });
        }
      }
    }
  }

  private static void validateSecretFiles(
      ParameterField<List<String>> secretFiles, final String configFileIdentifier, boolean allowExpression) {
    if (isNotInstanceOf(String.class, getParameterFieldValue(secretFiles))) {
      if (hasListValue(secretFiles, allowExpression)) {
        List<String> secretFilesValue = getParameterFieldValue(secretFiles);
        if (isNotEmpty(secretFilesValue)) {
          secretFilesValue.forEach(secretFileRef -> {
            if (isEmpty(secretFileRef)) {
              throw new InvalidRequestException(
                  format("Config file secret reference cannot be null or empty, ConfigFile identifier: %s",
                      configFileIdentifier));
            }
          });
        }
      }
    }
  }
}
