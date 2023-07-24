/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cdng.manifest.delegate;
import static io.harness.common.ParameterFieldHelper.getParameterFieldValue;
import static io.harness.data.structure.UUIDGenerator.convertBase64UuidToCanonicalForm;

import static java.lang.String.format;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.beans.FeatureName;
import io.harness.beans.FileReference;
import io.harness.cdng.CDStepHelper;
import io.harness.cdng.featureFlag.CDFeatureFlagHelper;
import io.harness.cdng.k8s.K8sEntityHelper;
import io.harness.cdng.manifest.ManifestStoreType;
import io.harness.cdng.manifest.ManifestType;
import io.harness.cdng.manifest.yaml.CustomRemoteStoreConfig;
import io.harness.cdng.manifest.yaml.GcsStoreConfig;
import io.harness.cdng.manifest.yaml.GitStoreConfig;
import io.harness.cdng.manifest.yaml.HelmChartManifestOutcome;
import io.harness.cdng.manifest.yaml.HelmManifestCommandFlag;
import io.harness.cdng.manifest.yaml.HttpStoreConfig;
import io.harness.cdng.manifest.yaml.K8sManifestOutcome;
import io.harness.cdng.manifest.yaml.KustomizeManifestOutcome;
import io.harness.cdng.manifest.yaml.ManifestOutcome;
import io.harness.cdng.manifest.yaml.OciHelmChartConfig;
import io.harness.cdng.manifest.yaml.OpenshiftManifestOutcome;
import io.harness.cdng.manifest.yaml.S3StoreConfig;
import io.harness.cdng.manifest.yaml.harness.HarnessStore;
import io.harness.cdng.manifest.yaml.kinds.KustomizeManifestCommandFlag;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfig;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpConnectorDTO;
import io.harness.delegate.beans.connector.helm.HttpHelmConnectorDTO;
import io.harness.delegate.beans.connector.helm.OciHelmConnectorDTO;
import io.harness.delegate.beans.storeconfig.CustomRemoteStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.GcsHelmStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.HttpHelmStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.LocalFileStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.OciHelmStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.S3HelmStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.StoreDelegateConfig;
import io.harness.delegate.task.helm.HelmCommandFlag;
import io.harness.delegate.task.k8s.HelmChartManifestDelegateConfig;
import io.harness.delegate.task.k8s.K8sManifestDelegateConfig;
import io.harness.delegate.task.k8s.KustomizeManifestDelegateConfig;
import io.harness.delegate.task.k8s.ManifestDelegateConfig;
import io.harness.delegate.task.k8s.OpenshiftManifestDelegateConfig;
import io.harness.filestore.dto.node.FileStoreNodeDTO;
import io.harness.filestore.service.FileStoreService;
import io.harness.helm.HelmSubCommandType;
import io.harness.k8s.model.HelmVersion;
import io.harness.manifest.CustomManifestSource;
import io.harness.ng.core.NGAccess;
import io.harness.ng.core.filestore.NGFileType;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.yaml.ParameterField;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_K8S})
@Singleton
@OwnedBy(HarnessTeam.CDP)
public class K8sManifestDelegateMapper {
  public static final String MANIFEST_OUTCOME_INCOMPATIBLE_ERROR_MESSAGE =
      "Incompatible manifest store type. Cannot convert manifest outcome to HelmChartManifestOutcome.";
  @Inject private K8sEntityHelper k8sEntityHelper;
  @Inject private CDFeatureFlagHelper cdFeatureFlagHelper;
  @Inject private CDStepHelper cdStepHelper;
  @Inject private FileStoreService fileStoreService;

