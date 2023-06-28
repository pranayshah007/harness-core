/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.service.impl;

import static io.harness.data.encoding.EncodingUtils.encodeBase64;
import static io.harness.delegate.message.ManagerMessageConstants.SELF_DESTRUCT;
import static io.harness.delegate.utils.DelegateServiceConstants.STREAM_DELEGATE;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.data.structure.UUIDGenerator;
import io.harness.delegate.authenticator.DelegateSecretManager;
import io.harness.delegate.beans.Delegate;
import io.harness.delegate.beans.Delegate.DelegateKeys;
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
import io.harness.utils.Misc;

import software.wings.beans.Account;
import software.wings.service.intfc.account.AccountCrudObserver;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mongodb.DuplicateKeyException;
import dev.morphia.FindAndModifyOptions;
import dev.morphia.query.Query;
import dev.morphia.query.UpdateOperations;
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
import org.atmosphere.cpr.BroadcasterFactory;

@Singleton
@Slf4j
@ValidateOnExecution
@OwnedBy(HarnessTeam.DEL)
public class DelegateNgTokenServiceImpl implements DelegateNgTokenService, AccountCrudObserver {
  private static final String DEFAULT_TOKEN_NAME = "default_token";
  private final String TOKEN_NAME_ILLEGAL_CHARACTERS = "[~!@#$%^&*'\"/?<>,;.]";

  private final HPersistence persistence;
  private final OutboxService outboxService;
  private final DelegateSecretManager delegateSecretManager;
  private final BroadcasterFactory broadcasterFactory;

  @Inject
  public DelegateNgTokenServiceImpl(HPersistence persistence, OutboxService outboxService,
      DelegateSecretManager delegateSecretManager, BroadcasterFactory broadcasterFactory) {
    this.persistence = persistence;
    this.outboxService = outboxService;
    this.delegateSecretManager = delegateSecretManager;
    this.broadcasterFactory = broadcasterFactory;
  }

  @Override
  public DelegateTokenDetails createToken(
      String accountId, DelegateEntityOwner owner, String tokenName, Long revokeAfter) {
    String token = encodeBase64(Misc.generateSecretKey());
    String tokenIdentifier = tokenName;
    if (owner != null) {
      String orgId = DelegateEntityOwnerHelper.extractOrgIdFromOwnerIdentifier(owner.getIdentifier());
      String projectId = DelegateEntityOwnerHelper.extractProjectIdFromOwnerIdentifier(owner.getIdentifier());
      tokenIdentifier = String.format("%s_%s_%s", tokenName, orgId, projectId);
    }
    DelegateToken delegateToken =
        DelegateToken.builder()
            .accountId(accountId)
            .owner(owner)
            .name(tokenName.trim())
            .isNg(true)
            .status(DelegateTokenStatus.ACTIVE)
            .value(token)
            .encryptedTokenId(delegateSecretManager.encrypt(accountId, token, tokenIdentifier.trim()))
            .createdByNgUser(SourcePrincipalContextBuilder.getSourcePrincipal())
            .revokeAfter(revokeAfter)
            .build();

    validateCreateDelegateTokenRequest(delegateToken);

    try {
      persistence.save(delegateToken);
    } catch (DuplicateKeyException e) {
      throw new InvalidRequestException(
          format("Token with given name %s already exists for given account.", tokenName));
    }

    publishCreateTokenAuditEvent(delegateToken);
    return getDelegateTokenDetails(delegateToken, true);
  }

  @Override
  public DelegateTokenDetails revokeDelegateToken(String accountId, String tokenName) {
    Query<DelegateToken> filterQuery = matchNameTokenQuery(accountId, tokenName);
    validateTokenToBeRevoked(filterQuery.get());

    log.info("Revoking delegate token: {} for account: {}", tokenName, accountId);
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
    List<Delegate> delegates = persistence.createQuery(Delegate.class)
                                   .filter(DelegateKeys.accountId, accountId)
                                   .filter(DelegateKeys.delegateTokenName, tokenName)
                                   .asList();
    delegates.forEach(delegate -> {
      broadcasterFactory.lookup(STREAM_DELEGATE + accountId, true).broadcast(SELF_DESTRUCT + delegate.getUuid());
      log.warn("Sent self destruct command to delegate {} due to revoked token", delegate.getUuid());
    });
    return getDelegateTokenDetails(updatedDelegateToken, false);
  }

