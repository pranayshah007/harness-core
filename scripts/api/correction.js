const yaml = require('js-yaml');
const fs = require('fs');
var words = yaml.safeLoad(fs.readFileSync('merged.yaml', 'utf8'));
var xTags = yaml.safeLoad(fs.readFileSync('x-tags.yaml', 'utf8'));
func()
function func() {
  words['x-tagGroups'] = xTags;
  const yamlStr = yaml.safeDump(words)

  fs.writeFile('mergeV2/merged.yaml', yamlStr, (err) => {
    if (err) {
        throw err;
    }
    console.log("YAML data is saved.");
  });
}