  public ManifestDelegateConfig getManifestDelegateConfig(ManifestOutcome manifestOutcome, Ambiance ambiance) {
    switch (manifestOutcome.getType()) {
      case ManifestType.K8Manifest:
        K8sManifestOutcome k8sManifestOutcome = (K8sManifestOutcome) manifestOutcome;
        return K8sManifestDelegateConfig.builder()
            .storeDelegateConfig(getStoreDelegateConfig(
                k8sManifestOutcome.getStore(), ambiance, manifestOutcome, manifestOutcome.getType()))
            .build();

      case ManifestType.HelmChart:
        HelmChartManifestOutcome helmChartManifestOutcome = (HelmChartManifestOutcome) manifestOutcome;
        HelmVersion helmVersion =
            getHelmVersionBasedOnFF(helmChartManifestOutcome.getHelmVersion(), AmbianceUtils.getAccountId(ambiance));
        return HelmChartManifestDelegateConfig.builder()
            .storeDelegateConfig(getStoreDelegateConfig(helmChartManifestOutcome.getStore(), ambiance, manifestOutcome,
                manifestOutcome.getType() + " manifest"))
            .chartName(getParameterFieldValue(helmChartManifestOutcome.getChartName()))
            .chartVersion(getParameterFieldValue(helmChartManifestOutcome.getChartVersion()))
            .helmVersion(helmVersion)
            .helmCommandFlag(getDelegateHelmCommandFlag(helmChartManifestOutcome.getCommandFlags()))
            .useCache(helmVersion != HelmVersion.V2
                && !cdFeatureFlagHelper.isEnabled(
                    AmbianceUtils.getAccountId(ambiance), FeatureName.DISABLE_HELM_REPO_YAML_CACHE))
            .checkIncorrectChartVersion(true)
            .useRepoFlags(helmVersion != HelmVersion.V2)
            .deleteRepoCacheDir(helmVersion != HelmVersion.V2)
            .skipApplyHelmDefaultValues(cdFeatureFlagHelper.isEnabled(
                AmbianceUtils.getAccountId(ambiance), FeatureName.CDP_SKIP_DEFAULT_VALUES_YAML_NG))
            .subChartPath(getParameterFieldValue(helmChartManifestOutcome.getSubChartPath()))
            .ignoreResponseCode(cdFeatureFlagHelper.isEnabled(AmbianceUtils.getAccountId(ambiance),
                FeatureName.CDS_USE_HTTP_CHECK_IGNORE_RESPONSE_INSTEAD_OF_SOCKET_NG))
            .build();

      case ManifestType.Kustomize:
        KustomizeManifestOutcome kustomizeManifestOutcome = (KustomizeManifestOutcome) manifestOutcome;
        StoreConfig storeConfig = kustomizeManifestOutcome.getStore();
        if (ManifestStoreType.HARNESS.equals(storeConfig.getKind())) {
          LocalFileStoreDelegateConfig localFileStoreDelegateConfig =
              (LocalFileStoreDelegateConfig) getStoreDelegateConfig(
                  storeConfig, ambiance, kustomizeManifestOutcome, manifestOutcome.getType() + " manifest");
          return KustomizeManifestDelegateConfig.builder()
              .storeDelegateConfig(localFileStoreDelegateConfig)
              .pluginPath(getParameterFieldValue(kustomizeManifestOutcome.getPluginPath()))
              .kustomizeDirPath(".")
              .kustomizeYamlFolderPath(kustomizeYamlFolderPathNotNullCheck(kustomizeManifestOutcome)
                      ? getParameterFieldValue(
                          getParameterFieldValue(kustomizeManifestOutcome.getOverlayConfiguration())
                              .getKustomizeYamlFolderPath())
                      : null)
              .commandFlags(getKustomizeCmdFlags(kustomizeManifestOutcome.getCommandFlags()))
              .build();
        } else if (!ManifestStoreType.isInGitSubset(storeConfig.getKind())) {
          throw new UnsupportedOperationException(
              format("Kustomize Manifest is not supported for store type: [%s]", storeConfig.getKind()));
        }
        GitStoreConfig gitStoreConfig = (GitStoreConfig) storeConfig;
        return KustomizeManifestDelegateConfig.builder()
            .storeDelegateConfig(getStoreDelegateConfig(kustomizeManifestOutcome.getStore(), ambiance, manifestOutcome,
                manifestOutcome.getType() + " manifest"))
            .kustomizeYamlFolderPath(kustomizeYamlFolderPathNotNullCheck(kustomizeManifestOutcome)
                    ? getParameterFieldValue(getParameterFieldValue(kustomizeManifestOutcome.getOverlayConfiguration())
                                                 .getKustomizeYamlFolderPath())
                    : null)
            .pluginPath(getParameterFieldValue(kustomizeManifestOutcome.getPluginPath()))
            .kustomizeDirPath(getParameterFieldValue(gitStoreConfig.getFolderPath()))
            .commandFlags(getKustomizeCmdFlags(kustomizeManifestOutcome.getCommandFlags()))
            .build();

      case ManifestType.OpenshiftTemplate:
        OpenshiftManifestOutcome openshiftManifestOutcome = (OpenshiftManifestOutcome) manifestOutcome;
        return OpenshiftManifestDelegateConfig.builder()
            .storeDelegateConfig(getStoreDelegateConfig(openshiftManifestOutcome.getStore(), ambiance, manifestOutcome,
                manifestOutcome.getType() + " manifest"))
            .build();

      default:
        throw new UnsupportedOperationException(format("Unsupported Manifest type: [%s]", manifestOutcome.getType()));
    }
  }

