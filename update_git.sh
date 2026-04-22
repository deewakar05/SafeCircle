#!/bin/bash

echo "Saving current work..."
git stash

echo "Updating backend branch..."
git checkout backend
git checkout stash@{0} -- backend/
git commit -m "feat(backend): complete Phase 6 Route APIs & Models"

echo "Updating frontend branch..."
git checkout frontend
git checkout stash@{0} -- frontend/
git commit -m "feat(frontend): complete Phase 6 Route Editing & Map UI"

echo "Updating main branch..."
git checkout main
git checkout stash@{0} -- README.md
git commit -m "docs: check off Phase 6 in roadmap"

echo "Cleaning up..."
git stash drop

echo "Pushing all branches to GitHub..."
git push origin backend frontend main

echo "Done! All branches are updated."
