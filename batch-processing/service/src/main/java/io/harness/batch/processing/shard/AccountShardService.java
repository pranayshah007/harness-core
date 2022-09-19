/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.batch.processing.shard;

import io.harness.ModuleType;
import io.harness.batch.processing.config.BatchMainConfig;
import io.harness.batch.processing.config.PodInfoConfig;
import io.harness.batch.processing.dao.intfc.AccountShardMappingDao;
import io.harness.batch.processing.entities.AccountShardMapping;
import io.harness.licensing.Edition;
import io.harness.licensing.LicenseType;
import io.harness.licensing.beans.modules.ModuleLicenseDTO;
import io.harness.licensing.remote.NgLicenseHttpClient;
import io.harness.ng.core.dto.ResponseDTO;
import io.harness.utils.RestCallToNGManagerClientUtils;

import software.wings.beans.Account;
import software.wings.service.intfc.instance.CloudToHarnessMappingService;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import retrofit2.Call;

@Slf4j
@Component
public class AccountShardService {
  private BatchMainConfig mainConfig;
  private CloudToHarnessMappingService cloudToHarnessMappingService;
  private AccountShardMappingDao accountShardMappingDao;
  private NgLicenseHttpClient ngLicenseHttpClient;

  @Autowired
  public AccountShardService(BatchMainConfig mainConfig, CloudToHarnessMappingService cloudToHarnessMappingService,
      AccountShardMappingDao accountShardMappingDao, NgLicenseHttpClient ngLicenseHttpClient) {
    this.mainConfig = mainConfig;
    this.cloudToHarnessMappingService = cloudToHarnessMappingService;
    this.accountShardMappingDao = accountShardMappingDao;
    this.ngLicenseHttpClient = ngLicenseHttpClient;
  }

  public List<String> getCeEnabledAccountIds() {
    List<AccountLicenseDTO> ceEnabledAccounts = getCeEnabledAccounts();
    return ceEnabledAccounts.stream().map(AccountLicenseDTO::getAccountIdentifier).collect(Collectors.toList());
  }

  public List<AccountLicenseDTO> getCeEnabledAccounts() {
    log.info("Shard Id {} master pod {}", getShardId(), isMasterPod());

    List<AccountShardMapping> accountShardMappings = accountShardMappingDao.getAccountShardMapping();
    List<String> isolatedAccounts =
        accountShardMappings.stream().map(AccountShardMapping::getAccountId).collect(Collectors.toList());
    int shardId = getShardId();
    int replicas = getReplicaCount();
    List<AccountLicenseDTO> ceEnabledAccounts = getCeAccounts();
    List<AccountLicenseDTO> accounts;

    if (checkIsolatedPod(shardId, replicas)) {
      List<String> eligibleIsolatedAccounts =
          accountShardMappings.stream()
              .filter(accountShardMapping -> eligibleIsolatedAccount(accountShardMapping, shardId, replicas))
              .map(AccountShardMapping::getAccountId)
              .collect(Collectors.toList());
      accounts = ceEnabledAccounts.stream()
                     .filter(account -> eligibleIsolatedAccounts.contains(account.getAccountIdentifier()))
                     .collect(Collectors.toList());
    } else {
      accounts = ceEnabledAccounts.stream()
                     .filter(account -> !isolatedAccounts.contains(account.getAccountIdentifier()))
                     .filter(account -> eligibleAccount(account.getAccountIdentifier(), shardId, replicas))
                     .collect(Collectors.toList());
    }
    Set<String> collect = accounts.stream().map(AccountLicenseDTO::getAccountIdentifier).collect(Collectors.toSet());
    log.info("Account size {} :: {} :: {}", ceEnabledAccounts.size(), accounts.size(), collect);
    return accounts;
  }

  private List<AccountLicenseDTO> getCeAccounts() {
    List<ModuleLicenseDTO> ngAccounts = getNgAccounts();
    List<Account> cgAccounts = getCgAccounts();
    Set<String> ngAccountIds =
        ngAccounts.stream().map(ModuleLicenseDTO::getAccountIdentifier).collect(Collectors.toSet());
    List<AccountLicenseDTO> accounts = ngAccounts.stream()
                                           .map(ngAccount
                                               -> AccountLicenseDTO.builder()
                                                      .accountIdentifier(ngAccount.getAccountIdentifier())
                                                      .licenseType(ngAccount.getLicenseType())
                                                      .edition(ngAccount.getEdition())
                                                      .build())
                                           .collect(Collectors.toList());
    int ngAccountSize = ngAccounts.size();
    cgAccounts.stream()
        .filter(cgAccount -> !ngAccountIds.contains(cgAccount.getUuid()))
        .forEach(cgAccount
            -> accounts.add(AccountLicenseDTO.builder()
                                .accountIdentifier(cgAccount.getUuid())
                                .licenseType(LicenseType.PAID)
                                .edition(Edition.ENTERPRISE)
                                .build()));
    log.info("Account size Ng {} : Ng+CG {}", ngAccountSize, ngAccounts.size());
    return accounts;
  }

  private List<Account> getCgAccounts() {
    return cloudToHarnessMappingService.getCeEnabledAccounts();
  }

  private List<ModuleLicenseDTO> getNgAccounts() {
    long expiryTime = Instant.now().minus(15, ChronoUnit.DAYS).toEpochMilli();
    try {
      Call<ResponseDTO<List<ModuleLicenseDTO>>> moduleLicensesByModuleType =
          ngLicenseHttpClient.getModuleLicensesByModuleType(ModuleType.CE, expiryTime);
      return RestCallToNGManagerClientUtils.execute(moduleLicensesByModuleType);
    } catch (Exception ex) {
      log.error("Exception in account shard ", ex);
    }
    return Collections.emptyList();
  }

  private int getShardId() {
    PodInfoConfig podInfoConfig = mainConfig.getPodInfoConfig();
    String podName = podInfoConfig.getName();
    return Integer.parseInt(podName.substring(podName.lastIndexOf('-') + 1));
  }

  private boolean checkIsolatedPod(int shardId, int replicaCount) {
    return shardId >= replicaCount;
  }

  private int getReplicaCount() {
    PodInfoConfig podInfoConfig = mainConfig.getPodInfoConfig();
    int replica = podInfoConfig.getReplica();
    int isolatedReplica = podInfoConfig.getIsolatedReplica();
    return replica - isolatedReplica;
  }

  private boolean eligibleAccount(String accountId, int shardId, int replicas) {
    int hash = accountId.hashCode();
    int index = Math.abs(hash % replicas);
    return index == shardId;
  }

  private boolean eligibleIsolatedAccount(AccountShardMapping accountShardMapping, int shardId, int replicas) {
    return shardId + 1 == accountShardMapping.getShardId() + replicas;
  }

  public boolean isMasterPod() {
    int shardId = getShardId();
    return shardId == 0;
  }
}
