/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.utils;
import static io.harness.annotations.dev.HarnessTeam.PL;

import static org.apache.commons.lang3.StringUtils.startsWith;

import io.harness.annotations.dev.CodePulse;
import io.harness.annotations.dev.HarnessModuleComponent;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.ProductModule;
import io.harness.security.annotations.DelegateAuth;
import io.harness.security.annotations.NextGenManagerAuth;

import java.lang.reflect.Method;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ResourceInfo;
import javax.ws.rs.core.HttpHeaders;

@CodePulse(module = ProductModule.CDS, unitCoverageRequired = true, components = {HarnessModuleComponent.CDS_FIRST_GEN})
@OwnedBy(PL)
public class JerseyFilterUtils {
  //
  // DELEGATE

  public static boolean isDelegateRequest(ContainerRequestContext requestContext, ResourceInfo resourceInfo) {
    return isDelegateAPI(resourceInfo)
        && startsWith(requestContext.getHeaderString(HttpHeaders.AUTHORIZATION), "Delegate ");
  }

  private static boolean isDelegateAPI(ResourceInfo resourceInfo) {
    Class<?> resourceClass = resourceInfo.getResourceClass();
    Method resourceMethod = resourceInfo.getResourceMethod();

    return resourceMethod.getAnnotation(DelegateAuth.class) != null
        || resourceClass.getAnnotation(DelegateAuth.class) != null
        || resourceMethod.getAnnotation(io.harness.security.annotations.DelegateAuth2.class) != null
        || resourceClass.getAnnotation(io.harness.security.annotations.DelegateAuth2.class) != null;
  }

  //
  // NEXT GEN MANAGER

  public static boolean isNextGenManagerRequest(ResourceInfo resourceInfo) {
    return isNextGenManagerAPI(resourceInfo);
  }

  private static boolean isNextGenManagerAPI(ResourceInfo resourceInfo) {
    Class<?> resourceClass = resourceInfo.getResourceClass();
    Method resourceMethod = resourceInfo.getResourceMethod();
    return resourceMethod.getAnnotation(NextGenManagerAuth.class) != null
        || resourceClass.getAnnotation(NextGenManagerAuth.class) != null;
  }
}
