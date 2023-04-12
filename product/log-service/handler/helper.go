// Copyright 2021 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package handler

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"github.com/harness/harness-core/product/log-service/store"
	"github.com/harness/harness-core/product/log-service/types"
	"net/http"
	"regexp"
	"strings"
)

const (
	accountIDParam = "accountID"
	keyParam       = "key"
	snapshotParam  = "snapshot"
	usePrefixParam = "prefix"

	searchGpt = "searchGpt"
)

// Error types
var (
	errCommon       = errors.New("common error messages")
	errFileNotFound = errors.New("file not found")
	errAssertion    = errors.New("assertion error")
	errNetwork      = errors.New("network error")
	errMemory       = errors.New("memory error")
	errPermission   = errors.New("permission error")
	errDependency   = errors.New("dependency error")
	errConfig       = errors.New("configuration error")
)

func getNudges() []Nudge {
	// <search-term> <resolution> <error-msg>
	nudgesList := []Nudge{
		NewNudge("[Kk]illed", "Increase memory resources for the step", errors.New("out of memory")),
		NewNudge(".*git.* SSL certificate problem",
			"Set sslVerify to false in CI codebase properties", errors.New("SSL certificate error")),
		NewNudge("Cannot connect to the Docker daemon",
			"Setup dind if it's not running. If dind is running, privileged should be set to true",
			errors.New("could not connect to the docker daemon")),
		NewNudge("Fatal", searchGpt, errors.New("fatal error")),
		NewNudge("Error", searchGpt, errors.New("unknown error")),
	}
	for _, searchStr := range []string{"SyntaxError", "TypeError", "NameError", "ValueError"} {
		nudgesList = append(nudgesList, NewNudge(searchStr, searchGpt, errCommon))
	}
	for _, searchStr := range []string{"assertion failed", "assertion error", "test failed"} {
		nudgesList = append(nudgesList, NewNudge(searchStr, searchGpt, errAssertion))
	}
	for _, searchStr := range []string{"No such file or directory", "File not found", "Can't open file"} {
		nudgesList = append(nudgesList, NewNudge(searchStr, searchGpt, errFileNotFound))
	}
	for _, searchStr := range []string{"Connection refused", "Connection timed out", "401 Unauthorized"} {
		nudgesList = append(nudgesList, NewNudge(searchStr, searchGpt, errNetwork))
	}
	for _, searchStr := range []string{"Out of memory", "MemoryError", "malloc"} {
		nudgesList = append(nudgesList, NewNudge(searchStr, searchGpt, errMemory))
	}
	for _, searchStr := range []string{"Permission denied", "Access denied", "Operation not permitted"} {
		nudgesList = append(nudgesList, NewNudge(searchStr, searchGpt, errPermission))
	}
	for _, searchStr := range []string{"ModuleNotFoundError", "ImportError", "Dependency not found"} {
		nudgesList = append(nudgesList, NewNudge(searchStr, searchGpt, errDependency))
	}
	for _, searchStr := range []string{"Invalid configuration", "Configuration error", "Missing configuration"} {
		nudgesList = append(nudgesList, NewNudge(searchStr, searchGpt, errCommon))
	}
	return nudgesList
}

var nudges = getNudges()

// writeBadRequest writes the json-encoded error message
// to the response with a 400 bad request status code.
func WriteBadRequest(w http.ResponseWriter, err error) {
	writeError(w, err, 400)
}

// writeNotFound writes the json-encoded error message to
// the response with a 404 not found status code.
func WriteNotFound(w http.ResponseWriter, err error) {
	writeError(w, err, 404)
}

// writeInternalError writes the json-encoded error message
// to the response with a 500 internal server error.
func WriteInternalError(w http.ResponseWriter, err error) {
	writeError(w, err, 500)
}

func CreateAccountSeparatedKey(accountID string, key string) string {
	return accountID + "/" + key
}

// writeJSON writes the json-encoded representation of v to
// the response body.
func WriteJSON(w http.ResponseWriter, v interface{}, status int) {
	for k, v := range noCacheHeaders {
		w.Header().Set(k, v)
	}
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	enc := json.NewEncoder(w)
	enc.SetIndent("", "  ")
	enc.Encode(v)
}

// writeError writes the json-encoded error message to the
// response.
func writeError(w http.ResponseWriter, err error, status int) {
	out := struct {
		Message string `json:"error_msg"`
	}{err.Error()}
	WriteJSON(w, &out, status)
}

func uploadErrorLogs(ctx context.Context, store store.Store, key string, logStr string, errorMsgChan chan types.KeyErrorMsg) {
	errStrings := make([]string, 0)
	lastNLines := 50

	logStrSplit := strings.Split(logStr, "\n")
	size := len(logStrSplit)
	var errType string
	for idx := max(0, size-lastNLines); idx < size; idx++ { //nolint:gomnd
		line := logStrSplit[idx]
		// Iterate over the nudges and see if we get a match
		for _, n := range nudges {
			r, err := regexp.Compile(n.GetSearch())
			if err != nil {
				continue
			}
			if r.MatchString(line) && n.GetResolution() == searchGpt {
				errType = n.GetError().Error()
				errStrings = logStrSplit
			}
		}
	}
	if len(errStrings) == 0 {
		return
	}
	allErrStrings := strings.Join(errStrings, "\n")
	elem := types.KeyErrorMsg{Key: key, ErrorMsg: allErrStrings, ErrorType: errType}
	fmt.Println(fmt.Sprintf("[RUTVIJ] Sending elem to channel %s", elem))
	errorMsgChan <- elem
	//fmt.Println(fmt.Sprintf("[RUTVIJ] Found some error strings %s", allErrStrings))
	//bodyBytes := []byte(allErrStrings)
	//ioReader := ioutil.NopCloser(bytes.NewBuffer(bodyBytes))
	//store.Upload(ctx, rcaKey, ioReader)
}

func max(a, b int) int {
	if a > b {
		return a
	}
	return b
}
