/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ng.core.migration.background;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotations.dev.OwnedBy;
import io.harness.migration.NGMigration;
import io.harness.ng.core.user.UserInfo;
import io.harness.ng.core.user.entities.UserMetadata;
import io.harness.ng.core.user.entities.UserMetadata.UserMetadataKeys;
import io.harness.remote.client.CGRestUtils;
import io.harness.user.remote.UserClient;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.util.CloseableIterator;

@OwnedBy(PL)
@Slf4j
public class UserMetadataTwoFactorAuthenticationMigration implements NGMigration {
  private final MongoTemplate mongoTemplate;
  private final UserClient userClient;
  public static final int BATCH_SIZE = 500;

  @Inject
  public UserMetadataTwoFactorAuthenticationMigration(
      MongoTemplate mongoTemplate, @Named("PRIVILEGED") UserClient userClient) {
    this.mongoTemplate = mongoTemplate;
    this.userClient = userClient;
  }

  @Override
  public void migrate() {
    log.info(
        "UserMetadataTwoFactorAuthenticationMigration: Starting migration to add Two Factor Authentication field in UserMetadata collection");

    CloseableIterator<UserMetadata> iterator =
        mongoTemplate.stream(new Query().cursorBatchSize(BATCH_SIZE), UserMetadata.class);

    int count = 0;
    while (iterator.hasNext()) {
      if (count != 0 && count % BATCH_SIZE == 0) {
        log.info(String.format(
            "UserMetadataTwoFactorAuthenticationMigration: Migration completed for batch. Count so far- %d", count));
        try {
          Thread.sleep(TimeUnit.SECONDS.toMillis(10));
        } catch (Exception ex) {
          log.error(String.format(
              "UserMetadataTwoFactorAuthenticationMigration: Error while waking up. Failed to add Two Factor Authentication field in UserMetadata collection"));
        }
      }

      UserMetadata userMetadata = iterator.next();
      String userId = userMetadata.getUserId();
      try {
        Criteria criteria = Criteria.where(UserMetadataKeys.userId).is(userMetadata.getUserId());
        Update update = new Update();
        update.set(UserMetadataKeys.twoFactorAuthenticationEnabled, get2FAStatus(userId));
        mongoTemplate.updateFirst(new Query(criteria), update, UserMetadata.class);
        log.info("UserMetadataTwoFactorAuthenticationMigration: Migrated user {} successfully", userId);
        count++;
      } catch (Exception ex) {
        log.error(
            "UserMetadataTwoFactorAuthenticationMigration: Exception occurred while migrating two factor auth field for user {}",
            userId, ex);
      }
    }
  }

  private boolean get2FAStatus(String userId) {
    Optional<UserInfo> userInfoOptional = CGRestUtils.getResponse(userClient.getUserById(userId));
    if (userInfoOptional.isPresent()) {
      UserInfo userInfo = userInfoOptional.get();
      return userInfo.isTwoFactorAuthenticationEnabled();
    }
    return false;
  }
}
