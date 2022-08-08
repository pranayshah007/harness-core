/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.service.impl;

import static io.harness.data.encoding.EncodingUtils.decodeBase64ToString;
import static io.harness.data.encoding.EncodingUtils.encodeBase64;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.UUIDGenerator;
import io.harness.delegate.beans.DelegateEntityOwner;
import io.harness.delegate.beans.DelegateToken;
import io.harness.delegate.beans.DelegateToken.DelegateTokenKeys;
import io.harness.delegate.beans.DelegateTokenDetails;
import io.harness.delegate.beans.DelegateTokenDetails.DelegateTokenDetailsBuilder;
import io.harness.delegate.beans.DelegateTokenStatus;
import io.harness.delegate.dto.DelegateNgTokenDTO;
import io.harness.delegate.events.DelegateNgTokenCreateEvent;
import io.harness.delegate.events.DelegateNgTokenRevokeEvent;
import io.harness.delegate.service.intfc.DelegateNgTokenService;
import io.harness.delegate.utils.DelegateEntityOwnerHelper;
import io.harness.exception.InvalidRequestException;
import io.harness.outbox.api.OutboxService;
import io.harness.persistence.HPersistence;
import io.harness.security.SourcePrincipalContextBuilder;
import io.harness.service.intfc.DelegateCache;
import io.harness.utils.Misc;

import software.wings.beans.Account;
import software.wings.service.intfc.account.AccountCrudObserver;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mongodb.DuplicateKeyException;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.validation.executable.ValidateOnExecution;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.mongodb.morphia.FindAndModifyOptions;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.UpdateOperations;

@Singleton
@Slf4j
@ValidateOnExecution
@OwnedBy(HarnessTeam.DEL)
public class DelegateNgTokenServiceImpl implements DelegateNgTokenService, AccountCrudObserver {
  private static final String DEFAULT_TOKEN_NAME = "default_token";
  private final HPersistence persistence;
  private final OutboxService outboxService;
  private final DelegateCache delegateCache;

  @Inject
  public DelegateNgTokenServiceImpl(
      HPersistence persistence, OutboxService outboxService, DelegateCache delegateCache) {
    this.persistence = persistence;
    this.outboxService = outboxService;
    this.delegateCache = delegateCache;
  }

  @Override
  public DelegateTokenDetails createToken(String accountId, DelegateEntityOwner owner, String name) {
    DelegateToken delegateToken = DelegateToken.builder()
                                      .accountId(accountId)
                                      .owner(owner)
                                      .name(name.trim())
                                      .isNg(true)
                                      .status(DelegateTokenStatus.ACTIVE)
                                      .value(encodeBase64(Misc.generateSecretKey()))
                                      .createdByNgUser(SourcePrincipalContextBuilder.getSourcePrincipal())
                                      .build();

    try {
      persistence.save(delegateToken);
    } catch (DuplicateKeyException e) {
      throw new InvalidRequestException(format("Token with given name %s already exists for given account.", name));
    }

    publishCreateTokenAuditEvent(delegateToken);
    return getDelegateTokenDetails(delegateToken, true);
  }

  @Override
  public DelegateTokenDetails revokeDelegateToken(String accountId, DelegateEntityOwner owner, String tokenName) {
    Query<DelegateToken> filterQuery = matchNameTokenQuery(accountId, tokenName);
    validateTokenToBeRevoked(filterQuery.get());
    UpdateOperations<DelegateToken> updateOperations =
        persistence.createUpdateOperations(DelegateToken.class)
            .set(DelegateTokenKeys.status, DelegateTokenStatus.REVOKED)
            .set(DelegateTokenKeys.validUntil,
                Date.from(OffsetDateTime.now().plusDays(DelegateToken.TTL.toDays()).toInstant()));
    DelegateToken updatedDelegateToken =
        persistence.findAndModify(filterQuery, updateOperations, new FindAndModifyOptions());

    // we are not removing token from delegateTokenCache in DelegateTokenCacheHelper, since the cache has an expiry of 3
    // mins.

    publishRevokeTokenAuditEvent(updatedDelegateToken);

    return getDelegateTokenDetails(updatedDelegateToken, false);
  }

