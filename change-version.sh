#!/bin/bash

# Script to change the Maven project version and regenerate build.just
# Usage: ./change-version.sh <new-version>

# Check if version argument is provided
if [ $# -eq 0 ]; then
    echo "Error: No version specified"
    echo "Usage: $0 <new-version>"
    echo "Example: $0 1.2.3"
    echo "Example: $0 1.2.3-SNAPSHOT"
    exit 1
fi

NEW_VERSION=$1

echo "Changing version to: $NEW_VERSION"

# Warn if the working tree has changes other than the two files this script manages itself
# (pom.xml/build.just). Those are expected to differ before a bump and the script stages and commits
# them, so they shouldn't trigger the warning. Everything else is unexpected: anything staged would
# be swept into the "Bump version" commit, and the v$NEW_VERSION tag is created from it.
UNEXPECTED_CHANGES=$(git status --porcelain | grep -vE '^.. (pom\.xml|build\.just)$' || true)
if [ -n "$UNEXPECTED_CHANGES" ]; then
    echo
    echo "Warning: the working tree has changes other than the version files:"
    echo
    echo "$UNEXPECTED_CHANGES"
    echo
    echo "These are present while creating tag v$NEW_VERSION; anything staged will be included in the 'Bump version' commit."
    read -r -p "Continue with version bump and tagging? [y/N] " REPLY
    case "$REPLY" in
        [yY] | [yY][eE][sS]) ;;
        *)
            echo "Aborted."
            exit 1
            ;;
    esac
fi

# Change the Maven version
echo "Running: mvn versions:set -DnewVersion=$NEW_VERSION"
mvn versions:set -DnewVersion="$NEW_VERSION"

# Check if the version change was successful
if [ $? -ne 0 ]; then
    echo "Error: Failed to change version"
    exit 1
fi

# Clean up existing generated files
echo "Cleaning up existing generated files..."
rm -f build.just

# Regenerate build.just with the new version
echo "Regenerating build.just with new version..."
echo "Running: mvn process-resources"
mvn process-resources

# Check if the resource processing was successful
if [ $? -ne 0 ]; then
    echo "Error: Failed to regenerate build.just"
    exit 1
fi

# The antrun copy is failonerror="false" (a leftover from the Render-era Docker build, whose context
# had no template), so a missing template regenerates nothing rather than failing. Catch that here
# instead of at deploy time.
if [ ! -f build.just ]; then
    echo "Error: build.just was not generated — is build.just.template present?"
    exit 1
fi

echo "Successfully changed version to $NEW_VERSION"

# Commit the Maven version change
echo "Committing Maven version change..."
mvn versions:commit

# Check if versions:commit was successful
if [ $? -ne 0 ]; then
    echo "Error: Failed to commit Maven version change"
    exit 1
fi

# Add changed files to git
echo "Adding changed files to git..."
git add pom.xml build.just

# Check if git add was successful
if [ $? -ne 0 ]; then
    echo "Error: Failed to add files to git"
    exit 1
fi

# Commit to git
echo "Committing to git..."
git commit -m "Bump version to $NEW_VERSION"

# Check if git commit was successful
if [ $? -ne 0 ]; then
    echo "Error: Failed to commit to git"
    exit 1
fi

# Create git tag for the version
echo "Creating git tag: v$NEW_VERSION"
git tag -a "v$NEW_VERSION" -m "Version $NEW_VERSION"

# Check if git tag was successful
if [ $? -ne 0 ]; then
    echo "Error: Failed to create git tag"
    exit 1
fi

echo "Version change complete and committed to git with tag v$NEW_VERSION!"
echo "Git push commands copied to clipboard - paste and run when ready:"
echo "  git push && git push origin v$NEW_VERSION" | pbcopy
echo "  git push && git push origin v$NEW_VERSION"
