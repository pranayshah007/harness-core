// Copyright 2022 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package main

import (
	"bytes"
	"fmt"
	"io"
	"os"
	"sort"
	"strconv"
	"strings"

	"github.com/alexflint/go-arg"
	"github.com/bmatcuk/doublestar"

	junit "github.com/harness/harness-core/product/ci/split_tests/junit"
)

const (
	applicationName = "split-tests"
	numSplitsEnv    = "HARNESS_NODE_TOTAL" // Environment variable for total number of splits
	currentIndexEnv = "HARNESS_NODE_INDEX" // Environment variable for the current index
)

// CLI Arguments
var args struct {
	IncludeFilePattern string `arg:"--glob" help:"Glob pattern to find the test files"`
	ExcludeFilePattern string `arg:"--exclude-glob" help:"Glob pattern to exclude test files"`
	SplitIndex         int    `arg:"--split-index" help:"Index of the current split (or set HARNESS_NODE_INDEX)"`
	SplitTotal         int    `arg:"--split-total" help:"Total number of splits (or set HARNESS_NODE_TOTAL)"`
	SplitByLineCount   bool   `arg:"--split-by-linecount" help:"Use line count to estimate test times"`
	UseJunitXml        bool   `arg:"--use-junit" help:"Use junit XML for test times"`
	JunitXmlPath       string `arg:"--junit-path" help:"Path to Junit XML file to read test times"`
	Verbose            bool   `arg:"--verbose" help:"Enable verbose logging mode"`
}

/*
	Parses the command line args, sets default values and overrides if
	environment variables are set.
*/
func parseArgs() {
	// Set defaults here
	args.SplitByLineCount = true
	args.Verbose = false
	args.SplitIndex = -1
	args.SplitTotal = -1
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

	if args.SplitTotal == 0 || args.SplitIndex < 0 || args.SplitIndex > args.SplitTotal {
		fatalMsg("-split-index and -split-total (and environment variables) are missing or invalid\n")
	}
}

/*
	Map for <FileName, TimeDuration> for loading timing information.
	The time is a metric that's used to calculate weight of the split/bucket
	It doesn't necessarily have time units. For example, we could split the
	files based on filesize or lines of code in which case the time field
	indicates lines.
*/
type fileTimesListItem struct {
	name string
	time float64
}
type fileTimesList []fileTimesListItem

func (l fileTimesList) Len() int { return len(l) }

// Sorts by time descending, then by name ascending.
// Comparator in Golang is Less()
// Sort by name is required for deterministic order across machines
func (l fileTimesList) Less(i, j int) bool {
	return l[i].time > l[j].time ||
		(l[i].time == l[j].time && l[i].name < l[j].name)
}

func (l fileTimesList) Swap(i, j int) {
	temp := l[i]
	l[i] = l[j]
	l[j] = temp
}

// FIXME: Use Remote logger.
func printMsg(msg string, args ...interface{}) {
	if len(args) == 0 {
		fmt.Fprint(os.Stderr, msg)
	} else {
		fmt.Fprintf(os.Stderr, msg, args...)
	}
}

func fatalMsg(msg string, args ...interface{}) {
	printMsg(msg, args...)
	os.Exit(1)
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
func estimateFileTimesByLineCount(currentFileSet map[string]bool, fileTimes map[string]float64) {
	for fileName := range currentFileSet {
		file, err := os.Open(fileName)
		if err != nil {
			printMsg("failed to count lines in file %s: %v\n", fileName, err)
			continue
		}
		defer file.Close()
		lineCount, err := countLines(file)
		if err != nil {
			printMsg("failed to count lines in file %s: %v\n", fileName, err)
			continue
		}
		fileTimes[fileName] = float64(lineCount)
	}
}

/*
Counts the number of lines in a file or stdin
Args:
	io.Reader: File or Stdin
Returns:
	lineCount(int), error
*/
// Credit to http://stackoverflow.com/a/24563853/6678
func countLines(r io.Reader) (int, error) {
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

/*
Split files based on the provided timing data. The output is a list of
buckets/splits for files as well as the duration of each bucket.

Args:
	fileTimesMap: Map containing the time data: <fileName, Duration>
	splitTotal: Desired number of splits
Returns:
	List of buckets with filenames. Eg: [ ["file1", "file2"], ["file3"], ["file4", "file5"]]
	List of bucket durations. Eg: [10.4, 9.3, 10.5]
*/
func splitFiles(fileTimesMap map[string]float64, splitTotal int) ([][]string, []float64) {
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

/*
Removes non-existant files and adds new files to the file-times map.

Args:
	fileTimesMap: {fileName : time}
	currentFileSet: {fileName : true}

Returns:
	Nothing. Updates fileTimesMap in place.
*/
func processFiles(fileTimesMap map[string]float64, currentFileSet map[string]bool) {
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
		averageFileTime = 1.0
	}

	// Populate the file time for missing files.
	for file := range currentFileSet {
		if _, isSet := fileTimesMap[file]; isSet {
			continue
		}
		if args.UseJunitXml {
			printMsg("missing file time for %s\n", file)
		}
		fileTimesMap[file] = averageFileTime
	}
}

func main() {
	parseArgs()

	// We are not using filepath.Glob,
	// because it doesn't support '**' (to match all files in all nested directories)
	currentFiles, err := doublestar.Glob(args.IncludeFilePattern)
	if err != nil {
		printMsg("failed to enumerate current file set: %v", err)
		os.Exit(1)
	}
	currentFileSet := make(map[string]bool)
	for _, file := range currentFiles {
		currentFileSet[file] = true
	}

	// Exclude the specified files
	if args.ExcludeFilePattern != "" {
		excludedFiles, err := doublestar.Glob(args.ExcludeFilePattern)
		if err != nil {
			printMsg("failed to enumerate excluded file set: %v", err)
			os.Exit(1)
		}
		for _, file := range excludedFiles {
			delete(currentFileSet, file)
		}
	}

	// Construct a map of file times {fileName: Duration}
	fileTimesMap := make(map[string]float64)
	// TODO: Add support for SplitByTiming
	if args.SplitByLineCount {
		estimateFileTimesByLineCount(currentFileSet, fileTimesMap)
	} else if args.UseJunitXml {
		junit.GetFileTimesFromJUnitXML(args.JunitXmlPath, fileTimesMap)
	}

	processFiles(fileTimesMap, currentFileSet)
	buckets, bucketTimes := splitFiles(fileTimesMap, args.SplitTotal)
	if args.UseJunitXml {
		printMsg("expected test time: %0.1fs\n", bucketTimes[args.SplitIndex])
	}

	fmt.Println(strings.Join(buckets[args.SplitIndex], " "))
}