  public HelmVersion getHelmVersionBasedOnFF(HelmVersion helmVersion, String accountId) {
    if (helmVersion == HelmVersion.V2) {
      return helmVersion;
    }
    return cdFeatureFlagHelper.isEnabled(accountId, FeatureName.HELM_VERSION_3_8_0) == true ? HelmVersion.V380
                                                                                            : HelmVersion.V3;
  }

  public StoreDelegateConfig getStoreDelegateConfig(
      StoreConfig storeConfig, Ambiance ambiance, ManifestOutcome manifestOutcome, String validationErrorMessage) {
    if (ManifestStoreType.isInGitSubset(storeConfig.getKind())) {
      GitStoreConfig gitStoreConfig = (GitStoreConfig) storeConfig;
      ConnectorInfoDTO connectorDTO =
          cdStepHelper.getConnector(getParameterFieldValue(gitStoreConfig.getConnectorRef()), ambiance);
      cdStepHelper.validateManifest(storeConfig.getKind(), connectorDTO, validationErrorMessage);
      List<String> gitFilePaths;
      if (manifestOutcome.getType().equals(ManifestType.Kustomize)) {
        gitFilePaths = getKustomizeManifestBasePath(gitStoreConfig, manifestOutcome);
      } else {
        gitFilePaths = getPathsBasedOnManifest(gitStoreConfig, manifestOutcome.getType());
      }

      return cdStepHelper.getGitStoreDelegateConfig(
          gitStoreConfig, connectorDTO, manifestOutcome, gitFilePaths, ambiance);
    }

    if (ManifestStoreType.HARNESS.equals(storeConfig.getKind())) {
      HarnessStore harnessStore = (HarnessStore) storeConfig;
      String scopedFilePath = harnessStore.getFiles().getValue().get(0);
      NGAccess ngAccess = AmbianceUtils.getNgAccess(ambiance);
      FileReference fileReference = FileReference.of(scopedFilePath, ngAccess.getAccountIdentifier(),
          ngAccess.getOrgIdentifier(), ngAccess.getProjectIdentifier());
      Optional<FileStoreNodeDTO> optionalFileStoreNodeDTO =
          fileStoreService.getWithChildrenByPath(fileReference.getAccountIdentifier(), fileReference.getOrgIdentifier(),
              fileReference.getProjectIdentifier(), fileReference.getPath(), false);
      if (optionalFileStoreNodeDTO.isPresent()) {
        FileStoreNodeDTO manifestFileDirectory = optionalFileStoreNodeDTO.get();
        if (NGFileType.FOLDER.equals(manifestFileDirectory.getType())) {
          return LocalFileStoreDelegateConfig.builder()
              .folder(manifestFileDirectory.getName())
              .filePaths(Arrays.asList(manifestFileDirectory.getPath().substring(1)))
              .manifestType(manifestOutcome.getType())
              .manifestIdentifier(manifestOutcome.getIdentifier())
              .build();
        }
      }
      return LocalFileStoreDelegateConfig.builder()
          .filePaths(harnessStore.getFiles().getValue())
          .manifestType(manifestOutcome.getType())
          .manifestIdentifier(manifestOutcome.getIdentifier())
          .build();
    }

    if (ManifestStoreType.CUSTOM_REMOTE.equals(storeConfig.getKind())) {
      CustomRemoteStoreConfig customRemoteStoreConfig = (CustomRemoteStoreConfig) storeConfig;

      return CustomRemoteStoreDelegateConfig.builder()
          .customManifestSource(CustomManifestSource.builder()
                                    .filePaths(Arrays.asList(customRemoteStoreConfig.getFilePath().getValue()))
                                    .script(customRemoteStoreConfig.getExtractionScript().getValue())
                                    .accountId(AmbianceUtils.getAccountId(ambiance))
                                    .build())
          .build();
    }

    if (ManifestStoreType.HTTP.equals(storeConfig.getKind())) {
      HttpStoreConfig httpStoreConfig = (HttpStoreConfig) storeConfig;
      ConnectorInfoDTO helmConnectorDTO =
          cdStepHelper.getConnector(getParameterFieldValue(httpStoreConfig.getConnectorRef()), ambiance);
      cdStepHelper.validateManifest(storeConfig.getKind(), helmConnectorDTO, validationErrorMessage);
      Preconditions.checkArgument(
          manifestOutcome instanceof HelmChartManifestOutcome, MANIFEST_OUTCOME_INCOMPATIBLE_ERROR_MESSAGE);

      return HttpHelmStoreDelegateConfig.builder()
          .repoName(getRepoName(ambiance, helmConnectorDTO.getIdentifier(), (HelmChartManifestOutcome) manifestOutcome))
          .repoDisplayName(helmConnectorDTO.getName())
          .httpHelmConnector((HttpHelmConnectorDTO) helmConnectorDTO.getConnectorConfig())
          .encryptedDataDetails(
              k8sEntityHelper.getEncryptionDataDetails(helmConnectorDTO, AmbianceUtils.getNgAccess(ambiance)))
          .build();
    }

    if (ManifestStoreType.OCI.equals(storeConfig.getKind())) {
      OciHelmChartConfig ociStoreConfig = (OciHelmChartConfig) storeConfig;
      ConnectorInfoDTO helmConnectorDTO =
          cdStepHelper.getConnector(getParameterFieldValue(ociStoreConfig.getConnectorReference()), ambiance);
      cdStepHelper.validateManifest(storeConfig.getKind(), helmConnectorDTO, validationErrorMessage);
      Preconditions.checkArgument(
          manifestOutcome instanceof HelmChartManifestOutcome, MANIFEST_OUTCOME_INCOMPATIBLE_ERROR_MESSAGE);

      return OciHelmStoreDelegateConfig.builder()
          .repoName(getRepoName(ambiance, helmConnectorDTO.getIdentifier(), (HelmChartManifestOutcome) manifestOutcome))
          .basePath(getParameterFieldValue(ociStoreConfig.getBasePath()))
          .repoDisplayName(helmConnectorDTO.getName())
          .ociHelmConnector((OciHelmConnectorDTO) helmConnectorDTO.getConnectorConfig())
          .encryptedDataDetails(
              k8sEntityHelper.getEncryptionDataDetails(helmConnectorDTO, AmbianceUtils.getNgAccess(ambiance)))
          .helmOciEnabled(true)
          .build();
    }

    if (ManifestStoreType.S3.equals(storeConfig.getKind())) {
      S3StoreConfig s3StoreConfig = (S3StoreConfig) storeConfig;
      ConnectorInfoDTO awsConnectorDTO =
          cdStepHelper.getConnector(getParameterFieldValue(s3StoreConfig.getConnectorRef()), ambiance);
      cdStepHelper.validateManifest(storeConfig.getKind(), awsConnectorDTO, validationErrorMessage);
      Preconditions.checkArgument(
          manifestOutcome instanceof HelmChartManifestOutcome, MANIFEST_OUTCOME_INCOMPATIBLE_ERROR_MESSAGE);

      return S3HelmStoreDelegateConfig.builder()
          .repoName(getRepoName(ambiance, awsConnectorDTO.getIdentifier(), (HelmChartManifestOutcome) manifestOutcome))
          .repoDisplayName(awsConnectorDTO.getName())
          .bucketName(getParameterFieldValue(s3StoreConfig.getBucketName()))
          .region(getParameterFieldValue(s3StoreConfig.getRegion()))
          .folderPath(getParameterFieldValue(s3StoreConfig.getFolderPath()))
          .awsConnector((AwsConnectorDTO) awsConnectorDTO.getConnectorConfig())
          .encryptedDataDetails(
              k8sEntityHelper.getEncryptionDataDetails(awsConnectorDTO, AmbianceUtils.getNgAccess(ambiance)))
          .useLatestChartMuseumVersion(cdFeatureFlagHelper.isEnabled(
              AmbianceUtils.getAccountId(ambiance), FeatureName.USE_LATEST_CHARTMUSEUM_VERSION))
          .build();
    }

    if (ManifestStoreType.GCS.equals(storeConfig.getKind())) {
      GcsStoreConfig gcsStoreConfig = (GcsStoreConfig) storeConfig;
      ConnectorInfoDTO gcpConnectorDTO =
          cdStepHelper.getConnector(getParameterFieldValue(gcsStoreConfig.getConnectorRef()), ambiance);
      cdStepHelper.validateManifest(storeConfig.getKind(), gcpConnectorDTO, validationErrorMessage);
      Preconditions.checkArgument(
          manifestOutcome instanceof HelmChartManifestOutcome, MANIFEST_OUTCOME_INCOMPATIBLE_ERROR_MESSAGE);

      return GcsHelmStoreDelegateConfig.builder()
          .repoName(getRepoName(ambiance, gcpConnectorDTO.getIdentifier(), (HelmChartManifestOutcome) manifestOutcome))
          .repoDisplayName(gcpConnectorDTO.getName())
          .bucketName(getParameterFieldValue(gcsStoreConfig.getBucketName()))
          .folderPath(getParameterFieldValue(gcsStoreConfig.getFolderPath()))
          .gcpConnector((GcpConnectorDTO) gcpConnectorDTO.getConnectorConfig())
          .encryptedDataDetails(
              k8sEntityHelper.getEncryptionDataDetails(gcpConnectorDTO, AmbianceUtils.getNgAccess(ambiance)))
          .useLatestChartMuseumVersion(cdFeatureFlagHelper.isEnabled(
              AmbianceUtils.getAccountId(ambiance), FeatureName.USE_LATEST_CHARTMUSEUM_VERSION))
          .build();
    }

    throw new UnsupportedOperationException(format("Unsupported Store Config type: [%s]", storeConfig.getKind()));
  }

