// Copyright 2022 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package main

import (
	stutils "github.com/harness/harness-core/product/ci/split_tests/utils"
	"os"
	"testing"
)

func Test_countLines(t *testing.T) {
	tests := []struct {
		name     string
		fileName string
		want     int
		wantErr  bool
	}{
		{
			name:     "Test1: 10 lines",
			fileName: "testdata/10_lines.txt",
			want:     10,
			wantErr:  false,
		},

		{
			name:     "Test2: 100 lines",
			fileName: "testdata/100_lines.txt",
			want:     100,
			wantErr:  false,
		},
		{
			name:     "Test2: 1000 lines",
			fileName: "testdata/1000_lines.txt",
			want:     1000,
			wantErr:  false,
		},
		{
			name:     "Test2: 5000 lines",
			fileName: "testdata/5000_lines.txt",
			want:     5000,
			wantErr:  false,
		},
	}
	for _, tt := range tests {
		fp, _ := os.Open(tt.fileName)
		defer fp.Close()
		t.Run(tt.name, func(t *testing.T) {
			got, err := stutils.CountLines(fp)
			if (err != nil) != tt.wantErr {
				t.Errorf("countLines() error = %v, wantErr %v", err, tt.wantErr)
				return
			}
			if got != tt.want {
				t.Errorf("countLines() = %v, want %v", got, tt.want)
			}
		})
	}
}

func Test_splitFiles(t *testing.T) {
	type args struct {
		fileTimesMap map[string]float64
		splitTotal   int
	}
	sampleFileTimesMap := map[string]float64{"file1": 1.0,
		"file2": 2.11,
		"file3": 3,
		"file4": 2.5}
	tests := []struct {
		name  string
		args  args
		want  [][]string
		want1 []float64
	}{
		{
			name: "Test1: 1 bucket",
			args: args{
				fileTimesMap: sampleFileTimesMap,
				splitTotal:   1,
			},
			want:  [][]string{{"file1", "file2", "file3", "file4"}},
			want1: []float64{8.61},
		},
		{
			name: "Test2: 2 buckets",
			args: args{
				fileTimesMap: sampleFileTimesMap,
				splitTotal:   2,
			},
			want:  [][]string{{"file1", "file3"}, {"file2", "file4"}},
			want1: []float64{4, 4.6},
		},
		{
			name: "Test3: 4 buckets",
			args: args{
				fileTimesMap: sampleFileTimesMap,
				splitTotal:   4,
			},
			want:  [][]string{{"file1"}, {"file2"}, {"file3"}, {"file4"}},
			want1: []float64{1.0, 2.11, 3, 2.5},
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			got, got1 := stutils.SplitFiles(tt.args.fileTimesMap, tt.args.splitTotal)
			if len(got1) != len(tt.want1) {
				t.Errorf("splitFiles() got1 = %v, want1 %v", got1, tt.want1)
			}
			if len(got) != len(tt.want) {
				t.Errorf("splitFiles() got = %v, want %v", got, tt.want)
			}
		})
	}
}

func Test_processFiles(t *testing.T) {
	tests := []struct {
		name           string
		fileTimesMap   map[string]float64
		currentFileSet map[string]bool
		avgTime        float64
	}{
		{
			name: "Test all files in current",
			fileTimesMap: map[string]float64{"file1": 1.0,
				"file2": 2.0,
				"file3": 3,
				"file4": 4},
			avgTime:        2.5,
			currentFileSet: map[string]bool{"file1": true, "file2": true, "file3": true, "file4": true},
		},
		{
			name: "Test a file deleted from current set",
			fileTimesMap: map[string]float64{"file1": 1.0,
				"file2": 2.0,
				"file3": 3,
				"file4": 4},
			avgTime:        2.5,
			currentFileSet: map[string]bool{"file1": true, "file3": true, "file4": true},
		},
		{
			name: "Test a new file in current set",
			fileTimesMap: map[string]float64{"file1": 1.0,
				"file2": 2.0,
				"file3": 3,
				"file4": 4},
			avgTime:        2.5,
			currentFileSet: map[string]bool{"new_file": true, "file1": true, "file2": true, "file3": true, "file4": true},
		},
	}
	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			stutils.ProcessFiles(tt.fileTimesMap, tt.currentFileSet, float64(1), false)
			if len(tt.fileTimesMap) != len(tt.currentFileSet) {
				t.Errorf("processFiles() got %v, want %v", len(tt.fileTimesMap), len(tt.currentFileSet))
			}
			// For newly added file check if the time is avg of other file times.
			if len(tt.currentFileSet) == 5 {
				if tt.fileTimesMap["new_file"] != tt.avgTime {
					t.Errorf("processFiles() new file avg got %d, want %d", tt.fileTimesMap["new_file"], tt.avgTime)
				}
			}
		})
	}
}
