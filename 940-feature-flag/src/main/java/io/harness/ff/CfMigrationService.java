/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.ff;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.ff.FeatureFlagSystem.CF;

import static java.time.Duration.ofMinutes;
import static org.joda.time.Minutes.minutes;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.beans.FeatureFlag;
import io.harness.beans.FeatureFlag.Scope;
import io.harness.beans.FeatureName;
import io.harness.cf.CFApi;
import io.harness.cf.CfMigrationConfig;
import io.harness.cf.client.api.CfClient;
import io.harness.cf.client.dto.Target;
import io.harness.cf.openapi.model.Feature;
import io.harness.cf.openapi.model.FeatureState;
import io.harness.cf.openapi.model.Features;
import io.harness.cf.openapi.model.InlineObject;
import io.harness.cf.openapi.model.InlineObject.KindEnum;
import io.harness.cf.openapi.model.PatchInstruction;
import io.harness.cf.openapi.model.PatchOperation;
import io.harness.cf.openapi.model.ServingRule;
import io.harness.cf.openapi.model.TargetMap;
import io.harness.cf.openapi.model.Variation;
import io.harness.cf.openapi.model.VariationMap;
import io.harness.lock.AcquiredLock;
import io.harness.lock.PersistentLocker;
import io.harness.security.JWTTokenServiceUtils;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
@OwnedBy(HarnessTeam.CF)
public class CfMigrationService {
  @Inject @Named("cfMigrationAPI") private CFApi cfAdminApi;
  @Inject private CfMigrationConfig cfMigrationConfig;
  @Inject private Provider<CfClient> cfClient;
  @Inject private PersistentLocker persistentLocker;
  @Inject private FeatureFlagConfig featureFlagConfig;

  void verifyBehaviorWithCF(FeatureName featureName, boolean featureValue, String accountId) {
    if (cfMigrationConfig.isEnabled()) {
      if (Scope.GLOBAL.equals(featureName.getScope()) || isEmpty(accountId)) {
        /**
         * If accountID is null or empty, use a static accountID
         */
        accountId = FeatureFlagConstants.STATIC_ACCOUNT_ID;
      }
      Target target = Target.builder().identifier(accountId).name(accountId).build();
      boolean cfFeatureValue = cfClient.get().boolVariation(featureName.name(), target, false);
      if (cfFeatureValue != featureValue) {
        log.error("CF MISMATCH WITH HARNESS FF -> FEATURE [{}], HARNESS FF [{}], CF [{}], target [{}]",
            featureName.name(), featureValue, cfFeatureValue, target.getIdentifier());
      } else {
        log.debug("CF MATCH with Harness FF -> Feature[{}], value = {}]", featureName.name(), cfFeatureValue);
      }
    }
  }

  void syncAllFlagsWithCFServer(FeatureFlagService featureFlagService, boolean syncRules) {
    /**
     * Wrapping this in a distributed lock to prevent multiple pods from syncing on featureflags together which will
     * cause race conditions
     */
    try (AcquiredLock lock = persistentLocker.waitToAcquireLock("CF_MIGRATION_SYNC", ofMinutes(3), ofMinutes(3))) {
      String cfAccount = cfMigrationConfig.getAccount();
      String cfOrg = cfMigrationConfig.getOrg();
      String cfProject = cfMigrationConfig.getProject();
      String cfEnvironment = cfMigrationConfig.getEnvironment();
      Set<String> cfFeatures = new HashSet<>();
      addApiKeyHeader(cfAdminApi);
      Features features = cfAdminApi.getAllFeatures(cfAccount, cfOrg, cfProject, cfEnvironment, 0, Integer.MAX_VALUE);
      Map<String, Feature> featureMap = new HashMap<>();
      for (Feature feature : features.getFeatures()) {
        featureMap.put(feature.getName(), feature);
        cfFeatures.add(feature.getName());
      }

      /**
       * For every feature flag in Harness, do the following
       */
      for (FeatureName featureName : FeatureName.values()) {
        Optional<FeatureFlag> featureFlag = featureFlagService.getFeatureFlag(featureName);
        /**
         * If featureFlag is not present in db, something is wrong on the Harness side. We will not sync this
         * featureFlag in CF
         */
        if (!featureFlag.isPresent()) {
          log.warn("CF-SYNC FeatureFlag [{}] is not present in Harness DB, skipping it ", featureName.name());
          continue;
        }
        /**
         * If Harness CF does not contain this featureFlag, go ahead and create it
         */
        if (!cfFeatures.contains(featureName.name())) {
          try {
            cfAdminApi.createFeatureFlag(
                cfAccount, cfOrg, createCFFeatureFlag(featureName.name(), featureName.getScope(), cfProject));
            Feature feature = cfAdminApi.getFeatureFlag(featureName.name(), cfAccount, cfOrg, cfProject, cfEnvironment);
            featureMap.put(featureName.name(), feature);
            log.info("CF-SYNC Created featureFlag [{}] in CF", featureName);
          } catch (Exception e) {
            log.error("CF-SYNC Failed to create featureFlag in CF", e);
            continue;
          }
        } else {
          log.info("CF-SYNC FeatureFlag [{}] already present in CF, not creating it again", featureName.name());
        }

        if (syncRules) {
          updateFeatureFlagInCF(featureFlag.get(), featureMap.get(featureName.name()));
        }
      }
    } catch (Exception e) {
      log.error("Failed to sync with Harness CF", e);
    }
  }

