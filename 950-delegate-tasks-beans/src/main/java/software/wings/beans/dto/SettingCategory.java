package software.wings.beans.dto;

import static software.wings.settings.SettingVariableTypes.AMAZON_S3;
import static software.wings.settings.SettingVariableTypes.AMAZON_S3_HELM_REPO;
import static software.wings.settings.SettingVariableTypes.APM_VERIFICATION;
import static software.wings.settings.SettingVariableTypes.APP_DYNAMICS;
import static software.wings.settings.SettingVariableTypes.ARTIFACTORY;
import static software.wings.settings.SettingVariableTypes.AWS;
import static software.wings.settings.SettingVariableTypes.AZURE;
import static software.wings.settings.SettingVariableTypes.AZURE_ARTIFACTS_PAT;
import static software.wings.settings.SettingVariableTypes.BAMBOO;
import static software.wings.settings.SettingVariableTypes.BASTION_HOST_CONNECTION_ATTRIBUTES;
import static software.wings.settings.SettingVariableTypes.BUG_SNAG;
import static software.wings.settings.SettingVariableTypes.CE_AWS;
import static software.wings.settings.SettingVariableTypes.CE_AZURE;
import static software.wings.settings.SettingVariableTypes.CE_GCP;
import static software.wings.settings.SettingVariableTypes.CUSTOM;
import static software.wings.settings.SettingVariableTypes.DATA_DOG;
import static software.wings.settings.SettingVariableTypes.DOCKER;
import static software.wings.settings.SettingVariableTypes.DYNA_TRACE;
import static software.wings.settings.SettingVariableTypes.ECR;
import static software.wings.settings.SettingVariableTypes.ELB;
import static software.wings.settings.SettingVariableTypes.ELK;
import static software.wings.settings.SettingVariableTypes.GCP;
import static software.wings.settings.SettingVariableTypes.GCR;
import static software.wings.settings.SettingVariableTypes.GCS;
import static software.wings.settings.SettingVariableTypes.GCS_HELM_REPO;
import static software.wings.settings.SettingVariableTypes.GIT;
import static software.wings.settings.SettingVariableTypes.HOST_CONNECTION_ATTRIBUTES;
import static software.wings.settings.SettingVariableTypes.HTTP_HELM_REPO;
import static software.wings.settings.SettingVariableTypes.INSTANA;
import static software.wings.settings.SettingVariableTypes.JENKINS;
import static software.wings.settings.SettingVariableTypes.JIRA;
import static software.wings.settings.SettingVariableTypes.KUBERNETES_CLUSTER;
import static software.wings.settings.SettingVariableTypes.LOGZ;
import static software.wings.settings.SettingVariableTypes.NEW_RELIC;
import static software.wings.settings.SettingVariableTypes.NEXUS;
import static software.wings.settings.SettingVariableTypes.OCI_HELM_REPO;
import static software.wings.settings.SettingVariableTypes.PCF;
import static software.wings.settings.SettingVariableTypes.PHYSICAL_DATA_CENTER;
import static software.wings.settings.SettingVariableTypes.PROMETHEUS;
import static software.wings.settings.SettingVariableTypes.RANCHER;
import static software.wings.settings.SettingVariableTypes.SERVICENOW;
import static software.wings.settings.SettingVariableTypes.SFTP;
import static software.wings.settings.SettingVariableTypes.SLACK;
import static software.wings.settings.SettingVariableTypes.SMB;
import static software.wings.settings.SettingVariableTypes.SMTP;
import static software.wings.settings.SettingVariableTypes.SPLUNK;
import static software.wings.settings.SettingVariableTypes.SPOT_INST;
import static software.wings.settings.SettingVariableTypes.STRING;
import static software.wings.settings.SettingVariableTypes.SUMO;
import static software.wings.settings.SettingVariableTypes.WINRM_CONNECTION_ATTRIBUTES;

import software.wings.settings.SettingVariableTypes;

import com.google.common.collect.Lists;
import java.util.List;
import lombok.Getter;

public enum SettingCategory {
  CLOUD_PROVIDER(
      Lists.newArrayList(PHYSICAL_DATA_CENTER, AWS, AZURE, GCP, KUBERNETES_CLUSTER, PCF, SPOT_INST, RANCHER)),

  CONNECTOR(Lists.newArrayList(SMTP, JENKINS, BAMBOO, SPLUNK, ELK, LOGZ, SUMO, APP_DYNAMICS, INSTANA, NEW_RELIC,
      DYNA_TRACE, BUG_SNAG, DATA_DOG, APM_VERIFICATION, PROMETHEUS, ELB, SLACK, DOCKER, ECR, GCR, NEXUS, ARTIFACTORY,
      AMAZON_S3, GCS, GIT, SMB, JIRA, SFTP, SERVICENOW, CUSTOM)),

  SETTING(Lists.newArrayList(
      HOST_CONNECTION_ATTRIBUTES, BASTION_HOST_CONNECTION_ATTRIBUTES, STRING, WINRM_CONNECTION_ATTRIBUTES)),

  HELM_REPO(Lists.newArrayList(HTTP_HELM_REPO, AMAZON_S3_HELM_REPO, GCS_HELM_REPO, OCI_HELM_REPO)),

  AZURE_ARTIFACTS(Lists.newArrayList(AZURE_ARTIFACTS_PAT)),

  CE_CONNECTOR(Lists.newArrayList(CE_AWS, CE_GCP, CE_AZURE));

  @Getter private List<SettingVariableTypes> settingVariableTypes;

  SettingCategory(List<SettingVariableTypes> settingVariableTypes) {
    this.settingVariableTypes = settingVariableTypes;
  }
}
