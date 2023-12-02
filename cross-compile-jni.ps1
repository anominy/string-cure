<#
 # Copyright 2023 anominy
 #
 # Licensed under the Apache License, Version 2.0 (the "License");
 # you may not use this file except in compliance with the License.
 # You may obtain a copy of the License at
 #
 #    http://www.apache.org/licenses/LICENSE-2.0
 #
 # Unless required by applicable law or agreed to in writing, software
 # distributed under the License is distributed on an "AS IS" BASIS,
 # WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 # See the License for the specific language governing permissions and
 # limitations under the License.
 #>

$JNI_MODULE_PATH = "${PSScriptRoot}/jni"
$JAVA_MODULE_PATH = "${PSScriptRoot}/src/main"

$JAVA_SOURCE_DIRECTORY_PATH = "${JAVA_MODULE_PATH}/java"
$JAVA_RESOURCE_DIRECTORY_PATH = "${JAVA_MODULE_PATH}/resources"

$SOURCE_PATH = "${JNI_MODULE_PATH}/src"
$INCLUDE_PATH = "${JNI_MODULE_PATH}/include"
$LIB_PATH = "${JNI_MODULE_PATH}/lib"
$TARGET_LIST_PATH = "${JNI_MODULE_PATH}/target-list.txt"

$JAVA_INPUT_FILE_PACKAGE = "io/github/anominy/stringcure"
$JAVA_INPUT_FILE_PATH = "${JAVA_SOURCE_DIRECTORY_PATH}/${JAVA_INPUT_FILE_PACKAGE}"

$JAVA_INPUT_FILE_PATH_ARRAY = @(
	"${JAVA_INPUT_FILE_PATH}/UStringCure.java"
	"${JAVA_INPUT_FILE_PATH}/UPath.java"
	"${JAVA_INPUT_FILE_PATH}/USystem.java"
)

$JAVA_REMOVE_FILE_PATH_ARRAY `
		= @($JAVA_INPUT_FILE_PATH_ARRAY -Replace "\.java$", ".class")

$JNI_INCLUDE_PATH = "${INCLUDE_PATH}/jdk8"
$JNI_INPUT_FILE_PREFIX = $JAVA_INPUT_FILE_PACKAGE.Replace("/", "_") + "_"
$JNI_INPUT_FILE_NAME = "UStringCure"

$JNI_OUTPUT_DIRECTORY_PATH = "${JAVA_RESOURCE_DIRECTORY_PATH}/native"
$JNI_OUTPUT_FILE_NAME = "strcure"

$DECANCER_INCLUDE_PATH = "${INCLUDE_PATH}/decancer"

javac -h "$INCLUDE_PATH" $JAVA_INPUT_FILE_PATH_ARRAY
Remove-Item -LiteralPath $JAVA_REMOVE_FILE_PATH_ARRAY

foreach ($targetLine in Get-Content -LiteralPath "$TARGET_LIST_PATH") {
	if ([string]::IsNullOrWhiteSpace($targetLine) `
			-Or $targetLine.StartsWith("---")) {
		continue
	}

	$targetLineSplit = $targetLine.Split(" ")

	$orderNumber = $targetLineSplit[0]
	$targetName = $targetLineSplit[1]

	if ([string]::IsNullOrWhiteSpace($orderNumber) `
			-Or [string]::IsNullOrWhiteSpace($targetName)) {
		continue
	}

	$platformName = $targetLineSplit[2]
	$outputSuffix0 = $targetLineSplit[3]
	$outputSuffix1 = $targetLineSplit[4]
	$dependencyNameArray = $targetLineSplit[5..$targetLineSplit.Length]

	$orderedTargetName = "${orderNumber}-${targetName}"
	$outputDirectoryPath = "${JNI_OUTPUT_DIRECTORY_PATH}/${orderedTargetName}"

	New-Item -Path $outputDirectoryPath `
		-ItemType Directory `
		-ErrorAction Ignore

	zig cc -target $targetName -c -O3 -fPIC `
		"${SOURCE_PATH}/${JNI_INPUT_FILE_PREFIX}${JNI_INPUT_FILE_NAME}.c" `
		-o "${outputDirectoryPath}/${JNI_OUTPUT_FILE_NAME}${outputSuffix0}" `
		-I"$INCLUDE_PATH" `
		-I"$JNI_INCLUDE_PATH" `
		-I"${JNI_INCLUDE_PATH}/${platformName}" `
		-I"$DECANCER_INCLUDE_PATH" `
		-L"${LIB_PATH}/${targetName}"

	zig cc -target $targetName -shared `
		-o "${outputDirectoryPath}/${JNI_OUTPUT_FILE_NAME}${outputSuffix1}" `
		"${outputDirectoryPath}/${JNI_OUTPUT_FILE_NAME}${outputSuffix0}" `
		-L"${LIB_PATH}/${targetName}" `
		-ldecancer -lc

	$jniRemoveFilePathArray = @(
		"${outputDirectoryPath}/${JNI_OUTPUT_FILE_NAME}.lib"
		"${outputDirectoryPath}/${JNI_OUTPUT_FILE_NAME}.pdb"
		"${outputDirectoryPath}/${JNI_OUTPUT_FILE_NAME}.o"
	)

	Remove-Item -LiteralPath $jniRemoveFilePathArray `
		-ErrorAction Ignore

	$outputDependenciesDirectoryPath = "${outputDirectoryPath}/dependencies"

	New-Item -Path $outputDependenciesDirectoryPath `
		-ItemType Directory `
		-ErrorAction Ignore

	foreach ($dependencyName in $dependencyNameArray) {
		Copy-Item -LiteralPath "${LIB_PATH}/${targetName}/${dependencyName}" `
			-Destination "${outputDependenciesDirectoryPath}" `
			-Force
	}
}
