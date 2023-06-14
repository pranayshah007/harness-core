/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.accesscontrol.roleassignments.migration;

import io.harness.accesscontrol.principals.users.persistence.UserDBO;
import io.harness.accesscontrol.principals.users.persistence.UserRepository;
import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.migration.NGMigration;
import io.harness.ng.core.user.UserInfo;
import io.harness.remote.client.CGRestUtils;
import io.harness.user.remote.UserClient;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.util.CloseableIterator;

@Slf4j
@Singleton
@OwnedBy(HarnessTeam.PL)
public class UserInfoMigration implements NGMigration {
  private final UserClient userClient;
  public static final int BATCH_SIZE = 1000;
  private MongoTemplate mongoTemplate;
  private final UserRepository userRepository;

  @Inject
  public UserInfoMigration(UserClient userClient, MongoTemplate mongoTemplate, UserRepository userRepository) {
    this.userClient = userClient;
    this.mongoTemplate = mongoTemplate;
    this.userRepository = userRepository;
  }

  private CloseableIterator<UserDBO> runQueryWithBatch(int batchSize) {
    Query query = new Query();
    query.cursorBatchSize(batchSize);
    return mongoTemplate.stream(query, UserDBO.class);
  }

  @Override
  public void migrate() {
    log.info("User Info Migration started");

    try {
      CloseableIterator<UserDBO> iterator = runQueryWithBatch(BATCH_SIZE);

      while (iterator.hasNext()) {
        UserDBO userDBO = iterator.next();
        try {
          Optional<UserInfo> userInfo = CGRestUtils.getResponse(userClient.getUserById(userDBO.getIdentifier(), false));
          if (userInfo.isPresent()) {
            userDBO.setEmail(userInfo.get().getEmail());
            userDBO.setName(userInfo.get().getName());
            userRepository.save(userDBO);
          }
        } catch (Exception exception) {
          log.error("Unexpected error occurred during the migration: UserInfo", exception);
        }
      }
    } catch (Exception ex) {
      log.error("Unexpected error occurred during the migration: UserInfo", ex);
    }
    log.info("User Info Migration completed.");
  }
}
