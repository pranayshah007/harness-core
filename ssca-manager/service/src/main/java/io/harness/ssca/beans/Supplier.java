/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ssca.beans;

import io.harness.ssca.enforcement.executors.mongo.MongoOperators;
import io.harness.ssca.entities.NormalizedSBOMComponentEntity.NormalizedSBOMEntityKeys;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import lombok.Builder;
import lombok.Data;
import org.bson.Document;
import org.eclipse.jgit.ignore.internal.Strings;

@Data
@Builder
public class Supplier {
  String name;
  String supplier;

  public Document uniquePackageFilter() {
    Document nameFilter = new Document(NormalizedSBOMEntityKeys.packageName.toLowerCase(), name);
    if (supplier.contains("!")) {
      String deniedVendors = Strings.split(supplier, '!').get(1);
      Map<String, Object> regexPattern = new HashMap<>();
      regexPattern.put(MongoOperators.MONGO_REGEX, deniedVendors);
      regexPattern.put(MongoOperators.MONGO_REGEX_OPTIONS, 'i');
      Document filter =
          new Document(NormalizedSBOMEntityKeys.packageOriginatorName.toLowerCase(), new Document(regexPattern));
      return new Document(MongoOperators.MONGO_AND, Arrays.asList(filter, nameFilter));
    } else {
      Pattern regex = Pattern.compile(supplier, Pattern.CASE_INSENSITIVE);
      Document filter = new Document(NormalizedSBOMEntityKeys.packageOriginatorName.toLowerCase(),
          new Document(
              MongoOperators.MONGO_NOT, new Document(MongoOperators.MONGO_IN, Collections.singletonList(regex))));
      return new Document(MongoOperators.MONGO_AND, Arrays.asList(filter, nameFilter));
    }
  }
}
