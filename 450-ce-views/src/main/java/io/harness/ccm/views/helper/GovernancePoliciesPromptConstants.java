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
  public static final Map<String, List<String>> AWS_SAMPLE_POLICIES_FOR_RESOURCE = new HashMap<>();
  public static final Map<String, List<String>> AZURE_SAMPLE_POLICIES_FOR_RESOURCE = new HashMap<>();
  static {
    AWS_SAMPLE_POLICIES_FOR_RESOURCE.put("ec2", List.of("ec2-underutilized-list", "ec2-unoptimized-ebs"));
    AWS_SAMPLE_POLICIES_FOR_RESOURCE.put("ebs", List.of("ebs-unattached-list", "ebs-unencrypted-ebs-list"));
  }
}
