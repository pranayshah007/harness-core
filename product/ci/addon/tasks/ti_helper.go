// Copyright 2021 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package tasks

import (
	"archive/zip"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"io/ioutil"
	"net/http"
	"os"
	"path/filepath"
	"runtime"
	"time"

	"github.com/ghodss/yaml"
	grpc_retry "github.com/grpc-ecosystem/go-grpc-middleware/retry"
	"github.com/harness/harness-core/commons/go/lib/filesystem"
	ti "github.com/harness/harness-core/product/ci/addon/remote"
	"github.com/harness/harness-core/product/ci/addon/testreports"
	"github.com/harness/harness-core/product/ci/addon/testreports/junit"
	"github.com/harness/harness-core/product/ci/common/external"
	"github.com/harness/harness-core/product/ci/engine/consts"
	grpcclient "github.com/harness/harness-core/product/ci/engine/grpc/client"
	pb "github.com/harness/harness-core/product/ci/engine/proto"
	stutils "github.com/harness/harness-core/product/ci/split_tests/utils"
	"github.com/harness/ti-client/types"
	"github.com/pkg/errors"
	"go.uber.org/zap"
)

var (
	newJunit = junit.New
)

func collectCg(ctx context.Context, stepID, cgDir string, timeMs int64, log *zap.SugaredLogger, start time.Time) error {
	if external.IsManualExecution() {
		log.Infow("Skipping call graph collection since it is a manual run")
		return nil
	}

	repo, err := external.GetRepo()
	if err != nil {
		return err
	}
	sha, err := external.GetSha()
	if err != nil {
		return err
	}
	source, err := external.GetSourceBranch()
	if err != nil {
		return err
	}
	target, err := external.GetTargetBranch()
	if err != nil {
		return err
	}
	// Create TI proxy client (lite engine)
	client, err := grpcclient.NewTiProxyClient(consts.LiteEnginePort, log)
	if err != nil {
		return err
	}
	defer client.CloseConn()
	req := &pb.UploadCgRequest{
		StepId:  stepID,
		Repo:    repo,
		Sha:     sha,
		Source:  source,
		Target:  target,
		DataDir: cgDir,
		TimeMs:  timeMs,
	}
	resp, err := client.Client().UploadCg(ctx, req)
	if err != nil {
		return errors.Wrap(err, "failed to upload cg to ti server")
	}

	log.Infow(resp.CgMsg)
	log.Infow(fmt.Sprintf("Successfully uploaded callgraph in %s time", time.Since(start)))
	return nil
}

func collectTestReports(ctx context.Context, reports []*pb.Report, stepID string, log *zap.SugaredLogger, start time.Time) error {
	// Test cases from reports are identified at a per-step level and won't cause overwriting/clashes
	// at the backend.
	if len(reports) == 0 {
		return nil
	}
	// Create TI proxy client (lite engine)
	client, err := grpcclient.NewTiProxyClient(consts.LiteEnginePort, log)
	if err != nil {
		return err
	}
	defer client.CloseConn()
	repo, _ := external.GetRepo()             // Add repo if it exists, otherwise keep it empty
	sha, _ := external.GetSha()               // Add sha if it exists, otherwise keep it empty
	commitLink, _ := external.GetCommitLink() // Add commit link if it exists, otherwise keep it empty
	for _, report := range reports {
		var rep testreports.TestReporter
		var err error

		x := report.GetType() // pass in report type in proto when other reports are reqd
		switch x {
		case pb.Report_UNKNOWN:
			return errors.New("report type is unknown")
		case pb.Report_JUNIT:
			rep = newJunit(report.GetPaths(), log)
		}

		var tests []string
		testc := rep.GetTests(ctx)
		for t := range testc {
			jt, _ := json.Marshal(t)
			tests = append(tests, string(jt))
		}

		if len(tests) == 0 {
			return nil // We're not erroring even if we can't find any tests to report
		}

		stream, err := client.Client().WriteTests(ctx, grpc_retry.Disable())
		if err != nil {
			return err
		}
		var curr []string
		for _, t := range tests {
			curr = append(curr, t)
			if len(curr)%batchSize == 0 {
				in := &pb.WriteTestsRequest{StepId: stepID, Tests: curr, Repo: repo, Sha: sha, CommitLink: commitLink}
				if serr := stream.Send(in); serr != nil {
					log.Errorw("write tests RPC failed", zap.Error(serr))
				}
				curr = []string{} // ignore RPC failures, try to write whatever you can
			}
		}
		if len(curr) > 0 {
			in := &pb.WriteTestsRequest{StepId: stepID, Tests: curr, Repo: repo, Sha: sha, CommitLink: commitLink}
			if serr := stream.Send(in); serr != nil {
				log.Errorw("write tests RPC failed", zap.Error(serr))
			}
			curr = []string{}
		}

		// Close the stream and receive result
		_, err = stream.CloseAndRecv()
		if err != nil {
			return err
		}
	}
	if len(reports) > 0 {
		log.Infow(fmt.Sprintf("Successfully collected test reports in %s time", time.Since(start)))
	}
	return nil
}

