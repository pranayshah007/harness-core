/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.persistence;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.mongo.tracing.TraceMode;
import io.harness.observer.Subject;

import com.mongodb.DBCollection;
import com.mongodb.DBObject;
import dev.morphia.Datastore;
import dev.morphia.query.DefaultQueryFactory;
import dev.morphia.query.Query;
import dev.morphia.query.QueryImpl;
import lombok.Getter;

@OwnedBy(HarnessTeam.PL)
public class QueryFactory extends DefaultQueryFactory {
  private final TraceMode traceMode;
  @Getter private final int maxOperationTimeInMillis;
  @Getter private final int maxDocumentsToBeFetched;
  @Getter private final Subject<Tracer> tracerSubject = new Subject<>();

  public QueryFactory(TraceMode traceMode, int maxOperationTimeInMillis, int maxDocumentsToBeFetched) {
    this.traceMode = traceMode;
    this.maxOperationTimeInMillis = maxOperationTimeInMillis;
    this.maxDocumentsToBeFetched = maxDocumentsToBeFetched;
  }

  @Override
  public <T> Query<T> createQuery(
      final Datastore datastore, final DBCollection collection, final Class<T> type, final DBObject query) {
    final QueryImpl<T> item = new HQuery<>(
        type, collection, datastore, traceMode, tracerSubject, maxOperationTimeInMillis, maxDocumentsToBeFetched);

    if (query != null) {
      item.setQueryObject(query);
    }
    return item;
  }
}
