/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.enforcement.executors.mongo;

public class MongoOperators {
  public static final String MONGO_AND = "$and";
  public static final String MONGO_OR = "$or";
  public static final String MONGO_NOT = "$not";
  public static final String MONGO_NAND = "$nand";
  public static final String MONGO_NOR = "$nor";
  public static final String MONGO_IN = "$in";
  public static final String MONGO_NOT_IN = "$nin";
  public static final String MONGO_REGEX = "$regex";
  public static final String MONGO_REGEX_OPTIONS = "$options";
  public static final String MONGO_NOT_EQUAL = "$ne";
  public static final String MONGO_EQUAL = "$eq";
  public static final String MONGO_LESS_THAN = "$lt";
  public static final String MONGO_LESS_THAN_EQUAL = "$lte";
  public static final String MONGO_GREATER_THAN = "$gt";
  public static final String MONGO_GREATER_THAN_EQUAL = "$gte";
}
