/*
 * Copyright 2023 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.views.helper;

import static io.harness.annotations.dev.HarnessTeam.CE;

import io.harness.annotations.dev.OwnedBy;

import com.google.common.collect.ImmutableMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import org.json.JSONObject;

@UtilityClass
@OwnedBy(CE)
public final class GovernancePoliciesPromptConstants {
  public static final String DEFAULT_POLICY = "\ninput: Delete unattached EBS Volumes\n"
      + "output: policies:\n"
      + "  - name: delete-unattached-volumes\n"
      + "    resource: ebs\n"
      + "    filters:\n"
      + "      - Attachments: []\n"
      + "      - State: available\n"
      + "    actions:\n"
      + "      - delete";
  public static final JSONObject DEFAULT_POLICY_CHAT_MODEL = new JSONObject();
  static {
    DEFAULT_POLICY_CHAT_MODEL.put("input", "Delete unattached EBS Volumes");
    DEFAULT_POLICY_CHAT_MODEL.put("output",
        "```policies:\n - name: delete-unattached-volumes\n resource: ebs\n filters:\n - Attachments: []\n - State: available\n actions:\n - delete```");
  }
  public static final ImmutableMap<String, String> CLAIMS = ImmutableMap.of("iss", "Harness Inc", "sub", "CCM");
  public static final String PROMPT_PATH = "governance/prompts/";
  public static final String POLICIES_PATH = "governance/policies/";
  public static final String TEXT_BISON = "text-bison";
  public static final String GPT3 = "gpt3";
  public static final Map<String, List<String>> AWS_DEFAULT_POLICIES = new HashMap<>();
  public static final Map<String, List<String>> AZURE_DEFAULT_POLICIES = new HashMap<>();
  public static final Map<String, List<String>> AWS_SAMPLE_POLICIES_FOR_RESOURCE = new HashMap<>();
  public static final Map<String, List<String>> AZURE_SAMPLE_POLICIES_FOR_RESOURCE = new HashMap<>();
  static {
    AWS_SAMPLE_POLICIES_FOR_RESOURCE.put("ami", List.of("ec2-ancient-images-list", "ami-ensure-encrypted"));
    AWS_SAMPLE_POLICIES_FOR_RESOURCE.put("asg", List.of("asg-unused-list", "asg-suspend-processes"));
    AWS_SAMPLE_POLICIES_FOR_RESOURCE.put("app-elb", List.of("alb-http2-enabled", "turn-on-elb-deletion-protection"));
    AWS_SAMPLE_POLICIES_FOR_RESOURCE.put(
        "cache-cluster", List.of("elasticache-cluster-list", "elasticache-delete-stale-clusters"));
    AWS_SAMPLE_POLICIES_FOR_RESOURCE.put("ebs", List.of("ebs-unencrypted-ebs-list", "delete-unattached-volumes"));
    AWS_SAMPLE_POLICIES_FOR_RESOURCE.put(
        "ebs-snapshot", List.of("ebs-old-ebs-snapshots-list", "delete-snapshot-with-no-volume"));
    AWS_SAMPLE_POLICIES_FOR_RESOURCE.put("ec2", List.of("ec2-underutilized-list", "ec2-old-instances-stop-age7Days"));
    AWS_SAMPLE_POLICIES_FOR_RESOURCE.put("ec2-host", List.of("ec2-host-with-id", "ec2-dedicated-hosts-rule"));
    AWS_SAMPLE_POLICIES_FOR_RESOURCE.put(
        "ec2-reserved", List.of("list-ec2-reserved-of-given-instanceType", "add-tag-for-convertible-ec2-reserved"));
    AWS_SAMPLE_POLICIES_FOR_RESOURCE.put("ecs-service", List.of("ecs-service-taggable", "no-public-ips-services"));
    AWS_SAMPLE_POLICIES_FOR_RESOURCE.put("efs", List.of("list-unused-efs", "optimize-efs-storage-tier"));
    AWS_SAMPLE_POLICIES_FOR_RESOURCE.put(
        "elastic-ip", List.of("list-eips-network-id-absent", "release-unattached-eips"));
    AWS_SAMPLE_POLICIES_FOR_RESOURCE.put("elb", List.of("elb-low-request-count-list", "elb-delete-unused"));
    AWS_SAMPLE_POLICIES_FOR_RESOURCE.put(
        "glue-job", List.of("find-glue-jobs-with-old-versions", "gluejob-enable-metrics"));
    AWS_SAMPLE_POLICIES_FOR_RESOURCE.put(
        "iam-policy", List.of("iam-no-used-allow-all-policy", "iam-delete-unused-policies"));
    AWS_SAMPLE_POLICIES_FOR_RESOURCE.put(
        "iam-user", List.of("iam-users-in-admin-group", "iam-mfa-active-key-no-login"));
    AWS_SAMPLE_POLICIES_FOR_RESOURCE.put("lambda", List.of("list-lambda-with-errors", "delete-lambda-unused"));
    AWS_SAMPLE_POLICIES_FOR_RESOURCE.put(
        "launch-config", List.of("asg-launch-config-old", "asg-unused-launch-config-delete"));
    AWS_SAMPLE_POLICIES_FOR_RESOURCE.put("launch-template-version",
        List.of("launch-template-with-Encrypted-as-true", "tag-launch-template-with-DeleteOnTermination-as-false"));
    AWS_SAMPLE_POLICIES_FOR_RESOURCE.put(
        "log-group", List.of("cloudwatch-stale-groups", "cloudwatch-set-log-group-retention"));
    AWS_SAMPLE_POLICIES_FOR_RESOURCE.put("network-addr", List.of("unused-eip-list", "release-network-addr"));
    AWS_SAMPLE_POLICIES_FOR_RESOURCE.put("rds", List.of("rds-low-util-list", "rds-delete-unused"));
    AWS_SAMPLE_POLICIES_FOR_RESOURCE.put(
        "rds-cluster-snapshot", List.of("rds-cluster-snapshots-expired", "rds-cluster-snapshot-prune-permissions"));
    AWS_SAMPLE_POLICIES_FOR_RESOURCE.put("rds-snapshot", List.of("rds-snapshot-orphan", "rds-snapshot-delete-stale"));
    AWS_SAMPLE_POLICIES_FOR_RESOURCE.put(
        "rds-subnet-group", List.of("rds-subnet-group-list-unused", "rds-subnet-group-delete"));
    AWS_SAMPLE_POLICIES_FOR_RESOURCE.put(
        "redshift-snapshot", List.of("redshift-old-snapshots", "redshift-snapshot-revoke-access"));
    AWS_SAMPLE_POLICIES_FOR_RESOURCE.put(
        "redshift", List.of("redshift-daily-snapshot-count", "redshift-remove-public-access"));
    AWS_SAMPLE_POLICIES_FOR_RESOURCE.put("s3", List.of("s3-bucket-encryption-off-list", "s3-disable-versioning"));
    AWS_SAMPLE_POLICIES_FOR_RESOURCE.put(
        "secrets-manager", List.of("list-unused-secrets-manager", "delete-cross-account-secrets"));

    AWS_DEFAULT_POLICIES.put("network-addr", List.of("unused-eip-list"));
    AWS_DEFAULT_POLICIES.put("ec2",
        List.of("ec2-underutilized-list", "ec2-old-instances-stop-age7Days", "ec2-attributes", "ec2-tag-filter"));
    AWS_DEFAULT_POLICIES.put("ebs-snapshot", List.of("ebs-old-ebs-snapshots-list"));
    AWS_DEFAULT_POLICIES.put("elb", List.of("elb-unused-list"));
    AWS_DEFAULT_POLICIES.put("rds", List.of("rds-unused-databases-list", "rds-low-util-list"));
    AWS_DEFAULT_POLICIES.put("s3", List.of("s3-bucket-encryption-off-list", "s3-bucket-lifecycle-null-list"));
    AWS_DEFAULT_POLICIES.put("ebs", List.of("delete-unattached-volumes"));

    AZURE_SAMPLE_POLICIES_FOR_RESOURCE.put("azure.cosmosdb", List.of("cosmosdb-low-usage", "cosmos-firewall-clear"));
    AZURE_SAMPLE_POLICIES_FOR_RESOURCE.put("azure.disk", List.of("orphaned-disk", "delete-unattached-disk"));
    AZURE_SAMPLE_POLICIES_FOR_RESOURCE.put(
        "azure.keyvault", List.of("inactive-keyvaults", "azure-keyvault-update-access-policies"));
    AZURE_SAMPLE_POLICIES_FOR_RESOURCE.put(
        "azure.loadbalancer", List.of("low-usage-load-balancers", "tag-loadbalancer-with-ipv6-frontend"));
    AZURE_SAMPLE_POLICIES_FOR_RESOURCE.put(
        "azure.networkinterface", List.of("orphaned-network-interface", "delete-nic-with-user-routes"));
    AZURE_SAMPLE_POLICIES_FOR_RESOURCE.put(
        "azure.publicip", List.of("publicip-under-dos-attack", "delete-orhpaned-publicip"));
    AZURE_SAMPLE_POLICIES_FOR_RESOURCE.put(
        "azure.resourcegroup", List.of("empty-resource-groups", "delete-tagged-resource-groups"));
    AZURE_SAMPLE_POLICIES_FOR_RESOURCE.put(
        "azure.sql-database", List.of("sql-database-no-tde", "update-short-term-backup-retention-policy"));
    AZURE_SAMPLE_POLICIES_FOR_RESOURCE.put(
        "azure.sql-server", List.of("expensive-sql-servers", "delete-sqlserver-under-utilized"));
    AZURE_SAMPLE_POLICIES_FOR_RESOURCE.put(
        "azure.storage-container", List.of("storage-container-public", "set-storage-container-access-private"));
    AZURE_SAMPLE_POLICIES_FOR_RESOURCE.put("azure.storage", List.of("storage-bypass", "enable-blob-storage-logging"));
    AZURE_SAMPLE_POLICIES_FOR_RESOURCE.put("azure.vm", List.of("vms-with-public-ip", "stop-underutilized-vms"));

    AZURE_DEFAULT_POLICIES.put("azure.cosmosdb", List.of("cosmosdb-low-usage"));
    AZURE_DEFAULT_POLICIES.put("azure.disk", List.of("orphaned-disk"));
    AZURE_DEFAULT_POLICIES.put("azure.keyvault", List.of("inactive-keyvaults"));
    AZURE_DEFAULT_POLICIES.put("azure.loadbalancer", List.of("tag-loadbalancer-with-ipv6-frontend"));
    AZURE_DEFAULT_POLICIES.put("azure.networkinterface", List.of("delete-nic-with-user-routes"));
    AZURE_DEFAULT_POLICIES.put("azure.publicip", List.of("publicip-under-dos-attack"));
    AZURE_DEFAULT_POLICIES.put("azure.resourcegroup", List.of("delete-tagged-resource-groups"));
    AZURE_DEFAULT_POLICIES.put("azure.sql-database", List.of("sql-database-no-tde"));
    AZURE_DEFAULT_POLICIES.put("azure.sql-server", List.of("delete-sqlserver-under-utilized"));
    AZURE_DEFAULT_POLICIES.put("azure.storage-container", List.of("storage-container-public"));
    AZURE_DEFAULT_POLICIES.put("azure.storage", List.of("enable-blob-storage-logging"));
    AZURE_DEFAULT_POLICIES.put("azure.vm", List.of("stop-underutilized-vms"));
  }
}
