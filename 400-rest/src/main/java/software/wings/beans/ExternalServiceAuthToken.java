/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.beans;

import static software.wings.beans.CGConstants.GLOBAL_APP_ID;
import static software.wings.utils.CryptoUtils.secureRandAlphaNumString;

import io.harness.annotation.HarnessEntity;
import io.harness.annotations.StoreIn;
import io.harness.ng.DbAliases;

import org.mongodb.morphia.annotations.Entity;

/**
 * Created by rsingh on 06/21/17.
 */
@StoreIn(DbAliases.HARNESS)
@Entity(value = "externalServiceAuthTokens", noClassnameStored = true)
@HarnessEntity(exportable = false)
public class ExternalServiceAuthToken extends Base {
  private long expireAt;

  @Override
  public boolean equals(Object o) {
    return super.equals(o) && true;
  }

  /**
   * Instantiates a new auth token.
   *
   * @param tokenExpiryInMillis the token expiry in millis
   */
  public ExternalServiceAuthToken(Long tokenExpiryInMillis) {
    setUuid(secureRandAlphaNumString(32));
    setAppId(GLOBAL_APP_ID);
    expireAt = System.currentTimeMillis() + tokenExpiryInMillis;
  }

  /**
   * Gets expire at.
   *
   * @return the expire at
   */
  public long getExpireAt() {
    return expireAt;
  }

  /**
   * Sets expire at.
   *
   * @param expireAt the expire at
   */
  public void setExpireAt(long expireAt) {
    this.expireAt = expireAt;
  }
}
