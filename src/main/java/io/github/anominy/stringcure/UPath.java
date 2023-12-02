/*
 * Copyright 2024 anominy
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

package io.github.anominy.stringcure;

import java.io.IOException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@SuppressWarnings("CallToPrintStackTrace")
final class UPath {
	public static final Comparator<Path> COMPARATOR = Comparator.nullsLast(UPath::compare);

	private static final Comparator<Integer> ORDER_COMPARATOR = Comparator.nullsLast(Integer::compare);
	private static final String ORDER_NUMBER_REGEX = "^(-?\\d{1,3})(?:[-_\\s].+)?$";
	private static final Pattern ORDER_NUMBER_PATTERN = Pattern.compile(ORDER_NUMBER_REGEX);

	public static String normalize(String path) {
		if (path == null || path.isEmpty()) {
			return path;
		}

		return path.trim()
				.replace("\\", "/")
				.replaceAll("/+", "/")
				.replaceAll("^/|/$", "");
	}

	public static int compare(Path path0, Path path1) {
		Integer order0 = getOrderNumber(path0);
		Integer order1 = getOrderNumber(path1);

		return ORDER_COMPARATOR.compare(order0, order1);
	}

	public static boolean isFile(Path path) {
		if (path == null) {
			return false;
		}

		try {
			return Files.isRegularFile(path);
		} catch (SecurityException e) {
			e.printStackTrace();
		}

		return false;
	}

	public static boolean isDirectory(Path path) {
		if (path == null) {
			return false;
		}

		try {
			return Files.isDirectory(path);
		} catch (SecurityException e) {
			e.printStackTrace();
		}

		return false;
	}

	@SuppressWarnings("unused")
	public static boolean delete(Path path) {
		if (isFile(path)) {
			return deleteFile(path);
		}

		if (isDirectory(path)) {
			return deleteDirectory(path);
		}

		return false;
	}

	public static boolean deleteFile(Path path) {
		if (!isFile(path)) {
			return false;
		}

		try {
			return Files.deleteIfExists(path);
		} catch (DirectoryNotEmptyException ignored) {
		} catch (IOException | SecurityException e) {
			e.printStackTrace();
		}

		return false;
	}

	public static boolean deleteDirectory(Path path) {
		if (!isDirectory(path)) {
			return false;
		}

		try (Stream<Path> stream = Files.walk(path)) {
			return stream.sorted(Comparator.reverseOrder())
					.map(it -> {
						try {
							return it.toFile();
						} catch (UnsupportedOperationException e) {
							e.printStackTrace();
						}

						return null;
					})
					.filter(Objects::nonNull)
					.map(it -> {
						try {
							return it.delete();
						} catch (SecurityException e) {
							e.printStackTrace();
						}

						return false;
					})
					.reduce(Boolean::logicalAnd)
					.orElse(true);
		} catch (IOException | SecurityException e) {
			e.printStackTrace();
		}

		return false;
	}

	private static Integer getOrderNumber(Path path) {
		if (path == null) {
			return null;
		}

		path = path.getFileName();
		if (path == null) {
			return null;
		}

		Matcher m = ORDER_NUMBER_PATTERN.matcher(path.toString());
		if (!m.matches()) {
			return null;
		}

		String order;
		try {
			order = m.group(1);
		} catch (IllegalStateException | IndexOutOfBoundsException ignored) {
			return null;
		}

		try {
			return Integer.parseInt(order);
		} catch (NumberFormatException ignored) {
		}

		return null;
	}

	private UPath() {
		throw new UnsupportedOperationException();
	}
}
