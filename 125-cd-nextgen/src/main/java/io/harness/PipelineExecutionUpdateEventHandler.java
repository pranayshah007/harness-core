package io.harness;

import static io.harness.cdng.k8s.K8sStepHelper.MISSING_INFRASTRUCTURE_ERROR;
import static io.harness.exception.WingsException.USER;
import static io.harness.pms.execution.utils.StatusUtils.isFinalStatus;

import io.harness.annotations.dev.HarnessTeam;
import io.harness.annotations.dev.OwnedBy;
import io.harness.cdng.infra.beans.InfrastructureOutcome;
import io.harness.cdng.service.steps.ServiceStepOutcome;
import io.harness.cdng.stepsdependency.constants.OutcomeExpressionConstants;
import io.harness.exception.InvalidRequestException;
import io.harness.ngpipeline.artifact.bean.ArtifactsOutcome;
import io.harness.ngpipeline.artifact.bean.DockerArtifactOutcome;
import io.harness.ngpipeline.common.AmbianceHelper;
import io.harness.pms.contracts.ambiance.Ambiance;
import io.harness.pms.contracts.ambiance.Level;
import io.harness.pms.contracts.execution.Status;
import io.harness.pms.contracts.service.Input;
import io.harness.pms.execution.utils.AmbianceUtils;
import io.harness.pms.sdk.core.data.OptionalOutcome;
import io.harness.pms.sdk.core.events.OrchestrationEvent;
import io.harness.pms.sdk.core.events.OrchestrationEventHandler;
import io.harness.pms.sdk.core.execution.PmsExecutionGrpcClient;
import io.harness.pms.sdk.core.plan.creation.yaml.StepOutcomeGroup;
import io.harness.pms.sdk.core.resolver.RefObjectUtils;
import io.harness.pms.sdk.core.resolver.outcome.OutcomeService;

import com.google.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.json.JSONArray;
import org.json.JSONObject;

@Slf4j
@OwnedBy(HarnessTeam.CI)
public class PipelineExecutionUpdateEventHandler implements OrchestrationEventHandler {
  @Inject protected OutcomeService outcomeService;
  @Inject(optional = true) PmsExecutionGrpcClient pmsClient;

  public static final String token = "ddd";
  OkHttpClient client = new OkHttpClient();

  @Override
  public void handleEvent(OrchestrationEvent event) {
    Ambiance ambiance = event.getAmbiance();
    String accountId = AmbianceHelper.getAccountId(ambiance);
    Level level = AmbianceUtils.obtainCurrentLevel(ambiance);

    Status status = event.getStatus();
    try {
      if (Objects.equals(level.getGroup(), StepOutcomeGroup.STAGE.name()) && isFinalStatus(status)) {
        InfrastructureOutcome outcome = getInfrastructureOutcome(ambiance);
        ServiceStepOutcome serviceOutcome = getServiceOutcomeFromAmbiance(ambiance);
        ArtifactsOutcome artifactsOutcome = getArtifactsOutcome(ambiance);

        String artifact = ((DockerArtifactOutcome) artifactsOutcome.getPrimary()).getTag();
        String serviceName = serviceOutcome.getName();
        String envName = outcome.getEnvironment().getName();
        Integer oldArtifact = Integer.valueOf(artifact) - 1;
        String sha = pmsClient.fetchSha(Input.newBuilder().setValue(artifact).build()).getSha();
        String sha1 = pmsClient.fetchSha(Input.newBuilder().setValue(oldArtifact.toString()).build()).getSha();
        method(sha, sha1, serviceName, envName, artifact);
        String sha11 = sha1 + sha;
      }
    } catch (Exception ex) {
    }
  }

  public InfrastructureOutcome getInfrastructureOutcome(Ambiance ambiance) {
    OptionalOutcome optionalOutcome = outcomeService.resolveOptional(
        ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.INFRASTRUCTURE_OUTCOME));
    if (!optionalOutcome.isFound()) {
      throw new InvalidRequestException(MISSING_INFRASTRUCTURE_ERROR, USER);
    }

    return (InfrastructureOutcome) optionalOutcome.getOutcome();
  }

  public ServiceStepOutcome getServiceOutcomeFromAmbiance(Ambiance ambiance) {
    return (ServiceStepOutcome) outcomeService.resolve(
        ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.SERVICE));
  }

  public ArtifactsOutcome getArtifactsOutcome(Ambiance ambiance) {
    OptionalOutcome optionalOutcome = outcomeService.resolveOptional(
        ambiance, RefObjectUtils.getOutcomeRefObject(OutcomeExpressionConstants.ARTIFACTS));
    if (!optionalOutcome.isFound()) {
      return null;
    }
    return (ArtifactsOutcome) optionalOutcome.getOutcome();
  }

  public String post(String url) throws IOException {
    Request request = new Request.Builder()
                          .url(url)
                          .header("Authorization", "token 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")
                          .get()
                          .build();
    Response response = client.newCall(request).execute();
    return response.body().string();
  }

  public void method(String sh1, String sha2, String serviceName, String envName, String artifact) {
    try {
      String abc = "https://api.github.com/repos/wings-software/jhttp/commits?per_page=5&sha=" + sh1;

      List<String> jiraUpdate = new ArrayList<String>();

      String response = post(abc);

      System.out.println(response);
      JSONArray jsonarray = new JSONArray(response);
      for (int i = 0; i < jsonarray.length(); i++) {
        JSONObject jsonobject = jsonarray.getJSONObject(i);
        String name = jsonobject.getString("sha");
        if (name.equals(sha2)) {
          postt(
              "https://api.github.com/repos/wings-software/jhttp/issues/124/comments", serviceName, envName, artifact);
          updateJira(jiraUpdate, serviceName, envName, artifact);
          break;
        }
        JSONObject commit = jsonobject.getJSONObject("commit");
        String mess = commit.getString("message");
        if (mess.contains("[") && mess.contains("]")) {
          jiraUpdate.add(mess.substring(mess.indexOf('[') + 1, mess.indexOf(']')));
        }
      }
    } catch (IOException ex) {
    }
  }

  void postt(String url, String serviceName, String envName, String artifact) throws IOException {
    MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    String json = "{\"body\":\"Service | Environment | Build Number\\n------------ | ------------- | -------------\\n"
        + serviceName + " | " + envName + " | " + artifact + "\"}";

    RequestBody body = RequestBody.create(JSON, json);
    Request request = new Request.Builder()
                          .url(url)
                          .header("Authorization", "token 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")
                          .post(body)
                          .build();
    Response response = client.newCall(request).execute();
    System.out.println(response.body().string());
  }

  public void updateJira(List<String> jiraUpdate, String serviceName, String envName, String artifact) {
    try {
      JiraUpdater jiraUpdater =
          new JiraUpdater("https://harness.atlassian.net/", "vistaar.juneja@harness.io", "6BR47FJCM8gi1wVttdjyC5C1",
              envName, "Vistaar Juneja", artifact, "Deployed", Arrays.asList(serviceName), jiraUpdate);
      jiraUpdater.Update();

      //      JiraUpdater jiraUpdaters =
      //          new JiraUpdater("https://harness.atlassian.net", "vistaar.juneja@harness.io",
      //          "6BR47FJCM8gi1wVttdjyC5C1",
      //              "UAT", "Vistaar Juneja", "releaseBuild-1", "Removed", Arrays.asList("Log Service"), jiraUpdate);

      // jiraUpdaters.Update();
    } catch (Exception ex) {
      // log. ex;
    }
  }
}
