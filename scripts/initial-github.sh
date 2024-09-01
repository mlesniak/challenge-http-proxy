#!/bin/bash

if [ -z "$1" ]; then
  echo "Usage: $0 repositoryName"
  exit 1
fi

rm -rf .git
git init
git add .
git commit -m "Initial commit"
gh repo create $1 --private --remote origin --source . --push
