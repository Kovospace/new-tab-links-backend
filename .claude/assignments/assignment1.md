# Agents & Claude code project preparation

This project will be backend service for NewTabLinks project. 
Local: /home/kovo/IdeaProjects/NewTabGroupedLinks
Repo: git@github.com:K0V0/NewTabGroupedLinks.git

In this assignment create only claude basic setup, this setup will be later updated on following assignments and tasks:

## CLAUDE.md

Implement claude.md with yet known and available knowledge. Will be extended in future by tasks and assignments.

## skills

If needed

## Developer agent

Agent that can be used by NewTabGroupedLinks to perform:
- calculation of risks and impact of changes
- amount of work
- implementation and changes itself
- agent is allowed to push to git, but only to ```feature/**``` or ```bugfix/***``` branches

## Principles to follow

- SOLID principles
- MVC pattern
- human readable code
- javadoc everywhere
- don't be affraid of rather having long variables / methods / classes names that helps programmer 
  to guess its functionality and goal solely by name
- put significant knowledge on features or debug that requires lot of work into memory
- document changes into claude.md, skills and other claude features if it gains processing speed or lower processing cost 
  and better context in the future
- personally do not like long methods and spaghetti code, split into multiple methods usable somewhere else
  use util classes or create second service
- I do not like extremly long classes too
- Minimize token usage, if something could be offloaded to skills or other claude features

## Tech stack & basic features

- use latest stable LTS version of Java
- use latest stable (and LTS) version of SpringBoot
- Document APIs using OpenApi (Swagger) with UI
- Dockerfile to build app on github pipeline

## Final words

For now, perform just claude setup. Spring Boot Starter or project structure will be the work for upcoming task