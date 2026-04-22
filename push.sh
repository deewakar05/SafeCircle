#!/bin/bash

echo "Switching to main branch..."
git checkout main

echo "Adding Phase 8 SOS changes..."
git add .

echo "Committing..."
git commit -m "feat: complete Phase 8 SOS Emergency Feature"

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

echo "Done! Phase 8 is fully published to all branches."
