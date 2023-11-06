// Copyright 2021 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package external

import (
	"context"
	"errors"
	"fmt"
	"io"
	"os"
	"strconv"
	"strings"

	"github.com/harness/harness-core/commons/go/lib/exec"
	"github.com/harness/harness-core/commons/go/lib/logs"
	plogs "github.com/harness/harness-core/product/ci/common/logs"
	"github.com/harness/harness-core/product/log-service/client"
	ticlient "github.com/harness/ti-client/client"
	"github.com/harness/ti-client/types"
	"go.uber.org/zap"
)

const (
	accountIDEnv       = "HARNESS_ACCOUNT_ID"
	orgIDEnv           = "HARNESS_ORG_ID"
	projectIDEnv       = "HARNESS_PROJECT_ID"
	buildIDEnv         = "HARNESS_BUILD_ID"
	stageIDEnv         = "HARNESS_STAGE_ID"
	pipelineIDEnv      = "HARNESS_PIPELINE_ID"
	tiSvcEp            = "HARNESS_TI_SERVICE_ENDPOINT"
	tiSvcToken         = "HARNESS_TI_SERVICE_TOKEN"
	logSvcEp           = "HARNESS_LOG_SERVICE_ENDPOINT"
	logSvcToken        = "HARNESS_LOG_SERVICE_TOKEN"
	logPrefixEnv       = "HARNESS_LOG_PREFIX"
	serviceLogKeyEnv   = "HARNESS_SERVICE_LOG_KEY"
	additionalCertsDir = "HARNESS_ADDITIONAL_CERTS_DIR"
	secretList         = "HARNESS_SECRETS_LIST"
	dBranch            = "DRONE_COMMIT_BRANCH"
	dSourceBranch      = "DRONE_SOURCE_BRANCH"
	dTargetBranch      = "DRONE_TARGET_BRANCH"
	dRemoteUrl         = "DRONE_REMOTE_URL"
	dCommitSha         = "DRONE_COMMIT_SHA"
	dCommitLink        = "DRONE_COMMIT_LINK"
	wrkspcPath         = "HARNESS_WORKSPACE"
	logUploadFf        = "HARNESS_CI_INDIRECT_LOG_UPLOAD_FF"
	gitBin             = "git"
	diffFilesCmdPR     = "%s diff --name-status --diff-filter=MADR HEAD@{1} HEAD -1"
	diffFilesCmdPush   = "%s diff --name-status --diff-filter=MADR %s %s"
	safeDirCommand     = "%s config --global --add safe.directory '*'"
	harnessStepIndex   = "HARNESS_STEP_INDEX"
	harnessStepTotal   = "HARNESS_STEP_TOTAL"
	harnessStageIndex  = "HARNESS_STAGE_INDEX"
	harnessStageTotal  = "HARNESS_STAGE_TOTAL"
	delegateLogURLEnv  = "HARNESS_LE_DELEGATE_LOG_URL"
	delegateTiURLEnv   = "HARNESS_LE_DELEGATE_TI_URL"
)

func GetChangedFilesPR(ctx context.Context, workspace string, log *zap.SugaredLogger, procWriter io.Writer) ([]types.File, error) {
	diffFilesCmdPR := fmt.Sprintf(diffFilesCmdPR, gitBin)
	return getChangedFiles(ctx, workspace, log, procWriter, diffFilesCmdPR)
}

func GetChangedFilesPush(ctx context.Context, workspace, lastSuccessfulCommitID, currentCommitID string, log *zap.SugaredLogger, procWriter io.Writer) ([]types.File, error) {
	diffFilesCmdPush := fmt.Sprintf(diffFilesCmdPush, gitBin, lastSuccessfulCommitID, currentCommitID)
	return getChangedFiles(ctx, workspace, log, procWriter, diffFilesCmdPush)
}

