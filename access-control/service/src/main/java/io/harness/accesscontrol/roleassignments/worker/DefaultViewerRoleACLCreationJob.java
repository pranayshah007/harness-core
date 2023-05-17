/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.roleassignments.worker;

import static io.harness.NGConstants.ACCOUNT_VIEWER_ROLE;
import static io.harness.NGConstants.DEFAULT_ACCOUNT_LEVEL_RESOURCE_GROUP_IDENTIFIER;
import static io.harness.NGConstants.DEFAULT_ACCOUNT_LEVEL_USER_GROUP_IDENTIFIER;
import static io.harness.NGConstants.DEFAULT_ORGANIZATION_LEVEL_RESOURCE_GROUP_IDENTIFIER;
import static io.harness.NGConstants.DEFAULT_ORGANIZATION_LEVEL_USER_GROUP_IDENTIFIER;
import static io.harness.NGConstants.DEFAULT_PROJECT_LEVEL_RESOURCE_GROUP_IDENTIFIER;
import static io.harness.NGConstants.DEFAULT_PROJECT_LEVEL_USER_GROUP_IDENTIFIER;
import static io.harness.NGConstants.ORGANIZATION_VIEWER_ROLE;
import static io.harness.NGConstants.PROJECT_VIEWER_ROLE;
import static io.harness.accesscontrol.principals.PrincipalType.USER_GROUP;
import static io.harness.authorization.AuthorizationServiceHeader.ACCESS_CONTROL_SERVICE;
import static io.harness.beans.FeatureName.PL_REGENERATE_ACL_FOR_DEFAULT_VIEWER_ROLE;

import static java.time.Duration.ofSeconds;

import io.harness.accesscontrol.acl.persistence.ACL;
import io.harness.accesscontrol.acl.persistence.repositories.ACLRepository;
import io.harness.accesscontrol.roleassignments.persistence.RoleAssignmentDBO;
import io.harness.accesscontrol.roleassignments.persistence.RoleAssignmentDBO.RoleAssignmentDBOKeys;
import io.harness.accesscontrol.scopes.core.ScopeLevel;
import io.harness.accesscontrol.scopes.harness.HarnessScopeLevel;
import io.harness.aggregator.consumers.ACLGeneratorService;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ff.FeatureFlagService;
import io.harness.lock.AcquiredLock;
import io.harness.lock.PersistentLocker;
import io.harness.security.SecurityContextBuilder;
import io.harness.security.dto.ServicePrincipal;

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.util.CloseableIterator;

@Slf4j
@Singleton
@OwnedBy(HarnessTeam.PL)
public class DefaultViewerRoleACLCreationJob implements Runnable {
  private static final int BATCH_SIZE = 1000;
  private static final String DEBUG_MESSAGE = "[DefaultViewerRoleACLCreationJob] ";
  private static final String DEBUG_SCOPE_MESSAGE = "Scope: ";
  private final FeatureFlagService featureFlagService;
  private final ACLRepository aclRepository;
  private final ACLGeneratorService aclGeneratorService;
  private final MongoTemplate mongoTemplate;
  private final PersistentLocker persistentLocker;
  private final Map<String, ACLCreationConfig> aclCreationConfig;
  private static final String LOCK_NAME = "DefaultViewerRoleACLCreationJob";

  @Inject
  public DefaultViewerRoleACLCreationJob(FeatureFlagService featureFlagService,
      @Named(ACL.PRIMARY_COLLECTION) ACLRepository aclRepository, ACLGeneratorService aclGeneratorService,
      MongoTemplate mongoTemplate, PersistentLocker persistentLocker) {
    this.featureFlagService = featureFlagService;
    this.aclRepository = aclRepository;
    this.aclGeneratorService = aclGeneratorService;
    this.mongoTemplate = mongoTemplate;
    this.persistentLocker = persistentLocker;
    this.aclCreationConfig = initialize();
  }

