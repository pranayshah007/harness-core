/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.service.impl;

import static java.lang.String.format;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.data.structure.UUIDGenerator;
import io.harness.delegate.authenticator.DelegateSecretManager;
import io.harness.delegate.beans.DelegateToken;
import io.harness.delegate.beans.DelegateToken.DelegateTokenKeys;
import io.harness.delegate.beans.DelegateTokenDetails;
import io.harness.delegate.beans.DelegateTokenDetails.DelegateTokenDetailsBuilder;
import io.harness.delegate.beans.DelegateTokenStatus;
import io.harness.exception.InvalidRequestException;
import io.harness.persistence.HPersistence;
import io.harness.service.intfc.DelegateTokenService;
import io.harness.utils.Misc;

import software.wings.beans.Account;
import software.wings.beans.Event;
import software.wings.service.impl.AuditServiceHelper;
import software.wings.service.intfc.account.AccountCrudObserver;
import software.wings.service.intfc.ownership.OwnedByAccount;

import com.google.inject.Inject;
import com.mongodb.DuplicateKeyException;
import dev.morphia.FindAndModifyOptions;
import dev.morphia.query.Query;
import dev.morphia.query.UpdateOperations;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.apache.commons.lang3.StringUtils;

@OwnedBy(HarnessTeam.DEL)
@TargetModule(HarnessModule._420_DELEGATE_SERVICE)
public class DelegateTokenServiceImpl implements DelegateTokenService, AccountCrudObserver, OwnedByAccount {
  @Inject private HPersistence persistence;
  @Inject private AuditServiceHelper auditServiceHelper;
  @Inject private DelegateSecretManager delegateSecretManager;

  private static final String DEFAULT_TOKEN_NAME = "default";

  @Override
  public DelegateTokenDetails createDelegateToken(String accountId, String name) {
    String token = Misc.generateSecretKey();
    DelegateToken delegateToken = DelegateToken.builder()
                                      .accountId(accountId)
                                      .createdAt(System.currentTimeMillis())
                                      .name(name.trim())
                                      .status(DelegateTokenStatus.ACTIVE)
                                      .value(token)
                                      .encryptedTokenId(delegateSecretManager.encrypt(accountId, token, name))
                                      .build();

    try {
      persistence.save(delegateToken);
    } catch (DuplicateKeyException e) {
      throw new InvalidRequestException(format("Token with given name %s already exists for the account.", name));
    }
    auditServiceHelper.reportForAuditingUsingAccountId(
        delegateToken.getAccountId(), null, delegateToken, Event.Type.CREATE);

    return getDelegateTokenDetails(delegateToken, true);
  }

  @Override
  public DelegateTokenDetails upsertDefaultToken(String accountId, String tokenValue) {
    Query<DelegateToken> query = persistence.createQuery(DelegateToken.class)
                                     .filter(DelegateTokenKeys.accountId, accountId)
                                     .filter(DelegateTokenKeys.name, DEFAULT_TOKEN_NAME);

    UpdateOperations<DelegateToken> updateOperations =
        persistence.createUpdateOperations(DelegateToken.class)
            .setOnInsert(DelegateTokenKeys.uuid, UUIDGenerator.generateUuid())
            .setOnInsert(DelegateTokenKeys.accountId, accountId)
            .set(DelegateTokenKeys.name, DEFAULT_TOKEN_NAME)
            .set(DelegateTokenKeys.status, DelegateTokenStatus.ACTIVE)
            .set(DelegateTokenKeys.value, tokenValue)
            .set(DelegateTokenKeys.encryptedTokenId,
                delegateSecretManager.encrypt(accountId, tokenValue, DEFAULT_TOKEN_NAME));

    DelegateToken delegateToken = persistence.upsert(query, updateOperations, HPersistence.upsertReturnNewOptions);

    return getDelegateTokenDetails(delegateToken, false);
  }