  @Override
  public List<DelegateTokenDetails> getDelegateTokens(
      String accountId, DelegateEntityOwner owner, DelegateTokenStatus status) {
    Query<DelegateToken> query = persistence.createQuery(DelegateToken.class)
                                     .filter(DelegateTokenKeys.accountId, accountId)
                                     .filter(DelegateTokenKeys.isNg, true)
                                     .filter(DelegateTokenKeys.owner, owner);
    if (status != null) {
      query = query.filter(DelegateTokenKeys.status, status);
    }
    return query.asList().stream().map(token -> getDelegateTokenDetails(token, false)).collect(Collectors.toList());
  }

  @Override
  public DelegateTokenDetails getDelegateToken(String accountId, String name) {
    DelegateToken delegateToken = matchNameTokenQuery(accountId, name).get();
    if (delegateToken != null) {
      return getDelegateTokenDetails(delegateToken, false);
    }
    return null;
  }

  // some old ng delegates are using accountKey as token, and the value of acccountKey is same as default token in cg
  // which is not encoded. So we should not decode it.
  @Override
  public String getDelegateTokenValue(String accountId, String name) {
    DelegateToken delegateToken = matchNameTokenQuery(accountId, name).get();
    if (delegateToken != null) {
      if (delegateToken.isNg()) {
        return decodeBase64ToString(delegateToken.getValue());
      } else {
        return delegateToken.getValue();
      }
    }
    log.warn("Not able to find delegate token {} for account {} . Please verify manually.", name, accountId);
    return null;
  }

  @Override
  public DelegateTokenDetails upsertDefaultToken(String accountId, DelegateEntityOwner owner, boolean skipIfExists) {
    Query<DelegateToken> query = persistence.createQuery(DelegateToken.class)
                                     .filter(DelegateTokenKeys.accountId, accountId)
                                     .filter(DelegateTokenKeys.name, getDefaultTokenName(owner));

    if (owner != null) {
      query = query.filter(DelegateTokenKeys.owner, owner);
    }

    Query<DelegateToken> queryExistsActive = query.filter(DelegateTokenKeys.status, DelegateTokenStatus.ACTIVE);
    Optional<DelegateToken> token = queryExistsActive.asList().stream().findAny();
    if (token.isPresent() && skipIfExists) {
      log.info("Active default Delegate NG Token already exists for account {}, organization {} and project {}",
          accountId, extractOrganization(owner), extractProject(owner));
      return getDelegateTokenDetails(token.get(), true);
    }

    UpdateOperations<DelegateToken> updateOperations =
        persistence.createUpdateOperations(DelegateToken.class)
            .setOnInsert(DelegateTokenKeys.uuid, UUIDGenerator.generateUuid())
            .setOnInsert(DelegateTokenKeys.accountId, accountId)
            .set(DelegateTokenKeys.name, getDefaultTokenName(owner))
            .set(DelegateTokenKeys.status, DelegateTokenStatus.ACTIVE)
            .set(DelegateTokenKeys.isNg, true)
            .set(DelegateTokenKeys.value, encodeBase64(Misc.generateSecretKey()));

    if (owner != null) {
      updateOperations.set(DelegateTokenKeys.owner, owner);
    }

    DelegateToken delegateToken = persistence.upsert(query, updateOperations, HPersistence.upsertReturnNewOptions);
    log.info("Default Delegate NG Token inserted/updated for account {}, organization {} and project {}", accountId,
        extractOrganization(owner), extractProject(owner));
    return getDelegateTokenDetails(delegateToken, true);
  }

  @Override
  public void onAccountCreated(Account account) {
    upsertDefaultToken(account.getUuid(), null, false);
  }

  @Override
  public void onAccountUpdated(Account account) {
    // do nothing
  }

  @Override
  public void deleteByAccountId(String accountId) {
    try {
      log.info("Deleting all delegate tokens for accountId {}", accountId);
      persistence.delete(persistence.createQuery(DelegateToken.class).filter(DelegateTokenKeys.accountId, accountId));
    } catch (Exception e) {
      log.error("Error occurred during deleting all delegate tokens for accountId {}", accountId, e);
    }
  }

  @Override
  public void deleteAllTokensOwnedByOrgAndProject(String accountId, DelegateEntityOwner owner) {
    try {
      log.info("Deleting all delegate tokens for accountId {} , org {} and project {}", accountId,
          extractOrganization(owner), extractProject(owner));
      persistence.delete(persistence.createQuery(DelegateToken.class)
                             .filter(DelegateTokenKeys.accountId, accountId)
                             .filter(DelegateTokenKeys.owner, owner));
    } catch (Exception e) {
      log.error("Error occurred during deleting all delegate tokens for accountId {} , org {} and project {}",
          accountId, extractOrganization(owner), extractProject(owner), e);
    }
  }