  @NotNull
  private HashMap<String, ACLCreationConfig> initialize() {
    HashMap<String, ACLCreationConfig> config = new HashMap<>();
    // PreQA accounts
    config.put("UP_hsGbkTfChsVu9GYgZbw", new ACLCreationConfig(true, true, true));

    // QA accounts
    config.put("8CsAEysJSCW4Z-bz-Nt2tQ", new ACLCreationConfig(true, true, true));

    // Prod 1 accounts
    config.put("hOGfjDXdRbeM1IHvM1to9Q", new ACLCreationConfig(true, false, false));
    config.put("llMnEGi_RaWKmr0ngrBsJA", new ACLCreationConfig(true, false, false));
    config.put("mTAwmqz1S4SUALu7bLm2jQ", new ACLCreationConfig(true, false, false));
    config.put("n6LTwcmUQRCcajToo7oWzw", new ACLCreationConfig(true, false, false));
    config.put("t11Mz11wRWSXjrCttxHypg", new ACLCreationConfig(true, false, false));
    config.put("wvEGAn7LR7ikwCW4uCW7Yw", new ACLCreationConfig(false, false, true));

    // Prod 2 accounts
    config.put("04Iq9MDcT9WOBwwS6C4oKw", new ACLCreationConfig(false, true, true));
    config.put("8d4c98vSRWOM5Ml1ZirT8A", new ACLCreationConfig(false, false, true));
    config.put("EGQPNQHxTNy9hYuowHxEUg", new ACLCreationConfig(false, false, true));
    config.put("XQeaPqSZTUOScNp_UNOfjw", new ACLCreationConfig(false, false, true));
    config.put("yTbabkw4SdCPZdnEWynUNg", new ACLCreationConfig(false, false, true));

    // Prod 3 accounts
    config.put("Hyt0C3nkQ56oydLO0JuQgw", new ACLCreationConfig(true, true, true));
    config.put("FZe6OYBoQny8mLvDElvz6Q", new ACLCreationConfig(true, true, true));

    return config;
  }

  @Override
  public void run() {
    log.info(DEBUG_MESSAGE + "started...");
    try (AcquiredLock<?> lock = persistentLocker.tryToAcquireInfiniteLockWithPeriodicRefresh(LOCK_NAME, ofSeconds(5))) {
      if (lock == null) {
        log.info(DEBUG_MESSAGE + "failed to acquire lock");
        return;
      }
      try {
        SecurityContextBuilder.setContext(new ServicePrincipal(ACCESS_CONTROL_SERVICE.getServiceId()));
        log.info(DEBUG_MESSAGE + "Setting SecurityContext completed.");
        execute();
      } catch (Exception ex) {
        log.error(DEBUG_MESSAGE + " unexpected error occurred while Setting SecurityContext", ex);
      } finally {
        SecurityContextBuilder.unsetCompleteContext();
        log.info(DEBUG_MESSAGE + "Unsetting SecurityContext completed.");
      }
    } catch (Exception ex) {
      log.error(DEBUG_MESSAGE + " failed to acquire lock", ex);
    }
    log.info(DEBUG_MESSAGE + " completed...");
  }

  @VisibleForTesting
  void execute() {
    try {
      Set<String> targetAccounts = getAccountsForFFEnabled();

      for (String account : targetAccounts) {
        if (aclCreationConfig.containsKey(account)) {
          ACLCreationConfig config = aclCreationConfig.get(account);
          if (config.isEnabledForAccount()) {
            migrate(account, HarnessScopeLevel.ACCOUNT);
          }
          if (config.isEnabledForOrganization()) {
            migrate(account, HarnessScopeLevel.ORGANIZATION);
          }
          if (config.isEnabledForProject()) {
            migrate(account, HarnessScopeLevel.PROJECT);
          }
          Thread.sleep(60000);
        }
      }
    } catch (Exception ex) {
      log.error(DEBUG_MESSAGE + "Failed to filter accounts for ff", ex);
    }
  }

  private Set<String> getAccountsForFFEnabled() {
    Set<String> accountIds = aclCreationConfig.keySet();
    Set<String> targetAccounts = new HashSet<>();
    try {
      for (String accountId : accountIds) {
        boolean enabledAclRegeneration =
            featureFlagService.isEnabled(PL_REGENERATE_ACL_FOR_DEFAULT_VIEWER_ROLE, accountId);
        if (enabledAclRegeneration) {
          targetAccounts.add(accountId);
        }
      }
    } catch (Exception ex) {
      log.error(DEBUG_MESSAGE + "Failed to filter accounts for FF PL_REGENERATE_ACL_FOR_DEFAULT_VIEWER_ROLE");
    }
    return targetAccounts;
  }

