package io.github.markpollack.loopy.boot;

import io.github.markpollack.workflow.patterns.graph.GraphCompositionStrategy;
import io.github.markpollack.workflow.patterns.graph.GraphResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GraphCompositionStrategyTest {

	@Test
	void threeNodeGraphExecutesInOrder() {
		GraphCompositionStrategy<String, String> graph = GraphCompositionStrategy.<String, String>builder("test-graph")
			.node("a", (ctx, input) -> "A:" + input)
			.node("b", (ctx, input) -> "B:" + input)
			.node("c", (ctx, input) -> "DONE")
			.startNode("a")
			.finishNode("c")
			.edge("a")
			.to("b")
			.and()
			.edge("b")
			.to("c")
			.build();

		GraphResult<String> result = graph.execute("hello");

		assertThat(result.isSuccess()).isTrue();
		assertThat(result.output()).isEqualTo("DONE");
		assertThat(result.pathTaken()).isEqualTo(List.of("a", "b", "c"));
	}

}
