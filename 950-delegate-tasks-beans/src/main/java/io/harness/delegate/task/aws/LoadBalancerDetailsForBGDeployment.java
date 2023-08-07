/*
 * Copyright 2020 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.delegate.task.aws;

import io.harness.annotation.RecasterAlias;

import lombok.Data;

@Data
@RecasterAlias("io.harness.delegate.task.aws.LoadBalancerDetailsForBGDeployment")
public class LoadBalancerDetailsForBGDeployment {
  public LoadBalancerDetailsForBGDeployment() {}
  private String loadBalancerName;
  private String loadBalancerArn;
  private String prodListenerPort;
  private String stageListenerPort;
  private String prodListenerArn;
  private String stageListenerArn;
  private String prodTargetGroupName;
  private String prodTargetGroupArn;
  private String stageTargetGroupName;
  private String stageTargetGroupArn;

  private boolean useSpecificRules;
  private String prodRuleArn;
  private String stageRuleArn;

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    private String loadBalancerName;
    private String loadBalancerArn;
    private String prodListenerPort;
    private String stageListenerPort;
    private String prodListenerArn;
    private String stageListenerArn;
    private String prodTargetGroupName;
    private String prodTargetGroupArn;
    private String stageTargetGroupName;
    private String stageTargetGroupArn;

    private boolean useSpecificRules;
    private String prodRuleArn;
    private String stageRuleArn;

    public Builder loadBalancerName(String loadBalancerName) {
      this.loadBalancerName = loadBalancerName;
      return this;
    }

    public Builder loadBalancerArn(String loadBalancerArn) {
      this.loadBalancerArn = loadBalancerArn;
      return this;
    }

    public Builder prodListenerPort(String prodListenerPort) {
      this.prodListenerPort = prodListenerPort;
      return this;
    }

    public Builder prodListenerArn(String prodListenerArn) {
      this.prodListenerArn = prodListenerArn;
      return this;
    }

    public Builder stageListenerPort(String stageListenerPort) {
      this.stageListenerPort = stageListenerPort;
      return this;
    }

    public Builder stageListenerArn(String stageListenerArn) {
      this.stageListenerArn = stageListenerArn;
      return this;
    }

    public Builder prodTargetGroupName(String prodTargetGroupName) {
      this.prodTargetGroupName = prodTargetGroupName;
      return this;
    }

    public Builder stageTargetGroupName(String stageTargetGroupName) {
      this.stageTargetGroupName = stageTargetGroupName;
      return this;
    }

    public Builder prodTargetGroupArn(String prodTargetGroupArn) {
      this.prodTargetGroupArn = prodTargetGroupArn;
      return this;
    }

    public Builder stageTargetGroupArn(String stageTargetGroupArn) {
      this.stageTargetGroupArn = stageTargetGroupArn;
      return this;
    }

    public Builder useSpecificRules(boolean useSpecificRules) {
      this.useSpecificRules = useSpecificRules;
      return this;
    }

    public Builder prodRuleArn(String prodRuleArn) {
      this.prodRuleArn = prodRuleArn;
      return this;
    }

    public Builder stageRuleArn(String stageRuleArn) {
      this.stageRuleArn = stageRuleArn;
      return this;
    }

    public LoadBalancerDetailsForBGDeployment build() {
      LoadBalancerDetailsForBGDeployment lbDetail = new LoadBalancerDetailsForBGDeployment();
      lbDetail.setLoadBalancerName(loadBalancerName);
      lbDetail.setLoadBalancerArn(loadBalancerArn);
      lbDetail.setProdListenerPort(prodListenerPort);
      lbDetail.setStageListenerPort(stageListenerPort);
      lbDetail.setProdListenerArn(prodListenerArn);
      lbDetail.setStageListenerArn(stageListenerArn);
      lbDetail.setProdTargetGroupName(prodTargetGroupName);
      lbDetail.setProdTargetGroupArn(prodTargetGroupArn);
      lbDetail.setStageTargetGroupName(stageTargetGroupName);
      lbDetail.setStageTargetGroupArn(stageTargetGroupArn);
      lbDetail.setUseSpecificRules(useSpecificRules);
      lbDetail.setProdRuleArn(prodRuleArn);
      lbDetail.setStageRuleArn(stageRuleArn);
      return lbDetail;
    }
  }
}
