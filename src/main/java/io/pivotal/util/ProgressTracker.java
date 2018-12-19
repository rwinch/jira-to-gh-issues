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

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import org.springframework.util.Assert;

/**
 * @author Rossen Stoyanchev
 */
public class ProgressTracker {

	private final int totalIterationsCount;

	private final int iterationsPerDot;

	private final int dotsPerLine;

	private LocalDateTime startTime;

	private int iteration = 0;

	private boolean suppressTracking;


	public ProgressTracker(int totalIterationsCount, boolean isDebugLoggingEnabled) {
		this.totalIterationsCount = totalIterationsCount;
		this.iterationsPerDot = totalIterationsCount > 100 ? 10 : 1;
		this.dotsPerLine = totalIterationsCount > 100 ? 100 : 10;
		this.suppressTracking = isDebugLoggingEnabled;
	}

	public ProgressTracker(int totalIterationsCount, int iterationsPerDot, int dotsPerLine, boolean isDebugLoggingEnabled) {
		this.totalIterationsCount = totalIterationsCount;
		this.iterationsPerDot = iterationsPerDot;
		this.dotsPerLine = dotsPerLine;
		this.suppressTracking = isDebugLoggingEnabled;
	}


	public void updateForIteration() {
		if (this.suppressTracking) {
			return;
		}
		if (iteration++ == 0) {
			this.startTime = LocalDateTime.now();
			System.out.print("++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++\n");
			return;
		}
		Assert.notNull(startTime, "Please start with iteration 0 to initialize the startTime");
		if (iteration % dotsPerLine * iterationsPerDot == 0) {
			String display = getStatus(iteration);
			System.out.print(".\t" + iteration + " of " + totalIterationsCount + "\t(" + display + ")\n");
		}
		else if (iteration % iterationsPerDot == 0) {
			System.out.print(".");
		}
	}

	private String getStatus(int iteration) {
		long elapsed = ChronoUnit.MILLIS.between(this.startTime, LocalDateTime.now());
		BigDecimal millisPerIteration = new BigDecimal(elapsed).divide(new BigDecimal(iteration), 2, BigDecimal.ROUND_UP);
		BigDecimal remain = new BigDecimal(totalIterationsCount - iteration);
		Duration remainDuration = Duration.ofMillis(remain.multiply(millisPerIteration).longValue());
		return millisPerIteration + " millis per iteration" +
				", Remain: " + formatDuration(remainDuration) +
				", Elapsed: " + formatDuration(Duration.ofMillis(elapsed));
	}

	private String formatDuration(Duration remainingDuration) {
		long hours = remainingDuration.toHours();
		if (hours > 0) {
			return hours + " hrs " + remainingDuration.minusHours(hours).toMinutes() + "min";
		}
		long minutes = remainingDuration.toMinutes();
		if (minutes > 0) {
			return minutes + " min " + remainingDuration.minusMinutes(minutes).getSeconds() + " sec";
		}
		long seconds = remainingDuration.getSeconds();
		if (seconds > 0) {
			return seconds + " sec " + remainingDuration.minusSeconds(seconds).toMillis() + " mill";
		}
		return remainingDuration.toMillis() + " mil";
	}

	public void stopProgress() {
		if (this.startTime == null || this.suppressTracking) {
			return;
		}
		System.out.println(".X\nDone\n" + getStatus(iteration) +
				"\n++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++");
		this.startTime = null;
		this.iteration = 0;
	}

}
