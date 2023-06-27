// Copyright 2021 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package junit

import (
	"context"
	"fmt"

	"github.com/harness/harness-core/commons/go/lib/filesystem"
	gojunit "github.com/harness/harness-core/product/ci/addon/gojunit"
	"github.com/harness/harness-core/product/ci/addon/testreports"
	"github.com/harness/ti-client/types"
	"github.com/mattn/go-zglob"
	"go.uber.org/zap"
)

const (
	buffSize   = 100
	strMaxSize = 8000 // Keep the last 8k characters in each field.
)

// Junit represents a test report in junit format
type Junit struct {
	Files []string
	Log   *zap.SugaredLogger
}

// getFiles resolves glob pattern for a given path and returns list of matching paths
func getFiles(path string) ([]string, error) {
	path, err := filesystem.ExpandTilde(path)
	if err != nil {
		return []string{}, err
	}
	matches, err := zglob.Glob(path)
	if err != nil {
		return []string{}, err
	}

	return matches, err
}

// New returns a new Junit test reporter
func New(paths []string, log *zap.SugaredLogger) testreports.TestReporter {
	var filenames []string
	set := make(map[string]struct{}) // unique set of XML files
	for _, path := range paths {
		files, err := getFiles(path)
		if err != nil {
			log.Errorw(fmt.Sprintf("errored while trying to get paths for %s", path), "path", path, zap.Error(err))
			continue
		}
		for _, file := range files {
			if _, ok := set[file]; !ok {
				set[file] = struct{}{}
				filenames = append(filenames, file)
			}
		}
	}
	log.Debugw(fmt.Sprintf("list of files to collect test reports from: %s", filenames))
	if len(filenames) == 0 {
		log.Errorw("could not find any files matching the provided report path")
	}
	return &Junit{
		Files: filenames,
		Log:   log,
	}
}

// GetTests parses XMLs and writes relevant data to the channel
func (j *Junit) GetTests(ctx context.Context) <-chan *types.TestCase {
	testc := make(chan *types.TestCase, buffSize)
	go func() {
		defer close(testc)
		fileMap := make(map[string]int)
		totalTests := 0
		for _, file := range j.Files {
			suites, err := gojunit.IngestFile(file)
			if err != nil {
				j.Log.Errorw(fmt.Sprintf("could not parse file %s. Error: %s", file, err), "file", file, zap.Error(err))
				continue
			}
			testsInFile := processTestSuites(testc, suites)
			totalTests += testsInFile
			fileMap[file] = testsInFile
		}
		j.Log.Info("Number of cases parsed in each file: ", fileMap)
		j.Log.Infow(fmt.Sprintf("parsed %d test cases", totalTests), "num_cases", totalTests)
	}()
	return testc
}

// processTestSuites recusively writes the test data from parsed data to the
// input channel and returns the total number of tests written to the channel
func processTestSuites(testc chan *types.TestCase, suites []gojunit.Suite) int {
	totalTests := 0
	for _, suite := range suites {
		for _, test := range suite.Tests {
			ct := convert(test, suite)
			if ct.Name != "" {
				testc <- ct
				totalTests += 1
			}
		}
		totalTests += processTestSuites(testc, suite.Suites)
	}
	return totalTests
}

// convert combines relevant information in test cases and test suites and parses it to our custom format
func convert(testCase gojunit.Test, testSuite gojunit.Suite) *types.TestCase {
	testCase.Result.Desc = restrictLength(testCase.Result.Desc)
	testCase.Result.Message = restrictLength(testCase.Result.Message)
	return &types.TestCase{
		Name:       testCase.Name,
		SuiteName:  testSuite.Name,
		ClassName:  testCase.Classname,
		FileName:   testCase.Filename,
		DurationMs: testCase.DurationMs,
		Result:     testCase.Result,
		SystemOut:  restrictLength(testCase.SystemOut),
		SystemErr:  restrictLength(testCase.SystemErr),
	}
}

// restrictLength trims string to last strMaxsize characters
func restrictLength(s string) string {
	if len(s) <= strMaxSize {
		return s
	}
	return s[len(s)-strMaxSize:]
}