  public boolean kustomizeYamlFolderPathNotNullCheck(KustomizeManifestOutcome kustomizeManifestOutcome) {
    return ParameterField.isNotNull(kustomizeManifestOutcome.getOverlayConfiguration())
        && ParameterField.isNotNull(
            getParameterFieldValue(kustomizeManifestOutcome.getOverlayConfiguration()).getKustomizeYamlFolderPath());
  }

  public List<String> getKustomizeManifestBasePath(GitStoreConfig gitStoreConfig, ManifestOutcome manifestOutcome) {
    List<String> paths = new ArrayList<>();
    KustomizeManifestOutcome kustomizeManifestOutcome = (KustomizeManifestOutcome) manifestOutcome;
    if (kustomizeYamlFolderPathNotNullCheck(kustomizeManifestOutcome)) {
      paths.add(getParameterFieldValue(gitStoreConfig.getFolderPath()));
    } else {
      paths.add("/");
    }
    return paths;
  }

  public List<String> getPathsBasedOnManifest(GitStoreConfig gitstoreConfig, String manifestType) {
    List<String> paths = new ArrayList<>();
    switch (manifestType) {
      case ManifestType.HelmChart:
        paths.add(getParameterFieldValue(gitstoreConfig.getFolderPath()));
        break;
      case ManifestType.Kustomize:
        // Set as repository root
        paths.add("/");
        break;

      default:
        paths.addAll(getParameterFieldValue(gitstoreConfig.getPaths()));
    }

    return paths;
  }

