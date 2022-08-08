/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.yaml;

import static software.wings.beans.yaml.YamlConstants.FIELD_HARNESS_API_VERSION;
import static software.wings.beans.yaml.YamlConstants.FIELD_TYPE;

import io.harness.exception.ExceptionUtils;

import java.io.Serializable;
import java.util.Collection;
import java.util.Comparator;
import java.util.Set;
import java.util.TreeSet;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.yaml.snakeyaml.introspector.BeanAccess;
import org.yaml.snakeyaml.introspector.Property;
import org.yaml.snakeyaml.introspector.PropertyUtils;
@Slf4j
class CustomPropertyUtils extends PropertyUtils {
  @Override
  protected Set<Property> createPropertySet(Class<? extends Object> type, BeanAccess bAccess) {
    try {
      Collection<Property> values = getPropertiesMap(type, BeanAccess.FIELD).values();
      Set<Property> result = new TreeSet<>(new CustomComparator());
      result.addAll(values);

      return result;
    } catch (Exception e) {
      log.error("Error while converting the object to yaml string: " + ExceptionUtils.getMessage(e), e);
    }

    return null;
  }

  private static class CustomComparator implements Comparator<Property>, Serializable {
    @Override
    public int compare(Property lhs, Property rhs) {
      if (FIELD_HARNESS_API_VERSION.equals(lhs.getName())) {
        return -1;
      } else if (FIELD_TYPE.equals(lhs.getName())) {
        if (FIELD_HARNESS_API_VERSION.equals(rhs.getName())) {
          return 1;
        } else {
          return -1;
        }
      }

      return StringUtils.compare(lhs.getName(), rhs.getName());
    }
  }
}
