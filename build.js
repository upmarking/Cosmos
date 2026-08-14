const { spawnSync } = require('child_process');
const path = require('path');
const fs = require('fs');

function runCommand(command, args, cwd) {
  console.log(`\n======================================================`);
  console.log(`Running: ${command} ${args.join(' ')}`);
  console.log(`Directory: ${cwd}`);
  console.log(`======================================================\n`);

  const result = spawnSync(command, args, {
    cwd: cwd,
    stdio: 'inherit',
    shell: true
  });

  if (result.status !== 0) {
    console.error(`\n[ERROR] Command failed with exit code ${result.status}`);
    if (result.error) {
      console.error(result.error);
    }
    process.exit(result.status || 1);
  }
}

function main() {
  const rootDir = __dirname;

  // 1. Install functions dependencies
  const functionsDir = path.join(rootDir, 'functions');
  if (fs.existsSync(functionsDir)) {
    console.log('Building Cloud Functions...');
    runCommand('npm', ['install'], functionsDir);
  }

  // 2. Build Android App
  const isWindows = process.platform === 'win32';
  const gradleWrapper = isWindows ? 'gradlew.bat' : './gradlew';
  const gradleCmd = path.join(rootDir, gradleWrapper);

  if (fs.existsSync(gradleCmd) || fs.existsSync(gradleCmd + '.bat')) {
    console.log('Building Android Application...');
    // We run assembleDebug to build the debug APK
    runCommand(gradleWrapper, ['assembleDebug'], rootDir);
  } else {
    console.log('Gradle wrapper not found, skipping Android build.');
  }

  console.log('\n======================================================');
  console.log('SUCCESS: All components built successfully!');
  console.log('======================================================\n');
}

main();