  public String getRepoName(Ambiance ambiance, String connectorId, HelmChartManifestOutcome manifestOutcome) {
    /*
      going forward, we will be creating default cache based on connectorId unless FF is enabled
      in which case we will create based on executionId
      details here: https://harness.atlassian.net/wiki/spaces/CDP/pages/21134344193/Helm+FFs+cleanup
     */
    boolean isNotHelmV2 = manifestOutcome != null && HelmVersion.V2 != manifestOutcome.getHelmVersion();
    boolean useCache = isNotHelmV2
        && !cdFeatureFlagHelper.isEnabled(
            AmbianceUtils.getAccountId(ambiance), FeatureName.DISABLE_HELM_REPO_YAML_CACHE);
    String repoId = useCache ? connectorId : ambiance.getPlanExecutionId();

    return convertBase64UuidToCanonicalForm(repoId);
  }

  private HelmCommandFlag getDelegateHelmCommandFlag(List<HelmManifestCommandFlag> commandFlags) {
    if (commandFlags == null) {
      return HelmCommandFlag.builder().valueMap(new HashMap<>()).build();
    }

    Map<HelmSubCommandType, String> commandsValueMap = new HashMap<>();
    for (HelmManifestCommandFlag commandFlag : commandFlags) {
      commandsValueMap.put(commandFlag.getCommandType().getSubCommandType(), commandFlag.getFlag().getValue());
    }

    return HelmCommandFlag.builder().valueMap(commandsValueMap).build();
  }

  private Map<String, String> getKustomizeCmdFlags(List<KustomizeManifestCommandFlag> kustomizeManifestCommandFlags) {
    if (kustomizeManifestCommandFlags != null) {
      Map<String, String> commandFlags = new HashMap<>();
      for (KustomizeManifestCommandFlag kustomizeManifestCommandFlag : kustomizeManifestCommandFlags) {
        commandFlags.put(kustomizeManifestCommandFlag.getCommandType().toKustomizeCommandName(),
            getParameterFieldValue(kustomizeManifestCommandFlag.getFlag()));
      }
      return commandFlags;
    }
    return null;
  }
}
