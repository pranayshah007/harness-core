/*
 * Copyright 2021 Harness Inc. All rights reserved.
 * Use of this source code is governed by the PolyForm Shield 1.0.0 license
 * that can be found in the licenses directory at the root of this repository, also available at
 * https://polyformproject.org/wp-content/uploads/2020/06/PolyForm-Shield-1.0.0.txt.
 */

package software.wings.yaml;

import static io.harness.annotations.dev.HarnessTeam.DX;
import static io.harness.data.structure.EmptyPredicate.isNotEmpty;

import static org.mongodb.morphia.mapping.Mapper.ID_KEY;

import io.harness.annotations.dev.HarnessModule;
import io.harness.annotations.dev.OwnedBy;
import io.harness.annotations.dev.TargetModule;
import io.harness.eraro.ErrorCode;
import io.harness.eraro.Level;
import io.harness.eraro.ResponseMessage;
import io.harness.exception.WingsException;
import io.harness.rest.RestResponse;
import io.harness.yaml.BaseYaml;
import io.harness.yaml.YamlRepresenter;
import io.harness.yaml.YamlUtils;

import software.wings.beans.Account;
import software.wings.beans.Application;
import software.wings.beans.Environment;
import software.wings.beans.InfrastructureProvisioner;
import software.wings.beans.Pipeline;
import software.wings.beans.Service;
import software.wings.beans.SettingAttribute;
import software.wings.beans.Workflow;
import software.wings.beans.command.ServiceCommand;
import software.wings.beans.trigger.Trigger;
import software.wings.dl.WingsPersistence;
import software.wings.service.intfc.yaml.YamlGitService;
import software.wings.settings.SettingVariableTypes;
import software.wings.yaml.YamlVersion.Type;
import software.wings.yaml.gitSync.GitSyncWebhook;
import software.wings.yaml.gitSync.GitSyncWebhook.GitSyncWebhookKeys;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.introspector.PropertyUtils;

@Slf4j
@OwnedBy(DX)
@TargetModule(HarnessModule._955_CG_YAML)
public class YamlHelper {
  public static void addResponseMessage(RestResponse rr, ErrorCode errorCode, Level level, String message) {
    ResponseMessage rm = ResponseMessage.builder().code(errorCode).level(level).message(message).build();

    List<ResponseMessage> responseMessages = rr.getResponseMessages();
    responseMessages.add(rm);
    rr.setResponseMessages(responseMessages);
  }

  public static void addUnrecognizedFieldsMessage(RestResponse rr) {
    addResponseMessage(
        rr, ErrorCode.UNRECOGNIZED_YAML_FIELDS, Level.ERROR, "ERROR: The Yaml provided contains unrecognized fields!");
  }

  public static void addCouldNotMapBeforeYamlMessage(RestResponse rr) {
    addResponseMessage(
        rr, ErrorCode.COULD_NOT_MAP_BEFORE_YAML, Level.ERROR, "ERROR: The BEFORE Yaml could not be mapped!");
  }

  public static void addMissingBeforeYamlMessage(RestResponse rr) {
    addResponseMessage(rr, ErrorCode.MISSING_BEFORE_YAML, Level.ERROR, "ERROR: The BEFORE Yaml is empty or missing!");
  }

  public static void addMissingYamlMessage(RestResponse rr) {
    addResponseMessage(rr, ErrorCode.MISSING_YAML, Level.ERROR, "ERROR: The Yaml is empty or missing!");
  }

  public static void addSettingAttributeNotFoundMessage(RestResponse rr, String uuid) {
    addResponseMessage(
        rr, ErrorCode.GENERAL_YAML_ERROR, Level.ERROR, "ERROR: No Setting Attribute found for uuid: '" + uuid + "'!");
  }

  public static void addUnknownSettingVariableTypeMessage(RestResponse rr, SettingVariableTypes settingVariableType) {
    addResponseMessage(rr, ErrorCode.GENERAL_YAML_ERROR, Level.ERROR,
        "ERROR: Unrecognized SettingVariableType: '" + settingVariableType + "'!");
  }

  public static YamlRepresenter getRepresenter() {
    return getRepresenter(true);
  }

  public static YamlRepresenter getRepresenter(boolean removeEmptyValues) {
    YamlRepresenter representer = new YamlRepresenter(removeEmptyValues);

    // use custom property utils so that PropertyUtils that doesn't sort alphabetically
    PropertyUtils pu = new CustomPropertyUtils();
    pu.setSkipMissingProperties(true);

    representer.setPropertyUtils(pu);

    return representer;
  }

