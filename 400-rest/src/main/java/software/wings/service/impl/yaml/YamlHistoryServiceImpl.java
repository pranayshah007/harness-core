/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl.yaml;

import static io.harness.eraro.ErrorCode.INVALID_ARGUMENT;
import static io.harness.validation.Validator.notNullCheck;

import io.harness.exception.WingsException;

import software.wings.dl.WingsPersistence;
import software.wings.service.intfc.yaml.YamlHistoryService;
import software.wings.yaml.YamlHelper;
import software.wings.yaml.YamlVersion;
import software.wings.yaml.YamlVersion.Type;
import software.wings.yaml.YamlVersion.YamlVersionKeys;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import javax.validation.executable.ValidateOnExecution;
import lombok.extern.slf4j.Slf4j;

/**
 * Yaml History Service Implementation class.
 *
 * @author bsollish
 */
@ValidateOnExecution
@Singleton
@Slf4j
public class YamlHistoryServiceImpl implements YamlHistoryService {
  @Inject private WingsPersistence wingsPersistence;

  /* (non-Javadoc)
   * @see software.wings.service.intfc.yaml.YamlHistoryService#save(software.wings.beans.YamlVersion)
   */
  @Override
  public YamlVersion save(YamlVersion yv) {
    notNullCheck("accountId", yv.getAccountId());

    //------- handle inEffectStart and End --------
    long currentTimeMs = System.currentTimeMillis();

    // get highest version in use
    YamlVersion highestVersion = getHighestVersion(yv.getEntityId(), yv.getType());

    if (highestVersion != null) {
      yv.setVersion(highestVersion.getVersion() + 1);

      // set the inEffectStart to the inEffectEnd of the previous version
      if (highestVersion.getInEffectEnd() != 0) {
        yv.setInEffectStart(highestVersion.getInEffectEnd());
      }
    } else { // if no previous version - assume this is the first
      yv.setVersion(1);
      yv.setInEffectStart(YamlHelper.getEntityCreatedAt(wingsPersistence, yv));
    }

    // set the inEffectStart of the new one
    yv.setInEffectEnd(currentTimeMs);
    //---------------------------------------------

    // TODO - not sure how we might want to check for duplicates (?)
    // YamlVersion yamlVersion = Validator.duplicateCheck(() -> wingsPersistence.saveAndGet(YamlVersion.class, yv),
    // "entityId", yv.getEntityId());
    YamlVersion yamlVersion = wingsPersistence.saveAndGet(YamlVersion.class, yv);

    return get(yamlVersion.getUuid());
  }

  /* (non-Javadoc)
   * @see software.wings.service.intfc.yaml.YamlHistoryService#get(java.lang.String)
   */
  @Override
  public YamlVersion get(String uuid) {
    YamlVersion yamlVersion = wingsPersistence.get(YamlVersion.class, uuid);

    if (yamlVersion == null) {
      throw new WingsException(INVALID_ARGUMENT).addParam("args", "YamlVersion - '" + uuid + "' doesn't exist!");
    }

    return yamlVersion;
  }

  /* (non-Javadoc)
   * @see software.wings.service.intfc.yaml.YamlHistoryService#get(java.lang.String)
   */
  @Override
  public List<YamlVersion> getList(String entityId, Type type) {
    return wingsPersistence.createQuery(YamlVersion.class)
        .filter(YamlVersionKeys.entityId, entityId)
        .filter(YamlVersionKeys.type, type)
        .order("-version")
        .asList();
  }

  @Override
  public YamlVersion getHighestVersion(String entityId, Type type) {
    List<YamlVersion> versions = wingsPersistence.createQuery(YamlVersion.class)
                                     .filter(YamlVersionKeys.entityId, entityId)
                                     .filter(YamlVersionKeys.type, type)
                                     .order("-version")
                                     .asList();

    return versions.isEmpty() ? null : versions.get(0);
  }

  @Override
  public void deleteByAccountId(String accountId) {
    wingsPersistence.delete(
        wingsPersistence.createQuery(YamlVersion.class).filter(YamlVersionKeys.accountId, accountId));
  }
}
