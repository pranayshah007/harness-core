/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.mongo.helper;

import static io.harness.annotations.dev.HarnessTeam.PL;
import static io.harness.mongo.helper.MongoConstants.ANALYTICS;

import io.harness.annotations.dev.OwnedBy;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;

@Slf4j
@OwnedBy(PL)
@Singleton
public class AnalyticsMongoTemplateHolder {
  @Inject @Named(ANALYTICS) public MongoTemplate analyticsMongoTemplate;

  public MongoTemplate getAnalyticsMongoTemplate() {
    return analyticsMongoTemplate;
  }
}