  public static RestResponse<YamlPayload> getYamlRestResponse(
      YamlGitService yamlGitSyncService, String entityId, String accountId, BaseYaml theYaml, String payloadName) {
    RestResponse rr = new RestResponse<>();

    String dumpedYaml = toYamlString(theYaml);
    YamlPayload yp = new YamlPayload(dumpedYaml);
    yp.setName(payloadName);

    rr.setResponseMessages(yp.getResponseMessages());

    if (isNotEmpty(yp.getYaml())) {
      rr.setResource(yp);
    }

    return rr;
  }

  public static RestResponse<YamlPayload> getYamlRestResponse(BaseYaml theYaml, String payloadName) {
    RestResponse rr = new RestResponse<>();

    String dumpedYaml = toYamlString(theYaml);
    YamlPayload yp = new YamlPayload(dumpedYaml);
    yp.setName(payloadName);

    rr.setResponseMessages(yp.getResponseMessages());

    if (isNotEmpty(yp.getYaml())) {
      rr.setResource(yp);
    }

    return rr;
  }

  public static RestResponse<YamlPayload> getYamlRestResponseForActualFile(String content, String payloadName) {
    RestResponse rr = new RestResponse<>();

    YamlPayload yp = new YamlPayload(content);
    yp.setName(payloadName);

    rr.setResponseMessages(yp.getResponseMessages());

    if (isNotEmpty(yp.getYaml())) {
      rr.setResource(yp);
    }

    return rr;
  }

  public static String toYamlString(BaseYaml theYaml) {
    Yaml yaml = new Yaml(YamlHelper.getRepresenter(), YamlUtils.getDumperOptions());
    return YamlUtils.cleanupYaml(yaml.dump(theYaml));
  }

  public static <E> List<E> findDifferenceBetweenLists(List<E> itemsA, List<E> itemsB) {
    // we need to make a copy of itemsA, because we don't want to modify itemsA!
    List<E> diffList = new ArrayList<>(itemsA);

    if (itemsB != null) {
      diffList.removeAll(itemsB);
    }

    return diffList;
  }

  public static <T> Optional<T> doMapperReadValue(
      RestResponse rr, ObjectMapper mapper, String yamlStr, Class<T> theClass) {
    try {
      T thing = mapper.readValue(yamlStr, theClass);
      return Optional.of(thing);
    } catch (WingsException e) {
      throw e;
    } catch (Exception e) {
      // bad before Yaml
      log.error("", e);
      YamlHelper.addCouldNotMapBeforeYamlMessage(rr);
      return Optional.empty();
    }
  }

  public static long getEntityCreatedAt(WingsPersistence wingsPersistence, YamlVersion yv) {
    String entityId = yv.getEntityId();
    Type type = yv.getType();

    switch (type) {
      case SETUP:
        return wingsPersistence.createQuery(Account.class).filter(ID_KEY, entityId).get().getCreatedAt();
      case APP:
        return wingsPersistence.createQuery(Application.class).filter(ID_KEY, entityId).get().getCreatedAt();
      case SERVICE:
        return wingsPersistence.createQuery(Service.class).filter(ID_KEY, entityId).get().getCreatedAt();
      case SERVICE_COMMAND:
        return wingsPersistence.createQuery(ServiceCommand.class).filter(ID_KEY, entityId).get().getCreatedAt();
      case ENVIRONMENT:
        return wingsPersistence.createQuery(Environment.class).filter(ID_KEY, entityId).get().getCreatedAt();
      case SETTING:
        return wingsPersistence.createQuery(SettingAttribute.class).filter(ID_KEY, entityId).get().getCreatedAt();
      case WORKFLOW:
        return wingsPersistence.createQuery(Workflow.class).filter(ID_KEY, entityId).get().getCreatedAt();
      case PIPELINE:
        return wingsPersistence.createQuery(Pipeline.class).filter(ID_KEY, entityId).get().getCreatedAt();
      case TRIGGER:
        return wingsPersistence.createQuery(Trigger.class).filter(ID_KEY, entityId).get().getCreatedAt();
      case PROVISIONER:
        return wingsPersistence.createQuery(InfrastructureProvisioner.class)
            .filter(ID_KEY, entityId)
            .get()
            .getCreatedAt();
      default:
        // nothing to do
    }

    return 0;
  }

  public static GitSyncWebhook verifyWebhookToken(
      WingsPersistence wingsPersistence, String accountId, String webhookToken) {
    GitSyncWebhook gsw = wingsPersistence.createQuery(GitSyncWebhook.class)
                             .filter(GitSyncWebhookKeys.webhookToken, webhookToken)
                             .filter(GitSyncWebhookKeys.accountId, accountId)
                             .get();

    if (gsw != null) {
      return gsw;
    }

    return null;
  }

  public static String trimYaml(String yamlString) {
    return yamlString == null ? null : yamlString.replaceAll("\\s*\\n", "\n");
  }
}
