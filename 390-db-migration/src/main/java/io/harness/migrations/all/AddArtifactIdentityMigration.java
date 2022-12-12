/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.migrations.all;

import static io.harness.data.structure.UUIDGenerator.generateUuid;
import static io.harness.persistence.HQuery.excludeAuthority;

import static software.wings.beans.artifact.ArtifactStreamType.AMI;
import static software.wings.beans.artifact.ArtifactStreamType.CUSTOM;
import static software.wings.service.impl.artifact.ArtifactCollectionUtils.getArtifactKeyFn;

import io.harness.migrations.Migration;
import io.harness.persistence.HIterator;

import software.wings.beans.artifact.ArtifactStream;
import software.wings.beans.artifact.ArtifactStreamAttributes;
import software.wings.dl.WingsPersistence;
import software.wings.persistence.artifact.Artifact;
import software.wings.persistence.artifact.Artifact.ArtifactKeys;
import software.wings.service.impl.artifact.ArtifactCollectionUtils;
import software.wings.service.intfc.ArtifactService;

import com.google.inject.Inject;
import com.mongodb.BasicDBObject;
import com.mongodb.BulkWriteOperation;
import com.mongodb.DBCollection;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.Sort;

@Slf4j
public class AddArtifactIdentityMigration implements Migration {
  @Inject private WingsPersistence wingsPersistence;
  @Inject private ArtifactService artifactService;
  @Inject private ArtifactCollectionUtils artifactCollectionUtils;

  @Override
  public void migrate() {
    try {
      final DBCollection collection = wingsPersistence.getCollection(Artifact.class);
      BulkWriteOperation bulkWriteOperation = collection.initializeUnorderedBulkOperation();

      log.info("Starting migration for artifacts");
      int counter = 1;
      int exceptionCount = 0;
      int duplicateCount = 0;
      try (HIterator<ArtifactStream> artifactStreams =
               new HIterator<>(wingsPersistence.createQuery(ArtifactStream.class, excludeAuthority).fetch())) {
        while (artifactStreams.hasNext()) {
          Set<String> buildIdentities = new HashSet();
          ArtifactStream artifactStream = artifactStreams.next();
          ArtifactStreamAttributes artifactStreamAttributes = null;
          try {
            artifactStreamAttributes = artifactCollectionUtils.getArtifactStreamAttributes(artifactStream, false);
          } catch (Exception ex) {
            exceptionCount = exceptionCount + 1;
          }
          try (HIterator<Artifact> artifacts =
                   new HIterator<>(prepareArtifactWithMetadataQuery(artifactStream).fetch())) {
            while (artifacts.hasNext()) {
              Artifact artifact = artifacts.next();

              if (counter % 1000 == 0) {
                bulkWriteOperation.execute();
                bulkWriteOperation = collection.initializeUnorderedBulkOperation();
                log.info("Artifacts : {} updated", counter);
              }
              ++counter;
              String uuid = generateUuid();
              String identity = uuid;
              try {
                identity = findArtifactIdentity(artifactStream, artifactStreamAttributes, artifact);
              } catch (Exception ex) {
                exceptionCount = exceptionCount + 1;
              }
              if (buildIdentities.contains(identity)) {
                duplicateCount = duplicateCount + 1;
                identity = identity + "_" + uuid;
              }

              buildIdentities.add(identity);

              bulkWriteOperation
                  .find(wingsPersistence.createQuery(Artifact.class)
                            .filter(ArtifactKeys.uuid, artifact.getUuid())
                            .getQueryObject())
                  .updateOne(new BasicDBObject("$set", new BasicDBObject(ArtifactKeys.buildIdentity, identity)));
            }
          }
        }
      }

      if (counter % 1000 != 1) {
        bulkWriteOperation.execute();
      }

      log.info("Artifacts duplicate count {}, exceptionCount {}, total artifacts {}", duplicateCount, exceptionCount,
          counter);

      addIdentityOnOrphanTriggers();
    } catch (Exception ex) {
      log.error("Exception while executing AddArtifactIdentityMigration", ex);
    }
  }

  private void addIdentityOnOrphanTriggers() {
    final DBCollection collection = wingsPersistence.getCollection(Artifact.class);
    BulkWriteOperation bulkWriteOperation = collection.initializeUnorderedBulkOperation();
    int counter = 1;
    try (HIterator<Artifact> artifacts = new HIterator<>(wingsPersistence.createQuery(Artifact.class, excludeAuthority)
                                                             .field(ArtifactKeys.buildIdentity)
                                                             .doesNotExist()
                                                             .fetch())) {
      while (artifacts.hasNext()) {
        Artifact artifact = artifacts.next();

        if (counter % 1000 == 0) {
          bulkWriteOperation.execute();
          bulkWriteOperation = collection.initializeUnorderedBulkOperation();
          log.info("Orphan Artifacts : {} updated", counter);
        }
        ++counter;
        String identity = generateUuid();

        bulkWriteOperation
            .find(wingsPersistence.createQuery(Artifact.class, excludeAuthority)
                      .filter(ArtifactKeys.uuid, artifact.getUuid())
                      .getQueryObject())
            .updateOne(new BasicDBObject("$set", new BasicDBObject(ArtifactKeys.buildIdentity, identity)));
      }
    }

    if (counter % 1000 != 1) {
      bulkWriteOperation.execute();
    }
  }

  private String findArtifactIdentity(
      ArtifactStream artifactStream, ArtifactStreamAttributes artifactStreamAttributes, Artifact artifact) {
    String artifactStreamType = artifactStream.getArtifactStreamType();
    Function<Artifact, String> keyFn = getArtifactKeyFn(artifactStreamType, artifactStreamAttributes);
    return keyFn.apply(artifact);
  }

  private Query<Artifact> prepareArtifactWithMetadataQuery(ArtifactStream artifactStream) {
    Query<Artifact> artifactQuery = wingsPersistence.createQuery(Artifact.class, excludeAuthority)
                                        .filter(ArtifactKeys.artifactStreamId, artifactStream.getUuid())
                                        .disableValidation();

    if (AMI.name().equals(artifactStream.getArtifactStreamType())) {
      artifactQuery.project(ArtifactKeys.revision, true);
    } else {
      artifactQuery.project(ArtifactKeys.metadata, true);
      artifactQuery.project(ArtifactKeys.revision, true);
    }

    artifactQuery.order(Sort.descending(Artifact.CREATED_AT_KEY));

    if (CUSTOM.name().equals(artifactStream.getArtifactStreamType())) {
      return artifactQuery;
    }
    artifactQuery.filter(ArtifactKeys.artifactSourceName, artifactStream.getSourceName());

    return artifactQuery;
  }
}
