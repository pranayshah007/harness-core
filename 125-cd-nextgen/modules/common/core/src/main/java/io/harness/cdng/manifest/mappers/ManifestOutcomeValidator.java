/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.manifest.mappers;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.cdng.manifest.ManifestType.HelmChart;
import static io.harness.cdng.manifest.ManifestType.K8Manifest;
import static io.harness.cdng.manifest.ManifestType.Kustomize;
import static io.harness.cdng.manifest.ManifestType.OpenshiftTemplate;
import static io.harness.common.ParameterFieldHelper.getParameterFieldValue;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.manifest.ManifestStoreType;
import io.harness.cdng.manifest.yaml.ArtifactoryStoreConfig;
import io.harness.cdng.manifest.yaml.CustomRemoteStoreConfig;
import io.harness.cdng.manifest.yaml.GcsStoreConfig;
import io.harness.cdng.manifest.yaml.GitStoreConfig;
import io.harness.cdng.manifest.yaml.HelmChartManifestOutcome;
import io.harness.cdng.manifest.yaml.K8sManifestOutcome;
import io.harness.cdng.manifest.yaml.KustomizeManifestOutcome;
import io.harness.cdng.manifest.yaml.ManifestOutcome;
import io.harness.cdng.manifest.yaml.OpenshiftManifestOutcome;
import io.harness.cdng.manifest.yaml.S3StoreConfig;
import io.harness.cdng.manifest.yaml.harness.HarnessStore;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfig;
import io.harness.delegate.beans.storeconfig.FetchType;
import io.harness.exception.InvalidArgumentsException;
import io.harness.pms.yaml.ParameterField;

import java.util.List;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

@OwnedBy(CDP)
@UtilityClass
public class ManifestOutcomeValidator {
  public void validate(ManifestOutcome manifestOutcome, boolean allowExpression) {
    if (manifestOutcome.getStore() != null) {
      validateStore(
          manifestOutcome.getStore(), manifestOutcome.getType(), manifestOutcome.getIdentifier(), allowExpression);
    }
    validateManifestOutcome(manifestOutcome, allowExpression);
  }

  public void validateStore(StoreConfig store, String manifestKind, String manifestId, boolean allowExpression) {
    if (ManifestStoreType.isInGitSubset(store.getKind())) {
      validateGitStore((GitStoreConfig) store, manifestKind, manifestId, allowExpression);
    } else if (ManifestStoreType.S3.equals(store.getKind())) {
      validateS3Store((S3StoreConfig) store, manifestId, allowExpression);
    } else if (ManifestStoreType.GCS.equals(store.getKind())) {
      validateGcsStore((GcsStoreConfig) store, manifestId, allowExpression);
    } else if (ManifestStoreType.ARTIFACTORY.equals(store.getKind())) {
      validateArtifactoryStore((ArtifactoryStoreConfig) store, manifestId, allowExpression);
    } else if (ManifestStoreType.HARNESS.equals(store.getKind())) {
      validateHarnessStore((HarnessStore) store, manifestId, allowExpression);
    } else if (ManifestStoreType.CUSTOM_REMOTE.equals(store.getKind())) {
      validateCustomRemoteStore((CustomRemoteStoreConfig) store, manifestId, allowExpression);
    }
  }

  private void validateManifestOutcome(ManifestOutcome manifestOutcome, boolean allowExpressions) {
    if (HelmChart.equals(manifestOutcome.getType())) {
      validateHelmChartManifest((HelmChartManifestOutcome) manifestOutcome, allowExpressions);
    } else if (K8Manifest.equals(manifestOutcome.getType())) {
      validateK8Manifest((K8sManifestOutcome) manifestOutcome, allowExpressions);
    } else if (OpenshiftTemplate.equals(manifestOutcome.getType())) {
      validateOpenshiftTemplateManifest((OpenshiftManifestOutcome) manifestOutcome, allowExpressions);
    } else if (Kustomize.equals(manifestOutcome.getType())) {
      validateKustomizeManifest((KustomizeManifestOutcome) manifestOutcome, allowExpressions);
    }
  }