  private CloseableIterator<RoleAssignmentDBO> runQueryWithBatch(String scopeIdentifier, ScopeLevel scopeLevel) {
    Pattern startsWithScope = Pattern.compile("^" + scopeIdentifier);
    Criteria criteria = Criteria.where(RoleAssignmentDBOKeys.scopeIdentifier)
                            .regex(startsWithScope)
                            .and(RoleAssignmentDBOKeys.resourceGroupIdentifier)
                            .is(getResourceGroupIdentifier(scopeLevel))
                            .and(RoleAssignmentDBOKeys.roleIdentifier)
                            .is(getRoleIdentifier(scopeLevel))
                            .and(RoleAssignmentDBOKeys.principalIdentifier)
                            .is(getPrincipalIdentifier(scopeLevel))
                            .and(RoleAssignmentDBOKeys.principalScopeLevel)
                            .is(scopeLevel.toString())
                            .and(RoleAssignmentDBOKeys.principalType)
                            .is(USER_GROUP)
                            .and(RoleAssignmentDBOKeys.scopeLevel)
                            .is(scopeLevel.toString());

    Query query = new Query();
    query.addCriteria(criteria);
    query.cursorBatchSize(BATCH_SIZE);
    return mongoTemplate.stream(query, RoleAssignmentDBO.class);
  }

  private String getPrincipalIdentifier(ScopeLevel scopeLevel) {
    if (HarnessScopeLevel.ORGANIZATION.equals(scopeLevel)) {
      return DEFAULT_ORGANIZATION_LEVEL_USER_GROUP_IDENTIFIER;
    } else if (HarnessScopeLevel.PROJECT.equals(scopeLevel)) {
      return DEFAULT_PROJECT_LEVEL_USER_GROUP_IDENTIFIER;
    } else {
      return DEFAULT_ACCOUNT_LEVEL_USER_GROUP_IDENTIFIER;
    }
  }

  private String getRoleIdentifier(ScopeLevel scopeLevel) {
    if (HarnessScopeLevel.ORGANIZATION.equals(scopeLevel)) {
      return ORGANIZATION_VIEWER_ROLE;
    } else if (HarnessScopeLevel.PROJECT.equals(scopeLevel)) {
      return PROJECT_VIEWER_ROLE;
    } else {
      return ACCOUNT_VIEWER_ROLE;
    }
  }

  private String getResourceGroupIdentifier(ScopeLevel scopeLevel) {
    if (HarnessScopeLevel.ORGANIZATION.equals(scopeLevel)) {
      return DEFAULT_ORGANIZATION_LEVEL_RESOURCE_GROUP_IDENTIFIER;
    } else if (HarnessScopeLevel.PROJECT.equals(scopeLevel)) {
      return DEFAULT_PROJECT_LEVEL_RESOURCE_GROUP_IDENTIFIER;
    } else {
      return DEFAULT_ACCOUNT_LEVEL_RESOURCE_GROUP_IDENTIFIER;
    }
  }

  private long upsertACLs(RoleAssignmentDBO roleAssignment) {
    aclRepository.deleteByRoleAssignmentId(roleAssignment.getId());

    long numberOfACLsCreated = aclGeneratorService.createACLsForRoleAssignment(roleAssignment);
    numberOfACLsCreated +=
        aclGeneratorService.createImplicitACLsForRoleAssignment(roleAssignment, new HashSet<>(), new HashSet<>());
    return numberOfACLsCreated;
  }

  public void migrate(String accountIdentifier, ScopeLevel scopeLevel) {
    String scopeIdentifier = "/ACCOUNT/" + accountIdentifier;
    log.info(DEBUG_MESSAGE + DEBUG_SCOPE_MESSAGE + scopeLevel + ", starting migration....");
    try (CloseableIterator<RoleAssignmentDBO> iterator = runQueryWithBatch(scopeIdentifier, scopeLevel)) {
      while (iterator.hasNext()) {
        RoleAssignmentDBO roleAssignmentDBO = iterator.next();
        try {
          log.info(DEBUG_MESSAGE + DEBUG_SCOPE_MESSAGE + scopeLevel
                  + ", Number of ACLs created during for roleAssignment {} is : {}",
              roleAssignmentDBO.getIdentifier(), upsertACLs(roleAssignmentDBO));
        } catch (Exception e) {
          log.info(DEBUG_MESSAGE + DEBUG_SCOPE_MESSAGE + scopeLevel
                  + ", Unable to process roleassignment: {} due to exception {}",
              roleAssignmentDBO.getIdentifier(), e);
        }
      }
    }
    log.info(DEBUG_MESSAGE + DEBUG_SCOPE_MESSAGE + scopeLevel + ", migration successful....");
  }

  @Data
  @AllArgsConstructor
  class ACLCreationConfig {
    boolean enabledForAccount;
    boolean enabledForOrganization;
    boolean enabledForProject;
  }
}
