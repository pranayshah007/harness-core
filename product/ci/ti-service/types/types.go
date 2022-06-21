// Copyright 2021 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package types

type Status string
type FileStatus string
type Selection string

const (
	// StatusPassed represents a passed test.
	StatusPassed = "passed"

	// StatusSkipped represents a test case that was intentionally skipped.
	StatusSkipped = "skipped"

	// StatusFailure represents a violation of declared test expectations,
	// such as a failed assertion.
	StatusFailed = "failed"

	// StatusError represents an unexpected violation of the test itself, such as
	// an uncaught exception.
	StatusError = "error"

	// SelectSourceCode represents a selection corresponding to source code changes.
	SelectSourceCode = "source_code"

	// SelectNewTest represents a selection corresponding to a new test (eg a new test
	// introduced in the PR).
	SelectNewTest = "new_test"

	// SelectUpdatedTest represents a selection corresponding to an updated test (eg an existing
	// test which was modified).
	SelectUpdatedTest = "updated_test"

	// SelectFlakyTest represents a selection of a test because it's flaky.
	SelectFlakyTest = "flaky_test"

	// FileModified represents a modified file. Keeping it consistent with git syntax.
	FileModified = "modified"

	// FileAdded represents a file which was added in the PR.
	FileAdded = "added"

	// FileDeleted represents a file which was deleted in the PR.
	FileDeleted = "deleted"
)

func ConvertToFileStatus(s string) FileStatus {
	switch s {
	case FileModified:
		return FileModified
	case FileAdded:
		return FileAdded
	case FileDeleted:
		return FileDeleted
	}
	return FileModified
}

type Result struct {
	Status  Status `json:"status"`
	Message string `json:"message"`
	Type    string `json:"type"`
	Desc    string `json:"desc"`
}

type ResponseMetadata struct {
	TotalPages    int `json:"totalPages"`
	TotalItems    int `json:"totalItems"`
	PageItemCount int `json:"pageItemCount"`
	PageSize      int `json:"pageSize"`
}

type TestCases struct {
	Metadata ResponseMetadata `json:"data"`
	Tests    []TestCase       `json:"content"`
}

type TestSuites struct {
	Metadata ResponseMetadata `json:"data"`
	Suites   []TestSuite      `json:"content"`
}

type TestCase struct {
	Name       string `json:"name"`
	ClassName  string `json:"class_name"`
	SuiteName  string `json:"suite_name"`
	Result     Result `json:"result"`
	DurationMs int64  `json:"duration_ms"`
	SystemOut  string `json:"stdout"`
	SystemErr  string `json:"stderr"`
}

type TestSummary struct {
	Name   string `json:"name"`
	Status Status `json:"status"`
}

type SummaryResponse struct {
	TotalTests      int   `json:"total_tests"`
	FailedTests     int   `json:"failed_tests"`
	SuccessfulTests int   `json:"successful_tests"`
	SkippedTests    int   `json:"skipped_tests"`
	TimeMs          int64 `json:"duration_ms"`
}

type StepInfo struct {
	Step  string `json:"step"`
	Stage string `json:"stage"`
}

type TestSuite struct {
	Name         string `json:"name"`
	DurationMs   int64  `json:"duration_ms"`
	TotalTests   int    `json:"total_tests"`
	FailedTests  int    `json:"failed_tests"`
	SkippedTests int    `json:"skipped_tests"`
	PassedTests  int    `json:"passed_tests"`
	FailPct      int    `json:"fail_pct"`
}

// Test Intelligence specific structs

// RunnableTest contains information about a test to run it.
// This is different from TestCase struct which contains information
// about a test case run. RunnableTest is used to run a test.
type RunnableTest struct {
	Pkg       string    `json:"pkg"`
	Class     string    `json:"class"`
	Method    string    `json:"method"`
	Selection Selection `json:"selection"` // information on why a test was selected
}

type SelectTestsResp struct {
	TotalTests    int            `json:"total_tests"`
	SelectedTests int            `json:"selected_tests"`
	NewTests      int            `json:"new_tests"`
	UpdatedTests  int            `json:"updated_tests"`
	SrcCodeTests  int            `json:"src_code_tests"`
	SelectAll     bool           `json:"select_all"` // We might choose to run all the tests
	Tests         []RunnableTest `json:"tests"`
}

type SelectTestsReq struct {
	// If this is specified, TI service will return saying it wants to run all the tests. We want to
	// maintain stats even when all the tests are run.
	SelectAll    bool     `json:"select_all"`
	Files        []File   `json:"files"`
	TargetBranch string   `json:"target_branch"`
	Repo         string   `json:"repo"`
	TiConfig     TiConfig `json:"ti_config"`
}

type SelectionDetails struct {
	New int `json:"new_tests"`
	Upd int `json:"updated_tests"`
	Src int `json:"source_code_changes"`
}

type SelectionOverview struct {
	Total        int              `json:"total_tests"`
	Skipped      int              `json:"skipped_tests"`
	TimeSavedMs  int              `json:"time_saved_ms"`
	TimeTakenMs  int              `json:"time_taken_ms"`
	Repo         string           `json:"repo"`
	SourceBranch string           `json:"source_branch"`
	TargetBranch string           `json:"target_branch"`
	Selected     SelectionDetails `json:"selected_tests"`
}

type File struct {
	Name   string     `json:"name"`
	Status FileStatus `json:"status"`
}

type DownloadLink struct {
	URL     string `json:"url"`
	RelPath string `json:"rel_path"` // this is the relative path to the artifact from the base URL
}

// This is a yaml file which may or may not exist in the root of the source code
// as .ticonfig. The contents of the file get deserialized into this object.
// Sample YAML:
// config:
//   ignore:
//     - README.md
//     - config.sh
type TiConfig struct {
	Config struct {
		Ignore []string `json:"ignore"`
	}
}

type DiffInfo struct {
	Sha   string
	Files []File
}

type MergePartialCgRequest struct {
	AccountId    string
	Repo         string
	TargetBranch string
	Diff         DiffInfo
}

// Visualization structures

// Simplified node
type VisNode struct {
	Id int `json:"id"`

	Package string `json:"package"`
	Class   string `json:"class"`
	File    string `json:"file"`
	Type    string `json:"type"`
	Root    bool   `json:"root,omitempty"`
	// Gives information about useful nodes which might be used by UI on which nodes to center
	Important bool `json:"important"`
}

type VisMapping struct {
	From int   `json:"from"`
	To   []int `json:"to"`
}

type GetVgReq struct {
	AccountId    string
	Repo         string
	SourceBranch string
	TargetBranch string
	Limit        int64
	Class        string
	DiffFiles    []File
}

type GetVgResp struct {
	Nodes []VisNode    `json:"nodes"`
	Edges []VisMapping `json:"edges"`
}
