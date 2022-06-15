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
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.manifest.ManifestStoreType;
import io.harness.cdng.manifest.yaml.GitStoreConfig;
import io.harness.cdng.manifest.yaml.harness.HarnessStore;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfig;
import io.harness.delegate.beans.storeconfig.FetchType;
import io.harness.exception.InvalidArgumentsException;
import io.harness.pms.yaml.ParameterField;

import java.util.List;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.tuple.Pair;

@OwnedBy(CDP)
@UtilityClass
public class ConfigFileOutcomeValidator {
  public static void validateStore(String configFileIdentifier, StoreConfig store, boolean allowExpression) {
    if (ManifestStoreType.isInGitSubset(store.getKind())) {
      validateGitStoreConfig(configFileIdentifier, (GitStoreConfig) store, allowExpression);
    }

    if (HARNESS_STORE_TYPE.equals(store.getKind())) {
      validateHarnessStore(configFileIdentifier, (HarnessStore) store, allowExpression);
    }
  }

  private static void validateGitStoreConfig(
      String configFileIdentifier, GitStoreConfig store, boolean allowExpression) {
    if (!hasValue(store.getConnectorReference(), allowExpression)) {
      throw new InvalidArgumentsException(
          format("Missing or empty connectorRef in %s store spec for configFile with identifier: %s", store.getKind(),
              configFileIdentifier));
    }

    if (FetchType.BRANCH == store.getGitFetchType()) {
      if (hasValue(store.getCommitId(), allowExpression)) {
        throw new InvalidArgumentsException(Pair.of("commitId", "Not allowed for gitFetchType: Branch"));
      }

      if (!hasValue(store.getBranch(), allowExpression)) {
        throw new InvalidArgumentsException(Pair.of("branch", "Cannot be empty or null for gitFetchType: Branch"));
      }
    }

    if (FetchType.COMMIT == store.getGitFetchType()) {
      if (hasValue(store.getBranch(), allowExpression)) {
        throw new InvalidArgumentsException(Pair.of("branch", "Not allowed for gitFetchType: Commit"));
      }

      if (!hasValue(store.getCommitId(), allowExpression)) {
        throw new InvalidArgumentsException(Pair.of("commitId", "Cannot be empty or null for gitFetchType: Commit"));
      }
    }
  }

  private static void validateHarnessStore(String configFileIdentifier, HarnessStore store, boolean allowExpression) {
    List<ParameterField<String>> fileReferences = store.getFileReferences();

    if (isEmpty(fileReferences)) {
      throw new InvalidArgumentsException(
          format("Missing or empty fileReferences in %s store for configFile with identifier: %s", store.getKind(),
              configFileIdentifier));
    }

    fileReferences.forEach(fileReference -> {
      if (!hasValue(fileReference, allowExpression)) {
        throw new InvalidArgumentsException(
            format("Missing or empty one of fileReferences in %s store spec for configFile with identifier: %s",
                store.getKind(), configFileIdentifier));
      }
    });
  }

  private boolean hasValue(ParameterField<String> parameterField, boolean allowExpression) {
    if (ParameterField.isNull(parameterField)) {
      return false;
    }

    if (allowExpression && parameterField.isExpression()) {
      return true;
    }

    return isNotEmpty(getParameterFieldValue(parameterField));
  }
}
