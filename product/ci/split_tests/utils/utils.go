// Copyright 2022 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package utils

import (
	"bufio"
	"bytes"
	"encoding/json"
	"fmt"
	"go.uber.org/zap"
	"io"
	"os"
	"sort"

	"github.com/bmatcuk/doublestar"
)

//func GetSplitTests(tests []types.RunnableTest, splitStrategy string, idx, total int) ([]types.RunnableTest, error) {
//	res := make([]types.RunnableTest, 0)
//
//	// Get test timing data and determine whether to split by file size
//	timeMap := map[string]float64{}
//	//timeMap, err := GetTestTimeData(splitStrategy)
//	//if err != nil {
//	//	splitStrategy = splitByFileSizeStr
//	//}
//
//	// Create current file set and current file map
//	var currentFileMap map[string][]types.RunnableTest
//	var currentFileSet map[string]bool
//	var testId string
//	for _, t := range tests {
//		switch splitStrategy {
//		case SplitByClassTimeStr:
//			testId = t.Pkg + t.Class
//		case SplitByFileSizeStr:
//			testId = t.Path
//		default:
//			msg := fmt.Sprintf("set one of %s / %s as split strategy", SplitByFileSizeStr, SplitByClassTimeStr)
//			return res, fmt.Errorf(msg)
//		}
//		currentFileSet[testId] = true
//		currentFileMap[testId] = append(currentFileMap[testId], t)
//	}
//
//	// Remove any unwanted entries from timeMap and populate average time
//	// for entries without any timing data
//	ProcessFiles(timeMap, currentFileSet, float64(1), false)
//
//	// Split tests into buckets
//	buckets, _ := stutils.SplitFiles(timeMap, total)
//
//	// Return tests from the current node's bucket
//	for _, testId := range buckets[idx] {
//		if _, ok := currentFileMap[testId]; !ok {
//			continue
//		}
//		res = append(res, currentFileMap[testId]...)
//	}
//	return res, nil
//}

/*
Removes non-existent files and adds new files to the file-times map.

Args:
	fileTimesMap: {fileName : time}
	currentFileSet: {fileName : true}

Returns:
	Nothing. Updates fileTimesMap in place.
*/
func ProcessFiles(fileTimesMap map[string]float64, currentFileSet map[string]bool, defaultTime float64, useJunitXml bool) {
	// First Remove the entries that no longer exist in the filesystem.
	for file := range fileTimesMap {
		if !currentFileSet[file] {
			delete(fileTimesMap, file)
		}
	}

	// For files that don't have time data, use the average value.
	averageFileTime := 0.0
	if len(fileTimesMap) > 0 { // To avoid divide-by-zero error
		for _, time := range fileTimesMap {
			averageFileTime += time
		}
		averageFileTime /= float64(len(fileTimesMap))
	} else {
		averageFileTime = float64(defaultTime)
	}

	// Populate the file time for missing files.
	for file := range currentFileSet {
		if _, isSet := fileTimesMap[file]; isSet {
			continue
		}
		if useJunitXml {
			//log.Warn(fmt.Sprintf("Missing file time for %s", file))
		}
		fileTimesMap[file] = averageFileTime
	}
}

/*
Calculate file times based on number of lines of code in the file.
This can be a simple proxy for splitting files when timing data is not
available.

Args:
	currentFileSet: {fileName : true}
	fileTimes: {fileName : time}
Returns:
	Nothing. Updates fileTimes map in place.
*/
func EstimateFileTimesByLineCount(log *zap.SugaredLogger, currentFileSet map[string]bool, fileTimes map[string]float64) {
	for fileName := range currentFileSet {
		lineCount, err := getLineCount(log, fileName)
		if err != nil {
			log.Errorw(fmt.Sprintf("failed to count lines in file %s", fileName), zap.Error(err))
			continue
		}
		fileTimes[fileName] = float64(lineCount)
	}
}