// GetChangedFiles executes a shell command and returns a list of files changed in the PR
// along with their corresponding status
func getChangedFiles(ctx context.Context, workspace string, log *zap.SugaredLogger, procWriter io.Writer, diffFilesCmd string) ([]types.File, error) {
	cmdContextFactory := exec.OsCommandContextGracefulWithLog(log)
	cmd := cmdContextFactory.CmdContext(ctx, "sh", "-c", fmt.Sprintf(safeDirCommand, gitBin)).
		WithDir(workspace).WithStdout(procWriter).WithStderr(procWriter)
	out, err := cmd.Output()
	if err != nil {
		return nil, err
	}

	cmd = cmdContextFactory.CmdContext(ctx, "sh", "-c", diffFilesCmd).
		WithDir(workspace).WithStdout(procWriter).WithStderr(procWriter)
	out, err = cmd.Output()
	if err != nil {
		return nil, err
	}
	res := []types.File{}

	for _, l := range strings.Split(string(out), "\n") {
		t := strings.Fields(l)
		// t looks like:
		// <M/A/D file_name> for modified/added/deleted files
		// <RXYZ old_file new_file> for renamed files where XYZ denotes %age similarity
		if len(t) == 0 {
			break
		}

		if t[0][0] == 'M' {
			res = append(res, types.File{Status: types.FileModified, Name: t[1]})
		} else if t[0][0] == 'A' {
			res = append(res, types.File{Status: types.FileAdded, Name: t[1]})
		} else if t[0][0] == 'D' {
			res = append(res, types.File{Status: types.FileDeleted, Name: t[1]})
		} else if t[0][0] == 'R' {
			res = append(res, types.File{Status: types.FileDeleted, Name: t[1]})
			res = append(res, types.File{Status: types.FileAdded, Name: t[2]})
		} else {
			// Log the error, don't error out for now
			log.Errorw(fmt.Sprintf("unsupported file status: %s, file name: %s", t[0], t[1]))
			return res, nil
		}
	}
	return res, nil
}

func GetNudges() []logs.Nudge {
	// <search-term> <resolution> <error-msg>
	return []logs.Nudge{
		logs.NewNudge("[Kk]illed", "Increase memory resources for the step", errors.New("Out of memory")),
		logs.NewNudge(".*git.* SSL certificate problem",
			"Set sslVerify to false in CI codebase properties", errors.New("SSL certificate error")),
		logs.NewNudge("Cannot connect to the Docker daemon",
			"Setup dind if it's not running. If dind is running, privileged should be set to true",
			errors.New("Could not connect to the docker daemon")),
		logs.NewNudge("x509",
			"If you are using self signed certs, Harness allows setting them "+
				"at a global level on the delegate agent. Visit documentation for more details!",
			errors.New("Certificate issue")),
		logs.NewNudge("storage: bucket doesn't exist", "Ensure the bucket is created", errors.New("Bucket does not exist")),
		logs.NewNudge("kaniko should only be run inside of a container", "Container runtime is not detected. Please add an environment variable named “container” with a value of “docker” to the stage's environment variables.",
			errors.New("Kaniko container runtime error")),
	}
}

func GetSecrets() []logs.Secret {
	res := []logs.Secret{}
	secrets := os.Getenv(secretList)
	if secrets == "" {
		return res
	}
	secretList := strings.Split(secrets, ",")
	for _, skey := range secretList {
		sval := os.Getenv(skey)
		if sval == "" {
			fmt.Printf("could not find secret env variable for: %s\n", skey)
			continue
		}
		// Mask all the secrets for now
		res = append(res, logs.NewSecret(skey, sval, true))
	}
	return res
}

// GetHTTPRemoteLogger returns a remote HTTP logger for a key.
func GetHTTPRemoteLogger(key string) (*logs.RemoteLogger, error) {
	client, err := GetRemoteHTTPClient()
	if err != nil {
		return nil, err
	}
	indirectUpload, err := GetLogUploadFF()
	if err != nil {
		return nil, err
	}
	rw, err := plogs.NewRemoteWriter(client, key, GetNudges(), indirectUpload)
	if err != nil {
		return nil, err
	}
	rws := logs.NewReplacer(rw, GetSecrets()) // Remote writer with secrets masked
	rl, err := logs.NewRemoteLogger(rws)
	if err != nil {
		return nil, err
	}
	return rl, nil
}

