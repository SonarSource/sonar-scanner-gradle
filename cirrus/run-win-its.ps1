# To avoid noise in the output
$ProgressPreference = 'SilentlyContinue'

# Install Maven
$mvnVersion = "3.8.9"
$mvnInstalls = "${env:CIRRUS_WORKING_DIR}\mvn"
$destinationPath = "$mvnInstalls\$mvnVersion"
if (Test-Path -Path  $destinationPath)
{
    Write-Host $destinationPath" already exists"
    return
}

$major = $mvnVersion.Split('.')[0]
Write-Host "Installing Maven ${mvnVersion}"
$zipFile = "apache-maven-${mvnVersion}-bin.zip"

$binaryUrl = "https://downloads.apache.org/maven/maven-${major}/${mvnVersion}/binaries/${zipFile}"

# Download the file
$zipPath = "${env:CIRRUS_WORKING_DIR}\${zipFile}"
Write-Host "Download from '$binaryUrl' into '$zipPath'"
Invoke-WebRequest -Uri $binaryUrl -OutFile $zipPath
Expand-Archive -Path $zipPath -DestinationPath $mvnInstalls

$mvnInstall = Get-ChildItem -Path $mvnInstalls -Directory |
        Where-Object Name -EQ "apache-maven-${mvnVersion}" |
        Select-Object -ExpandProperty FullName
if ($mvnInstall)
{
    Rename-Item -Path $mvnInstall -NewName $mvnVersion
}
Remove-Item -Path $zipPath
& "$destinationPath\bin\mvn.cmd" --version

# Ensure the BUILD_NUMBER environment variable is set
if (-not $env:BUILD_NUMBER)
{
    Write-Error "The 'BUILD_NUMBER' environment variable is not set."
    # Stop the script if the variable is missing
    exit 1
}

$filePath = "gradle.properties"

# 1. Read the current version from the file
# This finds the line starting with 'version=', splits it at '=', and takes the second part.
$currentVersion = ((Get-Content -Path $filePath | Where-Object { $_ -match '^version=' }) -split '=')[1].Trim()

# 2. Remove any suffix like "-SNAPSHOT" to get the base release version
$releaseVersion = $currentVersion -replace '-.*'

# 3. Count the number of dots in the release version
$numberDots = ($releaseVersion.Split('.')).Count - 1

# 4. Construct the new version string
if ($numberDots -lt 2)
{
    # If version is "1.2", new version becomes "1.2.0.123"
    $newVersion = "$releaseVersion.0.$env:BUILD_NUMBER"
}
else
{
    # If version is "1.2.3", new version becomes "1.2.3.123"
    $newVersion = "$releaseVersion.$env:BUILD_NUMBER"
}

# 5. Create a backup and replace the version in the file
Copy-Item -Path $filePath -Destination "$filePath.bak" -Force

# We use [regex]::Escape to ensure characters in the version (like '.') are treated literally
(Get-Content -Path $filePath) -replace [regex]::Escape($currentVersion), $newVersion | Set-Content -Path $filePath

Write-Host "Version updated in '$filePath' from '$currentVersion' to '$newVersion'"


& "$destinationPath\bin\mvn.cmd" -f property-dump-plugin/pom.xml --batch-mode install
cd integrationTests
& "$destinationPath\bin\mvn.cmd" org.codehaus.mojo:versions-maven-plugin:2.7:set -DnewVersion="${newVersion}" -DgenerateBackupPoms=false -B -e

& "$destinationPath\bin\mvn.cmd" --errors --batch-mode clean verify -D"gradle.version"="9.0.0" -D"androidGradle.version"="NOT_AVAILABLE"
if ($LASTEXITCODE -ne 0)
{
    exit 1
}