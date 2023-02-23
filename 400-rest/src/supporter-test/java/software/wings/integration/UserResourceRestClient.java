/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.integration;

import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.network.Localhost.getLocalHostName;

import static software.wings.beans.Account.Builder.anAccount;
import static software.wings.beans.CGConstants.GLOBAL_APP_ID;
import static software.wings.beans.User.Builder.anUser;
import static software.wings.utils.WingsIntegrationTestConstants.API_BASE;
import static software.wings.utils.WingsIntegrationTestConstants.DEFAULT_USER_KEY;
import static software.wings.utils.WingsIntegrationTestConstants.adminPassword;
import static software.wings.utils.WingsIntegrationTestConstants.adminUserEmail;
import static software.wings.utils.WingsIntegrationTestConstants.adminUserName;
import static software.wings.utils.WingsIntegrationTestConstants.defaultAccountId;
import static software.wings.utils.WingsIntegrationTestConstants.defaultAccountName;
import static software.wings.utils.WingsIntegrationTestConstants.defaultCompanyName;
import static software.wings.utils.WingsIntegrationTestConstants.delegateAccountSecret;

import static java.lang.String.format;
import static org.apache.commons.codec.binary.Base64.encodeBase64String;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.crypto.bcrypt.BCrypt.hashpw;

import io.harness.persistence.HPersistence;
import io.harness.rest.RestResponse;

import software.wings.beans.Account;
import software.wings.beans.User;
import software.wings.beans.User.UserKeys;
import software.wings.service.intfc.AccountService;
import software.wings.service.intfc.RoleService;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.nimbusds.jose.EncryptionMethod;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWEAlgorithm;
import com.nimbusds.jose.JWEHeader;
import com.nimbusds.jose.KeyLengthException;
import com.nimbusds.jose.crypto.DirectEncrypter;
import com.nimbusds.jwt.EncryptedJWT;
import com.nimbusds.jwt.JWTClaimsSet;
import java.net.UnknownHostException;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import javax.crypto.spec.SecretKeySpec;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.Invocation.Builder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.GenericType;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.springframework.security.crypto.bcrypt.BCrypt;

@Singleton
@Slf4j
public class UserResourceRestClient {
  @Inject private HPersistence persistence;
  @Inject private AccountService accountService;
  @Inject private RoleService roleService;

  private ConcurrentHashMap<String, User> userCachedEntity = new ConcurrentHashMap<>();

  public User getLoggedInUser(Client client) {
    return userCachedEntity.computeIfAbsent(DEFAULT_USER_KEY, key -> fetchOrCreateUser(client));
  }

  private User fetchOrCreateUser(Client client) {
    User user = null;
    try {
      user = loginUser(client, adminUserEmail, new String(adminPassword));
    } catch (Exception e) {
      // ignore
    }
    if (user == null || user.getToken() == null || isEmpty(user.getAccounts()) || user.getAccounts().get(0) == null
        || user.getAccounts().get(0).getUuid() == null) {
      Account account = setupAccountUserFirstTime();
      user = loginUser(client, adminUserEmail, new String(adminPassword));
    }
    return user;
  }

  public String getUserToken(Client client) {
    return getLoggedInUser(client) == null ? "" : getLoggedInUser(client).getToken();
  }

  public String getDelegateToken(Client client) {
    return getDelegateToken(API_BASE, getSeedAccount(client).getUuid(), delegateAccountSecret);
  }

  public Account getSeedAccount(Client client) {
    return getLoggedInUser(client).getAccounts().get(0);
  }

  public User loginUser(Client client, final String userName, final String password) {
    String basicAuthValue = "Basic " + encodeBase64String(format("%s:%s", userName, password).getBytes());
    RestResponse<User> response;
    response = client.target(API_BASE + "/users/login")
                   .request()
                   .header("Authorization", basicAuthValue)
                   .get(new GenericType<RestResponse<User>>() {});
    if (response.getResource() != null) {
      return response.getResource();
    }
    return null;
  }

