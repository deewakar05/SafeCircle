#!/bin/bash

echo "Switching to main branch..."
git checkout main

echo "Restoring Phase 6 files from backend and frontend branches..."
git checkout backend -- backend/src/main/java/com/safecircle/model/Group.java
git checkout backend -- backend/src/main/java/com/safecircle/service/GroupService.java
git checkout backend -- backend/src/main/java/com/safecircle/controller/GroupController.java
git checkout frontend -- frontend/src/services/api.js
git checkout frontend -- frontend/src/pages/TrackingPage.jsx

echo "Adding all files (including the new RouteDto.java)..."
git add .

echo "Committing the full Phase 6 to main..."
git commit -m "feat: complete Phase 6 Route Planning & Navigation (unified)"

echo "Synchronizing backend branch to resolve conflicts..."
git checkout backend
git reset --hard main

echo "Synchronizing frontend branch to resolve conflicts..."
git checkout frontend
git reset --hard main

echo "Force pushing to GitHub to clear conflicts..."
git push origin main
git push origin backend --force
git push origin frontend --force

echo "Done! The GitHub conflicts are resolved and all branches are fully updated."
