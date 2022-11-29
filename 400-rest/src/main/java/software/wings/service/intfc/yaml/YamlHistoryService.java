/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.intfc.yaml;

import software.wings.service.intfc.ownership.OwnedByAccount;
import software.wings.yaml.YamlVersion;
import software.wings.yaml.YamlVersion.Type;

import java.util.List;
import org.hibernate.validator.constraints.NotEmpty;

/**
 * Yaml History Service.
 *
 * @author bsollish
 */
public interface YamlHistoryService extends OwnedByAccount {
  /**
   * Save.
   *
   * @param yv the yaml version
   * @return the yaml version
   */
  YamlVersion save(YamlVersion yv);

  /**
   * Find by uuid.
   *
   * @param uuid the uuid
   * @return the yaml version
   */
  YamlVersion get(@NotEmpty String uuid);

  /**
   * Find by entityId.
   *
   * @param entityId the entityId
   * @param type the yaml type
   * @return the yaml version
   */
  List<YamlVersion> getList(String entityId, Type type);

  /**
   * get back the highest version
   *
   * @param entityId the entityId
   * @param type the yaml type
   * @return the yaml version
   */
  YamlVersion getHighestVersion(String entityId, Type type);
}
