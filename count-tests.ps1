Get-ChildItem "C:\Users\user\AndroidStudioProjects\StudentCodex\app\build\test-results\testDebugUnitTest" -Filter *.xml | ForEach-Object {
  $x = [xml](Get-Content $_.FullName)
  $tests = 0; $failures = 0; $name = ""
  if ($x.testsuites) { $tests = [int]$x.testsuites.tests; $failures = [int]$x.testsuites.failures; $name = $x.testsuites.name }
  elseif ($x.testsuite) { $tests = [int]$x.testsuite.tests; $failures = [int]$x.testsuite.failures; $name = $x.testsuite.name }
  Write-Output "$name tests=$tests failures=$failures"
}