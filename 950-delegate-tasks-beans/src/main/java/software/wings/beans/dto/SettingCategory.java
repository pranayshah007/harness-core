package software.wings.beans.dto;

import com.google.common.collect.Lists;
import lombok.Getter;
import software.wings.settings.SettingVariableTypes;

import java.util.List;

import static software.wings.settings.SettingVariableTypes.*;
import static software.wings.settings.SettingVariableTypes.CE_AZURE;

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

        @Getter
        private List<SettingVariableTypes> settingVariableTypes;

        SettingCategory(List<SettingVariableTypes> settingVariableTypes) {
            this.settingVariableTypes = settingVariableTypes;
        }
}