  private void validateHelmChartManifest(HelmChartManifestOutcome helmChartManifest, boolean allowExpression) {
    String manifestStoreKind = helmChartManifest.getStore().getKind();
    if (ManifestStoreType.isInStorageRepository(manifestStoreKind)) {
      if (!hasValue(helmChartManifest.getChartName(), allowExpression)) {
        throw new InvalidArgumentsException(
            Pair.of("chartName", format("required for %s store type", manifestStoreKind)));
      }
    } else {
      if (hasValue(helmChartManifest.getChartName(), allowExpression)) {
        throw new InvalidArgumentsException(
            Pair.of("chartName", format("not allowed for %s store type", manifestStoreKind)));
      }
    }

    if (hasValue(helmChartManifest.getChartVersion(), allowExpression)) {
      if (!ManifestStoreType.isInStorageRepository(manifestStoreKind)) {
        throw new InvalidArgumentsException(
            Pair.of("chartVersion", format("not allowed for %s store", manifestStoreKind)));
      }
    }

    if (!optionalFieldHasValue(helmChartManifest.getValuesPaths())) {
      throw new InvalidArgumentsException(Pair.of("valuesPaths",
          format(
              "Path for values.yaml files for manifest identifier: %s and manifest type: %s is not valid. Check in the values.yaml setup in the manifest configuration to make sure it's not empty",
              helmChartManifest.getIdentifier(), helmChartManifest.getType())));
    }
  }

  private void validateK8Manifest(K8sManifestOutcome k8sManifestOutcome, boolean allowExpression) {
    if (!optionalFieldHasValue(k8sManifestOutcome.getValuesPaths())) {
      throw new InvalidArgumentsException(Pair.of("valuesPaths",
          format(
              "Path for values.yaml files for manifest identifier: %s and manifest type: %s is not valid. Check in the values.yaml setup in the manifest configuration to make sure it's not empty",
              k8sManifestOutcome.getIdentifier(), k8sManifestOutcome.getType())));
    }
  }

  private void validateOpenshiftTemplateManifest(
      OpenshiftManifestOutcome openshiftManifestOutcome, boolean allowExpression) {
    if (!optionalFieldHasValue(openshiftManifestOutcome.getParamsPaths())) {
      throw new InvalidArgumentsException(Pair.of("paramsPaths",
          format(
              "Path for params.yaml files for manifest identifier: %s and manifest type: %s is not valid. Check in the params.yaml setup in the manifest configuration to make sure it's not empty",
              openshiftManifestOutcome.getIdentifier(), openshiftManifestOutcome.getType())));
    }
  }

  private void validateKustomizeManifest(KustomizeManifestOutcome kustomizeManifestOutcome, boolean allowExpression) {
    if (!optionalFieldHasValue(kustomizeManifestOutcome.getPatchesPaths())) {
      throw new InvalidArgumentsException(Pair.of("patchesPaths",
          format(
              "Path for patches.yaml files for manifest identifier: %s and manifest type: %s is not valid. Check in the params.yaml setup in the manifest configuration to make sure it's not empty",
              kustomizeManifestOutcome.getIdentifier(), kustomizeManifestOutcome.getType())));
    }
  }

