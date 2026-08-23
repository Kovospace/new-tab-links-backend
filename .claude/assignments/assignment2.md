# Spring boot & app basics

## Build settings

- build Dockerfile
- implement workflow that will use template https://github.com/Kovospace/kovostack-github-workflows/tree/2.0.2 to build docker image and deploy app

## Project structure

- generate module-like folder structure, where each module will contain all layers of application
  - common folder may contain parts like filters, security, commonly used beans, etc ...
  - specialized folder will contain part of app functionality, for example "user" will contain 
    - dtos
    - repositories
    - controllers
    - services
    - utils
    - mappers
- set all parameter defaults in properties file

## Tech stack

- use Maven
- if mappers needed, use MapStruct
- flyway migrations will be situated in separate repository and will be baked as docker image that will be used 
  as init container in kubernetes. The version of schema (docker image) will be tied with current application by 
  custom maven parameter flyway.migrations.schema.version.
  This is to be implemented yet, no repo with flyway migrations exists yet
- application will use websocket connection to trigger refresh for certain user