/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.manifest.resources;

import static io.harness.common.ParameterFieldHelper.getParameterFieldValue;
import static io.harness.connector.ConnectorModule.DEFAULT_CONNECTOR_SERVICE;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;
import static io.harness.data.structure.UUIDGenerator.convertBase64UuidToCanonicalForm;
import static io.harness.utils.DelegateOwner.getNGTaskSetupAbstractionsWithOwner;

import static java.lang.String.format;

import io.harness.beans.DelegateTaskRequest;
import io.harness.beans.FeatureName;
import io.harness.beans.IdentifierRef;
import io.harness.cdng.featureFlag.CDFeatureFlagHelper;
import io.harness.cdng.k8s.K8sEntityHelper;
import io.harness.cdng.manifest.ManifestStoreType;
import io.harness.cdng.manifest.mappers.ManifestOutcomeMapper;
import io.harness.cdng.manifest.resources.dtos.HelmChartResponseDTO;
import io.harness.cdng.manifest.resources.dtos.HelmManifestInternalDTO;
import io.harness.cdng.manifest.yaml.AzureRepoStore;
import io.harness.cdng.manifest.yaml.BitbucketStore;
import io.harness.cdng.manifest.yaml.CustomRemoteStoreConfig;
import io.harness.cdng.manifest.yaml.GcsStoreConfig;
import io.harness.cdng.manifest.yaml.GitLabStore;
import io.harness.cdng.manifest.yaml.GitStore;
import io.harness.cdng.manifest.yaml.GithubStore;
import io.harness.cdng.manifest.yaml.HelmChartManifestOutcome;
import io.harness.cdng.manifest.yaml.HttpStoreConfig;
import io.harness.cdng.manifest.yaml.ManifestAttributes;
import io.harness.cdng.manifest.yaml.OciHelmChartConfig;
import io.harness.cdng.manifest.yaml.OciHelmChartStoreGenericConfig;
import io.harness.cdng.manifest.yaml.S3StoreConfig;
import io.harness.cdng.manifest.yaml.harness.HarnessStore;
import io.harness.cdng.manifest.yaml.kinds.HelmChartManifest;
import io.harness.cdng.manifest.yaml.oci.OciHelmChartStoreConfig;
import io.harness.cdng.manifest.yaml.oci.OciHelmChartStoreConfigType;
import io.harness.cdng.manifest.yaml.oci.OciHelmChartStoreConfigWrapper;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfig;
import io.harness.connector.ConnectorInfoDTO;
import io.harness.connector.ConnectorResponseDTO;
import io.harness.connector.services.ConnectorService;
import io.harness.delegate.beans.DelegateResponseData;
import io.harness.delegate.beans.connector.ConnectorType;
import io.harness.delegate.beans.connector.awsconnector.AwsConnectorDTO;
import io.harness.delegate.beans.connector.gcpconnector.GcpConnectorDTO;
import io.harness.delegate.beans.connector.helm.HttpHelmConnectorDTO;
import io.harness.delegate.beans.connector.helm.OciHelmConnectorDTO;
import io.harness.delegate.beans.connector.helm.OciHelmDockerApiListTagsTaskParams;
import io.harness.delegate.beans.connector.helm.OciHelmDockerApiListTagsTaskResponse;
import io.harness.delegate.beans.storeconfig.GcsHelmStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.HttpHelmStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.S3HelmStoreDelegateConfig;
import io.harness.delegate.beans.storeconfig.StoreDelegateConfig;
import io.harness.delegate.task.helm.HelmFetchChartVersionRequestNG;
import io.harness.delegate.task.helm.HelmFetchChartVersionResponse;
import io.harness.delegate.task.k8s.HelmChartManifestDelegateConfig;
import io.harness.exception.DelegateServiceDriverException;
import io.harness.exception.InvalidRequestException;
import io.harness.exception.WingsException;
import io.harness.exception.exceptionmanager.ExceptionManager;
import io.harness.k8s.model.HelmVersion;
import io.harness.ng.core.BaseNGAccess;
import io.harness.ng.core.NGAccess;
import io.harness.ng.core.service.services.ServiceEntityService;
import io.harness.pms.yaml.ParameterField;
import io.harness.pms.yaml.YamlNode;
import io.harness.pms.yaml.YamlUtils;
import io.harness.service.DelegateGrpcClientWrapper;
import io.harness.utils.IdentifierRefHelper;

