$ErrorActionPreference = "Stop"
$version = "8.9"
$root = Split-Path $PSScriptRoot -Parent
$cacheRoot = if ($env:GRADLE_USER_HOME) { $env:GRADLE_USER_HOME } else { Join-Path $env:USERPROFILE ".gradle" }
$cache = Join-Path $cacheRoot "void-bootstrap\$version"
$bin = Join-Path $cache "gradle-$version\bin\gradle.bat"
if (!(Test-Path $bin)) {
 New-Item -ItemType Directory -Force $cache | Out-Null
 $archive = Join-Path $cache "distribution.zip"
 $url = "https://services.gradle.org/distributions/gradle-$version-bin.zip"
 Invoke-WebRequest -Uri $url -OutFile $archive
 $expected = (Invoke-WebRequest -Uri "$url.sha256" -UseBasicParsing).Content.Trim()
 $actual = (Get-FileHash $archive -Algorithm SHA256).Hash
 if ($actual -ne $expected) { Remove-Item $archive; throw "Gradle checksum mismatch" }
 Expand-Archive -Force $archive $cache
 Remove-Item $archive
}
& $bin -p $root @args
exit $LASTEXITCODE
