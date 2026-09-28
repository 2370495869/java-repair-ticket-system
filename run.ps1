Set-Location $PSScriptRoot
& "$PSScriptRoot\mvnw.cmd" -B -ntp verify
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& java -jar "$PSScriptRoot\target\java-repair-ticket-system.jar" --spring.profiles.active=local @args
exit $LASTEXITCODE
