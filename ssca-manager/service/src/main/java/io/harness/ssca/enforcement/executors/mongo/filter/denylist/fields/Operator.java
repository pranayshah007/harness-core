/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.enforcement.executors.mongo.filter.denylist.fields;

import io.harness.ssca.enforcement.executors.mongo.MongoOperators;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum Operator {
  LESS_THAN_EQUAL("<=", MongoOperators.MONGO_LESS_THAN_EQUAL, MongoOperators.MONGO_LESS_THAN),
  LESS_THAN("<", MongoOperators.MONGO_LESS_THAN, MongoOperators.MONGO_LESS_THAN),
  GREATER_THAN_EQUAL(">=", MongoOperators.MONGO_GREATER_THAN_EQUAL, MongoOperators.MONGO_GREATER_THAN),
  GREATER_THAN(">", MongoOperators.MONGO_GREATER_THAN, MongoOperators.MONGO_GREATER_THAN),
  NOT_EQUAL("!", MongoOperators.MONGO_NOT_EQUAL, MongoOperators.MONGO_NOT_EQUAL),
  UNKNOWN("", "", "");

  String numericString;
  String primaryMongoString;
  String secondaryMongoString;
}