func getTestTime(ctx context.Context, log *zap.SugaredLogger, splitStrategy string) (map[string]float64, error) {
	req := types.GetTestTimesReq{}
	var res types.GetTestTimesResp
	var err error
	fileTimesMap := map[string]float64{}

	switch splitStrategy {
	case stutils.SplitByFileTimeStr:
		req.IncludeFilename = true
		res, err = ti.GetTestTimes(context.Background(), log, req)
		fileTimesMap = stutils.ConvertMap(res.FileTimeMap)
	case stutils.SplitByClassTimeStr:
		req.IncludeClassname = true
		res, err = ti.GetTestTimes(context.Background(), log, req)
		fileTimesMap = stutils.ConvertMap(res.ClassTimeMap)
	case stutils.SplitByTestcaseTimeStr:
		req.IncludeTestCase = true
		res, err = ti.GetTestTimes(context.Background(), log, req)
		fileTimesMap = stutils.ConvertMap(res.TestTimeMap)
	case stutils.SplitByTestSuiteTimeStr:
		req.IncludeTestSuite = true
		res, err = ti.GetTestTimes(context.Background(), log, req)
		fileTimesMap = stutils.ConvertMap(res.SuiteTimeMap)
	case stutils.SplitByFileSizeStr:
		return map[string]float64{}, nil
	default:
		return map[string]float64{}, nil
	}
	if err != nil {
		return map[string]float64{}, err
	}
	return fileTimesMap, nil
}

// selectTests takes a list of files which were changed as input and gets the tests
// to be run corresponding to that.
func selectTests(ctx context.Context, files []types.File, runSelected bool, stepID string, log *zap.SugaredLogger, fs filesystem.FileSystem) (types.SelectTestsResp, error) {
	res := types.SelectTestsResp{}
	repo, err := external.GetRepo()
	if err != nil {
		return res, err
	}
	sha, err := external.GetSha()
	if err != nil {
		return res, err
	}
	source, err := external.GetSourceBranch()
	if err != nil {
		return res, err
	}
	target, err := external.GetTargetBranch()
	if err != nil {
		return res, err
	}
	// Create TI proxy client (lite engine)
	client, err := grpcclient.NewTiProxyClient(consts.LiteEnginePort, log)
	if err != nil {
		return res, err
	}
	defer client.CloseConn()
	// Get TI config
	ticonfig, err := getTiConfig(fs)
	if err != nil {
		return res, err
	}
	b, err := json.Marshal(&types.SelectTestsReq{SelectAll: !runSelected, Files: files, TiConfig: ticonfig})
	if err != nil {
		return res, err
	}
	req := &pb.SelectTestsRequest{
		StepId:       stepID,
		Repo:         repo,
		Sha:          sha,
		SourceBranch: source,
		TargetBranch: target,
		Body:         string(b),
	}
	resp, err := client.Client().SelectTests(ctx, req)
	if err != nil {
		return types.SelectTestsResp{}, err
	}
	var selection types.SelectTestsResp
	err = json.Unmarshal([]byte(resp.Selected), &selection)
	if err != nil {
		log.Errorw("could not unmarshal select tests response on addon", zap.Error(err))
		return types.SelectTestsResp{}, err
	}
	return selection, nil
}

func getTiConfig(fs filesystem.FileSystem) (types.TiConfig, error) {
	wrkspcPath, err := external.GetWrkspcPath()
	res := types.TiConfig{}
	if err != nil {
		return res, errors.Wrap(err, "could not get ti config")
	}
	path := fmt.Sprintf("%s/%s", wrkspcPath, tiConfigPath)
	_, err = os.Stat(path)
	if os.IsNotExist(err) {
		return res, nil
	}
	var data []byte
	err = fs.ReadFile(path, func(r io.Reader) error {
		data, err = ioutil.ReadAll(r)
		return err
	})
	if err != nil {
		return res, errors.Wrap(err, "could not read ticonfig file")
	}
	err = yaml.Unmarshal(data, &res)
	if err != nil {
		return res, errors.Wrap(err, "could not unmarshal ticonfig file")
	}
	return res, nil
}

// installAgents checks if the required artifacts are installed for the language
// and if not, installs them. It returns back the directory where all the agents are installed.
func installAgents(ctx context.Context, path, language, framework, frameworkVersion, buildEnvironment string, log *zap.SugaredLogger, fs filesystem.FileSystem) (string, error) {
	// Create TI proxy client (lite engine)
	client, err := grpcclient.NewTiProxyClient(consts.LiteEnginePort, log)
	if err != nil {
		return "", err
	}
	req := &pb.DownloadLinkRequest{
		Language:  language,
		Os:        runtime.GOOS,
		Arch:      runtime.GOARCH,
		Framework: framework,
		Version:   frameworkVersion,
		Env:       buildEnvironment,
	}
	resp, err := client.Client().DownloadLink(ctx, req)
	if err != nil {
		return "", err
	}
	var links []types.DownloadLink
	err = json.Unmarshal([]byte(resp.Links), &links)

	var installDir string // directory where all the agents are installed

	// Install the Artifacts
	for idx, l := range links {
		absPath := filepath.Join(path, l.RelPath)
		if idx == 0 {
			installDir = filepath.Dir(absPath)
		} else if filepath.Dir(absPath) != installDir {
			return "", fmt.Errorf("artifacts don't have the same relative path: link %s and installDir %s", l, installDir)
		}
		err := downloadFile(ctx, absPath, l.URL, fs)
		if err != nil {
			return "", err
		}
	}

	return installDir, nil
}

