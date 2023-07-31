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
import lombok.NoArgsConstructor;
import org.bson.Document;
import org.eclipse.jgit.ignore.internal.Strings;

@NoArgsConstructor
public class LicenseField implements Field {
  @Override
  public boolean isMatched(DenyListItem denyListItem) {
    return denyListItem.getLicense() != null;
  }

  @Override
  public Document getQueryDocument(DenyListItem denyListItem) {
    if (denyListItem.getLicense().contains("!")) {
      String allowLicense = Strings.split(denyListItem.getLicense(), '!').get(1);
      Map<String, Object> regexPattern = new HashMap<>();
      regexPattern.put(MongoOperators.MONGO_REGEX, allowLicense);
      regexPattern.put(MongoOperators.MONGO_REGEX_OPTIONS, 'i');
      return new Document(NormalizedSBOMEntityKeys.packageLicense.toLowerCase(),
          new Document(MongoOperators.MONGO_NOT, new Document(regexPattern)));
    } else {
      Map<String, Object> regexPattern = new HashMap<>();
      regexPattern.put(MongoOperators.MONGO_REGEX, denyListItem.getLicense());
      regexPattern.put(MongoOperators.MONGO_REGEX_OPTIONS, 'i');
      return new Document(NormalizedSBOMEntityKeys.packageLicense.toLowerCase(), new Document(regexPattern));
    }
  }
}
