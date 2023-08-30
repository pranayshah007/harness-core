/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ng.core.migration.background;
import static io.harness.utils.IdentifierRefHelper.MAX_RESULT_THRESHOLD_FOR_SPLIT;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import io.harness.account.utils.AccountUtils;
import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.ProductModule;
import io.harness.encryption.Scope;
import io.harness.migration.NGMigration;
import io.harness.mongo.MongoPersistence;
import io.harness.ng.core.serviceoverride.beans.NGServiceOverridesEntity;
import io.harness.ng.core.serviceoverride.beans.NGServiceOverridesEntity.NGServiceOverridesEntityKeys;
import io.harness.persistence.HIterator;
import io.harness.pms.yaml.YamlField;
import io.harness.pms.yaml.YamlUtils;
import io.harness.scope.ScopeHelper;
import io.harness.utils.IdentifierRefHelper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.inject.Inject;
import dev.morphia.query.Query;
import java.io.IOException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Update;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_PIPELINE})
@Slf4j
public class UpdateEnvironmentRefValueInServiceOverrideNGMigration implements NGMigration {
  @Inject private MongoPersistence mongoPersistence;
  @Inject private MongoTemplate mongoTemplate;
  @Inject private AccountUtils accountUtils;
  private static final String DEBUG_LOG = "[UpdateEnvironmentRefValueInServiceOverrideNGMigration]: ";
  private static final String SERVICE_OVERRIDES_NODE = "serviceOverrides";

  @Override
  public void migrate() {
    try {
      log.info(DEBUG_LOG + "Starting migration of updating environmentRef value in serviceOverridesNG");
      List<String> accountIdentifiers = accountUtils.getAllNGAccountIds();

      accountIdentifiers.forEach(accountId -> {
        try {
          log.info(DEBUG_LOG
              + "Starting migration of updating environmentRef value in serviceOverridesNG for account : " + accountId);
          Query<NGServiceOverridesEntity> ngServiceOverridesEntityQuery =
              mongoPersistence.createQuery(NGServiceOverridesEntity.class)
                  .filter(NGServiceOverridesEntityKeys.accountId, accountId);

          try (HIterator<NGServiceOverridesEntity> iterator = new HIterator<>(ngServiceOverridesEntityQuery.fetch())) {
            for (NGServiceOverridesEntity serviceOverride : iterator) {
              try {
                final String environmentRefInDb = serviceOverride.getEnvironmentRef();
                String[] envRefInDbSplit = StringUtils.split(environmentRefInDb, ".", MAX_RESULT_THRESHOLD_FOR_SPLIT);
                Scope scope = ScopeHelper.getScope(serviceOverride.getAccountId(), serviceOverride.getOrgIdentifier(),
                    serviceOverride.getProjectIdentifier());
                if ((envRefInDbSplit == null || envRefInDbSplit.length == 1) && !Scope.PROJECT.equals(scope)) {
                  String qualifiedEnvRef = IdentifierRefHelper.getRefFromIdentifierOrRef(serviceOverride.getAccountId(),
                      serviceOverride.getOrgIdentifier(), serviceOverride.getProjectIdentifier(), environmentRefInDb);
                  Update update = new Update();
                  update.set(NGServiceOverridesEntityKeys.environmentRef, qualifiedEnvRef);

                  String originalYaml = serviceOverride.getYaml();
                  String updatedYaml = getEnvRefUpdatedYaml(qualifiedEnvRef, originalYaml);

                  // here we are assuming all overrides have the yaml and environmentRef field, Else no update operation
                  // will be performed
                  if (isNotBlank(updatedYaml)) {
                    Criteria serviceOverrideEqualityCriteria = getServiceOverrideEqualityCriteria(serviceOverride);
                    org.springframework.data.mongodb.core.query.Query query =
                        new org.springframework.data.mongodb.core.query.Query(serviceOverrideEqualityCriteria);

                    update.set(NGServiceOverridesEntityKeys.environmentRef, qualifiedEnvRef)
                        .set(NGServiceOverridesEntityKeys.yaml, updatedYaml);

                    mongoTemplate.findAndModify(
                        query, update, new FindAndModifyOptions().returnNew(true), NGServiceOverridesEntity.class);
                  } else {
                    log.error(String.format(DEBUG_LOG
                            + "Yaml could not be updated for serviceOverride with accountId: [%s], orgId: [%s], projectId: [%s], serviceRef: [%s], environmentRef: [%s]",
                        serviceOverride.getAccountId(), serviceOverride.getOrgIdentifier(),
                        serviceOverride.getProjectIdentifier(), serviceOverride.getServiceRef(), environmentRefInDb));
                  }
                }
              } catch (Exception e) {
                log.error(
                    String.format(DEBUG_LOG
                            + "Failed to migrate serviceOverride with accountId: [%s], orgId: [%s], projectId: [%s], serviceRef: [%s], environmentRef: [%s]",
                        serviceOverride.getAccountId(), serviceOverride.getOrgIdentifier(),
                        serviceOverride.getProjectIdentifier(), serviceOverride.getServiceRef(),
                        serviceOverride.getEnvironmentRef()),
                    e);
              }
            }
          }

          log.info(DEBUG_LOG
              + "Migration of updating environmentRef value in serviceOverridesNG completed for account : "
              + accountId);
        } catch (Exception e) {
          log.error(DEBUG_LOG
                  + "Migration of updating environmentRef value in serviceOverridesNG failed for account: " + accountId,
              e);
        }
      });
      log.info(DEBUG_LOG + "Migration of updating environmentRef value in serviceOverridesNG completed");
    } catch (Exception e) {
      log.error(DEBUG_LOG + "Migration of updating environmentRef value in serviceOverridesNG failed.", e);
    }
  }

  private static String getEnvRefUpdatedYaml(String qualifiedEnvRef, String originalYaml) throws IOException {
    String updatedYaml = StringUtils.EMPTY;
    if (isNotBlank(originalYaml)) {
      YamlField yamlField = YamlUtils.readTree(originalYaml);
      JsonNode serviceOverridesJsonNode =
          yamlField.getNode().getField(SERVICE_OVERRIDES_NODE).getNode().getCurrJsonNode();
      YamlField envRefYamlField = yamlField.getNode()
                                      .getField(SERVICE_OVERRIDES_NODE)
                                      .getNode()
                                      .getField(NGServiceOverridesEntityKeys.environmentRef);
      if (envRefYamlField != null) {
        JsonNode envRefJsonNode = envRefYamlField.getNode().getCurrJsonNode();
        if (envRefJsonNode != null && envRefJsonNode.isTextual()) {
          ((ObjectNode) serviceOverridesJsonNode).put(NGServiceOverridesEntityKeys.environmentRef, qualifiedEnvRef);
        }
        updatedYaml = YamlUtils.writeYamlString(yamlField.getNode().getCurrJsonNode());
      }
    }
    return updatedYaml;
  }

  private Criteria getServiceOverrideEqualityCriteria(NGServiceOverridesEntity requestServiceOverride) {
    return Criteria.where(NGServiceOverridesEntityKeys.id).is(requestServiceOverride.getId());
  }
}
