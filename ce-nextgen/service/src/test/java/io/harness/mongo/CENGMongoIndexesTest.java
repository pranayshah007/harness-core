/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.mongo;

import static io.harness.rule.OwnerRule.UTSAV;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.category.element.UnitTests;
import io.harness.ccm.CENGTestBase;
import io.harness.ng.DbAliases;
import io.harness.persistence.HPersistence;
import io.harness.persistence.Store;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mongodb.morphia.AdvancedDatastore;
import org.mongodb.morphia.Morphia;
import org.mongodb.morphia.ObjectFactory;

@Slf4j
public class CENGMongoIndexesTest extends CENGTestBase {
  @Inject HPersistence persistence;
  @Inject @Named("morphiaClasses") Set<Class> classes;
  @Inject ObjectFactory objectFactory;

  @Test
  @Owner(developers = UTSAV)
  @Category(UnitTests.class)
  public void testConfirmAllIndexesInEventsDB() throws IOException {
    Morphia morphia = new Morphia();
    morphia.getMapper().getOptions().setObjectFactory(objectFactory);
    morphia.getMapper().getOptions().setMapSubPackages(false);
    morphia.map(classes);

    AdvancedDatastore advancedDatastore = persistence.getDatastore(Store.builder().name(DbAliases.CENG).build());
    // get indexes from ONLY "events" db
    List<IndexCreator> indexCreators =
        IndexManagerSession.allIndexes(advancedDatastore, morphia, Store.builder().name(DbAliases.CENG).build(), false);

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
