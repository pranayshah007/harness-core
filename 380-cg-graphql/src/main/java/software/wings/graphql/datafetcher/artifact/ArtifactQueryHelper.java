/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.graphql.datafetcher.artifact;

import static io.harness.annotations.dev.HarnessTeam.CDC;
import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.annotations.dev.OwnedBy;

import software.wings.graphql.datafetcher.DataFetcherUtils;
import software.wings.graphql.schema.type.aggregation.QLIdFilter;
import software.wings.graphql.schema.type.aggregation.artifact.QLArtifactFilter;
import software.wings.persistence.artifact.Artifact;
import software.wings.persistence.artifact.Artifact.ArtifactKeys;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.query.FieldEnd;
import org.mongodb.morphia.query.Query;

@OwnedBy(CDC)
@Singleton
@Slf4j
public class ArtifactQueryHelper {
  @Inject private DataFetcherUtils utils;

  public void setQuery(List<QLArtifactFilter> filters, Query query) {
    if (isEmpty(filters)) {
      return;
    }

    filters.forEach(filter -> {
      FieldEnd<? extends Query<Artifact>> field;

      if (filter.getArtifact() != null) {
        field = query.field("_id");
        QLIdFilter artifactFilter = filter.getArtifact();
        utils.setIdFilter(field, artifactFilter);
      }

      if (filter.getArtifactSource() != null) {
        field = query.field(ArtifactKeys.artifactStreamId);
        QLIdFilter artifactSourceFilter = filter.getArtifactSource();
        utils.setIdFilter(field, artifactSourceFilter);
      }

      if (filter.getArtifactStreamType() != null) {
        field = query.field(ArtifactKeys.artifactStreamType);
        QLIdFilter artifactStreamTypeFilter = filter.getArtifactStreamType();
        utils.setIdFilter(field, artifactStreamTypeFilter);
      }
    });
  }
}
