package worker

import "gopkg.in/alecthomas/kingpin.v2"

type serverCommand struct {
	envfile string
}

func (c *serverCommand) run(*kingpin.ParseContext) error {
	return nil
}

func Register(app *kingpin.Application) {
	c := new(serverCommand)

	cmd := app.Command("worker", "start the worker").
		Action(c.run)

	cmd.Flag("env-file", "environment file").
		Default(".env").
		StringVar(&c.envfile)
}