  private void addApiKeyHeader(CFApi cfApi) {
    cfApi.getApiClient().addDefaultHeader("api-key",
        "Bearer "
            + JWTTokenServiceUtils.generateJWTToken(ImmutableMap.of("type", "APIKey", "name", "CG-MANAGER"),
                minutes(10).toStandardDuration().getMillis(), cfMigrationConfig.getApiKey()));
  }

  void syncFeatureFlagWithCF(FeatureFlag featureFlag) {
    /**
     * Sync featureFlag update to CF server if ( CF is enabled or cfMigration is Enabled )
     */
    if (CF.equals(featureFlagConfig.getFeatureFlagSystem()) || cfMigrationConfig.isEnabled()) {
      log.info("CF-SYNC Updating featureFlag [{}] to CF Server", featureFlag.getName());
      try {
        addApiKeyHeader(cfAdminApi);

        Feature cfFeature = cfAdminApi.getFeatureFlag(featureFlag.getName(), cfMigrationConfig.getAccount(),
            cfMigrationConfig.getOrg(), cfMigrationConfig.getProject(), cfMigrationConfig.getEnvironment());
        if (cfFeature == null) {
          log.error("CF-SYNC Did not find featureFlag [{}] in CF Server, cannot update this featureFlag",
              featureFlag.getName());
          return;
        }
        updateFeatureFlagInCF(featureFlag, cfFeature);
      } catch (Exception e) {
        log.error("CF-SYNC Failed to sync featureFlag [{}] in environment [{}]", featureFlag.getName(),
            cfMigrationConfig.getEnvironment(), e);
      }
    }
  }

