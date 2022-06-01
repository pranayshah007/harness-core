/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package software.wings.service.impl;

import static io.harness.annotations.dev.HarnessTeam.CDP;
import static io.harness.data.structure.EmptyPredicate.isEmpty;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static java.util.Arrays.asList;

import io.harness.annotations.dev.OwnedBy;
import io.harness.expression.ExpressionEvaluator;

import software.wings.api.DeploymentType;
import software.wings.beans.AwsInstanceFilter;
import software.wings.common.InfrastructureConstants;
import software.wings.expression.ManagerExpressionEvaluator;

import com.amazonaws.services.ec2.model.Filter;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

@Singleton
@OwnedBy(CDP)
public class AwsUtils {
  @Inject private ManagerExpressionEvaluator expressionEvaluator;

  public String getHostnameFromPrivateDnsName(String dnsName) {
    return isNotEmpty(dnsName) ? dnsName.split("\\.")[0] : "";
  }

  public String getHostnameFromConvention(Map<String, Object> context, String hostNameConvention) {
    if (isEmpty(hostNameConvention)) {
      hostNameConvention = InfrastructureConstants.DEFAULT_AWS_HOST_NAME_CONVENTION;
    }
    return expressionEvaluator.substitute(hostNameConvention, context);
  }

  @NotNull
  public List<Filter> getFilters(DeploymentType deploymentType, AwsInstanceFilter instanceFilter) {
    List<Filter> filters = new ArrayList<>();
    filters.add(new Filter("instance-state-name").withValues("running"));
    if (instanceFilter != null) {
      if (isNotEmpty(instanceFilter.getVpcIds())) {
        filters.add(new Filter("vpc-id", instanceFilter.getVpcIds()));
      }
      if (isNotEmpty(instanceFilter.getTags())) {
        Multimap<String, String> tags = ArrayListMultimap.create();
        instanceFilter.getTags().forEach(tag -> {
          if (!(ExpressionEvaluator.containsVariablePattern((String) tag.getKey())
                  || ExpressionEvaluator.containsVariablePattern((String) tag.getValue()))) {
            tags.put(tag.getKey(), tag.getValue());
          }
        });
        tags.keySet().forEach(key -> filters.add(new Filter("tag:" + key, new ArrayList<>(tags.get(key)))));
      }
      if (DeploymentType.WINRM == deploymentType) {
        filters.add(new Filter("platform", asList("windows")));
      }
    }
    return filters;
  }
}
