/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package io.harness.terraform.expression;

public interface TerraformPlanExpressionInterface {
  String FUNCTOR_NAME = "terraformPlan";
  String DELEGATE_FUNCTOR_NAME = "delegateTerraformPlan";

  String DELEGATE_EXPRESSION = "${" + DELEGATE_FUNCTOR_NAME + ".obtainPlan(\"%s\", %d).%s()}";
  String HUMAN_READABLE_DELEGATE_EXPRESSION =
      "${" + DELEGATE_FUNCTOR_NAME + ".obtainHumanReadablePlan(\"%s\", %d).%s()}";
  String TERRAFORM_CLOUD_PLAN_DELEGATE_EXPRESSION = "${" + DELEGATE_FUNCTOR_NAME + ".obtainCloudPlan(\"%s\", %d).%s()}";
  String POLICY_CHECKS_DELEGATE_EXPRESSION = "${" + DELEGATE_FUNCTOR_NAME + ".obtainPolicyChecks(\"%s\", %d).%s()}";

  String EXAMPLE_USAGE = "${" + FUNCTOR_NAME + ".jsonFilePath()}";
  String DESTROY_EXAMPLE_USAGE = "${" + FUNCTOR_NAME + ".destroy.jsonFilePath()}";
  String HUMAN_READABLE_EXAMPLE_USAGE = "${" + FUNCTOR_NAME + ".humanReadableFilePath()}";
  String DESTROY_HUMAN_READABLE_EXAMPLE_USAGE = "${" + FUNCTOR_NAME + "destroy.humanReadableFilePath()}";

  String jsonFilePath();
  String humanReadableFilePath();
  String policyChecksJsonFilePath();
}