  @Override
  public List<DelegateTokenDetails> getDelegateTokens(
      String accountId, DelegateEntityOwner owner, DelegateTokenStatus status, boolean includeValue) {
    Query<DelegateToken> query = persistence.createQuery(DelegateToken.class)
                                     .filter(DelegateTokenKeys.accountId, accountId)
                                     .filter(DelegateTokenKeys.isNg, true)
                                     .filter(DelegateTokenKeys.owner, owner);
    if (status != null) {
      query = query.filter(DelegateTokenKeys.status, status);
    }
    return query.asList()
        .stream()
        .map(token -> getDelegateTokenDetails(token, includeValue))
        .collect(Collectors.toList());
  }

  @Override
  public DelegateTokenDetails getDelegateToken(String accountId, String name) {
    return getDelegateToken(accountId, name, false);
  }

  @Override
  public DelegateTokenDetails getDelegateToken(String accountId, String name, boolean includeValue) {
    DelegateToken delegateToken = matchNameTokenQuery(accountId, name).get();
    if (delegateToken != null) {
      return getDelegateTokenDetails(delegateToken, includeValue);
    }
    return null;
  }

  // some old ng delegates are using accountKey as token, and the value of acccountKey is same as default token in cg
  // which is not encoded. So we should not decode it.
  @Override
  public String getDelegateTokenValue(String accountId, String name) {
    DelegateToken delegateToken = matchNameTokenQuery(accountId, name).get();
    if (delegateToken != null) {
      return delegateSecretManager.getDelegateTokenValue(delegateToken);
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
    String tokenIdentifier = getDefaultTokenName(owner);
    if (owner != null) {
      updateOperations.set(DelegateTokenKeys.owner, owner);
      String orgId = DelegateEntityOwnerHelper.extractOrgIdFromOwnerIdentifier(owner.getIdentifier());
      String projectId = DelegateEntityOwnerHelper.extractProjectIdFromOwnerIdentifier(owner.getIdentifier());
      tokenIdentifier = String.format("%s_%s_%s", getDefaultTokenName(owner), orgId, projectId);
    }
    String tokenNameSanitized = StringUtils.replaceAll(tokenIdentifier, TOKEN_NAME_ILLEGAL_CHARACTERS, "_");
    updateOperations.set(DelegateTokenKeys.encryptedTokenId,
        delegateSecretManager.encrypt(accountId, getDefaultTokenName(owner), tokenNameSanitized.trim()));

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

  public void autoRevokeExpiredTokens() {
    List<DelegateToken> delegateTokenList = persistence.createQuery(DelegateToken.class)
                                                .filter(DelegateTokenKeys.isNg, true)
                                                .filter(DelegateTokenKeys.status, DelegateTokenStatus.ACTIVE)
                                                .field(DelegateTokenKeys.revokeAfter)
                                                .lessThan(System.currentTimeMillis())
                                                .project(DelegateTokenKeys.accountId, true)
                                                .project(DelegateTokenKeys.name, true)
                                                .asList();
    delegateTokenList.forEach(
        delegateToken -> revokeDelegateToken(delegateToken.getAccountId(), delegateToken.getName()));
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
      delegateTokenDetailsBuilder.value(delegateSecretManager.getBase64EncodedTokenValue(delegateToken));
    }

    if (delegateToken.getOwner() != null) {
      delegateTokenDetailsBuilder.ownerIdentifier(delegateToken.getOwner().getIdentifier());
    }

    if (delegateToken.getRevokeAfter() != null) {
      delegateTokenDetailsBuilder.revokeAfter(delegateToken.getRevokeAfter());
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

  private void validateCreateDelegateTokenRequest(DelegateToken delegateToken) {
    Long revokeAfter = delegateToken.getRevokeAfter();
    if (revokeAfter != null && revokeAfter < System.currentTimeMillis()) {
      throw new InvalidRequestException("Token revocation time can not be less than current time.");
    }
  }
}
