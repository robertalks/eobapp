#!/bin/sh

git tag -d "$@" 
git push origin --delete "$@"