  public Account setupAccountUserFirstTime() {
    // Not going through register user route as account id/secret should match the delegate config
    Account account = accountService.get(defaultAccountId);

    if (account == null) {
      account = anAccount()
                    .withUuid(defaultAccountId)
                    .withAccountKey(delegateAccountSecret)
                    .withAccountName(defaultAccountName)
                    .withCompanyName(defaultCompanyName)
                    .build();
      try {
        account = accountService.save(account, false);
      } catch (Exception e) {
        account = accountService.get(defaultAccountId);
      }
    }

    User user = persistence.createQuery(User.class).filter(User.EMAIL_KEY, adminUserEmail).get();

    if (user == null) {
      user = anUser().email(adminUserEmail).name(adminUserName).password(adminPassword).build();
      user.setAppId(GLOBAL_APP_ID);
      user.getAccounts().add(account);
      user.setEmailVerified(true);
      String hashed = hashpw(new String(user.getPassword()), BCrypt.gensalt());
      user.setPasswordHash(hashed);
      user.setPasswordChangedAt(System.currentTimeMillis());
      user.setRoles(Lists.newArrayList(roleService.getAccountAdminRole(account.getUuid())));
      try {
        persistence.save(user);
      } catch (Exception e) {
        user = persistence.createQuery(User.class).filter(User.EMAIL_KEY, adminUserEmail).get();
      }
    } else {
      Account finalAccount = account;
      if (isEmpty(user.getAccounts())
          || !user.getAccounts().stream().anyMatch(account1 -> finalAccount.getUuid().equals(account1.getUuid()))) {
        persistence.update(user, persistence.createUpdateOperations(User.class).addToSet(UserKeys.accounts, account));
      }
    }

    return account;
  }

  public String getDelegateToken(String url, String accountId, String delegateAccountSecret) {
    JWTClaimsSet jwtClaims = new JWTClaimsSet.Builder()
                                 .issuer(getLocalHostName())
                                 .subject(accountId)
                                 .audience(url)
                                 .expirationTime(new Date(System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(5)))
                                 .notBeforeTime(new Date())
                                 .issueTime(new Date())
                                 .jwtID(UUID.randomUUID().toString())
                                 .build();

    JWEHeader header = new JWEHeader(JWEAlgorithm.DIR, EncryptionMethod.A128GCM);
    EncryptedJWT jwt = new EncryptedJWT(header, jwtClaims);
    DirectEncrypter directEncrypter = null;
    byte[] encodedKey = new byte[0];
    try {
      encodedKey = Hex.decodeHex(delegateAccountSecret.toCharArray());
    } catch (DecoderException e) {
      log.error("", e);
    }
    try {
      directEncrypter = new DirectEncrypter(new SecretKeySpec(encodedKey, 0, encodedKey.length, "AES"));
    } catch (KeyLengthException e) {
      log.error("", e);
    }

    try {
      jwt.encrypt(directEncrypter);
    } catch (JOSEException e) {
      log.error("", e);
    }
    return jwt.serialize();
  }

  User getUser(Client client, String userToken) {
    WebTarget target = client.target(API_BASE + "/user/user");
    RestResponse<User> response =
        getRequestBuilderWithAuthHeader(userToken, target).get(new GenericType<RestResponse<User>>() {});
    assertThat(response.getResource()).isInstanceOf(User.class);
    return response.getResource();
  }

  public Builder getRequestBuilderWithAuthHeader(String userToken, WebTarget target) {
    return target.request().header("Authorization", "Bearer " + userToken);
  }

  protected Builder getDelegateRequestBuilderWithAuthHeader(String delegateToken, WebTarget target)
      throws UnknownHostException {
    return target.request().header("Authorization", "Delegate " + delegateToken);
  }

  protected Builder getRequestBuilder(WebTarget target) {
    return target.request();
  }

  public void clearCache() {
    this.userCachedEntity.clear();
  }
}
