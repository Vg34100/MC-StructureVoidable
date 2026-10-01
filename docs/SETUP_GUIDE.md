# Complete Setup Guide

This guide shows how to set up the exact same multi-version automation system for **any Minecraft mod**.

## 🎯 Overview

You'll create a system that:
- Automatically ports new features to older MC versions
- Tests compatibility across multiple MC versions
- Provides proper IntelliJ integration for each version
- Handles both Fabric and NeoForge metadata updates

## 📋 Prerequisites

- **Existing Minecraft mod** (Architectury recommended for cross-platform)
- **Git repository** on GitHub
- **Basic knowledge** of Gradle, GitHub Actions

## 🚀 Step-by-Step Setup

### 1. Repository Structure Setup

Ensure your mod has this structure:
```
your-mod/
├── build.gradle                 # Root build file
├── gradle.properties            # Main version config
├── common/
│   └── src/main/java/           # Shared mod code
├── fabric/
│   ├── build.gradle
│   └── src/main/resources/fabric.mod.json
└── neoforge/
    ├── build.gradle
    └── src/main/resources/META-INF/neoforge.mods.toml
```

### 2. Create Version Property Files

Create directory and version files:
```bash
mkdir -p gradle/versions
```

**Example: `gradle/versions/mc-1214.properties`**
```properties
# Minecraft 1.21.4 Version Properties  
# Use with: ./gradlew build -PmcVersion=1214

minecraft_version=1.21.4
architectury_api_version=15.0.3
fabric_loader_version=0.17.2
fabric_api_version=0.119.4+1.21.4
neoforge_version=21.4.152

# Update archives name to reflect version
archives_name=your-mod-1-21-4

# ModMenu version (for Fabric only)
modmenu_version=13.0.2
```

**Example: `gradle/versions/mc-1213.properties`**
```properties
# Minecraft 1.21.3 Version Properties
# Use with: ./gradlew build -PmcVersion=1213

minecraft_version=1.21.3
architectury_api_version=14.0.4
fabric_loader_version=0.17.2
fabric_api_version=0.114.1+1.21.3
neoforge_version=21.3.91

# Update archives name to reflect version  
archives_name=your-mod-1-21-3

# ModMenu version (for Fabric only)
modmenu_version=12.0.0
```

### 3. Update Root build.gradle

Add version switching logic at the **top** of your root `build.gradle`:

```gradle
plugins {
    id 'dev.architectury.loom' version '1.10-SNAPSHOT' apply false
    id 'architectury-plugin' version '3.4-SNAPSHOT'
    id 'com.github.johnrengelman.shadow' version '8.1.1' apply false
}

// Load version-specific properties if mcVersion parameter is provided
if (project.hasProperty('mcVersion')) {
    def versionFile = file("gradle/versions/mc-${project.mcVersion}.properties")
    if (versionFile.exists()) {
        def versionProps = new Properties()
        versionFile.withInputStream { versionProps.load(it) }
        versionProps.each { key, value ->
            project.ext[key] = value
        }
        println "Loaded version configuration: MC ${project.ext.minecraft_version}"
    } else {
        throw new GradleException("Version file not found: ${versionFile.path}")
    }
}

architectury {
    minecraft = project.minecraft_version
}

// Rest of your existing build.gradle...
```

### 4. Research Version Dependencies

For each MC version you want to support, find compatible versions:

