/*
 * Copyright 2022 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.
 */

package io.harness.ccm.views.service.impl;

import static io.harness.annotations.dev.HarnessTeam.CE;

import io.harness.EntityType;
import io.harness.annotations.dev.OwnedBy;
import io.harness.ccm.views.dao.RuleDAO;
import io.harness.ccm.views.entities.Rule;
import io.harness.ccm.views.helper.GovernanceRuleFilter;
import io.harness.ccm.views.helper.RuleList;
import io.harness.ccm.views.service.GovernanceRuleService;
import io.harness.exception.InvalidRequestException;
import io.harness.pms.yaml.YamlUtils;
import io.harness.yaml.validator.YamlSchemaValidator;

import com.google.common.collect.Lists;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.zeroturnaround.exec.ProcessExecutor;

@Slf4j
@Singleton
@OwnedBy(CE)
public class GovernanceRuleServiceImpl implements GovernanceRuleService {
  @Inject private RuleDAO ruleDAO;
  @Inject private YamlSchemaValidator yamlSchemaValidator;
  @Override
  public boolean save(Rule rules) {
    return ruleDAO.save(rules);
  }

  @Override
  public boolean delete(String accountId, String uuid) {
    return ruleDAO.delete(accountId, uuid);
  }

  @Override
  public Rule update(Rule rules, String accountId) {
    return ruleDAO.update(rules, accountId);
  }

  @Override
  public RuleList list(GovernanceRuleFilter governancePolicyFilter) {
    return ruleDAO.list(governancePolicyFilter);
  }

  @Override
  public List<Rule> list(String accountId, List<String> uuid) {
    return ruleDAO.check(accountId, uuid);
  }

  @Override
  public Rule fetchByName(String accountId, String name, boolean create) {
    return ruleDAO.fetchByName(accountId, name, create);
  }

  @Override
  public Rule fetchById(String accountId, String name, boolean create) {
    return ruleDAO.fetchById(accountId, name, create);
  }

  @Override
  public void check(String accountId, List<String> rulesIdentifiers) {
    List<Rule> rules = ruleDAO.check(accountId, rulesIdentifiers);
    if (rules.size() != rulesIdentifiers.size()) {
      for (Rule it : rules) {
        log.info("{} {} ", it, it.getUuid());
        rulesIdentifiers.remove(it.getUuid());
      }
      if (!rulesIdentifiers.isEmpty()) {
        throw new InvalidRequestException("No such rules exist:" + rulesIdentifiers);
      }
    }
  }

  @Override
  public void customRuleLimit(String accountId) {
    GovernanceRuleFilter governancePolicyFilter = GovernanceRuleFilter.builder().build();
    governancePolicyFilter.setAccountId(accountId);
    governancePolicyFilter.setIsOOTB(false);
    if (list(governancePolicyFilter).getRules().size() >= 300) {
      throw new InvalidRequestException("You have exceeded the limit for rules creation");
    }
  }

  @Override
  public void custodianValidate(Rule rule) {
    try {
      String fileName = String.join(rule.getName(), rule.getAccountId(), ".yaml");

      FileWriter fw = new FileWriter(fileName, true);
      BufferedWriter bw = new BufferedWriter(fw);
      bw.write(rule.getRulesYaml());
      bw.newLine();
      bw.close();

      final ArrayList<String> Validatecmd = Lists.newArrayList("custodian", "validate", fileName);
      String processResult = getProcessExecutor().command(Validatecmd).readOutput(true).execute().outputString();
      log.info("{}", processResult);

      File file = new File(fileName);
      file.delete();

      if (processResult.contains("Configuration invalid")) {
        processResult =
            processResult.substring(processResult.indexOf("Configuration invalid"), processResult.lastIndexOf('\n'));
        if (processResult.indexOf("custodian.commands:ERROR")
            != processResult.lastIndexOf("custodian.commands:ERROR")) {
          throw new InvalidRequestException(processResult.substring(
              processResult.indexOf("custodian.commands:ERROR") + 24, processResult.lastIndexOf('\n')));
        } else {
          throw new InvalidRequestException(
              processResult.substring(processResult.indexOf("custodian.commands:ERROR") + 24));
        }
      }

    } catch (IOException | InterruptedException | TimeoutException e) {
      throw new InvalidRequestException("Policy YAML is malformed");
    }
  }

  @Override
  public void validateAWSSchema(Rule rule) {
    log.info("yaml: {}", rule.getRulesYaml());
    try {
      YamlUtils.readTree(rule.getRulesYaml());
      Set<String> ValidateMsg = yamlSchemaValidator.validate(rule.getRulesYaml(), EntityType.CCM_GOVERNANCE_RULE_AWS);
      if (ValidateMsg.size() > 0) {
        log.info(ValidateMsg.toString());
      }
    } catch (IOException e) {
      throw new InvalidRequestException("Policy YAML is malformed");
    }
  }

  ProcessExecutor getProcessExecutor() {
    return new ProcessExecutor();
  }
}