import software.wings.beans.TaskType;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class HelmChartServiceImpl implements HelmChartService {
  public static final long DEFAULT_TIMEOUT = 6000L;
  public static final List<ConnectorType> VALID_CONNECTOR_TYPES =
      Arrays.asList(ConnectorType.AWS, ConnectorType.GCP, ConnectorType.HTTP_HELM_REPO, ConnectorType.OCI_HELM_REPO);
  @Inject private ServiceEntityService serviceEntityService;
  @Inject private K8sEntityHelper k8sEntityHelper;
  @Inject private CDFeatureFlagHelper cdFeatureFlagHelper;
  @Inject private DelegateGrpcClientWrapper delegateGrpcClientWrapper;
  @Inject private ExceptionManager exceptionManager;
  @Named(DEFAULT_CONNECTOR_SERVICE) @Inject private ConnectorService connectorService;
  private static final int PAGE_SIZE = 100000;

  private HelmChartResponseDTO getHelmChartVersionDetails(String accountId, String orgId, String projectId,
      HelmChartManifestOutcome helmChartManifestOutcome, String connectorId, String chartName, String region,
      String bucketName, String folderPath, String lastTag) {
    NGAccess ngAccess =
        BaseNGAccess.builder().accountIdentifier(accountId).orgIdentifier(orgId).projectIdentifier(projectId).build();

    Map<String, String> owner = getNGTaskSetupAbstractionsWithOwner(
        ngAccess.getAccountIdentifier(), ngAccess.getOrgIdentifier(), ngAccess.getProjectIdentifier());
    Map<String, String> abstractions = new HashMap<>(owner);
    if (ngAccess.getOrgIdentifier() != null) {
      abstractions.put("orgIdentifier", ngAccess.getOrgIdentifier());
    }
    if (ngAccess.getProjectIdentifier() != null && ngAccess.getOrgIdentifier() != null) {
      abstractions.put("projectIdentifier", ngAccess.getProjectIdentifier());
    }
    abstractions.put("ng", "true");
    abstractions.put("owner", ngAccess.getOrgIdentifier() + "/" + ngAccess.getProjectIdentifier());

    DelegateTaskRequest delegateTaskRequest = null;
    String taskTypeName = null;

    if (helmChartManifestOutcome.getStore() instanceof OciHelmChartConfig) {
      IdentifierRef connectorRef = IdentifierRefHelper.getIdentifierRef(connectorId, accountId, orgId, projectId);
      ConnectorInfoDTO helmConnector = getConnector(connectorRef.getAccountIdentifier(),
          connectorRef.getOrgIdentifier(), connectorRef.getProjectIdentifier(), connectorRef.getIdentifier());
      OciHelmConnectorDTO ociHelmConnectorDTO = (OciHelmConnectorDTO) helmConnector.getConnectorConfig();

      OciHelmDockerApiListTagsTaskParams ociHelmDockerApiListTagsTaskParams =
          OciHelmDockerApiListTagsTaskParams.builder()
              .ociHelmConnector(ociHelmConnectorDTO)
              .encryptionDetails(k8sEntityHelper.getEncryptionDataDetails(helmConnector, ngAccess))
              .chartName(chartName)
              .pageSize(PAGE_SIZE)
              .lastTag(lastTag)
              .build();

      taskTypeName = TaskType.OCI_HELM_DOCKER_API_LIST_TAGS_TASK_NG.name();
      delegateTaskRequest = DelegateTaskRequest.builder()
                                .accountId(ngAccess.getAccountIdentifier())
                                .taskType(taskTypeName)
                                .taskParameters(ociHelmDockerApiListTagsTaskParams)
                                .executionTimeout(java.time.Duration.ofSeconds(DEFAULT_TIMEOUT))
                                .taskSetupAbstractions(abstractions)
                                .taskSelectors(ociHelmConnectorDTO.getDelegateSelectors())
                                .build();
    } else {
      HelmVersion helmVersion = getHelmVersionBasedOnFF(helmChartManifestOutcome.getHelmVersion(), accountId);

      StoreDelegateConfig storeDelegateConfig = getStoreDelegateConfig(
          helmChartManifestOutcome, accountId, orgId, projectId, connectorId, region, bucketName, folderPath);

      HelmChartManifestDelegateConfig helmChartManifestDelegateConfig =
          HelmChartManifestDelegateConfig.builder()
              .storeDelegateConfig(storeDelegateConfig)
              .chartName(
                  isNotEmpty(chartName) ? chartName : getParameterFieldValue(helmChartManifestOutcome.getChartName()))
              .chartVersion(getParameterFieldValue(helmChartManifestOutcome.getChartVersion()))
              .helmVersion(helmVersion)
              .useCache(helmVersion != HelmVersion.V2
                  && !cdFeatureFlagHelper.isEnabled(accountId, FeatureName.DISABLE_HELM_REPO_YAML_CACHE))
              .checkIncorrectChartVersion(true)
              .useRepoFlags(helmVersion != HelmVersion.V2)
              .deleteRepoCacheDir(helmVersion != HelmVersion.V2)
              .build();

      HelmFetchChartVersionRequestNG helmFetchChartVersionRequestNG =
          HelmFetchChartVersionRequestNG.builder()
              .helmChartManifestDelegateConfig(helmChartManifestDelegateConfig)
              .build();

      taskTypeName = TaskType.HELM_FETCH_CHART_VERSIONS_TASK_NG.name();
      delegateTaskRequest = DelegateTaskRequest.builder()
                                .accountId(ngAccess.getAccountIdentifier())
                                .taskType(taskTypeName)
                                .taskParameters(helmFetchChartVersionRequestNG)
                                .executionTimeout(java.time.Duration.ofSeconds(DEFAULT_TIMEOUT))
                                .taskSetupAbstractions(abstractions)
                                .taskSelectors(getDelegateSelectors(storeDelegateConfig))
                                .build();
    }

    DelegateResponseData delegateResponseData = null;
    try {
      delegateResponseData = delegateGrpcClientWrapper.executeSyncTaskV2(delegateTaskRequest);
    } catch (DelegateServiceDriverException ex) {
      throw exceptionManager.processException(ex, WingsException.ExecutionContext.MANAGER, log);
    }

    if (helmChartManifestOutcome.getStore() instanceof OciHelmChartConfig) {
      OciHelmDockerApiListTagsTaskResponse ociHelmDockerApiListTagsTaskResponse =
          (OciHelmDockerApiListTagsTaskResponse) delegateResponseData;
      return HelmChartResponseDTO.builder()
          .helmChartVersions(ociHelmDockerApiListTagsTaskResponse.getChartVersions())
          .lastTag(ociHelmDockerApiListTagsTaskResponse.getLastTag())
          .build();
    } else {
      HelmFetchChartVersionResponse helmFetchChartVersionResponse =
          (HelmFetchChartVersionResponse) delegateResponseData;
      List<String> chartVersions = helmFetchChartVersionResponse.getChartVersionsList();

      if (chartVersions == null) {
        log.error("Something has gone wrong; no list of chart versions returned");
      }

      return HelmChartResponseDTO.builder().helmChartVersions(chartVersions).build();
    }
  }

  private StoreConfig getHelmChartStoreConfig(
      String storeType, String connectorId, String region, String bucketName, String folderPath) {
    StoreConfig storeConfig;

    if (!ManifestStoreType.HelmAllRepo.contains(storeType)) {
      throw new InvalidRequestException(
          format("HelmChart store type not recognized! [%s] is not in list of approved store types [%s]", storeType,
              ManifestStoreType.HelmAllRepo));
    }

    switch (storeType) {
      case ManifestStoreType.HTTP: {
        storeConfig = HttpStoreConfig.builder().connectorRef(ParameterField.createValueField(connectorId)).build();
        break;
      }
      case ManifestStoreType.GCS: {
        if (isEmpty(bucketName)) {
          throw new InvalidRequestException("query param bucketName: must not be null");
        }

        if (isEmpty(folderPath)) {
          throw new InvalidRequestException("query param folderPath: must not be null");
        }

        storeConfig = GcsStoreConfig.builder()
                          .connectorRef(ParameterField.createValueField(connectorId))
                          .bucketName(ParameterField.createValueField(bucketName))
                          .folderPath(ParameterField.createValueField(folderPath))
                          .build();
        break;
      }
      case ManifestStoreType.S3: {
        if (isEmpty(region)) {
          throw new InvalidRequestException("query param region: must not be null");
        }

        if (isEmpty(bucketName)) {
          throw new InvalidRequestException("query param bucketName: must not be null");
        }

        if (isEmpty(folderPath)) {
          throw new InvalidRequestException("query param folderPath: must not be null");
        }
        storeConfig = S3StoreConfig.builder()
                          .connectorRef(ParameterField.createValueField(connectorId))
                          .bucketName(ParameterField.createValueField(bucketName))
                          .region(ParameterField.createValueField(region))
                          .folderPath(ParameterField.createValueField(folderPath))
                          .build();
        break;
      }
      case ManifestStoreType.GIT: {
        storeConfig = GitStore.builder().connectorRef(ParameterField.createValueField(connectorId)).build();
        break;
      }
      case ManifestStoreType.GITHUB: {
        storeConfig = GithubStore.builder().connectorRef(ParameterField.createValueField(connectorId)).build();
        break;
      }
      case ManifestStoreType.GITLAB: {
        storeConfig = GitLabStore.builder().connectorRef(ParameterField.createValueField(connectorId)).build();
        break;
      }
      case ManifestStoreType.BITBUCKET: {
        storeConfig = BitbucketStore.builder().connectorRef(ParameterField.createValueField(connectorId)).build();
        break;
      }
      case ManifestStoreType.OCI: {
        OciHelmChartStoreConfig ociHelmChartStoreConfig =
            OciHelmChartStoreGenericConfig.builder().connectorRef(ParameterField.createValueField(connectorId)).build();
        OciHelmChartStoreConfigWrapper ociHelmChartStoreConfigWrapper = OciHelmChartStoreConfigWrapper.builder()
                                                                            .type(OciHelmChartStoreConfigType.GENERIC)
                                                                            .spec(ociHelmChartStoreConfig)
                                                                            .build();
        storeConfig = OciHelmChartConfig.builder()
                          .config(ParameterField.createValueField(ociHelmChartStoreConfigWrapper))
                          .build();
        break;
      }
      case ManifestStoreType.CUSTOM_REMOTE: {
        storeConfig = CustomRemoteStoreConfig.builder().build();
        break;
      }
      case ManifestStoreType.AZURE_REPO: {
        storeConfig = AzureRepoStore.builder().connectorRef(ParameterField.createValueField(connectorId)).build();
        break;
      }
      case ManifestStoreType.HARNESS: {
        storeConfig = HarnessStore.builder().build();
        break;
      }
      default:
        throw new InvalidRequestException(format("HelmChart store type not recognized [%s]", storeType));
    }

    return storeConfig;
  }

  @Override
  public HelmChartResponseDTO getHelmChartVersionDetails(String accountId, String orgId, String projectId,
      String connectorId, String chartName, String region, String bucketName, String folderPath, String lastTag,
      String storeType, String helmVersion) {
    StoreConfig storeConfig = getHelmChartStoreConfig(storeType, connectorId, region, bucketName, folderPath);

    HelmChartManifestOutcome helmChartManifestOutcome = HelmChartManifestOutcome.builder()
                                                            .chartName(ParameterField.createValueField(chartName))
                                                            .chartVersion(ParameterField.<String>builder().build())
                                                            .helmVersion(HelmVersion.fromString(helmVersion))
                                                            .store(storeConfig)
                                                            .build();

    return getHelmChartVersionDetails(accountId, orgId, projectId, helmChartManifestOutcome, connectorId, chartName,
        region, bucketName, folderPath, lastTag);
  }

  @Override
  public HelmChartResponseDTO getHelmChartVersionDetailsV2(String accountId, String orgId, String projectId,
      String serviceRef, String manifestPath, String connectorId, String chartName, String region, String bucketName,
      String folderPath, String lastTag) {
    HelmManifestInternalDTO helmChartManifest =
        locateManifestInService(accountId, orgId, projectId, serviceRef, manifestPath);
    HelmChartManifestOutcome helmChartManifestOutcome = getHelmChartManifestOutcome(helmChartManifest);

    return getHelmChartVersionDetails(accountId, orgId, projectId, helmChartManifestOutcome, connectorId, chartName,
        region, bucketName, folderPath, lastTag);
  }

  @Override
  public HelmManifestInternalDTO locateManifestInService(
      String accountId, String orgId, String projectId, String serviceRef, String manifestPath) {
    YamlNode manifest = serviceEntityService.getYamlNodeForFqn(accountId, orgId, projectId, serviceRef, manifestPath);

    final HelmManifestInternalDTO helmChartManifest;
    try {
      helmChartManifest = YamlUtils.read(manifest.toString(), HelmManifestInternalDTO.class);
    } catch (IOException e) {
      throw new InvalidRequestException("Unable to read helm chart manifest", e);
    }
    return helmChartManifest;
  }

  @Override
  public StoreDelegateConfig getStoreDelegateConfig(HelmChartManifestOutcome helmChartManifestOutcome, String accountId,
      String orgId, String projectId, String connectorId, String region, String bucketName, String folderPath) {
    StoreConfig storeConfig = helmChartManifestOutcome.getStore();
    NGAccess ngAccess =
        BaseNGAccess.builder().accountIdentifier(accountId).orgIdentifier(orgId).projectIdentifier(projectId).build();

    ConnectorInfoDTO helmConnectorDTO;
    String connectorReference;

    if (isNotEmpty(connectorId)) {
      IdentifierRef connectorRef = IdentifierRefHelper.getIdentifierRef(connectorId, accountId, orgId, projectId);
      helmConnectorDTO = getConnector(connectorRef.getAccountIdentifier(), connectorRef.getOrgIdentifier(),
          connectorRef.getProjectIdentifier(), connectorRef.getIdentifier());
      connectorReference = connectorId;
    } else {
      helmConnectorDTO = k8sEntityHelper.getConnectorInfoDTO(storeConfig.getConnectorReference().getValue(), ngAccess);
      connectorReference = storeConfig.getConnectorReference().getValue();
    }

    if (storeConfig instanceof HttpStoreConfig) {
      return HttpHelmStoreDelegateConfig.builder()
          .repoName(convertBase64UuidToCanonicalForm(connectorReference))
          .repoDisplayName(helmConnectorDTO.getName())
          .httpHelmConnector((HttpHelmConnectorDTO) helmConnectorDTO.getConnectorConfig())
          .encryptedDataDetails(k8sEntityHelper.getEncryptionDataDetails(helmConnectorDTO, ngAccess))
          .build();
    } else if (storeConfig instanceof S3StoreConfig) {
      S3StoreConfig s3StoreConfig = (S3StoreConfig) storeConfig;
      return S3HelmStoreDelegateConfig.builder()
          .repoName(convertBase64UuidToCanonicalForm(connectorReference))
          .repoDisplayName(helmConnectorDTO.getName())
          .bucketName(isNotEmpty(bucketName) ? bucketName : getParameterFieldValue(s3StoreConfig.getBucketName()))
          .region(isNotEmpty(region) ? region : getParameterFieldValue(s3StoreConfig.getRegion()))
          .folderPath(isNotEmpty(folderPath) ? folderPath : getParameterFieldValue(s3StoreConfig.getFolderPath()))
          .awsConnector((AwsConnectorDTO) helmConnectorDTO.getConnectorConfig())
          .encryptedDataDetails(k8sEntityHelper.getEncryptionDataDetails(helmConnectorDTO, ngAccess))
          .useLatestChartMuseumVersion(
              cdFeatureFlagHelper.isEnabled(accountId, FeatureName.USE_LATEST_CHARTMUSEUM_VERSION))
          .build();
    } else if (storeConfig instanceof GcsStoreConfig) {
      GcsStoreConfig gcsStoreConfig = (GcsStoreConfig) storeConfig;
      return GcsHelmStoreDelegateConfig.builder()
          .repoName(convertBase64UuidToCanonicalForm(connectorReference))
          .repoDisplayName(helmConnectorDTO.getName())
          .bucketName(isNotEmpty(bucketName) ? bucketName : getParameterFieldValue(gcsStoreConfig.getBucketName()))
          .folderPath(isNotEmpty(folderPath) ? folderPath : getParameterFieldValue(gcsStoreConfig.getFolderPath()))
          .gcpConnector((GcpConnectorDTO) helmConnectorDTO.getConnectorConfig())
          .encryptedDataDetails(k8sEntityHelper.getEncryptionDataDetails(helmConnectorDTO, ngAccess))
          .useLatestChartMuseumVersion(
              cdFeatureFlagHelper.isEnabled(accountId, FeatureName.USE_LATEST_CHARTMUSEUM_VERSION))
          .build();
    }
    throw new InvalidRequestException("Unsupported store config type");
  }

  public HelmVersion getHelmVersionBasedOnFF(HelmVersion helmVersion, String accountId) {
    if (helmVersion == HelmVersion.V2) {
      return helmVersion;
    }
    return cdFeatureFlagHelper.isEnabled(accountId, FeatureName.HELM_VERSION_3_8_0) == true ? HelmVersion.V380
                                                                                            : HelmVersion.V3;
  }

  public HelmChartManifestOutcome getHelmChartManifestOutcome(HelmManifestInternalDTO helmManifestInternalDTO) {
    ManifestAttributes manifestAttributes = helmManifestInternalDTO.getSpec();
    HelmChartManifest helmChartManifest = (HelmChartManifest) manifestAttributes;
    return (HelmChartManifestOutcome) ManifestOutcomeMapper.toManifestOutcome(helmChartManifest, 0);
  }

  public Set<String> getDelegateSelectors(StoreDelegateConfig storeDelegateConfig) {
    if (storeDelegateConfig instanceof HttpHelmStoreDelegateConfig) {
      return ((HttpHelmStoreDelegateConfig) storeDelegateConfig).getHttpHelmConnector().getDelegateSelectors();
    } else if (storeDelegateConfig instanceof S3HelmStoreDelegateConfig) {
      return ((S3HelmStoreDelegateConfig) storeDelegateConfig).getAwsConnector().getDelegateSelectors();
    } else if (storeDelegateConfig instanceof GcsHelmStoreDelegateConfig) {
      return ((GcsHelmStoreDelegateConfig) storeDelegateConfig).getGcpConnector().getDelegateSelectors();
    }
    throw new InvalidRequestException("Unsupported store config type");
  }

  public ConnectorInfoDTO getConnector(String accountId, String orgId, String projectId, String connectorId) {
    Optional<ConnectorResponseDTO> connectorDTO = connectorService.get(accountId, orgId, projectId, connectorId);

    if (!connectorDTO.isPresent() || !isAValidHelmConnector(connectorDTO.get())) {
      throw new InvalidRequestException(
          format("Connector not found for identifier : [%s] ", connectorId), WingsException.USER);
    }
    return connectorDTO.get().getConnector();
  }

  private boolean isAValidHelmConnector(@Valid @NotNull ConnectorResponseDTO connectorResponseDTO) {
    return VALID_CONNECTOR_TYPES.contains(connectorResponseDTO.getConnector().getConnectorType());
  }
}
