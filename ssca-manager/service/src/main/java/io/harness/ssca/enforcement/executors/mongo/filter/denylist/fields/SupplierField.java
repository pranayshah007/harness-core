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

import java.util.HashMap;
import java.util.Map;
import lombok.AllArgsConstructor;
import org.bson.Document;
import org.eclipse.jgit.ignore.internal.Strings;

@AllArgsConstructor
public class SupplierField implements Field {
  @Override
  public boolean isMatched(DenyListItem denyListItem) {
    return denyListItem.getSupplier() != null;
  }

  @Override
  public Document getQueryDocument(DenyListItem denyListItem) {
    if (denyListItem.getSupplier().contains("!")) {
      String allowSupplier = Strings.split(denyListItem.getSupplier(), '!').get(1);
      Map<String, Object> regexPattern = new HashMap<>();
      regexPattern.put(MongoOperators.MONGO_REGEX, allowSupplier);
      regexPattern.put(MongoOperators.MONGO_REGEX_OPTIONS, 'i');
      return new Document(NormalizedSBOMEntityKeys.packageOriginatorName.toLowerCase(),
          new Document(MongoOperators.MONGO_NOT, new Document(regexPattern)));
    } else {
      Map<String, Object> regexPattern = new HashMap<>();
      regexPattern.put(MongoOperators.MONGO_REGEX, denyListItem.getSupplier());
      regexPattern.put(MongoOperators.MONGO_REGEX_OPTIONS, 'i');
      return new Document(NormalizedSBOMEntityKeys.packageOriginatorName.toLowerCase(), new Document(regexPattern));
    }
  }
}
