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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import org.bson.Document;

@AllArgsConstructor
public class VersionField implements Field {
  @Override
  public boolean isMatched(DenyListItem denyListItem) {
    return denyListItem.getVersion() != null;
  }

  @Override
  public Document getQueryDocument(DenyListItem denyListItem) {
    if (denyListItem.getVersion().contains("*")) {
      return null;
    } else {
      Operator operator = getOperator(denyListItem.getVersion());
      String versionString = denyListItem.getVersion().split(operator.getNumericString())[1];
      List<Integer> numericVersions = getVersion(versionString);

      return getDocument(numericVersions, operator);
    }
  }

  private Document getDocument(List<Integer> numericVersions, Operator operator) {
    List<Document> mainDocument = new ArrayList<>();
    if (operator != Operator.UNKNOWN) {
      if (numericVersions.get(2) != -1) {
        mainDocument.add(getSubDocument(numericVersions, 3, operator));
      }
      if (numericVersions.get(1) != -1) {
        mainDocument.add(getSubDocument(numericVersions, 2, operator));
      }
      if (numericVersions.get(0) != -1) {
        mainDocument.add(getSubDocument(numericVersions, 1, operator));
      }
      return new Document(MongoOperators.MONGO_OR, mainDocument);
    }

    Map<String, Object> versionMap = new HashMap<>();
    versionMap.put(NormalizedSBOMEntityKeys.majorVersion.toLowerCase(), numericVersions.get(0));
    versionMap.put(NormalizedSBOMEntityKeys.minorVersion.toLowerCase(), numericVersions.get(1));
    versionMap.put(NormalizedSBOMEntityKeys.patchVersion.toLowerCase(), numericVersions.get(2));
    return new Document(versionMap);
  }

  private Document getSubDocument(List<Integer> numericVersions, int versionField, Operator operator) {
    switch (versionField) {
      case 1:
        return new Document(NormalizedSBOMEntityKeys.majorVersion.toLowerCase(),
            new Document(operator.getSecondaryMongoString(), numericVersions.get(0)));
      case 2:
        Document majorVersionDocument =
            new Document(NormalizedSBOMEntityKeys.majorVersion.toLowerCase(), numericVersions.get(0));
        Document minorVersionDocument = new Document(NormalizedSBOMEntityKeys.minorVersion.toLowerCase(),
            new Document(operator.getSecondaryMongoString(), numericVersions.get(1)));
        return new Document(MongoOperators.MONGO_AND, Arrays.asList(majorVersionDocument, minorVersionDocument));
      case 3:
        majorVersionDocument =
            new Document(NormalizedSBOMEntityKeys.majorVersion.toLowerCase(), numericVersions.get(0));
        minorVersionDocument =
            new Document(NormalizedSBOMEntityKeys.minorVersion.toLowerCase(), numericVersions.get(1));
        Document patchVersionDocument = new Document(NormalizedSBOMEntityKeys.patchVersion.toLowerCase(),
            new Document(operator.getPrimaryMongoString(), numericVersions.get(2)));
        return new Document(
            MongoOperators.MONGO_AND, Arrays.asList(majorVersionDocument, minorVersionDocument, patchVersionDocument));
      default:
        return null;
    }
  }

  private Operator getOperator(String version) {
    for (Operator op : Operator.values()) {
      if (version.contains(op.numericString)) {
        return op;
      }
    }

    return Operator.UNKNOWN;
  }

  private List<Integer> getVersion(String version) {
    if (version == null) {
      return Arrays.asList(-1, -1, -1);
    }
    version = version.trim();
    String[] splitVersion = version.split("[.]");
    switch (splitVersion.length) {
      case 1:
        return Arrays.asList(Integer.parseInt(splitVersion[0]), -1, -1);
      case 2:
        return Arrays.asList(Integer.parseInt(splitVersion[0]), Integer.parseInt(splitVersion[1]), -1);
      case 3:
        return Arrays.asList(
            Integer.parseInt(splitVersion[0]), Integer.parseInt(splitVersion[1]), Integer.parseInt(splitVersion[2]));
      default:
        return Arrays.asList(-1, -1, -1);
    }
  }
}