  @Override
  public String getDefaultTokenName(DelegateEntityOwner owner) {
    return owner == null ? DEFAULT_TOKEN_NAME : DEFAULT_TOKEN_NAME.concat("_" + owner.getIdentifier());
  }

  private Query<DelegateToken> matchNameTokenQuery(String accountId, String tokenName) {
    return persistence.createQuery(DelegateToken.class)
        .field(DelegateTokenKeys.accountId)
        .equal(accountId)
        .field(DelegateTokenKeys.name)
        .equal(tokenName);
  }

  private DelegateTokenDetails getDelegateTokenDetails(DelegateToken delegateToken, boolean includeTokenValue) {
    DelegateTokenDetailsBuilder delegateTokenDetailsBuilder = DelegateTokenDetails.builder()
                                                                  .accountId(delegateToken.getAccountId())
                                                                  .name(delegateToken.getName())
                                                                  .createdAt(delegateToken.getCreatedAt())
                                                                  .createdByNgUser(delegateToken.getCreatedByNgUser())
                                                                  .status(delegateToken.getStatus());

    if (includeTokenValue) {
      delegateTokenDetailsBuilder.value(decodeBase64ToString(delegateToken.getValue()));
    }

    if (delegateToken.getOwner() != null) {
      delegateTokenDetailsBuilder.ownerIdentifier(delegateToken.getOwner().getIdentifier());
    }

    return delegateTokenDetailsBuilder.build();
  }

  private void publishCreateTokenAuditEvent(DelegateToken delegateToken) {
    DelegateNgTokenDTO token = convert(delegateToken);
    outboxService.save(DelegateNgTokenCreateEvent.builder().token(token).build());
  }

  private void publishRevokeTokenAuditEvent(DelegateToken delegateToken) {
    DelegateNgTokenDTO token = convert(delegateToken);
    outboxService.save(DelegateNgTokenRevokeEvent.builder().token(token).build());
  }

  private void validateTokenToBeRevoked(DelegateToken delegateToken) {
    if (delegateToken == null) {
      throw new InvalidRequestException("Specified token does not exist.");
    }
    if (DelegateTokenStatus.REVOKED.equals(delegateToken.getStatus())) {
      throw new InvalidRequestException("Specified token is already revoked.");
    }
  }

  private String extractOrganization(DelegateEntityOwner owner) {
    return owner != null ? DelegateEntityOwnerHelper.extractOrgIdFromOwnerIdentifier(owner.getIdentifier())
                         : StringUtils.EMPTY;
  }

  private String extractProject(DelegateEntityOwner owner) {
    return owner != null ? DelegateEntityOwnerHelper.extractProjectIdFromOwnerIdentifier(owner.getIdentifier())
                         : StringUtils.EMPTY;
  }

  private DelegateNgTokenDTO convert(DelegateToken delegateToken) {
    return DelegateNgTokenDTO.builder()
        .accountIdentifier(delegateToken.getAccountId())
        .orgIdentifier(DelegateEntityOwnerHelper.extractOrgIdFromOwnerIdentifier(
            delegateToken.getOwner() != null ? delegateToken.getOwner().getIdentifier() : null))
        .projectIdentifier(DelegateEntityOwnerHelper.extractProjectIdFromOwnerIdentifier(
            delegateToken.getOwner() != null ? delegateToken.getOwner().getIdentifier() : null))
        .name(delegateToken.getName())
        .identifier(delegateToken.getUuid())
        .build();
  }

  @Override
  public Map<String, Boolean> isDelegateTokenActive(String accountId, List<String> tokensNameList) {
    Map<String, Boolean> delegateTokenStatusMap = new HashMap<>();
    List<DelegateToken> delegateTokens = persistence.createQuery(DelegateToken.class)
                                             .filter(DelegateTokenKeys.accountId, accountId)
                                             .field(DelegateTokenKeys.name)
                                             .in(tokensNameList)
                                             .project(DelegateTokenKeys.name, true)
                                             .project(DelegateTokenKeys.status, true)
                                             .asList();
    delegateTokens.forEach(delegateToken
        -> delegateTokenStatusMap.put(
            delegateToken.getName(), DelegateTokenStatus.ACTIVE.equals(delegateToken.getStatus())));
    return delegateTokenStatusMap;
  }
}
