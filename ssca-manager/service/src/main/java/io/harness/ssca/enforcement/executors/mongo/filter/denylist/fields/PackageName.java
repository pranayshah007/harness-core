/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.enforcement.executors.mongo.filter.denylist.fields;

import io.harness.ssca.beans.DenyList.DenyListItem;
import io.harness.ssca.enforcement.executors.mongo.MongoOperators;
import io.harness.ssca.entities.NormalizedSBOMComponentEntity.NormalizedSBOMEntityKeys;

import lombok.NoArgsConstructor;
import org.bson.Document;

@NoArgsConstructor
public class PackageName implements Field {
  private static boolean containsRegexCharacters(String input) {
    String regexPattern = ".*[.*+?()\\[\\]{}|^$\\\\].*";
    return input.matches(regexPattern);
  }

  @Override
  public boolean isMatched(DenyListItem denyListItem) {
    return denyListItem.getPackageName() != null;
  }

  @Override
  public Document getQueryDocument(DenyListItem denyListItem) {
    String packageName = denyListItem.getPackageName();
    boolean containRegex = containsRegexCharacters(packageName);

    return containRegex ? new Document(NormalizedSBOMEntityKeys.packageName.toLowerCase(),
               new Document(MongoOperators.MONGO_REGEX, packageName))
                        : new Document(NormalizedSBOMEntityKeys.packageName.toLowerCase(), packageName);
  }
}
