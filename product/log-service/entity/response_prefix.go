// Copyright 2023 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package entity

import (
	"encoding/json"
	"time"
)

type ResponsePrefixDownload struct {
	Value   string    `json:"link"`
	Status  string    `json:"status"`
	Expire  time.Time `json:"expires"`
	Message string    `json:"message,omitempty"`
}

func (i ResponsePrefixDownload) MarshalBinary() ([]byte, error) {
	return json.Marshal(i)
}

func (i ResponsePrefixDownload) UnmarshalBinary(b []byte) error {
	return json.Unmarshal(b, &i)
}

func (i ResponsePrefixDownload) IsEmpty() bool {
	return i.Value == ""
}
