/*
 * Copyright 2016-2021 DiffPlug
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.diffplug.gradle.spotless;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import javax.inject.Inject;

import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileSystemOperations;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.TaskAction;
import org.gradle.work.ChangeType;
import org.gradle.work.FileChange;
import org.gradle.work.InputChanges;

import com.diffplug.common.base.StringPrinter;
import com.diffplug.spotless.Formatter;
import com.diffplug.spotless.PaddedCell;

@CacheableTask
public abstract class SpotlessTaskImpl extends SpotlessTask {
	@Internal
	abstract DirectoryProperty getProjectDir();

	void init(Provider<SpotlessTaskService> service) {
		getTaskService().set(service);
		getProjectDir().set(getProject().getProjectDir());
	}

	@Inject
	protected abstract FileSystemOperations getFs();

	@TaskAction
	public void performAction(InputChanges inputs) throws Exception {
		getTaskService().get().registerSourceAlreadyRan(this);
		if (target == null) {
			throw new GradleException("You must specify 'Iterable<File> target'");
		}

		if (!inputs.isIncremental()) {
			getLogger().info("Not incremental: removing prior outputs");
			getFs().delete(d -> d.delete(outputDirectory));
			Files.createDirectories(outputDirectory.toPath());
		}

		try (Formatter formatter = buildFormatter()) {
			for (FileChange fileChange : inputs.getFileChanges(target)) {
				File input = fileChange.getFile();
				if (fileChange.getChangeType() == ChangeType.REMOVED) {
					deletePreviousResult(input);
				} else {
					if (input.isFile()) {
						processInputFile(formatter, input);
					}
				}
			}
		}
	}

	private void processInputFile(Formatter formatter, File input) throws IOException {
		File output = getOutputFile(input);
		getLogger().debug("Applying format to " + input + " and writing to " + output);
		PaddedCell.DirtyState dirtyState;
		if (getRatchet() != null && getRatchet().isClean(getProjectDir().get().getAsFile(), getRootTreeSha(), input)) {
			dirtyState = PaddedCell.isClean();
		} else {
			dirtyState = PaddedCell.calculateDirtyState(formatter, input);
		}
		if (dirtyState.isClean()) {
			// Remove previous output if it exists
			Files.deleteIfExists(output.toPath());
		} else if (dirtyState.didNotConverge()) {
			getLogger().warn("Skipping '" + input + "' because it does not converge.  Run {@code spotlessDiagnose} to understand why");
		} else {
			Path parentDir = output.toPath().getParent();
			if (parentDir == null) {
				throw new IllegalStateException("Every file has a parent folder.");
			}
			Files.createDirectories(parentDir);
			// Need to copy the original file to the tmp location just to remember the file attributes
			Files.copy(input.toPath(), output.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
			dirtyState.writeCanonicalTo(output);
		}
	}

	private void deletePreviousResult(File input) throws IOException {
		File output = getOutputFile(input);
		if (output.isDirectory()) {
			getFs().delete(d -> d.delete(output));
		} else {
			Files.deleteIfExists(output.toPath());
		}
	}

	private File getOutputFile(File input) {
		File projectDir = getProjectDir().get().getAsFile();
		String outputFileName = FormatExtension.relativize(projectDir, input);
		if (outputFileName == null) {
			throw new IllegalArgumentException(StringPrinter.buildString(printer -> {
				printer.println("Spotless error! All target files must be within the project dir.");
				printer.println("  project dir: " + projectDir.getAbsolutePath());
				printer.println("       target: " + input.getAbsolutePath());
			}));
		}
		return new File(outputDirectory, outputFileName);
	}
}
