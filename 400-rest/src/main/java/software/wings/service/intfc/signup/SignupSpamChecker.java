/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.intfc.signup;

import static io.harness.annotations.dev.HarnessTeam.PL;

import static software.wings.app.ManagerCacheRegistrar.TRIAL_EMAIL_CACHE;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;

import software.wings.beans.UserInvite;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import javax.cache.Cache;
import javax.cache.CacheException;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(PL)
@Slf4j
@Singleton
@TargetModule(HarnessModule._950_NG_AUTHENTICATION_SERVICE)
public class SignupSpamChecker {
  private static final int REGISTRATION_SPAM_THRESHOLD = 3;
  @Inject @Named(TRIAL_EMAIL_CACHE) private Cache<String, Integer> trialEmailCache;

  public boolean isSpam(UserInvite userInvite) {
    // HAR-7639: If the same email is being used repeatedly for trial signup, it's likely a spam activity.
    // Reject/throttle these registration request to avoid the verification or access-your-account email spamming
    // the legitimate trial user's mailbox.
    String emailAddress = userInvite.getEmail();
    Preconditions.checkNotNull(trialEmailCache, "Email cache is null. ");
    Integer registrationCount = trialEmailCache.get(emailAddress);
    if (registrationCount == null) {
      registrationCount = 1;
    } else {
      registrationCount += 1;
    }
    try {
      trialEmailCache.put(emailAddress, registrationCount);
    } catch (CacheException e) {
      log.error("Unable to set trial email data into cache", e);
    }
    if (registrationCount > REGISTRATION_SPAM_THRESHOLD) {
      log.info(
          "Trial registration has been performed already using the email from user invite '{}' shortly before, rejecting this request.",
          userInvite.getUuid());
      return true;
    }
    return false;
  }
}
