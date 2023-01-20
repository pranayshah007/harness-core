/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.mongo;

import static io.harness.govern.IgnoreThrowable.ignoredOnPurpose;
import static io.harness.mongo.IndexManager.Mode.INSPECT;
import static io.harness.mongo.IndexManager.Mode.MANUAL;

import io.harness.mongo.index.migrator.Migrator;
import io.harness.persistence.store.Store;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Singleton;
import com.mongodb.DBCollection;
import dev.morphia.AdvancedDatastore;
import dev.morphia.Morphia;
import dev.morphia.mapping.MappedClass;
import java.util.Map;
import javax.annotation.Nullable;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
public class IndexManager {
  public enum Mode { AUTO, MANUAL, INSPECT }

  @Inject Injector injector;
  @Nullable @Inject Map<String, Migrator> migrators;

  public void ensureIndexes(Mode mode, AdvancedDatastore datastore, Morphia morphia, Store store) {
    try {
      IndexManagerSession session = new IndexManagerSession(datastore, migrators, mode == null ? MANUAL : mode);
      if (session.ensureIndexes(morphia, store) && mode == INSPECT) {
        throw new IndexManagerInspectException();
      }
    } catch (IndexManagerReadOnlyException exception) {
      ignoredOnPurpose(exception);
      log.warn("The user has read only access.");
    } finally {
      if (mode == INSPECT || mode == MANUAL) {
        log.info("the inspection finished");
      }
    }
  }

  public static Map<String, IndexCreator> indexCreators(MappedClass mc, DBCollection collection) {
    return IndexManagerSession.indexCreators(mc, collection);
  }
}
