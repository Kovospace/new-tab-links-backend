# Adding flyway migrations repository to the project and implementing it

## Assignment

- repo is located here: https://github.com/Kovospace/new-tab-links-migrations
- create docker image based on flyway image with needed migrations
- use github workflow template from https://github.com/Kovospace/kovostack-github-workflows to build the app but:
  - increase last version number of most recent tag of main in that repo and set it 
  - set image name tag version to copy that tag version
    - modify workflow template to support custom version num if provided intead of current SHA as tag part after colon
    - release new version of template and use that
  - do not add deployment option for that workflow