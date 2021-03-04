package io.harness.delegate.task.manifests.request;

import static io.harness.expression.Expression.ALLOW_SECRETS;

import io.harness.expression.Expression;
import io.harness.expression.ExpressionReflectionUtils.NestedAnnotationResolver;
import io.harness.manifest.CustomManifestSource;

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
