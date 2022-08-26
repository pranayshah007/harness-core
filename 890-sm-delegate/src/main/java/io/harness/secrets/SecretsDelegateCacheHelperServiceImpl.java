/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.secrets;

import io.harness.delegate.DelegateConfigurationServiceProvider;
import io.harness.delegate.DelegatePropertiesServiceProvider;
import io.harness.managerclient.AccountPreference;
import io.harness.managerclient.AccountPreferenceQuery;
import io.harness.managerclient.GetDelegatePropertiesRequest;
import io.harness.managerclient.GetDelegatePropertiesResponse;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.protobuf.Any;
import com.google.protobuf.InvalidProtocolBufferException;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class SecretsDelegateCacheHelperServiceImpl implements SecretsDelegateCacheHelperService {
  @Inject private DelegateConfigurationServiceProvider delegateConfigurationServiceProvider;
  @Inject private DelegatePropertiesServiceProvider delegatePropertiesServiceProvider;

  /*
   * Method to allow user configurable cache expiry ttl in hours specified in account preferences.
   * Default will be 1H, if we encounter an exception or unable to fetch the value for any reason
   */
  @Override
  public Duration initializeCacheExpiryTTL() {
    try {
      GetDelegatePropertiesRequest delegatePropertiesRequest =
          GetDelegatePropertiesRequest.newBuilder()
              .setAccountId(delegateConfigurationServiceProvider.getAccount())
              .addRequestEntry(Any.pack(AccountPreferenceQuery.newBuilder().build()))
              .build();

      // call to manager to get the TTL value from account pref
      GetDelegatePropertiesResponse delegatePropertiesResponse =
          delegatePropertiesServiceProvider.getDelegateProperties(delegatePropertiesRequest);

      if (delegatePropertiesResponse != null
          && delegatePropertiesResponse.getResponseEntry(0).is(AccountPreference.class)) {
        AccountPreference secretManagerCacheTTL =
            delegatePropertiesResponse.getResponseEntry(0).unpack(AccountPreference.class);
        if (secretManagerCacheTTL != null) {
          long expireCacheTTLFromAccountPref = secretManagerCacheTTL.getDelegateSecretsCacheTTLInHours();
          log.info("Fetched SecretsCacheTTL from Account Preferences and set expiry to {} hours",
              expireCacheTTLFromAccountPref);
          return Duration.ofHours(expireCacheTTLFromAccountPref);
        }
      }
      log.info("SecretsCacheTTL not set in account preferences, defaulting to 1 hour");
      return Duration.ofHours(1);

    } catch (InvalidProtocolBufferException invalidProtocolBufferException) {
      log.warn("Unable to fetch secretsCacheExpiryTTL from manager for account {}, defaulting to 1 hour",
          delegateConfigurationServiceProvider.getAccount(), invalidProtocolBufferException);
      return Duration.ofHours(1);
    } catch (Exception exception) {
      log.warn("Unable to fetch secretsCacheExpiryTTL from manager for account {}, defaulting to 1 hour",
          delegateConfigurationServiceProvider.getAccount(), exception);
      return Duration.ofHours(1);
    }
  }
}
