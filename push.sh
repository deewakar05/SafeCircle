#!/bin/bash

echo "Switching to main branch..."
git checkout main

echo "Adding all Phase 7 and cleanup changes..."
git add .

echo "Committing..."
git commit -m "chore: Phase 7 UI polish and architectural cleanup"

echo "Syncing backend branch..."
git checkout backend
git reset --hard main

echo "Syncing frontend branch..."
git checkout frontend
git reset --hard main

echo "Pushing all branches to GitHub..."
git push origin main
git push origin backend --force
git push origin frontend --force

echo "Done! Phase 7 is fully published."
