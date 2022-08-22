/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.beans.ci.pod;

import static io.harness.expression.Expression.ALLOW_SECRETS;

import io.harness.expression.Expression;
import io.harness.expression.ExpressionReflectionUtils.NestedAnnotationResolver;

import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NonNull;

@Data
@AllArgsConstructor
public abstract class PodParams<T extends ContainerParams> implements NestedAnnotationResolver {
  @NonNull private String name;
  @Expression(ALLOW_SECRETS) @NonNull private String namespace;
  @Expression(ALLOW_SECRETS) private Map<String, String> annotations;
  @Expression(ALLOW_SECRETS) private Map<String, String> labels;
  @Expression(ALLOW_SECRETS) private List<T> containerParamsList;
  private List<T> initContainerParamsList;
  private List<PVCParams> pvcParamList;
  private List<HostAliasParams> hostAliasParamsList;
  private Integer runAsUser;
  private String serviceAccountName;
  private Boolean automountServiceAccountToken;
  private Map<String, String> nodeSelector;
  private List<PodToleration> tolerations;
  private List<PodVolume> volumes;
  private String runtime;
  private String priorityClassName;
  private Long activeDeadLineSeconds;

  public abstract PodParams.Type getType();

  public enum Type { K8 }
}
