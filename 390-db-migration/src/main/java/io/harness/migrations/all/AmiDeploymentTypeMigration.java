/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.migrations.all;

import static io.harness.beans.PageRequest.PageRequestBuilder.aPageRequest;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import static software.wings.beans.AmiDeploymentType.AWS_ASG;
import static software.wings.beans.AmiDeploymentType.SPOTINST;

import static dev.morphia.mapping.Mapper.ID_KEY;
import static java.util.stream.Collectors.toList;

import io.harness.beans.PageRequest;
import io.harness.beans.PageResponse;
import io.harness.beans.SearchFilter.Operator;
import io.harness.migrations.Migration;

import software.wings.beans.Account;
import software.wings.beans.AmiDeploymentType;
import software.wings.beans.AwsAmiInfrastructureMapping;
import software.wings.beans.AwsAmiInfrastructureMapping.AwsAmiInfrastructureMappingKeys;
import software.wings.beans.InfrastructureMapping;
import software.wings.dl.WingsPersistence;
import software.wings.infra.AwsAmiInfrastructure;
import software.wings.infra.InfraMappingInfrastructureProvider;
import software.wings.infra.InfrastructureDefinition;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.AppService;
import software.wings.service.intfc.InfrastructureDefinitionService;
import software.wings.service.intfc.InfrastructureMappingService;

import com.google.inject.Inject;
import dev.morphia.query.Query;
import dev.morphia.query.UpdateOperations;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AmiDeploymentTypeMigration implements Migration {
  @Inject private AppService appService;
  @Inject private AccountService accountService;
  @Inject private InfrastructureMappingService infraMappingService;
  @Inject private WingsPersistence wingsPersistence;
  @Inject private InfrastructureDefinitionService definitionService;

  private void performInfraMappingMigrationForAppId(String appId) {
    try {
      log.info("Starting to perform Ami Deployment Type migration for app: [{}]", appId);
      PageRequest<InfrastructureMapping> pageRequest = new PageRequest<>();
      pageRequest.addFilter("appId", Operator.EQ, appId);
      PageResponse<InfrastructureMapping> response = infraMappingService.list(pageRequest);
      List<InfrastructureMapping> infraMappingList = response.getResponse();
      if (isEmpty(infraMappingList)) {
        log.info("Done with Ami Deployment Type migration for app: [{}]", appId);
        return;
      }
      List<InfrastructureMapping> aminfraMappingList =
          infraMappingList.stream().filter(mapping -> mapping instanceof AwsAmiInfrastructureMapping).collect(toList());
      if (isEmpty(aminfraMappingList)) {
        log.info("Done with Ami Deployment Type migration for app: [{}]", appId);
        return;
      }
      aminfraMappingList.forEach(amiMapping -> {
        AwsAmiInfrastructureMapping awsAmiInfrastructureMapping = (AwsAmiInfrastructureMapping) amiMapping;
        try {
          AmiDeploymentType amiDeploymentType = awsAmiInfrastructureMapping.getAmiDeploymentType();
          if (amiDeploymentType != SPOTINST) {
            // The getter returned AWS_ASG.
            // So either the value in the DB is NULL.
            // Or the value is AWS_ASG.
            // For both the cases, we set AWS_ASG
            UpdateOperations<AwsAmiInfrastructureMapping> operations =
                wingsPersistence.createUpdateOperations(AwsAmiInfrastructureMapping.class);
            operations.set(AwsAmiInfrastructureMappingKeys.amiDeploymentType, AWS_ASG);

            // We can't use infaMappingService.update also. As it would load exsiting mapping and then call
            // getter.
            Query<AwsAmiInfrastructureMapping> query = wingsPersistence.createQuery(AwsAmiInfrastructureMapping.class)
                                                           .filter(ID_KEY, awsAmiInfrastructureMapping.getUuid());
            wingsPersistence.update(query, operations);
          }
        } catch (Exception ex) {
          log.warn("Error while updating Ami Deployment Types for Aws Ami Infra Mapping: [{}]",
              awsAmiInfrastructureMapping.getUuid(), ex);
        }
      });
      log.info("Done with Ami Deployment Type migration for app: [{}]", appId);
    } catch (Exception ex) {
      log.warn("Error while updating Ami Deployment Types for app: [{}]", appId, ex);
    }
  }

  private void performInfraDefMigrationForAppId(String appId) {
    try {
      PageRequest<InfrastructureDefinition> infraDefRequest = new PageRequest<>();
      infraDefRequest.addFilter("appId", Operator.EQ, appId);
      PageResponse<InfrastructureDefinition> infraDefResponse = definitionService.list(infraDefRequest);
      List<InfrastructureDefinition> infraDefList = infraDefResponse.getResponse();
      if (isEmpty(infraDefList)) {
        log.info("Done with Infra Def Ami Deployment Type migration for app: [{}]", appId);
        return;
      }
      infraDefList.forEach(def -> {
        try {
          InfraMappingInfrastructureProvider infrastructure = def.getInfrastructure();
          if (!(infrastructure instanceof AwsAmiInfrastructure)) {
            return;
          }
          AwsAmiInfrastructure awsAmiInfrastructure = (AwsAmiInfrastructure) infrastructure;
          AmiDeploymentType infraDefAmiDeploymentType = awsAmiInfrastructure.getAmiDeploymentType();
          if (infraDefAmiDeploymentType != SPOTINST) {
            // The getter returned AWS_ASG.
            // So either the value in the DB is NULL.
            // Or the value is AWS_ASG.
            // For both the cases, we set AWS_ASG
            UpdateOperations<InfrastructureDefinition> operations =
                wingsPersistence.createUpdateOperations(InfrastructureDefinition.class);
            operations.set("infrastructure.amiDeploymentType", AWS_ASG);

            // We can't use infaMappingService.update also. As it would load exsiting mapping and then call
            // getter.
            Query<InfrastructureDefinition> query =
                wingsPersistence.createQuery(InfrastructureDefinition.class).filter(ID_KEY, def.getUuid());
            wingsPersistence.update(query, operations);
          }
        } catch (Exception ex) {
          log.warn("Error while updating Ami Deployment Types for Aws Ami Infra Definition: [{}]", def.getUuid(), ex);
        }
      });
      log.info("Done with Infradef Ami Deployment Type migration for app: [{}]", appId);
    } catch (Exception ex) {
      log.warn("Error while updating Infradef Ami Deployment Types for app: [{}]", appId, ex);
    }
  }

  @Override
  public void migrate() {
    PageRequest<Account> accountPageRequest = aPageRequest().addFieldsIncluded("_id").build();
    List<Account> accounts = accountService.list(accountPageRequest);
    if (isEmpty(accounts)) {
      return;
    }
    accounts.forEach(account -> {
      try {
        List<String> appIds = appService.getAppIdsByAccountId(account.getUuid());
        if (isEmpty(appIds)) {
          return;
        }
        appIds.forEach(appId -> {
          performInfraMappingMigrationForAppId(appId);
          performInfraDefMigrationForAppId(appId);
          log.info("Done with Infradef and Inframapping Ami Deployment Type migration for app: [{}]", appId);
        });
      } catch (Exception ex) {
        log.warn("Error while updating Ami Deployment Types for account: [{}]", account.getUuid(), ex);
      }
    });
  }
}