func getLineCount(log *zap.SugaredLogger, fileName string) (int, error) {
	file, err := os.Open(fileName)
	if err != nil {
		return 0, err
	}
	defer file.Close()
	lineCount, err := CountLines(file)
	if err != nil {
		return 0, err
	}
	return lineCount, nil
}

/*
CountLines counts the number of lines in a file or stdin
Args:
	io.Reader: File or Stdin
Returns:
	lineCount(int), error
*/
// Credit to http://stackoverflow.com/a/24563853/6678
func CountLines(r io.Reader) (int, error) {
	buf := make([]byte, 32*1024)
	count := 0
	lineSep := []byte{'\n'}

	for {
		c, err := r.Read(buf)
		count += bytes.Count(buf[:c], lineSep)

		switch {
		case err == io.EOF:
			return count, nil

		case err != nil:
			return count, err
		}
	}
}

func ReadTestDataFromFile(log *zap.SugaredLogger, filePath string) map[string]bool {
	currentFileSet := make(map[string]bool)

	file, err := os.Open(filePath)
	if err != nil {
		log.Fatalw(fmt.Sprintf("Unable to read the file from the given path %s", err))
	}
	defer file.Close()

	reader := bufio.NewReader(file)
	for {
		line, _, err := reader.ReadLine()
		if err == io.EOF {
			break
		}
		currentFileSet[string(line)] = true
	}
	return currentFileSet
}

func GetTestData(log *zap.SugaredLogger, patterns []string, excludePatterns []string) (map[string]bool, error) {
	currentFileSet := make(map[string]bool)

	// We are not using filepath.Glob,
	// because it doesn't support '**' (to match all files in all nested directories)
	for _, pattern := range patterns {
		currentFiles, err := doublestar.Glob(pattern)
		if err != nil {
			log.Errorw("Failed to enumerate files while detecting tests", zap.Error(err))
			return currentFileSet, err
		}

		for _, file := range currentFiles {
			currentFileSet[file] = true
		}
	}

	// Exclude the specified files
	for _, excludePattern := range excludePatterns {
		excludedFiles, err := doublestar.Glob(excludePattern)
		if err != nil {
			log.Fatalw("failed to enumerate excluded file set", zap.Error(err))
		}
		for _, file := range excludedFiles {
			delete(currentFileSet, file)
		}
	}
	return currentFileSet, nil
}

func ConvertMap(intMap map[string]int) map[string]float64 {
	fileTimesMap := make(map[string]float64)
	for k, v := range intMap {
		fileTimesMap[k] = float64(v)
	}
	return fileTimesMap
}

func ConvertMapToJson(timeMap map[string]float64) []byte {
	timeMapJson, _ := json.Marshal(timeMap)
	return timeMapJson
}

/*
SplitFiles splits files based on the provided timing data. The output is a list of
buckets/splits for files as well as the duration of each bucket.

Args:
	fileTimesMap: Map containing the time data: <fileName, Duration>
	splitTotal: Desired number of splits
Returns:
	List of buckets with filenames. Eg: [ ["file1", "file2"], ["file3"], ["file4", "file5"]]
	List of bucket durations. Eg: [10.4, 9.3, 10.5]
*/
func SplitFiles(fileTimesMap map[string]float64, splitTotal int) ([][]string, []float64) {
	buckets := make([][]string, splitTotal)
	bucketTimes := make([]float64, splitTotal)

	// Build a sorted list of files
	fileTimesList := make(fileTimesList, len(fileTimesMap))
	for file, time := range fileTimesMap {
		fileTimesList = append(fileTimesList, fileTimesListItem{file, time})
	}
	sort.Sort(fileTimesList)

	for _, file := range fileTimesList {
		// find bucket with min weight
		minBucket := 0
		for bucket := 1; bucket < splitTotal; bucket++ {
			if bucketTimes[bucket] < bucketTimes[minBucket] {
				minBucket = bucket
			}
		}
		// add file to bucket
		buckets[minBucket] = append(buckets[minBucket], file.name)
		bucketTimes[minBucket] += file.time
	}

	return buckets, bucketTimes
}
