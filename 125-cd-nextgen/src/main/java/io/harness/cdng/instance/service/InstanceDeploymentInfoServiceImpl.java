/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.instance.service;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.cdng.instance.InstanceDeploymentInfoStatus.IN_PROGRESS;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.execution.ExecutionInfoKey;
import io.harness.cdng.instance.InstanceDeploymentInfo;
import io.harness.cdng.instance.InstanceDeploymentInfoStatus;
import io.harness.data.structure.EmptyPredicate;
import io.harness.entities.ArtifactDetails;
import io.harness.entities.instanceinfo.InstanceInfo;
import io.harness.entities.instanceinfo.SshWinrmInstanceInfo;
import io.harness.exception.InvalidArgumentsException;
import io.harness.exception.InvalidRequestException;
import io.harness.repositories.instance.InstanceDeploymentInfoRepository;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Singleton
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
@OwnedBy(CDP)
public class InstanceDeploymentInfoServiceImpl implements InstanceDeploymentInfoService {
  private InstanceDeploymentInfoRepository instanceDeploymentInfoRepository;

  @Override
  public void updateStatus(@NotNull ExecutionInfoKey key, @NotNull InstanceDeploymentInfoStatus status) {
    if (status == InstanceDeploymentInfoStatus.FAILED) {
      deleteByExecutionInfoKey(key);
    } else {
      UpdateResult updateResult = instanceDeploymentInfoRepository.updateStatus(key, status);
      if (!updateResult.wasAcknowledged()) {
        throw new InvalidRequestException(
            format("Unable to update instance deployment info status, accountIdentifier: %s, orgIdentifier: %s, "
                    + "projectIdentifier: %s, deploymentIdentifier: %s, stageStatus: %s",
                key.getScope().getAccountIdentifier(), key.getScope().getOrgIdentifier(),
                key.getScope().getProjectIdentifier(), key.getDeploymentIdentifier(), status));
      }
    }
  }

  @Override
  public void updateStatus(
      @NotNull ExecutionInfoKey key, @NotNull String host, @NotNull InstanceDeploymentInfoStatus status) {
    UpdateResult updateResult = instanceDeploymentInfoRepository.updateStatus(key, host, status);
    if (!updateResult.wasAcknowledged()) {
      throw new InvalidRequestException(
          format("Unable to update instance deployment info status, accountIdentifier: %s, orgIdentifier: %s, "
                  + "projectIdentifier: %s, deploymentIdentifier: %s, stageStatus: %s",
              key.getScope().getAccountIdentifier(), key.getScope().getOrgIdentifier(),
              key.getScope().getProjectIdentifier(), key.getDeploymentIdentifier(), status));
    }
  }

  private void deleteByExecutionInfoKey(@NotNull ExecutionInfoKey key) {
    DeleteResult deleteResult = instanceDeploymentInfoRepository.deleteByExecutionInfoKey(key);
    if (!deleteResult.wasAcknowledged()) {
      throw new InvalidRequestException(
          format("Unable to delete instance deployment info for accountIdentifier: %s, orgIdentifier: %s, "
                  + "projectIdentifier: %s, deploymentIdentifier: %s",
              key.getScope().getAccountIdentifier(), key.getScope().getOrgIdentifier(),
              key.getScope().getProjectIdentifier(), key.getDeploymentIdentifier()));
    }
  }

  @Override
  public List<InstanceDeploymentInfo> getByHosts(ExecutionInfoKey executionInfoKey, List<String> hosts) {
    return instanceDeploymentInfoRepository.listByHosts(executionInfoKey, hosts);
  }

  public ExecutionInfoKey filter(ExecutionInfoKey executionInfoKey) {
    return ExecutionInfoKey.builder()
        .scope(executionInfoKey.getScope())
        .serviceIdentifier(executionInfoKey.getServiceIdentifier())
        .envIdentifier(executionInfoKey.getEnvIdentifier())
        .infraIdentifier(executionInfoKey.getInfraIdentifier())
        .build();
  }

  @Override
  public void createAndUpdate(
      ExecutionInfoKey executionInfoKey, List<InstanceInfo> instanceInfos, ArtifactDetails artifactDetails) {
    if (EmptyPredicate.isEmpty(instanceInfos)
        || instanceInfos.stream().anyMatch(i -> !(i instanceof SshWinrmInstanceInfo))) {
      throw new InvalidArgumentsException("Invalid instanceInfo provided");
    }

    List<String> hosts =
        instanceInfos.stream().map(i -> ((SshWinrmInstanceInfo) i).getHost()).distinct().collect(Collectors.toList());

    List<InstanceDeploymentInfo> instanceDeploymentInfosFromDb = getByHosts(filter(executionInfoKey), hosts);
    List<String> hostsFromDb = instanceDeploymentInfosFromDb.stream()
                                   .map(i -> ((SshWinrmInstanceInfo) i.getInstanceInfo()).getHost())
                                   .collect(Collectors.toList());

    instanceDeploymentInfoRepository.updateArtifactAndStatus(
        executionInfoKey, executionInfoKey.getDeploymentIdentifier(), hostsFromDb, artifactDetails, IN_PROGRESS);

    List<InstanceDeploymentInfo> instanceDeploymentInfos = new ArrayList<>();
    instanceInfos.stream().filter(i -> !hostsFromDb.contains(((SshWinrmInstanceInfo) i).getHost())).forEach(i -> {
      InstanceDeploymentInfo instanceDeploymentInfo =
          InstanceDeploymentInfo.builder()
              .accountIdentifier(executionInfoKey.getScope().getAccountIdentifier())
              .orgIdentifier(executionInfoKey.getScope().getOrgIdentifier())
              .projectIdentifier(executionInfoKey.getScope().getProjectIdentifier())
              .serviceIdentifier(executionInfoKey.getServiceIdentifier())
              .envIdentifier(executionInfoKey.getEnvIdentifier())
              .infraIdentifier(executionInfoKey.getInfraIdentifier())
              .instanceInfo(i)
              .artifactDetails(artifactDetails)
              .status(IN_PROGRESS)
              .build();
      instanceDeploymentInfos.add(instanceDeploymentInfo);
    });

    if (EmptyPredicate.isNotEmpty(instanceDeploymentInfos)) {
      instanceDeploymentInfoRepository.saveAll(instanceDeploymentInfos);
    }
  }
}
