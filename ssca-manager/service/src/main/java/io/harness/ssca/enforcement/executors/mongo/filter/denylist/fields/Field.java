/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.enforcement.executors.mongo.filter.denylist.fields;

import io.harness.ssca.beans.DenyList.DenyListItem;

import org.bson.Document;

public interface Field {
  boolean isMatched(DenyListItem denyListItem);

  Document getQueryDocument(DenyListItem denyListItem);
}
