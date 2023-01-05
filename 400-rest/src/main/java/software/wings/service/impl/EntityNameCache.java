/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static dev.morphia.mapping.Mapper.ID_KEY;
import static org.apache.commons.lang3.StringUtils.isBlank;

import io.harness.eraro.ErrorCode;
import io.harness.exception.WingsException;
import io.harness.persistence.NameAccess;

import software.wings.beans.Application;
import software.wings.beans.EntityType;
import software.wings.beans.Environment;
import software.wings.beans.InfrastructureMapping;
import software.wings.beans.InfrastructureProvisioner;
import software.wings.beans.Pipeline;
import software.wings.beans.Service;
import software.wings.beans.SettingAttribute;
import software.wings.beans.Workflow;
import software.wings.beans.artifact.ArtifactStream;
import software.wings.beans.template.Template;
import software.wings.beans.template.TemplateFolder;
import software.wings.beans.trigger.Trigger;
import software.wings.dl.WingsPersistence;
import software.wings.verification.CVConfiguration;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Singleton
@Slf4j
public class EntityNameCache {
  @Inject private WingsPersistence wingsPersistence;

  private LoadingCache<EntityNameCacheKey, String> entityNameCache =
      CacheBuilder.newBuilder()
          .maximumSize(20000)
          .expireAfterWrite(15, TimeUnit.MINUTES)
          .build(new CacheLoader<EntityNameCacheKey, String>() {
            @Override
            public String load(EntityNameCacheKey entityNameCacheKey) {
              return fetchEntityName(entityNameCacheKey);
            }
          });

  private String fetchEntityName(EntityNameCacheKey entityNameCacheKey) {
    Class claz = null;
    switch (entityNameCacheKey.entityType) {
      case APPLICATION: {
        claz = Application.class;
        break;
      }
      case SERVICE: {
        claz = Service.class;
        break;
      }
      case ENVIRONMENT: {
        claz = Environment.class;
        break;
      }
      case WORKFLOW: {
        claz = Workflow.class;
        break;
      }
      case PIPELINE: {
        claz = Pipeline.class;
        break;
      }
      case PROVISIONER: {
        claz = InfrastructureProvisioner.class;
        break;
      }
      case TEMPLATE: {
        claz = Template.class;
        break;
      }
      case TEMPLATE_FOLDER: {
        claz = TemplateFolder.class;
        break;
      }
      case TRIGGER: {
        claz = Trigger.class;
        break;
      }
      case INFRASTRUCTURE_MAPPING: {
        claz = InfrastructureMapping.class;
        break;
      }
      case ARTIFACT_STREAM: {
        claz = ArtifactStream.class;
        break;
      }
      case VERIFICATION_CONFIGURATION: {
        claz = CVConfiguration.class;
        break;
      }
      case SETTING_ATTRIBUTE: {
        claz = SettingAttribute.class;
        break;
      }
      default: {
        log.error("Invalid entity type For EntityNameCache: " + entityNameCacheKey.entityType);
      }
    }

    if (claz != null) {
      NameAccess nameAccess = getNameForEntity(entityNameCacheKey, claz);
      if (nameAccess != null) {
        return nameAccess.getName();
      }
    }
    return null;
  }

  private NameAccess getNameForEntity(EntityNameCacheKey entityNameCacheKey, Class claz) {
    return (NameAccess) wingsPersistence.createQuery(claz)
        .filter(ID_KEY, entityNameCacheKey.entityId)
        .project("name", true)
        .get();
  }

  /**
   * This will be public facing api for outside
   */
  public String getEntityName(EntityType entityType, String entityId) throws ExecutionException {
    if (entityType == null || isBlank(entityId)) {
      throw new WingsException(ErrorCode.INVALID_ARGUMENT, "EntityType or EntityId cant be null")
          .addParam("message", "EntityType or EntityId cant be null");
    }

    return entityNameCache.get(new EntityNameCacheKey(entityId, entityType));
  }

  public void invalidateCache(EntityType entityType, String entityId) {
    entityNameCache.refresh(new EntityNameCacheKey(entityId, entityType));
  }

  @AllArgsConstructor
  private static class EntityNameCacheKey {
    private String entityId;
    private EntityType entityType;

    public String generateKey() {
      return new StringBuilder(128).append(entityType.name()).append('_').append(entityId).toString();
    }

    @Override
    public String toString() {
      return generateKey();
    }

    @Override
    public boolean equals(Object o) {
      if (!(o instanceof EntityNameCacheKey)) {
        return false;
      }

      return generateKey().equals(((EntityNameCacheKey) o).generateKey());
    }

    @Override
    public int hashCode() {
      return generateKey().hashCode();
    }
  }
}
