/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.serverless;

import static io.harness.common.ParameterFieldHelper.getParameterFieldValue;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.exception.WingsException.USER;

import static java.lang.String.format;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.cdng.manifest.ManifestStoreType;
import io.harness.cdng.manifest.ManifestType;
import io.harness.cdng.manifest.yaml.GitStoreConfig;
import io.harness.cdng.manifest.yaml.ManifestOutcome;
import io.harness.cdng.manifest.yaml.S3StoreConfig;
import io.harness.cdng.manifest.yaml.ServerlessAwsLambdaManifestOutcome;
import io.harness.cdng.manifest.yaml.storeConfig.StoreConfig;
import io.harness.data.structure.EmptyPredicate;
import io.harness.delegate.beans.instancesync.ServerInstanceInfo;
import io.harness.delegate.beans.instancesync.mapper.ServerlessAwsLambdaFunctionToServerInstanceInfoMapper;
import io.harness.delegate.beans.serverless.ServerlessAwsLambdaDeployResult;
import io.harness.delegate.beans.serverless.ServerlessDeployResult;
import io.harness.delegate.task.serverless.ServerlessAwsLambdaDeployConfig;
import io.harness.delegate.task.serverless.ServerlessAwsLambdaManifestConfig;
import io.harness.delegate.task.serverless.ServerlessAwsLambdaManifestConfig.ServerlessAwsLambdaManifestConfigBuilder;
import io.harness.delegate.task.serverless.ServerlessDeployConfig;
import io.harness.delegate.task.serverless.ServerlessManifestConfig;
import io.harness.exception.InvalidRequestException;
import io.harness.git.model.FetchFilesResult;
import io.harness.git.model.GitFile;
import io.harness.pms.contracts.ambiance.Ambiance;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.hibernate.validator.constraints.NotEmpty;

@CodePulse(
    module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_SERVERLESS})
@OwnedBy(HarnessTeam.CDP)
@Singleton
public class ServerlessAwsLambdaStepHelper implements ServerlessStepHelper {
  @Inject private ServerlessStepUtils serverlessStepUtils;

  @Override
  public ManifestOutcome getServerlessManifestOutcome(@NotEmpty Collection<ManifestOutcome> manifestOutcomes) {
    List<ManifestOutcome> serverlessManifests =
        manifestOutcomes.stream()
            .filter(manifestOutcome -> ManifestType.ServerlessAwsLambda.equals(manifestOutcome.getType()))
            .collect(Collectors.toList());
    if (isEmpty(serverlessManifests)) {
      throw new InvalidRequestException("Manifests are mandatory for Serverless Aws Lambda step", USER);
    }
    if (serverlessManifests.size() > 1) {
      throw new InvalidRequestException("There can be only a single manifest for Serverless Aws Lambda step", USER);
    }
    return serverlessManifests.get(0);
  }

  @Override
  public String getConfigOverridePath(ManifestOutcome manifestOutcome) {
    if (manifestOutcome instanceof ServerlessAwsLambdaManifestOutcome) {
      ServerlessAwsLambdaManifestOutcome serverlessAwsLambdaManifestOutcome =
          (ServerlessAwsLambdaManifestOutcome) manifestOutcome;
      return getParameterFieldValue(serverlessAwsLambdaManifestOutcome.getConfigOverridePath());
    }
    throw new UnsupportedOperationException(
        format("Unsupported serverless manifest type: [%s]", manifestOutcome.getType()));
  }

  @Override
  public ServerlessDeployConfig getServerlessDeployConfig(ServerlessSpecParameters serverlessSpecParameters) {
    if (serverlessSpecParameters instanceof ServerlessAwsLambdaDeployStepParameters) {
      ServerlessAwsLambdaDeployStepParameters serverlessAwsLambdaDeployStepParameters =
          (ServerlessAwsLambdaDeployStepParameters) serverlessSpecParameters;
      return ServerlessAwsLambdaDeployConfig.builder()
          .commandOptions(serverlessAwsLambdaDeployStepParameters.getCommandOptions().getValue())
          .build();
    }
    throw new UnsupportedOperationException(
        format("Unsupported serverless spec : [%s]", serverlessSpecParameters.getClass()));
  }