  @Override
  public void revokeDelegateToken(String accountId, String tokenName) {
    Query<DelegateToken> filterQuery = persistence.createQuery(DelegateToken.class)
                                           .field(DelegateTokenKeys.accountId)
                                           .equal(accountId)
                                           .field(DelegateTokenKeys.name)
                                           .equal(tokenName);

    DelegateToken originalDelegateToken = filterQuery.get();
    UpdateOperations<DelegateToken> updateOperations =
        persistence.createUpdateOperations(DelegateToken.class)
            .set(DelegateTokenKeys.status, DelegateTokenStatus.REVOKED)
            .set(DelegateTokenKeys.validUntil,
                Date.from(OffsetDateTime.now().plusDays(DelegateToken.TTL.toDays()).toInstant()));

    // we are not removing token from delegateTokenCache in DelegateTokenCacheHelper, since the cache has an expiry of 3
    // mins

    DelegateToken updatedDelegateToken =
        persistence.findAndModify(filterQuery, updateOperations, new FindAndModifyOptions());
    auditServiceHelper.reportForAuditingUsingAccountId(
        accountId, originalDelegateToken, updatedDelegateToken, Event.Type.UPDATE);
  }

  @Override
  public void deleteDelegateToken(String accountId, String tokenName) {
    Query<DelegateToken> deleteQuery = persistence.createQuery(DelegateToken.class)
                                           .field(DelegateTokenKeys.accountId)
                                           .equal(accountId)
                                           .field(DelegateTokenKeys.name)
                                           .equal(tokenName);

    DelegateToken delegateToken = deleteQuery.get();

    if (!delegateToken.getName().equals(DEFAULT_TOKEN_NAME)) {
      persistence.delete(delegateToken);
      auditServiceHelper.reportDeleteForAuditingUsingAccountId(accountId, delegateToken);
    }
  }

  @Override
  public String getTokenValue(String accountId, String tokenName) {
    DelegateToken delegateToken = persistence.createQuery(DelegateToken.class)
                                      .field(DelegateTokenKeys.accountId)
                                      .equal(accountId)
                                      .field(DelegateTokenKeys.name)
                                      .equal(tokenName)
                                      .get();

    return delegateToken != null ? delegateSecretManager.getDelegateTokenValue(delegateToken) : null;
  }

  @Override
  public List<DelegateTokenDetails> getDelegateTokens(String accountId, DelegateTokenStatus status, String tokenName) {
    List<DelegateToken> queryResults;

    Query<DelegateToken> query = persistence.createQuery(DelegateToken.class)
                                     .field(DelegateTokenKeys.accountId)
                                     .equal(accountId)
                                     .field(DelegateTokenKeys.isNg)
                                     .notEqual(true);

    if (null != status) {
      query = query.field(DelegateTokenKeys.status).equal(status);
    }

    if (!StringUtils.isEmpty(tokenName)) {
      query = query.field(DelegateTokenKeys.name).startsWith(tokenName);
    }

    queryResults = query.asList();

    List<DelegateTokenDetails> delegateTokenDetailsList = new ArrayList<>();

    // Removing token values
    queryResults.forEach(token -> delegateTokenDetailsList.add(getDelegateTokenDetails(token, false)));

    return delegateTokenDetailsList;
  }

  private DelegateTokenDetails getDelegateTokenDetails(DelegateToken delegateToken, boolean includeTokenValue) {
    DelegateTokenDetailsBuilder delegateTokenDetailsBuilder = DelegateTokenDetails.builder();

    delegateTokenDetailsBuilder.uuid(delegateToken.getUuid())
        .accountId(delegateToken.getAccountId())
        .name(delegateToken.getName())
        .createdAt(delegateToken.getCreatedAt())
        .createdBy(delegateToken.getCreatedBy())
        .status(delegateToken.getStatus());

    if (includeTokenValue) {
      delegateTokenDetailsBuilder.value(delegateSecretManager.getDelegateTokenValue(delegateToken));
    }

    return delegateTokenDetailsBuilder.build();
  }

  @Override
  public void onAccountCreated(Account account) {
    upsertDefaultToken(account.getUuid(), account.getAccountKey());
  }

  @Override
  public void onAccountUpdated(Account account) {
    // do nothing
  }

  @Override
  public void deleteByAccountId(String accountId) {
    persistence.delete(persistence.createQuery(DelegateToken.class).filter(DelegateTokenKeys.accountId, accountId));
  }
}