  private void updateFeatureFlagInCF(FeatureFlag featureFlag, Feature cfFeature) {
    try {
      String cfAccount = cfMigrationConfig.getAccount();
      String cfOrg = cfMigrationConfig.getOrg();
      String cfProject = cfMigrationConfig.getProject();
      String cfEnvironment = cfMigrationConfig.getEnvironment();
      List<PatchInstruction> instructions = new ArrayList<>();

      /**
       * If for the given environment, this featureFlag has state off, go ahead and turn it on
       * Turn featureFlag on if it is off
       */
      if (cfFeature.getEnvProperties().getState().getValue().equals(FeatureState.OFF.getValue())) {
        /*
         * Turn featureFlag on
         * */
        log.info("CF-SYNC FeatureFlag [{}] in CF has state OFF, turning it on", featureFlag.getName());
        PatchInstruction turnOnPatchInstruction = cfAdminApi.getFeatureFlagOnPatchInstruction(true);
        instructions.add(turnOnPatchInstruction);
      } else {
        log.info("CF-SYNC FeatureFlag [{}] in CF has state ON", featureFlag.getName());
      }

      /**
       * If the featureFlag is enabled on Harness side, then we should turn the defaultServe to on
       * This is based on the logic in isEnabled
       *
       * If featureFlag is enabled, then
       * turn defaultOnVariation to true if it is set to false
       *
       */
      final String defaultServeVariation = cfFeature.getEnvProperties().getDefaultServe().getVariation();
      if (featureFlag.isEnabled()) {
        if (defaultServeVariation.equals("false")) {
          PatchInstruction patchInstruction = cfAdminApi.getFeatureFlagDefaultServePatchInstruction(true);
          instructions.add(patchInstruction);
          log.info("CF-SYNC CF FeatureFlag [{}] is has defaultServe OFF, turning it ON", featureFlag.getName());
        } else {
          log.info(
              "CF-SYNC CF FeatureFlag [{}] is has defaultServe ON, not turning it ON again", featureFlag.getName());
        }
      } else {
        if (defaultServeVariation.equals("true")) {
          PatchInstruction patchInstruction = cfAdminApi.getFeatureFlagDefaultServePatchInstruction(false);
          instructions.add(patchInstruction);
          log.info("CF-SYNC CF FeatureFlag [{}] is has defaultServe ON, turning it OFF", featureFlag.getName());
        }
      }
      /**
       * If featureFlag list of accounts is present, add custom rule for each accountID and serveVariation to
       * true
       */
      Set<String> accountIds = featureFlag.getAccountIds();
      Set<String> rulesTobeDeleted = new HashSet<>();
      Set<String> tobeAddedAccountIds = new HashSet<>();
      final List<ServingRule> rules = cfFeature.getEnvProperties().getRules();
      final List<VariationMap> variationMaps = cfFeature.getEnvProperties().getVariationMap();
      int maxPriority = 0;
      if (accountIds != null && accountIds.size() > 0) {
        tobeAddedAccountIds.addAll(accountIds);
        if (rules != null && rules.size() > 0) {
          for (ServingRule rule : rules) {
            if (rule.getClauses().size() > 0) {
              final String accountId = rule.getClauses().get(0).getValues().get(0);
              if (tobeAddedAccountIds.contains(accountId)) {
                tobeAddedAccountIds.remove(accountId);
              } else {
                rulesTobeDeleted.add(rule.getRuleId());
              }
            } else {
              log.warn(
                  "CF-SYNC Invalid rule [{}] for FF[{}], environment [{}] , does not contain any clause, marking for deletion",
                  rule.getRuleId(), featureFlag.getName(), cfEnvironment);
              rulesTobeDeleted.add(rule.getRuleId());
            }

            maxPriority = Math.max(maxPriority, rule.getPriority());
          }
        }

      } else {
        /**
         * If there are no accounts set for this rule, delete all rules for the featureFlag
         */
        if (rules.size() > 0) {
          rules.forEach(rule -> rulesTobeDeleted.add(rule.getRuleId()));
        }

        if (variationMaps != null && variationMaps.size() > 0) {
          for (VariationMap variationMap : variationMaps) {
            List<String> targets = new ArrayList<>();

            for (TargetMap targetMap : variationMap.getTargets()) {
              if (!tobeAddedAccountIds.contains(targetMap.getIdentifier())) {
                targets.add(targetMap.getIdentifier());
              }
            }

            if (targets.size() > 0) {
              instructions.add(cfAdminApi.getRemoveTargetToVariationMapParams("true", targets));
            }
          }
        }
      }

      if (tobeAddedAccountIds.size() > 0) {
        log.info(
            "CF-SYNC Creating Rules for AccountIDs for FF[{}], environment [{}]", featureFlag.getName(), cfEnvironment);
        List<PatchInstruction> accountToBeAddedPatchInstruction =
            cfAdminApi.getFeatureFlagRulesForTargetingAccounts(tobeAddedAccountIds);
        instructions.addAll(accountToBeAddedPatchInstruction);
      }

      if (rulesTobeDeleted.size() > 0) {
        for (String rule : rulesTobeDeleted) {
          log.info("CF-SYNC Deleting unwanted rule for FF[{}], environment [{}], ruleID : [{}]", featureFlag.getName(),
              cfEnvironment, rule);
          instructions.add(cfAdminApi.removeRule(rule));
        }
      }

      if (instructions.size() > 0) {
        PatchOperation patchOperation = PatchOperation.builder().instructions(instructions).build();
        cfFeature =
            cfAdminApi.patchFeature(featureFlag.getName(), cfAccount, cfOrg, cfProject, cfEnvironment, patchOperation);
      }
    } catch (Exception e) {
      log.error("CF-SYNC Failed to sync featureFlag [{}] in environment [{}]", featureFlag.getName(),
          cfMigrationConfig.getEnvironment(), e);
    }
  }

  private InlineObject createCFFeatureFlag(String featureName, Scope scope, String project) {
    InlineObject inlineObject = new InlineObject();
    inlineObject.setProject(project);
    inlineObject.setIdentifier(featureName);
    inlineObject.setName(featureName);
    inlineObject.setKind(KindEnum.BOOLEAN);
    Variation trueVariation = new Variation();
    trueVariation.setIdentifier("true");
    trueVariation.setName("true");
    trueVariation.setValue("true");

    Variation falseVariation = new Variation();
    falseVariation.setIdentifier("false");
    falseVariation.setName("false");
    falseVariation.setValue("false");

    List<Variation> variations = new ArrayList<>();
    variations.add(trueVariation);
    variations.add(falseVariation);

    inlineObject.setVariations(variations);
    inlineObject.setDefaultOffVariation(falseVariation.getIdentifier());
    inlineObject.setDefaultOnVariation(falseVariation.getIdentifier());
    inlineObject.setPermanent(false);
    inlineObject.setArchived(false);

    return inlineObject;
  }
}
