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

import java.io.IOError;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.*;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@SuppressWarnings("CallToPrintStackTrace")
final class USystem {
	private static final String JAVA_IO_TEMP_DIRECTORY_PROPERTY = UPath.normalize(System.getProperty("java.io.tmpdir"));
	private static final String LIBRARY_TEMP_DIRECTORY_PREFIX = "[" + USystem.class.getName() + "]";
	private static final String LIBRARY_DEPENDENCY_DIRECTORY_NAME = "dependencies";

	static {
		boolean isDeleted = deleteTempDirectories();
		if (!isDeleted) {
			String ref = "[\"" + JAVA_IO_TEMP_DIRECTORY_PROPERTY
					+ "\" & \"" + LIBRARY_TEMP_DIRECTORY_PREFIX + "\"]";

			new IOException("Previous library temporary directories "
					+ ref + " couldn't be deleted").printStackTrace();
		}
	}

	public static boolean loadNativeLibrary(String libraryPath, String libraryName) {
		if (libraryName == null || libraryName.isEmpty()) {
			throw new IllegalArgumentException("Library name mustn't be <null/empty>");
		}
		if (libraryName.contains("/") || libraryName.contains("\\")) {
			throw new IllegalArgumentException("Library name mustn't contain '/' & '\\' symbols");
		}

		libraryPath = UPath.normalize(libraryPath);
		if (libraryPath == null) {
			libraryPath = "";
		}

		URI libraryPathUri;
		try {
			URL libraryPathUrl = Thread.currentThread()
					.getContextClassLoader()
					.getResource(libraryPath);

			if (libraryPathUrl == null) {
				throw new IOException("Library path couldn't be found");
			}

			libraryPathUri = libraryPathUrl.toURI();
		} catch (IOException | URISyntaxException e) {
			e.printStackTrace();

			return false;
		}

		FileSystem fileSystem = null;
		try {
			fileSystem = FileSystems.newFileSystem(libraryPathUri, Collections.emptyMap());
		} catch (ProviderNotFoundException
				 | IOException
				 | SecurityException e) {
			e.printStackTrace();
		} catch (IllegalArgumentException
				 | FileSystemAlreadyExistsException
				 | UnsupportedOperationException ignored) {
		}

		try {
			return loadNativeLibraryFromUri(libraryPathUri, libraryName);
		} finally {
			if (fileSystem != null) {
				try {
					fileSystem.close();
				} catch (IOException e) {
					e.printStackTrace();
				} catch (UnsupportedOperationException ignored) {
				}
			}
		}
	}

	private static boolean loadNativeLibraryFromUri(URI libraryPathUri, String libraryName) {
		Path libraryNioPath;
		try {
			libraryNioPath = Paths.get(libraryPathUri);
		} catch (IllegalArgumentException
				 | FileSystemNotFoundException
				 | SecurityException e) {
			new IOException("Library directory couldn't be found", e)
					.printStackTrace();

			return false;
		}

		List<Path> libraryDirectoryNioPathList;
		try (Stream<Path> stream = Files.list(libraryNioPath)) {
			libraryDirectoryNioPathList = stream.sorted(UPath.COMPARATOR)
					.collect(Collectors.toList());

			if (libraryDirectoryNioPathList.isEmpty()) {
				throw new IOException("Library directory couldn't be found");
			}
		} catch (IOException | SecurityException e) {
			e.printStackTrace();

			return false;
		}

		Path libraryTempDirectoryNioPath;
		try {
			libraryTempDirectoryNioPath = Files.createTempDirectory(
					LIBRARY_TEMP_DIRECTORY_PREFIX + " ");
		} catch (IllegalArgumentException
				 | UnsupportedOperationException
				 | IOException
				 | SecurityException e) {
			new IOException("Library temporary directory couldn't be created", e)
					.printStackTrace();

			return false;
		}

		for (Path libraryDirectoryNioPath : libraryDirectoryNioPathList) {
			try (Stream<Path> stream = Files.walk(libraryDirectoryNioPath)) {
				String libraryTempDirectoryPath = libraryTempDirectoryNioPath.toAbsolutePath()
						.toString();

				String libraryDirectoryPath = libraryDirectoryNioPath.toAbsolutePath()
						.toString();

				stream.forEach(it -> {
					try {
						String sourceRelativePath = it.toAbsolutePath()
								.toString()
								.substring(libraryDirectoryPath.length());

						Path destinationNioPath = Paths.get(
								libraryTempDirectoryPath, sourceRelativePath);

						Files.copy(it, destinationNioPath, StandardCopyOption.REPLACE_EXISTING);
					} catch (DirectoryNotEmptyException ignored) {
					} catch (InvalidPathException
							 | IOException
							 | UnsupportedOperationException
							 | SecurityException e) {
						new IOException("Library file couldn't be copied", e)
								.printStackTrace();
					}
				});
			} catch (IOError | IOException | SecurityException e) {
				new IOException("Library files couldn't be copied", e)
						.printStackTrace();

				continue;
			}

			Path libraryTempDependenciesDirectoryNioPath
					= libraryTempDirectoryNioPath.resolve(LIBRARY_DEPENDENCY_DIRECTORY_NAME);

			boolean isLoaded = loadNativeLibrariesFromDirectory(libraryTempDependenciesDirectoryNioPath);
			if (!isLoaded) {
				continue;
			}

			Path libraryTempFileNioPath;
			try {
				libraryTempFileNioPath = libraryTempDirectoryNioPath.resolve(
						System.mapLibraryName(libraryName));
			} catch (NullPointerException | InvalidPathException e) {
				new IOException("Library temporary path couldn't be resolved", e)
						.printStackTrace();

				continue;
			}

			try {
				System.load(libraryTempFileNioPath.toAbsolutePath()
						.toString());

				return true;
			} catch (IOError | SecurityException e) {
				new IOException("Library couldn't be loaded", e)
						.printStackTrace();
			} catch (UnsatisfiedLinkError ignored) {
			} finally {
				UPath.deleteDirectory(libraryTempDirectoryNioPath);
			}
		}

		return false;
	}

	private static boolean loadNativeLibrariesFromDirectory(Path path) {
		if (path == null) {
			return true;
		}

		try (Stream<Path> stream = Files.walk(path)) {
			return stream.filter(UPath::isFile)
					.map(it -> {
						try {
							return it.toAbsolutePath()
									.toString();
						} catch (IOError | SecurityException e) {
							e.printStackTrace();
						}

						return null;
					})
					.filter(Objects::nonNull)
					.map(it -> {
						try {
							System.load(it);
							return true;
						} catch (SecurityException e) {
							e.printStackTrace();
						} catch (UnsatisfiedLinkError ignored) {
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


	private static boolean deleteTempDirectories() {
		if (JAVA_IO_TEMP_DIRECTORY_PROPERTY == null
				|| JAVA_IO_TEMP_DIRECTORY_PROPERTY.isEmpty()) {
			return false;
		}

		try (Stream<Path> stream = Files.list(Paths.get(JAVA_IO_TEMP_DIRECTORY_PROPERTY))) {
			stream.filter(it -> {
				try {
					return it.getName(it.getNameCount() - 1)
							.toString()
							.startsWith(LIBRARY_TEMP_DIRECTORY_PREFIX);
				} catch (IllegalArgumentException ignored) {
				}

				return false;
			}).forEach(UPath::deleteDirectory);

			return true;
		} catch (NotDirectoryException | InvalidPathException ignored) {
		} catch (IOException | SecurityException e) {
			e.printStackTrace();
		}

		return false;
	}

	private USystem() {
		throw new UnsupportedOperationException();
	}
}
