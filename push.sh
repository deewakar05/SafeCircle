#!/bin/bash

echo "Switching to main branch..."
git checkout main

echo "Adding config changes..."
git add .

echo "Committing..."
git commit -m "feat: finalize SafeCircle production-ready system"

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

echo "Done! The production-ready system is fully published to GitHub."
