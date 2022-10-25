/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

/**
 *
 */

package software.wings.common;

import io.harness.serializer.MapperUtils;

import software.wings.api.ServiceTemplateElement;
import software.wings.beans.Service;
import software.wings.beans.ServiceTemplate;

import lombok.experimental.UtilityClass;

/**
 * The type Service template expression processor.
 *
 * @author Rishi
 */
@UtilityClass
public class ServiceTemplateExpressionProcessor {
  /**
   * Convert to service template element service template element.
   *
   * @param serviceTemplate the service template
   * @return the service template element
   */
  static ServiceTemplateElement convertToServiceTemplateElement(ServiceTemplate serviceTemplate, Service service) {
    ServiceTemplateElement ste = new ServiceTemplateElement();
    MapperUtils.mapObject(serviceTemplate, ste);

    ste.setServiceElement(ServiceExpressionProcessor.convertToServiceElement(service));
    return ste;
  }
}
