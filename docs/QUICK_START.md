# Quick Start Guide

This guide shows how to use the existing multi-version system in this mod.

## 🚀 Current Setup

This mod supports **automatic multi-version development** for:
- **MC 1.21.5** (master branch) - Main development
- **MC 1.21.4** (auto/mc-1.21.4 branch) - Auto-generated
- **MC 1.21.3** (auto/mc-1.21.3 branch) - Auto-generated

## 📦 Working with Branches

### Master Branch (1.21.5 Development)
```bash
git checkout master
# Develop new features here
# IntelliJ shows 1.21.5 MC libraries
```

### Auto Branches (Compatibility Testing)
```bash
# Switch to 1.21.4 version
git checkout auto/mc-1.21.4
# IntelliJ syncs gradle → Shows 1.21.4 MC libraries
# Fix compatibility issues here

# Switch to 1.21.3 version  
git checkout auto/mc-1.21.3
# IntelliJ syncs gradle → Shows 1.21.3 MC libraries
# Fix compatibility issues here
```

## 🔧 Local Version Testing

You can test different versions locally without switching branches:

```bash
# Default 1.21.5
./gradlew build

# Test with 1.21.4 versions
./gradlew build -PmcVersion=1214

# Test with 1.21.3 versions
./gradlew build -PmcVersion=1213
```

## ⚙️ IntelliJ Integration

### Run Configurations
Each branch has its own properly configured environment:
- **master**: Fabric/NeoForge run configs use 1.21.5
- **auto/mc-1.21.4**: Fabric/NeoForge run configs use 1.21.4
- **auto/mc-1.21.3**: Fabric/NeoForge run configs use 1.21.3

### Library Decompilation
- Middle-click on Minecraft classes shows **correct version** for current branch
- BlockEntityRenderer in 1.21.5 shows `render(..., Vec3)` signature
- BlockEntityRenderer in 1.21.4/1.21.3 shows `render(...)` signature (no Vec3)

## 🔄 Development Workflow

### 1. Develop Features (Master)
```bash
git checkout master
# Make changes to your mod
git add .
git commit -m "Add new feature"
git push origin master
```

### 2. Auto-Port Triggers
When you push to master, GitHub Actions automatically:
- Updates `auto/mc-1.21.4` and `auto/mc-1.21.3` branches
- Updates all dependency versions
- Updates both Fabric and NeoForge metadata files

### 3. Fix Compatibility Issues
```bash
# Check if auto-port succeeded
git fetch origin
git checkout auto/mc-1.21.4

# Fix any compilation errors
# Usually interface signature changes like BlockEntityRenderer
git add .
git commit -m "Fix MC 1.21.4 compatibility"
git push origin auto/mc-1.21.4
```

### 4. Build Matrix Testing
Pushing to any branch triggers build testing:
- ✅ **master** builds with 1.21.5
- ✅ **auto/mc-1.21.4** builds with 1.21.4 (after fixes)
- ✅ **auto/mc-1.21.3** builds with 1.21.3 (after fixes)

## 📋 Common Tasks

### Add New MC Version Support
1. Create version property file: `gradle/versions/mc-1212.properties`
2. Update `.github/workflows/auto-port.yml` matrix
3. Push to master to trigger auto-branch creation

### Cherry-Pick Fixes Between Versions
```bash
# Fix applied to mc-1.21.4, want to apply to mc-1.21.3
git checkout auto/mc-1.21.3
git cherry-pick <commit-hash>
git push origin auto/mc-1.21.3
```

### Check Build Status
- Go to **Actions** tab in GitHub
- **Auto-Port to MC Versions** - Creates/updates auto branches
- **Build Matrix Test** - Tests all versions compile

## 🎯 Key Benefits

- **✅ Single codebase** - Develop features once on master
- **✅ Automatic porting** - GitHub Actions handle version updates
- **✅ IntelliJ integration** - Correct MC libraries per branch
- **✅ Build verification** - Automated testing ensures compatibility
- **✅ Easy fixes** - Cherry-pick identical fixes between versions

## 📚 Next Steps

- [Complete Usage Guide](USAGE.md) - Detailed daily workflows
- [Troubleshooting Guide](TROUBLESHOOTING.md) - Fix common issues
- [System Architecture](ARCHITECTURE.md) - How everything works