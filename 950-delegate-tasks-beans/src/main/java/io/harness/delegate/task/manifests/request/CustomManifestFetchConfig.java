/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.manifests.request;

import static io.harness.expression.Expression.ALLOW_SECRETS;

import io.harness.expression.Expression;
import io.harness.manifest.CustomManifestSource;
import io.harness.reflection.ExpressionReflectionUtils.NestedAnnotationResolver;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class CustomManifestFetchConfig implements NestedAnnotationResolver {
  /**
   *  Unique identifier that is used in response as map key to create a correlation between request source and response
   *  output, i.e. in kubernetes deployments {@link software.wings.helpers.ext.k8s.request.K8sValuesLocation} enum name
   *  is used as key that relate to the location of values overrides (environment, service, etc.)
   */
  String key;

  /**
   * Indicate if files are required (if set to false, will not fail task if any of the files is missing)
   */
  boolean required;

  /**
   * Indicate if output from this configuration script should be used for other configurations without custom script
   * i.e. is used in kubernetes deployments to reuse output from service manifest (default source) for values overrides
   * without own script
   */
  boolean defaultSource;

  @Expression(ALLOW_SECRETS) CustomManifestSource customManifestSource;
}
