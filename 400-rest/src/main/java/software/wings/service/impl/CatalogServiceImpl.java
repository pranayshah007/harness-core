/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

/**
 *
 */

package software.wings.service.impl;

import static io.harness.data.structure.EmptyPredicate.isEmpty;

import io.harness.exception.WingsException;
import io.harness.ff.FeatureFlagService;
import io.harness.serializer.YamlUtils;

import software.wings.beans.CatalogItem;
import software.wings.service.intfc.CatalogService;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * The Class CatalogServiceImpl.
 *
 * @author Rishi
 */
@Singleton
@Slf4j
public class CatalogServiceImpl implements CatalogService {
  private Map<String, List<CatalogItem>> catalogs;

  /**
   * Instantiates a new catalog service impl.
   *
   * @param yamlUtils the yaml utils
   */
  @Inject
  public CatalogServiceImpl(YamlUtils yamlUtils, FeatureFlagService featureFlagService) {
    try {
      URL url = this.getClass().getResource("/configs/catalogs.yml");
      String yaml = Resources.toString(url, Charsets.UTF_8);
      catalogs = yamlUtils.read(yaml, new TypeReference<Map<String, List<CatalogItem>>>() {});

      for (List<CatalogItem> catalogItems : catalogs.values()) {
        Collections.sort(catalogItems, CatalogItem.displayOrderComparator);
      }
    } catch (Exception e) {
      throw new WingsException(e);
    }
  }

  /* (non-Javadoc)
   * @see software.wings.service.intfc.CatalogService#getCatalogItems(java.lang.String)
   */
  @Override
  public List<CatalogItem> getCatalogItems(String catalogType) {
    if (catalogs == null) {
      return null;
    }
    return catalogs.get(catalogType);
  }

  /* (non-Javadoc)
   * @see software.wings.service.intfc.CatalogService#getCatalogs(java.lang.String[])
   */
  @Override
  public Map<String, List<CatalogItem>> getCatalogs(String... catalogTypes) {
    if (catalogs == null) {
      return null;
    }
    if (isEmpty(catalogTypes)) {
      return catalogs;
    }

    Map<String, List<CatalogItem>> maps = new HashMap<>();
    for (String catalogType : catalogTypes) {
      maps.put(catalogType, catalogs.get(catalogType));
    }
    return maps;
  }
}
