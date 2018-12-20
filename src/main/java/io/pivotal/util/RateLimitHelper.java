/*
 * Copyright 2002-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.pivotal.util;

import java.time.Duration;

import reactor.core.publisher.Mono;

/**
 * Rate limiter to ensure more efficient adherence to GitHub's limit of 1 update
 * per second. Simply sleeping before a call tends to stretch the overall time
 * as there is some additional processing time between calls.
 *
 * @author Rossen Stoyanchev
 */
public class RateLimitHelper {

	private static final Mono<Object> parentMono = Mono.just("foo");

	private final Duration timeBetweenCalls = Duration.ofSeconds(1);

	private Mono<Object> nextPermit;


	public void obtainPermitToCall() {
		if (nextPermit != null) {
			nextPermit.block();
		}
		resetNextPermit();
	}

	private void resetNextPermit() {
		nextPermit = parentMono.delayElement(timeBetweenCalls);
	}

}
