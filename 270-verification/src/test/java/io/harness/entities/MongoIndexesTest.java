/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.entities;

import static io.harness.rule.OwnerRule.SOWMYA;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.VerificationBase;
import io.harness.category.element.UnitTests;
import io.harness.mongo.IndexCreator;
import io.harness.mongo.IndexManagerSession;
import io.harness.ng.DbAliases;
import io.harness.persistence.HPersistence;
import io.harness.persistence.Store;
import io.harness.rule.Owner;

import software.wings.beans.Account;

import com.google.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mongodb.morphia.Morphia;
import org.mongodb.morphia.ObjectFactory;

@Slf4j
public class MongoIndexesTest extends VerificationBase {
  @Inject HPersistence persistence;
  @Inject ObjectFactory objectFactory;

  Set<Class> classes = VerificationMorphiaClasses.classes;

  @Test
  @Owner(developers = SOWMYA)
  @Category(UnitTests.class)
  public void testConfirmAllIndexes() throws IOException {
    Morphia morphia = new Morphia();
    morphia.getMapper().getOptions().setObjectFactory(objectFactory);
    morphia.getMapper().getOptions().setMapSubPackages(true);
    morphia.map(classes);

    List<IndexCreator> indexCreators = IndexManagerSession.allIndexes(
        persistence.getDatastore(Account.class), morphia, Store.builder().name(DbAliases.HARNESS).build());

    List<String> indexes = indexCreators.stream()
                               .map(creator
                                   -> creator.getCollection().getName() + " " + creator.getOptions().toString() + " "
                                       + creator.getKeys().toString())
                               .sorted()
                               .collect(Collectors.toList());

    List<String> expectedIndexes;
    try (InputStream in = getClass().getResourceAsStream("/mongo/indexes.txt")) {
      expectedIndexes = IOUtils.readLines(in, "UTF-8");
    }

    assertThat(indexes).isEqualTo(expectedIndexes);
  }
}
