/*
 * Copyright 2015-2017 DiffPlug
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
package com.diffplug.spotless.java;

import java.util.Objects;

import com.diffplug.spotless.FormatterStep;
import com.diffplug.spotless.Provisioner;

/** Uses google-java-format, but only to remove unused imports. */
public class RemoveUnusedImportsStep {
	// prevent direct instantiation
	private RemoveUnusedImportsStep() {}

	static final String NAME = "removeUnusedImports";

	public static FormatterStep create(Provisioner provisioner) {
		Objects.requireNonNull(provisioner, "provisioner");
		return FormatterStep.createLazy(NAME,
				() -> new GoogleJavaFormatStep.State(NAME, GoogleJavaFormatStep.defaultVersion(), provisioner),
				GoogleJavaFormatStep.State::createRemoveUnusedImportsOnly);
	}
}