// GetRemoteHTTPClient returns a new HTTP client to talk to log service using information available in env.
func GetRemoteHTTPClient() (client.Client, error) {
	account, err := GetAccountId()
	if err != nil {
		return nil, err
	}
	token, ok := os.LookupEnv(logSvcToken)
	if !ok {
		return nil, fmt.Errorf("log service token not set %s", logSvcToken)
	}
	certsDir := GetAdditionalCertsDir()

	delegateLogURL, delegateLogURLOk := os.LookupEnv(delegateLogURLEnv)
	internalLogURL, internalLogURLOk := os.LookupEnv(logSvcEp)

	if delegateLogURLOk && !isUrlSame(delegateLogURL, internalLogURL) {
		httpClient := client.NewHTTPClient(delegateLogURL, account, token, false, certsDir)
		err := httpClient.Healthz(context.Background())
		if err == nil {
			fmt.Printf("%s env is set with value: %s \n", delegateLogURLEnv, delegateLogURL)
			return httpClient, nil
		} else {
			fmt.Printf("Failed to ping log-service: %w\n", err)
		}
	}
	if internalLogURLOk {
		fmt.Printf("Using internalLogURL %s:\n", internalLogURL)
		return client.NewHTTPClient(internalLogURL, account, token, false, certsDir), nil
	}

	return nil, fmt.Errorf("No usable Log URL found.")

}

// GetLogKey returns a key for log service
func GetLogKey(id string) (string, error) {
	logPrefix, ok := os.LookupEnv(logPrefixEnv)
	if !ok {
		return "", fmt.Errorf("log prefix variable not set %s", logPrefixEnv)
	}

	return fmt.Sprintf("%s/%s", logPrefix, id), nil
}

// GetServiceLogKey returns log key for service
func GetServiceLogKey() (string, error) {
	logKey, ok := os.LookupEnv(serviceLogKeyEnv)
	if !ok {
		return "", fmt.Errorf("service log key variable not set %s", serviceLogKeyEnv)
	}

	return logKey, nil
}

// GetAdditionalCertsPath returns additional certs path
func GetAdditionalCertsDir() string {
	return os.Getenv(additionalCertsDir)
}

// GetTiHTTPClient returns a client to talk to the TI service
func GetTiHTTPClient(repo, sha, commitLink string, skipVerify bool) ticlient.Client {
	tiEndpoint, _ := GetTiSvcEp()
	token, _ := GetTiSvcToken()
	accountID, _ := GetAccountId()
	orgID, _ := GetOrgId()
	projectID, _ := GetProjectId()
	pipelineID, _ := GetPipelineId()
	buildID, _ := GetBuildId()
	stageID, _ := GetStageId()
	certsDir := GetAdditionalCertsDir()
	delegateTiURL, delegateTiURLOk := os.LookupEnv(delegateTiURLEnv)
	if delegateTiURLOk && !isUrlSame(delegateTiURL, tiEndpoint) {
		tiHttpClient := ticlient.NewHTTPClient(delegateTiURL, token, accountID, orgID, projectID, pipelineID, buildID, stageID, repo, sha,
			commitLink, skipVerify, certsDir)
		err := tiHttpClient.Healthz(context.Background())
		if err == nil {
			fmt.Printf("%s env is set with value: %s\n", delegateTiURLEnv, delegateTiURL)
			return tiHttpClient
		} else {
			fmt.Printf("Failed to ping ti-service: %w\n", err)
		}
	}

	return ticlient.NewHTTPClient(tiEndpoint, token, accountID, orgID, projectID, pipelineID, buildID, stageID, repo, sha,
		commitLink, skipVerify, certsDir)
}

// GetTiHTTPClientWithToken returns a client to talk to the TI service
func GetTiHTTPClientWithToken(repo, sha, commitLink string, skipVerify bool, token string) ticlient.Client {
	endpoint, _ := GetTiSvcEp()
	accountID, _ := GetAccountId()
	orgID, _ := GetOrgId()
	projectID, _ := GetProjectId()
	pipelineID, _ := GetPipelineId()
	buildID, _ := GetBuildId()
	stageID, _ := GetStageId()
	certsDir := GetAdditionalCertsDir()
	return ticlient.NewHTTPClient(endpoint, token, accountID, orgID, projectID, pipelineID, buildID, stageID, repo, sha,
		commitLink, skipVerify, certsDir)
}

