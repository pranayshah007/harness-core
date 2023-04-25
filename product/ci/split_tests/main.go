// Copyright 2022 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package main

import (
	"context"
	"fmt"
	arg "github.com/alexflint/go-arg"
	"github.com/harness/harness-core/product/ci/split_tests/ti"
	stutils "github.com/harness/harness-core/product/ci/split_tests/utils"
	"github.com/harness/harness-core/product/ci/ti-service/types"
	"go.uber.org/zap"
	"os"
	"strconv"
	"strings"

	junit "github.com/harness/harness-core/product/ci/split_tests/junit"
)

const (
	splitByFileTimeStr      = stutils.SplitByFileTimeStr
	splitByClassTimeStr     = stutils.SplitByClassTimeStr
	splitByTestcaseTimeStr  = stutils.SplitByTestcaseTimeStr
	splitByTestSuiteTimeStr = stutils.SplitByTestSuiteTimeStr
	splitByFileSizeStr      = stutils.SplitByFileSizeStr
)

// CLI Arguments
var args struct {
	IncludeFilePattern []string `arg:"--glob" help:"Glob pattern to find the test files"`
	ExcludeFilePattern []string `arg:"--exclude-glob" help:"Glob pattern to exclude test files"`
	SplitIndex         int      `arg:"--split-index" help:"Index of the current split (or set HARNESS_NODE_INDEX)"`
	SplitTotal         int      `arg:"--split-total" help:"Total number of splits (or set HARNESS_NODE_TOTAL)"`
	SplitBy            string   `arg:"--split-by" help:"Split by"`
	FilePath           string   `arg:"--file-path" help:"Full path including filename"`
	UseJunitXml        bool     `arg:"--use-junit" help:"Use junit XML for test times"`
	JunitXmlPath       string   `arg:"--junit-path" help:"Path to Junit XML file to read test times"`
	Verbose            bool     `arg:"--verbose" help:"Enable verbose logging mode"`
	DefaultTime        int      `arg:"--default-time" help:"Default time in ms if any timing data is missing"`
}

var log *zap.SugaredLogger

/*
Parses the command line args, sets default values and overrides if
environment variables are set.
*/
func parseArgs() {
	// Set defaults here
	args.SplitIndex = -1
	args.SplitTotal = -1
	args.DefaultTime = 1
	arg.MustParse(&args)

	var err error
	if args.SplitTotal == -1 {
		args.SplitTotal, err = strconv.Atoi(os.Getenv("HARNESS_NODE_TOTAL"))
		if err != nil {
			args.SplitTotal = -1
		}
	}

	if args.SplitIndex == -1 {
		args.SplitIndex, err = strconv.Atoi(os.Getenv("HARNESS_NODE_INDEX"))
		if err != nil {
			args.SplitIndex = -1
		}
	}

	if args.SplitTotal < 1 || args.SplitIndex >= args.SplitTotal {
		fmt.Fprintf(os.Stderr, "--split-index and --split-total (and environment variables) are missing or invalid\n")
		os.Exit(1)
	}

	if args.SplitBy == "" {
		args.SplitBy = splitByFileSizeStr
	}

	switch args.SplitBy {
	case splitByTestcaseTimeStr, splitByTestSuiteTimeStr, splitByClassTimeStr:
		if args.FilePath == "" {
			fmt.Fprintf(os.Stderr, "--file-path cannot be empty if --split-by is set to class_timing | suite_timing | testcase_timing")
			os.Exit(1)
		}
	}
}

func initLogger() {
	config := zap.NewProductionConfig()
	config.OutputPaths = []string{"stderr"}
	if args.Verbose {
		config.Level.SetLevel(zap.DebugLevel)
	}
	zapLogger, err := config.Build()
	if err != nil {
		fmt.Fprintf(os.Stderr, "logger initialization failed with error:", err)
		os.Exit(1)
	}
	log = zapLogger.Sugar()
}

func main() {
	parseArgs()
	initLogger()

	// Get current file set from the glob pattern
	currentFileSet, err := stutils.GetTestData(log, args.IncludeFilePattern, args.ExcludeFilePattern)
	if err != nil {
		log.Fatalw(err.Error())
	}

	// Construct a map of file times {fileName: Duration}
	fileTimesMap, readTestData := getFileTimes(currentFileSet)
	if readTestData {
		currentFileSet = stutils.ReadTestDataFromFile(log, args.FilePath)
	}
	stutils.ProcessFiles(fileTimesMap, currentFileSet, float64(args.DefaultTime), args.UseJunitXml)
	log.Debug(fmt.Sprintf("Test time map: %s", stutils.ConvertMapToJson(fileTimesMap)))

	buckets, bucketTimes := stutils.SplitFiles(fileTimesMap, args.SplitTotal)
	if args.UseJunitXml {
		log.Debug(fmt.Sprintf("Expected test time: %0.1fs\n", bucketTimes[args.SplitIndex]))
	}

	fmt.Println(strings.Join(buckets[args.SplitIndex], " "))
}

func getFileTimes(currentFileSet map[string]bool) (map[string]float64, bool) {
	fileTimesMap := make(map[string]float64)
	readTestData := true

	if args.UseJunitXml {
		junit.GetFileTimesFromJUnitXML(args.JunitXmlPath, fileTimesMap)
	} else if args.SplitBy != "" {
		var err error
		req := types.GetTestTimesReq{}
		var res types.GetTestTimesResp
		switch args.SplitBy {
		case splitByFileTimeStr:
			req.IncludeFilename = true
			res, err = ti.GetTestTimes(context.Background(), log, req)
			fileTimesMap = stutils.ConvertMap(res.FileTimeMap)
			if args.FilePath == "" {
				readTestData = false
			}
		case splitByClassTimeStr:
			req.IncludeClassname = true
			res, err = ti.GetTestTimes(context.Background(), log, req)
			fileTimesMap = stutils.ConvertMap(res.ClassTimeMap)
		case splitByTestcaseTimeStr:
			req.IncludeTestCase = true
			res, err = ti.GetTestTimes(context.Background(), log, req)
			fileTimesMap = stutils.ConvertMap(res.TestTimeMap)
		case splitByTestSuiteTimeStr:
			req.IncludeTestSuite = true
			res, err = ti.GetTestTimes(context.Background(), log, req)
			fileTimesMap = stutils.ConvertMap(res.SuiteTimeMap)
		case splitByFileSizeStr:
			stutils.EstimateFileTimesByLineCount(log, currentFileSet, fileTimesMap)
			return fileTimesMap, false
		}

		if err != nil || len(fileTimesMap) == 0 {
			log.Warnw("error getting timing data with given arguments, falling back to splitting by file size")
			fileTimesMap := make(map[string]float64)
			stutils.EstimateFileTimesByLineCount(log, currentFileSet, fileTimesMap)
			return fileTimesMap, false
		}
	}
	return fileTimesMap, readTestData
}
