/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.cdng.migration;

import static io.harness.annotations.dev.HarnessTeam.CDP;

import io.harness.account.AccountClient;
import io.harness.annotations.dev.OwnedBy;
import io.harness.delegate.AccountId;
import io.harness.dtos.InfrastructureMappingDTO;
import io.harness.dtos.instancesyncperpetualtaskinfo.InstanceSyncPerpetualTaskInfoDTO;
import io.harness.entities.InfrastructureMapping;
import io.harness.entities.InfrastructureMapping.InfrastructureMappingNGKeys;
import io.harness.entities.Instance;
import io.harness.entities.Instance.InstanceKeys;
import io.harness.entities.instancesyncperpetualtaskinfo.InstanceSyncPerpetualTaskInfo;
import io.harness.entities.instancesyncperpetualtaskinfo.InstanceSyncPerpetualTaskInfo.InstanceSyncPerpetualTaskInfoKeys;
import io.harness.exception.InvalidRequestException;
import io.harness.grpc.DelegateServiceGrpcClient;
import io.harness.mappers.InstanceSyncPerpetualTaskInfoMapper;
import io.harness.migration.NGMigration;
import io.harness.models.constants.InstanceSyncConstants;
import io.harness.ng.core.dto.AccountDTO;
import io.harness.perpetualtask.*;
import io.harness.perpetualtask.internal.PerpetualTaskRecord;
import io.harness.perpetualtask.internal.PerpetualTaskRecord.PerpetualTaskRecordKeys;
import io.harness.persistence.HIterator;
import io.harness.persistence.HPersistence;
import io.harness.remote.client.CGRestUtils;
import io.harness.repositories.infrastructuremapping.InfrastructureMappingRepository;
import io.harness.service.infrastructuremapping.InfrastructureMappingService;
import io.harness.service.instance.InstanceService;
import io.harness.service.instancesyncperpetualtaskinfo.InstanceSyncPerpetualTaskInfoService;

import software.wings.dl.WingsPersistence;

import com.google.inject.Inject;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.Durations;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.query.Query;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;

@OwnedBy(CDP)
@AllArgsConstructor(onConstructor = @__({ @Inject }))
@Slf4j
public class UpdateCorruptInstances implements NGMigration {
  private AccountClient accountClient;
  private InfrastructureMappingService infrastructureMappingService;
  private InfrastructureMappingRepository infrastructureMappingRepository;
  private HPersistence persistence;
  private InstanceService instanceService;
  private MongoTemplate mongoTemplate;
  private InstanceSyncPerpetualTaskInfoService instanceSyncPerpetualTaskInfoService;
  private DelegateServiceGrpcClient delegateServiceGrpcClient;
  private final List<String> accountIdsToMigrate = Collections.singletonList("O9QxgA8NTtuL63DKjr3h_A");
  private WingsPersistence wingsPersistence;

  @Override
  public void migrate() {
    log.info("Migrating instances mapped to wrong infrastructure mapping");
    long totalPerpetualTasksToCreate = 0;
    try {
      List<AccountDTO> accounts = CGRestUtils.getResponse(accountClient.getAllAccounts());
      for (AccountDTO account : accounts) {
        long perpetualTasksToCreate = migrateAccount(account.getIdentifier());
        totalPerpetualTasksToCreate += perpetualTasksToCreate;
        log.info("Perpetual tasks to be created for account {}: {}", account.getIdentifier(), perpetualTasksToCreate);
      }
      log.info("Total perpetual tasks to be created: {}", totalPerpetualTasksToCreate);
    } catch (Exception ex) {
      log.error("Unexpected error occurred while migrating instances mapped to wrong infrastructure mapping", ex);
    }
  }

  private long migrateAccount(String accountId) {
    log.info("Migrating instances mapped to wrong infrastructure mapping for account: {}", accountId);
    long perpetualTasksToCreate = 0;
    try (HIterator<InfrastructureMapping> infrastructureMappings =
             new HIterator<>(getFetchInfrastructureMappingsQuery(accountId).fetch())) {
      while (infrastructureMappings.hasNext()) {
        InfrastructureMapping oldInfrastructureMapping = infrastructureMappings.next();
        perpetualTasksToCreate += migrateInfraMapping(oldInfrastructureMapping);
      }
    }
    return perpetualTasksToCreate;
  }