func GetTiSvcEp() (string, error) {
	endpoint, ok := os.LookupEnv(tiSvcEp)
	if !ok {
		return "", fmt.Errorf("TI service endpoint environment variable not set %s", tiSvcEp)
	}
	return endpoint, nil
}

func GetAccountId() (string, error) {
	account, ok := os.LookupEnv(accountIDEnv)
	if !ok {
		return "", fmt.Errorf("account ID environment variable not set %s", accountIDEnv)
	}
	return account, nil
}

func GetOrgId() (string, error) {
	org, ok := os.LookupEnv(orgIDEnv)
	if !ok {
		return "", fmt.Errorf("org ID environment variable not set %s", orgIDEnv)
	}
	return org, nil
}

func GetProjectId() (string, error) {
	project, ok := os.LookupEnv(projectIDEnv)
	if !ok {
		return "", fmt.Errorf("project ID environment variable not set %s", projectIDEnv)
	}
	return project, nil
}

func GetPipelineId() (string, error) {
	pipeline, ok := os.LookupEnv(pipelineIDEnv)
	if !ok {
		return "", fmt.Errorf("pipeline ID environment variable not set %s", pipelineIDEnv)
	}
	return pipeline, nil
}

func GetBuildId() (string, error) {
	build, ok := os.LookupEnv(buildIDEnv)
	if !ok {
		return "", fmt.Errorf("build ID environment variable not set %s", buildIDEnv)
	}
	return build, nil
}

func GetStageId() (string, error) {
	stage, ok := os.LookupEnv(stageIDEnv)
	if !ok {
		return "", fmt.Errorf("stage ID environment variable not set %s", stageIDEnv)
	}
	return stage, nil
}

func GetBranch() (string, error) {
	source, ok := os.LookupEnv(dBranch)
	if !ok {
		return "", fmt.Errorf("branch variable not set %s", dBranch)
	}
	return source, nil
}

func GetSourceBranch() (string, error) {
	source, ok := os.LookupEnv(dSourceBranch)
	if !ok {
		return "", fmt.Errorf("source branch variable not set %s", dSourceBranch)
	}
	return source, nil
}

func GetTargetBranch() (string, error) {
	target, ok := os.LookupEnv(dTargetBranch)
	if !ok {
		return "", fmt.Errorf("target branch variable not set %s", dTargetBranch)
	}
	return target, nil
}

func GetRepo() (string, error) {
	stage, ok := os.LookupEnv(dRemoteUrl)
	if !ok {
		return "", fmt.Errorf("remote url variable not set %s", dRemoteUrl)
	}
	return stage, nil
}

// If FF is not set or is not set to "true", we return the value false
func GetLogUploadFF() (bool, error) {
	indirectUpload, ok := os.LookupEnv(logUploadFf)
	if !ok {
		return false, nil
	}
	if indirectUpload == "true" {
		return true, nil
	}
	return false, nil
}

func GetSha() (string, error) {
	stage, ok := os.LookupEnv(dCommitSha)
	if !ok {
		return "", fmt.Errorf("commit sha variable not set %s", dCommitSha)
	}
	return stage, nil
}

func GetWrkspcPath() (string, error) {
	path, ok := os.LookupEnv(wrkspcPath)
	if !ok {
		return "", fmt.Errorf("workspace path variable not set %s", wrkspcPath)
	}
	return path, nil
}

func GetCommitLink() (string, error) {
	link, ok := os.LookupEnv(dCommitLink)
	if !ok {
		return "", fmt.Errorf("commit link variable not set %s", dCommitLink)
	}
	return link, nil
}

func GetTiSvcToken() (string, error) {
	token, ok := os.LookupEnv(tiSvcToken)
	if !ok {
		return "", fmt.Errorf("ti service token variable not set %s", tiSvcToken)
	}
	return token, nil
}