  private void validateGitStore(GitStoreConfig store, String manifestKind, String manifestId, boolean allowExpression) {
    if (!hasValue(store.getConnectorRef(), allowExpression)) {
      throw new InvalidArgumentsException(
          format("Missing or empty connectorRef in %s store spec for manifest with identifier: %s", store.getKind(),
              manifestId));
    }

    switch (manifestKind) {
      case HelmChart:
      case Kustomize:
        if (!hasValue(store.getFolderPath(), allowExpression)) {
          throw new InvalidArgumentsException(Pair.of("folderPath",
              format("is required for store type '%s' and manifest type '%s' in manifest with identifier: %s",
                  store.getKind(), manifestKind, manifestId)));
        }
        break;

      default:
        if (!hasValue(store.getPaths())) {
          throw new InvalidArgumentsException(Pair.of("paths",
              format("is required for store type '%s' and manifest type '%s' in manifest with identifier: %s",
                  store.getKind(), manifestKind, manifestId)));
        }
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

  private void validateS3Store(S3StoreConfig store, String manifestId, boolean allowExpression) {
    if (!hasValue(store.getConnectorRef(), allowExpression)) {
      throw new InvalidArgumentsException(
          format("Missing or empty connectorRef in S3 store spec for manifest with identifier: %s", manifestId));
    }

    if (!hasValue(store.getRegion(), allowExpression)) {
      throw new InvalidArgumentsException(Pair.of("region", "Cannot be empty or null for S3 store"));
    }

    if (!hasValue(store.getBucketName(), allowExpression)) {
      throw new InvalidArgumentsException(Pair.of("bucketName", "Cannot be empty or null for S3 store"));
    }
  }

  private void validateArtifactoryStore(ArtifactoryStoreConfig store, String manifestId, boolean allowExpression) {
    if (!hasValue(store.getConnectorRef(), allowExpression)) {
      throw new InvalidArgumentsException(format(
          "Missing or empty connectorRef in Artifactory store spec for manifest with identifier: %s", manifestId));
    }
    if (!hasValue(store.getRepositoryName(), allowExpression)) {
      throw new InvalidArgumentsException(Pair.of("repositoryName", "Cannot be empty or null for Artifact store"));
    }
  }

  private void validateGcsStore(GcsStoreConfig store, String manifestId, boolean allowExpression) {
    if (!hasValue(store.getConnectorRef(), allowExpression)) {
      throw new InvalidArgumentsException(
          format("Missing or empty connectorRef in Gcs store spec for manifest with identifier: %s", manifestId));
    }

    if (!hasValue(store.getBucketName(), allowExpression)) {
      throw new InvalidArgumentsException(Pair.of("bucketName", "Cannot be empty or null for Gcs store"));
    }
  }

  private void validateHarnessStore(HarnessStore store, String manifestId, boolean allowExpression) {
    if (hasValue(store.getConnectorReference(), allowExpression)) {
      throw new InvalidArgumentsException(
          format("Non empty connectorRef in Harness store spec for manifest with identifier: %s", manifestId));
    }

    if (!hasValue(store.getFiles())) {
      throw new InvalidArgumentsException(Pair.of("files", "Cannot be empty or null for Harness store"));
    }
  }

  private void validateCustomRemoteStore(CustomRemoteStoreConfig store, String manifestId, boolean allowExpression) {
    if (hasValue(store.getConnectorReference(), allowExpression)) {
      throw new InvalidArgumentsException(
          format("Non empty connectorRef in Custom Remote store spec for manifest with identifier: %s", manifestId));
    }

    if (!hasValue(store.getFilePath(), allowExpression)) {
      throw new InvalidArgumentsException(Pair.of("filePath", "Cannot be empty or null for Custom Remote store"));
    }

    if (!hasValue(store.getExtractionScript(), allowExpression)) {
      throw new InvalidArgumentsException(
          Pair.of("extractionScript", "Cannot be empty or null for Custom Remote store"));
    }
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

  private boolean hasValue(ParameterField<List<String>> parameterField) {
    if (!ParameterField.isNull(parameterField) && isNotEmpty(getParameterFieldValue(parameterField))) {
      List<String> stringList = getParameterFieldValue(parameterField);
      return stringList.stream().noneMatch(StringUtils::isBlank);
    }
    return false;
  }

  private boolean optionalFieldHasValue(ParameterField<List<String>> parameterField) {
    if (!ParameterField.isNull(parameterField) && isNotEmpty(getParameterFieldValue(parameterField))) {
      List<String> stringList = getParameterFieldValue(parameterField);
      return stringList.stream().noneMatch(StringUtils::isBlank);
    }
    return true;
  }
}
