package io.github.markpollack.loopy.boot;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BootBriefTest {

	@Test
	void recordHoldsAllFields() {
		var brief = new BootBrief("products-api", "com.acme", "products-api", "com.acme.productsapi",
				"spring-boot-rest", "21");

		assertThat(brief.name()).isEqualTo("products-api");
		assertThat(brief.groupId()).isEqualTo("com.acme");
		assertThat(brief.artifactId()).isEqualTo("products-api");
		assertThat(brief.packageName()).isEqualTo("com.acme.productsapi");
		assertThat(brief.template()).isEqualTo("spring-boot-rest");
		assertThat(brief.javaVersion()).isEqualTo("21");
	}

	@Test
	void templateConstantsMatch() {
		assertThat(BootBrief.TEMPLATE_PACKAGE).isEqualTo("com.example.app");
		assertThat(BootBrief.TEMPLATE_GROUP_ID).isEqualTo("com.example");
		assertThat(BootBrief.TEMPLATE_ARTIFACT_ID).isEqualTo("app");
	}

}
