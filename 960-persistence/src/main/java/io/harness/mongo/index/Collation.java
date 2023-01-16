/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.mongo.index;

import io.harness.mongo.collation.CollationLocale;
import io.harness.mongo.collation.CollationStrength;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class Collation {
  CollationLocale locale;
  CollationStrength strength;
}