  private long migrateInfraMapping(InfrastructureMapping oldInfrastructureMapping) {
    log.info("Migrating instances mapped to wrong infrastructure mapping for infra mapping: {}",
        oldInfrastructureMapping.getId());
    long perpetualTasksToCreate = 0;
    try (HIterator<Instance> instances = new HIterator<>(getFetchInstancesQuery(oldInfrastructureMapping).fetch())) {
      while (instances.hasNext()) {
        Instance instance = instances.next();
        boolean createPerpetualTask = migrateInstance(instance, oldInfrastructureMapping);
        perpetualTasksToCreate += createPerpetualTask ? 1 : 0;
      }
    }
    return perpetualTasksToCreate;
  }

  private boolean migrateInstance(Instance instance, InfrastructureMapping oldInfrastructureMapping) {
    if (!oldInfrastructureMapping.getOrgIdentifier().equals(instance.getOrgIdentifier())
        || !oldInfrastructureMapping.getProjectIdentifier().equals(instance.getProjectIdentifier())) {
      // Found corrupt instance
      // Check if correct infra mapping exists for it
      if (accountIdsToMigrate.contains(oldInfrastructureMapping.getAccountIdentifier())) {
        InfrastructureMappingDTO newInfraMappingDTO =
            createNewOrReturnExistingInfrastructureMapping(instance, oldInfrastructureMapping);
        createPerpetualTask(oldInfrastructureMapping, newInfraMappingDTO);
        instanceService.updateInfrastructureMapping(
            Collections.singletonList(instance.getId()), newInfraMappingDTO.getId());
      } else {
        // This will be removed.
        // It is just needed for estimating the number of perpetual tasks that will be created for all accounts
        Optional<InfrastructureMapping> correctInfraMappingOpt =
            infrastructureMappingRepository
                .findByAccountIdentifierAndOrgIdentifierAndProjectIdentifierAndInfrastructureKey(
                    oldInfrastructureMapping.getAccountIdentifier(), instance.getOrgIdentifier(),
                    instance.getProjectIdentifier(), oldInfrastructureMapping.getInfrastructureKey());
        return !correctInfraMappingOpt.isPresent();
      }
    }
    return false;
  }

  private Query<InfrastructureMapping> getFetchInfrastructureMappingsQuery(String accountId) {
    return persistence.createQuery(InfrastructureMapping.class)
        .filter(InfrastructureMappingNGKeys.accountIdentifier, accountId);
  }

  private Query<Instance> getFetchInstancesQuery(InfrastructureMapping infrastructureMapping) {
    return persistence.createQuery(Instance.class)
        .filter(InstanceKeys.accountIdentifier, infrastructureMapping.getAccountIdentifier())
        .filter(InstanceKeys.infrastructureMappingId, infrastructureMapping.getId())
        .filter(InstanceKeys.isDeleted, false);
  }

  private InfrastructureMappingDTO createNewOrReturnExistingInfrastructureMapping(
      Instance instance, InfrastructureMapping oldInfraMapping) {
    InfrastructureMappingDTO infrastructureMappingDTO = InfrastructureMappingDTO.builder()
                                                            .accountIdentifier(oldInfraMapping.getAccountIdentifier())
                                                            .orgIdentifier(instance.getOrgIdentifier())
                                                            .projectIdentifier(instance.getProjectIdentifier())
                                                            .serviceIdentifier(oldInfraMapping.getServiceId())
                                                            .envIdentifier(oldInfraMapping.getEnvId())
                                                            .infrastructureKey(oldInfraMapping.getInfrastructureKey())
                                                            .infrastructureKind(oldInfraMapping.getInfrastructureKind())
                                                            .connectorRef(oldInfraMapping.getConnectorRef())
                                                            .build();
    Optional<InfrastructureMappingDTO> infrastructureMappingDTOOptional =
        infrastructureMappingService.createNewOrReturnExistingInfrastructureMapping(infrastructureMappingDTO);
    if (infrastructureMappingDTOOptional.isPresent()) {
      return infrastructureMappingDTOOptional.get();
    } else {
      throw new InvalidRequestException("Failed to create infrastructure mapping");
    }
  }

