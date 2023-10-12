/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.mongo;

import static io.harness.rule.OwnerRule.GEORGE;

import static org.assertj.core.api.Assertions.assertThat;

import io.harness.NgManagerTestBase;
import io.harness.agent.sdk.HarnessAlwaysRun;
import io.harness.category.element.UnitTests;
import io.harness.connector.entities.Connector;
import io.harness.ng.DbAliases;
import io.harness.persistence.HPersistence;
import io.harness.persistence.store.Store;
import io.harness.rule.Owner;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import dev.morphia.Morphia;
import dev.morphia.ObjectFactory;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.junit.Test;
import org.junit.experimental.categories.Category;

@Slf4j
public class NgManagerMongoIndexesTest extends NgManagerTestBase {
  @Inject HPersistence persistence;
  @Inject @Named("morphiaClasses") Set<Class> classes;
  @Inject ObjectFactory objectFactory;

  @Test
  @Owner(developers = GEORGE)
  @Category(UnitTests.class)
  @HarnessAlwaysRun
  public void testConfirmAllIndexesInManager() throws IOException {
    Morphia morphia = new Morphia();
    morphia.getMapper().getOptions().setObjectFactory(objectFactory);
    morphia.getMapper().getOptions().setMapSubPackages(false);
    morphia.map(classes);

    List<IndexCreator> indexCreators = IndexManagerSession.allIndexes(
        persistence.getDatastore(Connector.class), morphia, Store.builder().name(DbAliases.NG_MANAGER).build());

    List<String> indexes = indexCreators.stream()
                               .map(creator
                                   -> creator.getCollection().getName() + " " + creator.getOptions().toString() + " "
                                       + creator.getKeys().toString())
                               .collect(Collectors.toList());
    addCIModuleLicenseIndex(indexes);

    List<String> expectedIndexes;
    try (InputStream in = getClass().getResourceAsStream("/mongo/indexes.txt")) {
      expectedIndexes = IOUtils.readLines(in, "UTF-8");
    }

    assertThat(indexes).isEqualTo(expectedIndexes);
  }

  void addCIModuleLicenseIndex(List<String> indexes) {
    String ciModuleLicenseIndex =
        "moduleLicenses {\"name\": \"moduleType_status_provisionMonthlyCICreditsIteration\", \"background\": true} {\"moduleType\": 1, \"status\": 1, \"provisionMonthlyCICreditsIteration\": 1}";
    indexes.add(ciModuleLicenseIndex);
    Collections.sort(indexes);
  }
}
