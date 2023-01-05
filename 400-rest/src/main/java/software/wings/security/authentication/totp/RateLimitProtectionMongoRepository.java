/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.security.authentication.totp;

import static io.harness.persistence.HPersistence.upsertReturnNewOptions;
import static io.harness.persistence.HPersistence.upsertReturnOldOptions;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.persistence.HPersistence;

import software.wings.beans.User;

import com.mongodb.BasicDBObject;
import dev.morphia.AdvancedDatastore;
import dev.morphia.query.Query;
import dev.morphia.query.UpdateOperations;
import java.util.ArrayList;

@OwnedBy(HarnessTeam.PL)
public class RateLimitProtectionMongoRepository implements RateLimitProtectionRepository {
  private final HPersistence persistence;

  public RateLimitProtectionMongoRepository(HPersistence persistence) {
    this.persistence = persistence;
  }

  @Override
  /**
   * TODO: maybe not needed
   */
  public void createRateLimitProtectionDataIfNotExists(String userUuid) {
    Query<User> findUser =
        persistence.createQuery(User.class).field("uuid").equal(userUuid).field("rateLimitProtection").not().exists();
    AdvancedDatastore ds = persistence.getDatastore(User.class);
    UpdateOperations<User> updateOperations =
        ds.createUpdateOperations(User.class).set("rateLimitProtection", RateLimitProtection.builder().build());
    persistence.update(findUser, updateOperations);
  }

  @Override
  public RateLimitProtection pruneIncorrectAttemptTimes(String userUuid, Long leastAllowedTime) {
    Query<User> findUser = persistence.createQuery(User.class).field("uuid").equal(userUuid);
    AdvancedDatastore ds = persistence.getDatastore(User.class);
    UpdateOperations<User> updateOperations = ds.createUpdateOperations(User.class,
        new BasicDBObject("$pull",
            new BasicDBObject(
                "rateLimitProtection.incorrectAttemptTimestamps", new BasicDBObject("$lt", leastAllowedTime))));
    RateLimitProtection rateLimitProtection = ds.findAndModify(findUser, updateOperations).getRateLimitProtection();
    if (rateLimitProtection.getIncorrectAttemptTimestamps() == null) {
      rateLimitProtection = rateLimitProtection.toBuilder().incorrectAttemptTimestamps(new ArrayList<>()).build();
    }
    return rateLimitProtection;
  }

  @Override
  public RateLimitProtection addIncorrectAttempt(String userUuid, Long time) {
    Query<User> findUser = persistence.createQuery(User.class).field("uuid").equal(userUuid);
    AdvancedDatastore ds = persistence.getDatastore(User.class);
    UpdateOperations<User> incrementTotalIncorrectAttempts =
        ds.createUpdateOperations(User.class)
            .inc("rateLimitProtection.totalIncorrectAttempts")
            .push("rateLimitProtection.incorrectAttemptTimestamps", time);
    return ds.findAndModify(findUser, incrementTotalIncorrectAttempts, upsertReturnNewOptions).getRateLimitProtection();
  }

  @Override
  public long getAndUpdateLastEmailSentToUserAt(String userUuid, Long newTimestamp) {
    Query<User> findUser = persistence.createQuery(User.class).field("uuid").equal(userUuid);
    AdvancedDatastore ds = persistence.getDatastore(User.class);
    UpdateOperations<User> incrementTotalIncorrectAttempts =
        ds.createUpdateOperations(User.class).set("rateLimitProtection.lastNotificationSentToUserAt", newTimestamp);
    return ds.findAndModify(findUser, incrementTotalIncorrectAttempts, upsertReturnOldOptions)
        .getRateLimitProtection()
        .getLastNotificationSentToUserAt();
  }

  @Override
  public long getAndUpdateLastEmailSentToSecOpsAt(String userUuid, Long newTimestamp) {
    Query<User> findUser = persistence.createQuery(User.class).field("uuid").equal(userUuid);
    AdvancedDatastore ds = persistence.getDatastore(User.class);
    UpdateOperations<User> incrementTotalIncorrectAttempts =
        ds.createUpdateOperations(User.class).set("rateLimitProtection.lastNotificationSentToSecOpsAt", newTimestamp);
    return ds.findAndModify(findUser, incrementTotalIncorrectAttempts, upsertReturnOldOptions)
        .getRateLimitProtection()
        .getLastNotificationSentToSecOpsAt();
  }
}
