/*
 * Copyright 2023 anominy
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include "io_github_anominy_stringcure_UStringCure.h"

#include <string.h>
#include <stddef.h>
#include <stdlib.h>
#include <stdint.h>

#include "jni.h"
#include "decancer.h"

JNIEXPORT jbyteArray JNICALL Java_io_github_anominy_stringcure_UStringCure_transform(JNIEnv *env, jclass clazz, jbyteArray input) {
	jsize length = (*env)->GetArrayLength(env, input);
	char *buff = malloc(length + 1);
	jbyte *bytes = (*env)->GetByteArrayElements(env, input, NULL);
	memcpy(buff, bytes, length);
	buff[length] = '\0';

	(*env)->ReleaseByteArrayElements(env, input, bytes, JNI_ABORT);

	size_t size = (size_t)length;
	void *cured_string = decancer_cure((uint8_t *)buff, size);
	const uint8_t *cured_raw_string = decancer_raw(cured_string, &size);

	jbyteArray output = (*env)->NewByteArray(env, (jsize)size);
	(*env)->SetByteArrayRegion(env, output, 0, size, (jbyte *)cured_raw_string);

	decancer_free(cured_string);
	free((void *)buff);

	return output;
}
