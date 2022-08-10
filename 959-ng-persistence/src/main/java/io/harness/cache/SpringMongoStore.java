/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.cache;

import static com.mongodb.ErrorCategory.DUPLICATE_KEY;
import static java.lang.String.format;
import static org.springframework.data.mongodb.core.query.Criteria.where;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cache.SpringCacheEntity.SpringCacheEntityKeys;
import io.harness.data.structure.EmptyPredicate;
import io.harness.serializer.KryoSerializer;
import io.harness.springdata.HMongoTemplate;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.mongodb.ErrorCategory;
import com.mongodb.MongoCommandException;
import java.sql.Date;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

@Singleton
@Slf4j
@OwnedBy(HarnessTeam.PIPELINE)
public class SpringMongoStore implements DistributedStore {
  private static final int VERSION = 1;

  @Inject private MongoTemplate mongoTemplate;
  @Inject private KryoSerializer kryoSerializer;

  @Override
  public <T extends Distributable> T get(long algorithmId, long structureHash, String key, List<String> params) {
    return get(null, algorithmId, structureHash, key, params);
  }

  public Long getEntityUpdatedAt(long algorithmId, long structureHash, String key, List<String> params) {
    return getEntityUpdatedAt(null, algorithmId, structureHash, key, params);
  }

  @Override
  public <T extends Distributable> T get(
      long contextHash, long algorithmId, long structureHash, String key, List<String> params) {
    return get(Long.valueOf(contextHash), algorithmId, structureHash, key, params);
  }

  public <T extends Distributable> void upsert(T entity, Duration ttl, long entityUpdatedAt) {
    upsertInternal(entity, ttl, false, entityUpdatedAt);
  }

  @Override
  public <T extends Distributable> void upsert(T entity, Duration ttl) {
    upsertInternal(entity, ttl, false, System.currentTimeMillis());
  }

  @Override
  public <T extends Distributable> void upsert(T entity, Duration ttl, boolean downgrade) {
    upsertInternal(entity, ttl, downgrade, System.currentTimeMillis());
  }

  private String canonicalKey(long algorithmId, long structureHash, String key, List<String> params) {
    if (EmptyPredicate.isEmpty(params)) {
      return format("%s/%d/%d/%d", key, VERSION, algorithmId, structureHash);
    }
    return format("%s/%d/%d/%d%d", key, VERSION, algorithmId, structureHash, Objects.hash(params.toArray()));
  }

  private Long getEntityUpdatedAt(
      Long contextValue, long algorithmId, long structureHash, String key, List<String> params) {
    try {
      Query query = new Query(
          where(SpringCacheEntityKeys.canonicalKey).is(canonicalKey(algorithmId, structureHash, key, params)));

      if (contextValue != null) {
        query.addCriteria(where(SpringCacheEntityKeys.contextValue).is(contextValue));
      }
      query.fields().include(SpringCacheEntityKeys.entityUpdatedAt).include(SpringCacheEntityKeys.contextValue);

      final io.harness.cache.SpringCacheEntity cacheEntity = mongoTemplate.findOne(query, SpringCacheEntity.class);

      if (cacheEntity == null) {
        return null;
      }

      return cacheEntity.getEntityUpdatedAt();
    } catch (RuntimeException ex) {
      log.error("Failed to obtain from cache", ex);
    }
    return null;
  }

  private <T extends Distributable> T get(
      Long contextValue, long algorithmId, long structureHash, String key, List<String> params) {
    try {
      Query query = new Query(
          where(SpringCacheEntityKeys.canonicalKey).is(canonicalKey(algorithmId, structureHash, key, params)));

      if (contextValue != null) {
        query.addCriteria(where(SpringCacheEntityKeys.contextValue).is(contextValue));
      }

      final io.harness.cache.SpringCacheEntity cacheEntity = mongoTemplate.findOne(query, SpringCacheEntity.class);

      if (cacheEntity == null) {
        return null;
      }

      return (T) kryoSerializer.asInflatedObject(cacheEntity.getEntity());
    } catch (RuntimeException ex) {
      log.error("Failed to obtain from cache", ex);
    }
    return null;
  }

  private <T extends Distributable> void upsertInternal(
      T entity, Duration ttl, boolean downgrade, long entityLastUpdatedAt) {
    final String canonicalKey =
        canonicalKey(entity.algorithmId(), entity.structureHash(), entity.key(), entity.parameters());
    Long contextValue =
        entity instanceof Nominal ? ((Nominal) entity).contextHash() : ((Ordinal) entity).contextOrder();

    try {
      Query query = new Query(where(SpringCacheEntityKeys.canonicalKey).is(canonicalKey));
      if (!downgrade && entity instanceof Ordinal) {
        query.addCriteria(where(SpringCacheEntityKeys.contextValue).lt(contextValue));
      }

      Update update = new Update()
                          .setOnInsert(SpringCacheEntityKeys.canonicalKey, canonicalKey)
                          .set(SpringCacheEntityKeys.contextValue, contextValue)
                          .set(SpringCacheEntityKeys.entity, kryoSerializer.asDeflatedBytes(entity))
                          .set(SpringCacheEntityKeys.validUntil, Date.from(OffsetDateTime.now().plus(ttl).toInstant()))
                          .set(SpringCacheEntityKeys.entityUpdatedAt, entityLastUpdatedAt);

      mongoTemplate.findAndModify(query, update, HMongoTemplate.upsertReturnNewOptions, SpringCacheEntity.class);
    } catch (MongoCommandException e) {
      if (ErrorCategory.fromErrorCode(e.getErrorCode()) != DUPLICATE_KEY) {
        log.error("Failed to update cache for key {}, hash {}", canonicalKey, contextValue, e);
      } else {
        new Exception().addSuppressed(e);
      }
    } catch (DuplicateKeyException e) {
      log.error("Failed to update cache for key {}, hash {} ", canonicalKey, contextValue, e);
    } catch (RuntimeException e) {
      log.error("Failed to update cache for key {}, hash {}", canonicalKey, contextValue, e);
    }
  }
}