  @Override
  public ServerlessManifestConfig getServerlessManifestConfig(
      ManifestOutcome manifestOutcome, Ambiance ambiance, Map<String, Object> manifestParams) {
    if (manifestOutcome instanceof ServerlessAwsLambdaManifestOutcome) {
      ServerlessAwsLambdaManifestOutcome serverlessAwsLambdaManifestOutcome =
          (ServerlessAwsLambdaManifestOutcome) manifestOutcome;
      Pair<String, String> manifestFilePathContent =
          (Pair<String, String>) manifestParams.get("manifestFilePathContent");

      StoreConfig storeConfig = serverlessAwsLambdaManifestOutcome.getStore();
      ServerlessAwsLambdaManifestConfigBuilder serverlessAwsLambdaManifestConfigBuilder =
          ServerlessAwsLambdaManifestConfig.builder();
      if (ManifestStoreType.isInGitSubset(storeConfig.getKind())) {
        GitStoreConfig gitStoreConfig = (GitStoreConfig) storeConfig;
        serverlessAwsLambdaManifestConfigBuilder.gitStoreDelegateConfig(
            serverlessStepUtils.getGitStoreDelegateConfig(ambiance, gitStoreConfig, manifestOutcome));
      } else {
        S3StoreConfig s3StoreConfig = (S3StoreConfig) storeConfig;
        serverlessAwsLambdaManifestConfigBuilder.s3StoreDelegateConfig(
            serverlessStepUtils.getS3StoreDelegateConfig(ambiance, s3StoreConfig, manifestOutcome));
      }
      return serverlessAwsLambdaManifestConfigBuilder.manifestPath(manifestFilePathContent.getKey())
          .configOverridePath(getConfigOverridePath(manifestOutcome))
          .build();
    }
    throw new UnsupportedOperationException(
        format("Unsupported serverless manifest type: [%s]", manifestOutcome.getType()));
  }

  @Override
  public List<ServerInstanceInfo> getServerlessDeployFunctionInstanceInfo(
      ServerlessDeployResult serverlessDeployResult, String infraStructureKey) {
    if (serverlessDeployResult instanceof ServerlessAwsLambdaDeployResult) {
      ServerlessAwsLambdaDeployResult serverlessAwsLambdaDeployResult =
          (ServerlessAwsLambdaDeployResult) serverlessDeployResult;
      if (EmptyPredicate.isEmpty(serverlessAwsLambdaDeployResult.getFunctions())) {
        return Collections.emptyList();
      }
      return ServerlessAwsLambdaFunctionToServerInstanceInfoMapper.toServerInstanceInfoList(
          serverlessAwsLambdaDeployResult.getFunctions(), serverlessAwsLambdaDeployResult.getRegion(),
          serverlessAwsLambdaDeployResult.getStage(), serverlessAwsLambdaDeployResult.getService(), infraStructureKey);
    }
    throw new UnsupportedOperationException(
        format("Unsupported serverless deploy instance: [%s]", serverlessDeployResult.getClass()));
  }

  @Override
  public Optional<Pair<String, String>> getManifestFileContent(
      Map<String, FetchFilesResult> fetchFilesResultMap, ManifestOutcome manifestOutcome) {
    if (manifestOutcome instanceof ServerlessAwsLambdaManifestOutcome) {
      ServerlessAwsLambdaManifestOutcome serverlessAwsLambdaManifestOutcome =
          (ServerlessAwsLambdaManifestOutcome) manifestOutcome;
      StoreConfig store = serverlessAwsLambdaManifestOutcome.getStore();
      if (ManifestStoreType.isInGitSubset(store.getKind())) {
        FetchFilesResult fetchFilesResult = fetchFilesResultMap.get(serverlessAwsLambdaManifestOutcome.getIdentifier());
        if (EmptyPredicate.isNotEmpty(fetchFilesResult.getFiles())) {
          GitFile gitFile = fetchFilesResult.getFiles().get(0);
          String filePath = getConfigOverridePath(manifestOutcome);
          if (EmptyPredicate.isEmpty(filePath)) {
            filePath = serverlessStepUtils.getManifestDefaultFileName(gitFile.getFilePath());
          }
          return Optional.of(ImmutablePair.of(filePath, gitFile.getFileContent()));
        }
      }
      return Optional.empty();
    }
    throw new UnsupportedOperationException(
        format("Unsupported serverless manifest type: [%s]", manifestOutcome.getType()));
  }
}
