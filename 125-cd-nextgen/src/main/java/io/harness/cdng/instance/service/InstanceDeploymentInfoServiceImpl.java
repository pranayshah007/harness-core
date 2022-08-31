/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.instance.service;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.cdng.instance.InstanceDeploymentInfoStatus.IN_PROGRESS;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static java.lang.String.format;

import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.Scope;
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
import java.util.Collections;
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
  public void updateStatus(
      @NotNull Scope scope, @NotNull String stageExecutionId, @NotNull InstanceDeploymentInfoStatus status) {
    UpdateResult updateResult = instanceDeploymentInfoRepository.updateStatus(scope, stageExecutionId, status);
    if (!updateResult.wasAcknowledged()) {
      throw new InvalidRequestException(
          format("Unable to update instance deployment info status, accountIdentifier: %s, orgIdentifier: %s, "
                  + "projectIdentifier: %s, stageExecutionId: %s, stageStatus: %s",
              scope.getAccountIdentifier(), scope.getOrgIdentifier(), scope.getProjectIdentifier(), stageExecutionId,
              status));
    }
  }

  @Override
  public List<InstanceDeploymentInfo> getByHostsAndArtifact(@NotNull ExecutionInfoKey executionInfoKey,
      List<String> hosts, @NotNull ArtifactDetails artifactDetails, InstanceDeploymentInfoStatus status) {
    if (isEmpty(hosts)) {
      return Collections.emptyList();
    }

    return instanceDeploymentInfoRepository.listByHostsAndArtifact(executionInfoKey, hosts, artifactDetails, status);
  }

  @Override
  public void createAndUpdate(ExecutionInfoKey executionInfoKey, List<InstanceInfo> instanceInfos,
      ArtifactDetails artifactDetails, final String stageExecutionId) {
    if (EmptyPredicate.isEmpty(instanceInfos)
        || instanceInfos.stream().anyMatch(i -> !(i instanceof SshWinrmInstanceInfo))) {
      throw new InvalidArgumentsException("Invalid instanceInfo provided");
    }

    List<String> hosts =
        instanceInfos.stream().map(i -> ((SshWinrmInstanceInfo) i).getHost()).distinct().collect(Collectors.toList());

    List<InstanceDeploymentInfo> instanceFromDb = getByHosts(executionInfoKey, hosts);
    List<String> hostsFromDb = instanceFromDb.stream()
                                   .map(i -> ((SshWinrmInstanceInfo) i.getInstanceInfo()).getHost())
                                   .collect(Collectors.toList());

    instanceDeploymentInfoRepository.updateArtifactAndStatus(
        executionInfoKey, hostsFromDb, artifactDetails, IN_PROGRESS, stageExecutionId);

    List<InstanceDeploymentInfo> newInstancesToBeAdded = new ArrayList<>();
    instanceInfos.stream()
        .filter(newInstance -> !hostsFromDb.contains(((SshWinrmInstanceInfo) newInstance).getHost()))
        .forEach(i -> {
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
                  .stageExecutionId(stageExecutionId)
                  .build();
          newInstancesToBeAdded.add(instanceDeploymentInfo);
        });

    if (isNotEmpty(newInstancesToBeAdded)) {
      instanceDeploymentInfoRepository.saveAll(newInstancesToBeAdded);
    }
  }

  @Override
  public List<InstanceDeploymentInfo> getByHosts(ExecutionInfoKey executionInfoKey, List<String> hosts) {
    return instanceDeploymentInfoRepository.listByHosts(executionInfoKey, hosts);
  }
}