func getCommitInfo(ctx context.Context, stepID string, log *zap.SugaredLogger) (commitId string, err error) {
	repo, err := external.GetRepo()
	if err != nil {
		return commitId, err
	}
	branch, err := external.GetSourceBranch()
	if err != nil {
		return commitId, err
	}
	client, err := grpcclient.NewTiProxyClient(consts.LiteEnginePort, log)
	if err != nil {
		return commitId, err
	}
	defer client.CloseConn()
	req := &pb.GetLastSuccCommitInfoRequest{
		StepId: stepID,
		Repo:   repo,
		Branch: branch,
	}
	resp, err := client.Client().GetLastSuccCommitInfo(ctx, req)
	if err != nil {
		return commitId, err
	}
	var commitInfo types.CommitInfoResp
	err = json.Unmarshal([]byte(resp.CommitInfo), &commitInfo)
	if err != nil {
		log.Errorw("could not unmarshal commit info response response on addon", zap.Error(err))
		return commitId, err
	}
	return commitInfo.LastSuccessfulCommitId, nil
}

func getChangedFilesPushTrigger(ctx context.Context, stepID, lastSuccessfulCommitID string, log *zap.SugaredLogger) (changedFiles []types.File, err error) {
	client, err := grpcclient.NewTiProxyClient(consts.LiteEnginePort, log)
	if err != nil {
		return changedFiles, err
	}
	defer client.CloseConn()
	req := &pb.GetChangedFilesPushTriggerRequest{
		StepId: stepID,
		LastSuccCommit: lastSuccessfulCommitID,
	}
	resp, err := client.Client().GetChangedFilesPushTrigger(ctx, req)
	if err != nil {
		return changedFiles, err
	}
	err = json.Unmarshal([]byte(resp.ChangedFiles), &changedFiles)
	if err != nil {
		log.Errorw("could not unmarshal changed files response on addon", zap.Error(err))
		return changedFiles, err
	}
	return changedFiles, nil
}

/*
Downloads url to path with specified fs.
Args:

	ctx (context.Context): context of the current thread
	path (string): local path where it downloads to
	url (url): remote file url
	fs (filesystem.FileSystem): file system used to create directory and save file

Returns:

	err (error): Error if there's one, nil otherwise.
*/
func downloadFile(ctx context.Context, path, url string, fs filesystem.FileSystem) error {
	// Create the nested directory if it doesn't exist
	dir := filepath.Dir(path)
	if err := fs.MkdirAll(dir, os.ModePerm); err != nil {
		return fmt.Errorf("could not create nested directory: %s", err)
	}
	// Create the file
	out, err := fs.Create(path)
	if err != nil {
		return err
	}
	defer out.Close()

	// Get the data
	req, err := http.NewRequestWithContext(ctx, "GET", url, http.NoBody)
	if err != nil {
		return err
	}
	resp, err := http.DefaultClient.Do(req)
	if err != nil {
		return err
	}
	defer resp.Body.Close()

	// Check server response
	if resp.StatusCode != http.StatusOK {
		return fmt.Errorf("bad status: %s", resp.Status)
	}

	// Write the body to file
	_, err = io.Copy(out, resp.Body)
	if err != nil {
		return err
	}

	return nil
}

func unzipSource(source, destination string, log *zap.SugaredLogger, fs filesystem.FileSystem) error {
	log.Infow(fmt.Sprintf("unzipping from %s to %s", source, destination))
	reader, err := zip.OpenReader(source)
	if err != nil {
		return err
	}
	defer func(reader *zip.ReadCloser) {
		err := reader.Close()
		if err != nil {
			log.Errorw("unable to unzip file", zap.Error(err))
		}
	}(reader)

	destination, err = filepath.Abs(destination)
	if err != nil {
		return err
	}

	for _, f := range reader.File {
		err := unzipFile(f, destination, fs)
		if err != nil {
			return err
		}
	}

	return nil
}

func unzipFile(f *zip.File, destination string, fs filesystem.FileSystem) error {
	filePath := filepath.Join(destination, f.Name)

	if f.FileInfo().IsDir() {
		if err := fs.MkdirAll(filePath, os.ModePerm); err != nil {
			return err
		}
		return nil
	}

	if err := fs.MkdirAll(filepath.Dir(filePath), os.ModePerm); err != nil {
		return err
	}

	destinationFile, err := fs.Create(filePath)
	defer destinationFile.Close()
	if err != nil {
		return err
	}

	zippedFile, err := f.Open()
	if err != nil {
		return err
	}
	defer zippedFile.Close()

	if _, err := io.Copy(destinationFile, zippedFile); err != nil {
		return err
	}
	return nil
}