  private void createPerpetualTask(
      InfrastructureMapping oldInfrastructureMapping, InfrastructureMappingDTO newInfrastructureMapping) {
    PerpetualTaskRecord perpetualTaskRecord =
        wingsPersistence.createQuery(PerpetualTaskRecord.class)
            .field(PerpetualTaskRecordKeys.client_params + ".infrastructureMappingId")
            .equal(oldInfrastructureMapping.getId())
            .get();

    if (perpetualTaskRecord == null) {
      log.warn("Could not find perpetual task for old infrastructure mapping: {}", oldInfrastructureMapping.getId());
      return;
    }

    PerpetualTaskExecutionBundle perpetualTaskExecutionBundle;
    try {
      perpetualTaskExecutionBundle =
          PerpetualTaskExecutionBundle.parseFrom(perpetualTaskRecord.getClientContext().getExecutionBundle());
    } catch (InvalidProtocolBufferException ex) {
      log.error("Error while parsing byte string to PerpetualTaskExecutionBundle of perpetual task: {}",
          perpetualTaskRecord.getUuid(), ex);
      return;
    }

    PerpetualTaskId perpetualTaskId = delegateServiceGrpcClient.createPerpetualTask(
        AccountId.newBuilder().setId(newInfrastructureMapping.getAccountIdentifier()).build(),
        perpetualTaskRecord.getPerpetualTaskType(), preparePerpetualTaskSchedule(),
        PerpetualTaskClientContextDetails.newBuilder().setExecutionBundle(perpetualTaskExecutionBundle).build(), true,
        getPerpetualTaskDescription(newInfrastructureMapping));

    log.info("Created new perpetual task: {}, old perpetual task: {}, old infra mapping: {}, new infra mapping: {}",
        perpetualTaskId, perpetualTaskRecord.getUuid(), oldInfrastructureMapping.getId(),
        newInfrastructureMapping.getId());

    createInstanceSyncPerpetualTaskInfo(oldInfrastructureMapping, newInfrastructureMapping, perpetualTaskId);
  }

  private void createInstanceSyncPerpetualTaskInfo(InfrastructureMapping oldInfrastructureMapping,
      InfrastructureMappingDTO newInfrastructureMapping, PerpetualTaskId perpetualTaskId) {
    org.springframework.data.mongodb.core.query.Query instanceSyncPerpetualTaskInfoQuery =
        new org.springframework.data.mongodb.core.query.Query(
            new Criteria()
                .and(InstanceSyncPerpetualTaskInfoKeys.accountIdentifier)
                .is(oldInfrastructureMapping.getAccountIdentifier())
                .and(InstanceSyncPerpetualTaskInfoKeys.infrastructureMappingId)
                .is(oldInfrastructureMapping.getId()));
    InstanceSyncPerpetualTaskInfo oldInstanceSyncPerpetualTaskInfo =
        mongoTemplate.findOne(instanceSyncPerpetualTaskInfoQuery, InstanceSyncPerpetualTaskInfo.class);
    if (oldInstanceSyncPerpetualTaskInfo == null) {
      log.warn("Could not find instance sync perpetual task info for old infrastructure mapping: {}",
          oldInfrastructureMapping.getId());
      return;
    }
    InstanceSyncPerpetualTaskInfo newInstanceSyncPerpetualTaskInfo =
        InstanceSyncPerpetualTaskInfo.builder()
            .accountIdentifier(oldInstanceSyncPerpetualTaskInfo.getAccountIdentifier())
            .perpetualTaskId(perpetualTaskId.getId())
            .infrastructureMappingId(newInfrastructureMapping.getId())
            .createdAt(System.currentTimeMillis())
            .lastUpdatedAt(System.currentTimeMillis())
            .deploymentInfoDetailsList(oldInstanceSyncPerpetualTaskInfo.getDeploymentInfoDetailsList())
            .build();
    InstanceSyncPerpetualTaskInfoDTO instanceSyncPerpetualTaskInfoDTO = instanceSyncPerpetualTaskInfoService.save(
        InstanceSyncPerpetualTaskInfoMapper.toDTO(newInstanceSyncPerpetualTaskInfo));
    log.info("Created new instance sync perpetual task info: {}, old instance sync perpetual task info: {}, "
            + "old infra mapping: {}, new infra mapping: {}",
        instanceSyncPerpetualTaskInfoDTO.getId(), oldInstanceSyncPerpetualTaskInfo.getId(),
        oldInfrastructureMapping.getId(), newInfrastructureMapping.getId());
  }

  private PerpetualTaskSchedule preparePerpetualTaskSchedule() {
    return PerpetualTaskSchedule.newBuilder()
        .setInterval(Durations.fromMinutes(InstanceSyncConstants.INTERVAL_MINUTES))
        .setTimeout(Durations.fromSeconds(InstanceSyncConstants.TIMEOUT_SECONDS))
        .build();
  }

  private String getPerpetualTaskDescription(InfrastructureMappingDTO infrastructureMappingDTO) {
    return String.format(
        "OrgIdentifier: [%s], ProjectIdentifier: [%s], ServiceIdentifier: [%s], EnvironmentIdentifier: [%s], InfrastructureKey: [%s]",
        infrastructureMappingDTO.getOrgIdentifier(), infrastructureMappingDTO.getProjectIdentifier(),
        infrastructureMappingDTO.getServiceIdentifier(), infrastructureMappingDTO.getEnvIdentifier(),
        infrastructureMappingDTO.getInfrastructureKey());
  }
}
