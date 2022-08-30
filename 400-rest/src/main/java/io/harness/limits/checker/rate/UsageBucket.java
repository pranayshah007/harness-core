/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.limits.checker.rate;

import static io.harness.annotations.dev.HarnessTeam.PL;

import io.harness.annotation.HarnessEntity;
import io.harness.annotation.StoreIn;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ng.DbAliases;
import io.harness.persistence.PersistentEntity;

import java.util.List;
import lombok.Value;
import lombok.experimental.FieldNameConstants;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;

/**
 * This collections tracks the usage for rate limiting purposes.
 */
@OwnedBy(PL)
@Value
@StoreIn(DbAliases.HARNESS)
@Entity(value = "usageBuckets", noClassnameStored = true)
@HarnessEntity(exportable = false)
@FieldNameConstants(innerTypeName = "UsageBucketKeys")
public class UsageBucket implements PersistentEntity {
  // key which uniquely identifies this bucket
  @Id private String key;

  /**
   * List of times when bucket is accessed in current window.
   * So, if you are trying to rate limit an API, you'd update this bucket every time the API is hit.
   * See {@link MongoSlidingWindowRateLimitChecker#checkAndConsume} for example.
   */
  private List<Long> accessTimes;
}
