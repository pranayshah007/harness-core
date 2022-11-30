/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.logging.AutoLogContext.OverrideBehavior.OVERRIDE_ERROR;

import static software.wings.beans.Base.ACCOUNT_ID_KEY2;
import static software.wings.beans.Permit.PERMIT_KEY_ID;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.artifact.ArtifactCollectionResponseHandler;
import io.harness.logging.AutoLogContext;

import software.wings.beans.Permit;
import software.wings.dl.WingsPersistence;
import software.wings.service.intfc.PermitService;

import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mongodb.DuplicateKeyException;
import lombok.extern.slf4j.Slf4j;

@OwnedBy(CDC)
@Singleton
@Slf4j
@TargetModule(HarnessModule._870_CG_ORCHESTRATION)
public class PermitServiceImpl implements PermitService {
  @Override
  public void deleteByAccountId(String accountId) {
    wingsPersistence.delete(wingsPersistence.createQuery(Permit.class).filter(ACCOUNT_ID_KEY2, accountId));
  }

  static class PermitLogContext extends AutoLogContext {
    public static final String PERMIT_KEY = "permitKey";
    public static final String PERMIT_GROUP = "permitGroup";

    PermitLogContext(String key, OverrideBehavior behavior) {
      super(ImmutableMap.<String, String>builder().put(PERMIT_KEY, key).build(), behavior);
    }

    PermitLogContext(Permit permit, OverrideBehavior behavior) {
      super(ImmutableMap.<String, String>builder()
                .put(PERMIT_KEY, permit.getKey())
                .put(PERMIT_GROUP, permit.getGroup())
                .build(),
          behavior);
    }
  }

  @Inject private WingsPersistence wingsPersistence;
  /*
  {1, 1, 2, 3, 5, 10} == 22 minutes cycle
  500 iterations ~= 80 cycles == 80 * 22 = 1760 > 24hrs
   */
  private static final int[] BACKOFF_MULTIPLIER = new int[] {1, 1, 2, 3, 5, 10};

  public static int getBackoffMultiplier(int failedCronAttempts) {
    return failedCronAttempts > 500 ? BACKOFF_MULTIPLIER.length - 1
                                    : BACKOFF_MULTIPLIER[failedCronAttempts % BACKOFF_MULTIPLIER.length];
  }

  public static boolean shouldSendAlert(int failedCronAttempts) {
    return ArtifactCollectionResponseHandler.MAX_FAILED_ATTEMPTS == failedCronAttempts;
  }

  @Override
  public String acquirePermit(Permit permit) {
    try (AutoLogContext ignore = new PermitLogContext(permit, OVERRIDE_ERROR)) {
      return wingsPersistence.save(permit);
    } catch (DuplicateKeyException ex) {
      if (log.isDebugEnabled()) {
        log.debug("Permit already exists");
      }
    } catch (Exception ex) {
      log.error("Unexpected error in issuing permit", ex);
    }
    return null;
  }

  @Override
  public boolean releasePermitByKey(String key) {
    Permit permit = wingsPersistence.createQuery(Permit.class).filter(PERMIT_KEY_ID, key).get();
    if (permit == null) {
      try (AutoLogContext ignore = new PermitLogContext(key, OVERRIDE_ERROR)) {
        log.info("Permit already deleted");
      }
      return true;
    }
    return wingsPersistence.delete(permit);
  }
}