func GetStepStrategyIteration(envs map[string]string) (int, error) {
	idxStr, ok := envs[harnessStepIndex]
	if !ok {
		return -1, fmt.Errorf("parallelism strategy iteration variable not set %s", harnessStepIndex)
	}
	idx, err := strconv.Atoi(idxStr)
	if err != nil {
		return -1, fmt.Errorf("unable to convert %s from string to int", harnessStepIndex)
	}
	return idx, nil
}

func GetStepStrategyIterations(envs map[string]string) (int, error) {
	totalStr, ok := envs[harnessStepTotal]
	if !ok {
		return -1, fmt.Errorf("parallelism total iteration variable not set %s", harnessStepTotal)
	}
	total, err := strconv.Atoi(totalStr)
	if err != nil {
		return -1, fmt.Errorf("unable to convert %s from string to int", harnessStepTotal)
	}
	return total, nil
}

func GetStageStrategyIteration(envs map[string]string) (int, error) {
	idxStr, ok := envs[harnessStageIndex]
	if !ok {
		return -1, fmt.Errorf("parallelism strategy iteration variable not set %s", harnessStageIndex)
	}
	idx, err := strconv.Atoi(idxStr)
	if err != nil {
		return -1, fmt.Errorf("unable to convert %s from string to int", harnessStageIndex)
	}
	return idx, nil
}

func GetStageStrategyIterations(envs map[string]string) (int, error) {
	totalStr, ok := envs[harnessStageTotal]
	if !ok {
		return -1, fmt.Errorf("parallelism total iteration variable not set %s", harnessStageTotal)
	}
	total, err := strconv.Atoi(totalStr)
	if err != nil {
		return -1, fmt.Errorf("unable to convert %s from string to int", harnessStageTotal)
	}
	return total, nil
}

func IsManualExecution() bool {
	_, err1 := GetSourceBranch()
	_, err2 := GetTargetBranch()
	_, err3 := GetSha()
	if err1 != nil || err2 != nil || err3 != nil {
		return true // if any of them are not set, treat as a manual execution
	}
	return false
}

func IsPushTriggerExecution() bool {
	if IsManualExecution() {
		return false
	}
	sourceBranch, _ := GetSourceBranch()
	targetBranch, _ := GetTargetBranch()
	if sourceBranch == targetBranch {
		return true
	}
	return false
}

func IsStepParallelismEnabled(envs map[string]string) bool {
	v1, err1 := GetStepStrategyIteration(envs)
	v2, err2 := GetStepStrategyIterations(envs)
	if err1 != nil || err2 != nil || v1 >= v2 || v2 <= 1 {
		return false
	}
	return true
}

func IsStageParallelismEnabled(envs map[string]string) bool {
	v1, err1 := GetStageStrategyIteration(envs)
	v2, err2 := GetStageStrategyIterations(envs)
	if err1 != nil || err2 != nil || v1 >= v2 || v2 <= 1 {
		return false
	}
	return true
}

func IsParallelismEnabled(envs map[string]string) bool {
	return IsStepParallelismEnabled(envs) || IsStageParallelismEnabled(envs)
}

func GetStepStrategyIterationFromEnv() (int, error) {
	idxStr, ok := os.LookupEnv(harnessStepIndex)
	if !ok {
		return -1, fmt.Errorf("parallelism strategy iteration variable not set %s", harnessStepIndex)
	}
	idx, err := strconv.Atoi(idxStr)
	if err != nil {
		return -1, fmt.Errorf("unable to convert %s from string to int", harnessStepIndex)
	}
	return idx, nil
}

func GetStepStrategyIterationsFromEnv() (int, error) {
	totalStr, ok := os.LookupEnv(harnessStepTotal)
	if !ok {
		return -1, fmt.Errorf("parallelism total iteration variable not set %s", harnessStepTotal)
	}
	total, err := strconv.Atoi(totalStr)
	if err != nil {
		return -1, fmt.Errorf("unable to convert %s from string to int", harnessStepTotal)
	}
	return total, nil
}

func isUrlSame(url1, url2 string) bool {
	if strings.TrimSuffix(url1, "/") != strings.TrimSuffix(url2, "/") {
		return false
	}
	return true
}
