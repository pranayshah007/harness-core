// Copyright 2020 Harness Inc. All rights reserved.
// Use of this source code is governed by the PolyForm Free Trial 1.0.0 license
// that can be found in the licenses directory at the root of this repository, also available at
// https://polyformproject.org/wp-content/uploads/2020/05/PolyForm-Free-Trial-1.0.0.txt.

package stream

import (
	"encoding/json"
	"os"

	client "github.com/harness/harness-core/product/log-service/client"

	"gopkg.in/alecthomas/kingpin.v2"
)

type infoCommand struct {
	server string
	token  string
}

func (c *infoCommand) run(*kingpin.ParseContext) error {
	client := client.NewHTTPClient(c.server, "test", c.token, false, "")
	info, err := client.Info(nocontext)
	if err != nil {
		return err
	}
	enc := json.NewEncoder(os.Stdout)
	enc.SetIndent("", "  ")
	return enc.Encode(info)
}

func registerInfo(app *kingpin.CmdClause) {
	c := new(infoCommand)

	cmd := app.Command("info", "return stream data").
		Action(c.run)

	cmd.Flag("server", "server endpoint").
		Default("http://localhost:8079").
		StringVar(&c.server)

	cmd.Arg("token", "server token").
		Required().
		StringVar(&c.token)
}