#### Method 1: Use Version Databases
- [Fabric Versions](https://fabricmc.net/develop/) - Official Fabric version info
- [NeoForge Versions](https://neoforged.net/) - Official NeoForge releases  
- [Architectury Versions](https://github.com/architectury/architectury-api/releases) - Architectury API releases
- [ModMenu Versions](https://modrinth.com/mod/modmenu/versions) - ModMenu compatibility

#### Method 2: Check Existing Mods
Look at popular mods' version support:
- [JEI](https://github.com/mezz/JustEnoughItems) 
- [REI](https://github.com/shedaniel/RoughlyEnoughItems)
- [Architectury API](https://github.com/architectury/architectury-api)

#### Method 3: Create Test Projects
1. Go to [Architectury Template](https://github.com/architectury/architectury-template)
2. Create new projects for each MC version
3. Note the auto-generated version numbers

### 5. Create GitHub Actions Workflows

Create `.github/workflows/` directory and add these files:

#### 5.1: Auto-Port Workflow

**`.github/workflows/auto-port.yml`**
```yaml
name: Auto-Port to MC Versions

on:
  push:
    branches: [master]
  workflow_dispatch:

jobs:
  auto-port:
    runs-on: ubuntu-latest
    strategy:
      matrix:
        version: ['1214', '1213']  # Add more versions as needed
        include:
          - version: '1214'
            minecraft_version: '1.21.4'
          - version: '1213'
            minecraft_version: '1.21.3'
    
    steps:
      - name: Checkout repository
        uses: actions/checkout@v4
        with:
          fetch-depth: 0
          
      - name: Configure Git
        run: |
          git config --global user.name 'github-actions[bot]'
          git config --global user.email 'github-actions[bot]@users.noreply.github.com'
          
      - name: Create/Update auto/mc-${{ matrix.minecraft_version }} branch
        run: |
          BRANCH_NAME="auto/mc-${{ matrix.minecraft_version }}"
          
          if git ls-remote --heads origin $BRANCH_NAME | grep -q $BRANCH_NAME; then
            echo "Branch $BRANCH_NAME exists, checking it out"
            git checkout -B $BRANCH_NAME origin/$BRANCH_NAME
          else
            echo "Branch $BRANCH_NAME does not exist, creating new one"
            git checkout -b $BRANCH_NAME
          fi
          
      - name: Update gradle.properties for MC ${{ matrix.minecraft_version }}
        run: |
          if [ -f "gradle/versions/mc-${{ matrix.version }}.properties" ]; then
            echo "Loading properties from gradle/versions/mc-${{ matrix.version }}.properties"
            
            while IFS='=' read -r key value || [ -n "$key" ]; do
              if [[ $key =~ ^[[:space:]]*# ]] || [[ -z "$key" ]]; then
                continue
              fi
              
              key=$(echo "$key" | xargs)
              value=$(echo "$value" | xargs)
              
              if grep -q "^${key}=" gradle.properties; then
                sed -i "s/^${key}=.*/${key}=${value}/" gradle.properties
                echo "Updated ${key}=${value}"
              else
                echo "${key}=${value}" >> gradle.properties
                echo "Added ${key}=${value}"
              fi
            done < gradle/versions/mc-${{ matrix.version }}.properties
          else
            echo "Version properties file not found"
            exit 1
          fi
          
      - name: Update mod metadata files for MC ${{ matrix.minecraft_version }}
        run: |
          ARCH_VERSION=$(grep "^architectury_api_version=" gradle.properties | cut -d'=' -f2)
          NEOFORGE_VERSION=$(grep "^neoforge_version=" gradle.properties | cut -d'=' -f2)
          
          # Update fabric.mod.json
          if [ -f "fabric/src/main/resources/fabric.mod.json" ]; then
            sed -i 's/"minecraft": "~[^"]*"/"minecraft": "~${{ matrix.minecraft_version }}"/' fabric/src/main/resources/fabric.mod.json
            echo "Updated fabric.mod.json minecraft dependency to ~${{ matrix.minecraft_version }}"
            
            if [ -n "$ARCH_VERSION" ]; then
              sed -i "s/\"architectury\": \"[^\"]*\"/\"architectury\": \">=$ARCH_VERSION\"/" fabric/src/main/resources/fabric.mod.json
              echo "Updated fabric.mod.json architectury dependency to >=$ARCH_VERSION"
            fi
          fi
          
          # Update NeoForge neoforge.mods.toml
          if [ -f "neoforge/src/main/resources/META-INF/neoforge.mods.toml" ]; then
            echo "Updating NeoForge neoforge.mods.toml dependencies..."
            
            sed -i "/modId = \"minecraft\"/,/versionRange =/ s/versionRange = \"\[.*,)\"/versionRange = \"[${{ matrix.minecraft_version }},)\"/" neoforge/src/main/resources/META-INF/neoforge.mods.toml
            echo "Updated neoforge.mods.toml minecraft dependency to [${{ matrix.minecraft_version }},)"
            
            if [ -n "$ARCH_VERSION" ]; then
              sed -i "/modId = \"architectury\"/,/versionRange =/ s/versionRange = \"\[.*,)\"/versionRange = \"[$ARCH_VERSION,)\"/" neoforge/src/main/resources/META-INF/neoforge.mods.toml
              echo "Updated neoforge.mods.toml architectury dependency to [$ARCH_VERSION,)"
            fi
            
            if [ -n "$NEOFORGE_VERSION" ]; then
              NEOFORGE_MAJOR=$(echo "$NEOFORGE_VERSION" | cut -d'.' -f1)
              sed -i "/modId = \"neoforge\"/,/versionRange =/ s/versionRange = \"\[.*,)\"/versionRange = \"[$NEOFORGE_MAJOR,)\"/" neoforge/src/main/resources/META-INF/neoforge.mods.toml
              echo "Updated neoforge.mods.toml neoforge dependency to [$NEOFORGE_MAJOR,)"
            fi
          fi
          
      - name: Commit and push changes
        run: |
          BRANCH_NAME="auto/mc-${{ matrix.minecraft_version }}"
          
          git add -A
          
          if git diff --staged --quiet; then
            echo "No changes to commit for $BRANCH_NAME"
          else
            git commit -m "Auto-port to MC ${{ matrix.minecraft_version }}

            - Updated gradle.properties with MC ${{ matrix.minecraft_version }} versions
            - Updated fabric.mod.json minecraft and architectury dependencies
            - Updated neoforge.mods.toml minecraft, architectury, and neoforge dependencies
            - Auto-generated from master branch
            
            🤖 Generated by GitHub Actions"
            
            git push origin $BRANCH_NAME
            echo "Pushed changes to $BRANCH_NAME"
          fi
```

#### 5.2: Build Matrix Workflow

**`.github/workflows/build-matrix.yml`**
```yaml
name: Build Matrix Test

on:
  push:
    branches: [master, 'auto/mc-*', 'auto/**']
  pull_request:
    branches: [master]
  workflow_dispatch:

jobs:
  build:
    runs-on: ubuntu-latest
    strategy:
      fail-fast: false
      matrix:
        include:
          - name: "MC 1.21.5"
            branch: "master"
          - name: "MC 1.21.4"
            branch: "auto/mc-1.21.4"
          - name: "MC 1.21.3"
            branch: "auto/mc-1.21.3"
    
    name: ${{ matrix.name }}
    
    steps:
      - name: Checkout repository
        uses: actions/checkout@v4
        with:
          fetch-depth: 0
          
      - name: Checkout target branch
        run: |
          if [ "${{ matrix.branch }}" == "master" ]; then
            echo "Already on master branch"
          else
            if git ls-remote --heads origin ${{ matrix.branch }} | grep -q ${{ matrix.branch }}; then
              git checkout ${{ matrix.branch }}
              echo "Checked out existing branch: ${{ matrix.branch }}"
            else
              echo "Branch ${{ matrix.branch }} does not exist, skipping this build"
              exit 0
            fi
          fi
        
      - name: Setup Java
        uses: actions/setup-java@v4
        with:
          distribution: 'temurin'
          java-version: '21'
          
      - name: Setup Gradle
        uses: gradle/gradle-build-action@v2
        
      - name: Make gradlew executable
        run: chmod +x ./gradlew
        
      - name: Build with Gradle
        run: |
          echo "Building branch: ${{ matrix.branch }}"
          ./gradlew build --no-daemon --stacktrace
          
      - name: Upload build artifacts
        uses: actions/upload-artifact@v4
        if: success()
        with:
          name: build-${{ matrix.name }}-${{ github.sha }}
          path: |
            fabric/build/libs/*.jar
            neoforge/build/libs/*.jar
          retention-days: 7
```

### 6. Enable GitHub Actions Permissions

1. Go to your GitHub repo → **Settings**
2. **Actions** → **General** (left sidebar)
3. **Workflow permissions** → Select **"Read and write permissions"**
4. Check **"Allow GitHub Actions to create and approve pull requests"**
5. Click **Save**

### 7. Test the System

#### 7.1: Initial Test
```bash
git add .
git commit -m "Add multi-version automation system"
git push origin master
```

Check **Actions** tab - you should see:
- ✅ **Auto-Port to MC Versions** creating auto branches
- ✅ **Build Matrix Test** testing all versions

#### 7.2: Version Parameter Test
```bash
# Test locally first
./gradlew properties | grep minecraft_version  # Should show main version
./gradlew properties -PmcVersion=1214 | grep minecraft_version  # Should show 1.21.4
```

#### 7.3: Branch Test
```bash
git fetch origin
git checkout auto/mc-1.21.4
# IntelliJ should sync and show 1.21.4 libraries
```

### 8. Customize for Your Mod

#### 8.1: Update Version Matrix
Edit `.github/workflows/auto-port.yml`:
```yaml
strategy:
  matrix:
    version: ['1214', '1213', '1212']  # Add/remove versions
    include:
      - version: '1214'
        minecraft_version: '1.21.4'
      - version: '1213' 
        minecraft_version: '1.21.3'
      - version: '1212'
        minecraft_version: '1.21.2'  # Add new versions
```

#### 8.2: Add More Property Files
Create more version files as needed:
```bash
# Example: Add 1.21.2 support
cat > gradle/versions/mc-1212.properties << 'EOF'
minecraft_version=1.21.2
# ... other versions
EOF
```

#### 8.3: Mod-Specific Metadata
If your mod has additional metadata files, update the auto-port workflow:
```yaml
# Add to the "Update mod metadata files" step
- name: Update custom metadata files
  run: |
    # Update your mod's specific config files
    # Example: Update custom version files
    if [ -f "src/main/resources/version.json" ]; then
      sed -i 's/"minecraft": "[^"]*"/"minecraft": "${{ matrix.minecraft_version }}"/' src/main/resources/version.json
    fi
```

## 🎯 Next Steps

- [Usage Guide](USAGE.md) - Learn daily workflows
- [Troubleshooting](TROUBLESHOOTING.md) - Fix common issues
- [Architecture Guide](ARCHITECTURE.md) - Understand the system

## 📝 Notes

- **Gradle Properties**: Always research correct version combinations
- **GitHub Actions**: Start simple, add complexity gradually  
- **Testing**: Local testing with `-PmcVersion` before automation
- **Documentation**: Keep version compatibility notes updated