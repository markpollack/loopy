package io.github.markpollack.loopy.agent.loop;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class AgentLoopAdvisorCostTest {

	@ParameterizedTest
	@CsvSource({ "claude-haiku-4-5-20251001, 100000, 50000, 0.28", "claude-sonnet-4-20250514, 100000, 50000, 1.05",
			"claude-opus-4-20250514, 100000, 50000, 5.25", "unknown-model, 100000, 50000, 1.05" })
	void estimateCostUsesModelSpecificRates(String model, long inputTokens, long outputTokens, double expectedCost) {
		double cost = AgentLoopAdvisor.estimateCost(inputTokens, outputTokens, model);
		assertThat(cost).isEqualTo(expectedCost, org.assertj.core.api.Assertions.within(0.01));
	}

	@Test
	void estimateCostWithNullModelDefaultsToSonnet() {
		double cost = AgentLoopAdvisor.estimateCost(100_000, 50_000, null);
		// Sonnet: 100K * $3/M + 50K * $15/M = $0.30 + $0.75 = $1.05
		assertThat(cost).isEqualTo(1.05, org.assertj.core.api.Assertions.within(0.01));
	}

	@Test
	void estimateCostHaikuIsDramaticallyLowerThanFlatRate() {
		// The bug: flat rate was tokens * $0.000006 (~$6/M average)
		// 844K tokens at flat rate = $5.07
		long totalTokens = 844_000;
		double oldFlatRate = totalTokens * 0.000006;
		assertThat(oldFlatRate).isGreaterThan(5.0);

		// Haiku with realistic split (mostly input for agent loops): ~750K in, ~94K out
		double haikuCost = AgentLoopAdvisor.estimateCost(750_000, 94_000, "claude-haiku-4-5-20251001");
		// 750K * $0.80/M + 94K * $4.00/M = $0.60 + $0.376 = $0.976
		assertThat(haikuCost).isLessThan(1.0);
		assertThat(haikuCost).isGreaterThan(0.0);
	}

}
